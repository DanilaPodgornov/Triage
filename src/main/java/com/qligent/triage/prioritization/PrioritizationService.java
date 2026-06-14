package com.qligent.triage.prioritization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.llm.AiSortValidator;
import com.qligent.triage.llm.LlmClient;
import com.qligent.triage.llm.WeightValidator;
import com.qligent.triage.llm.dto.AiSortResponse;
import com.qligent.triage.model.Project;
import com.qligent.triage.suggested.SuggestedTestCycle;
import com.qligent.triage.suggested.SuggestedTestCycleStorage;
import com.qligent.triage.zephyr.ZephyrScaleClient;
import com.qligent.triage.zephyr.dto.PriorityDto;
import com.qligent.triage.zephyr.dto.StatusDto;
import com.qligent.triage.zephyr.dto.TestCaseDetailDto;
import com.qligent.triage.zephyr.dto.TestExecutionDto;
import com.qligent.triage.zephyr.dto.ZephyrPage;

@Service
public class PrioritizationService {

	private static final Logger log = LoggerFactory.getLogger(PrioritizationService.class);
	private static final int EXECUTION_PAGE_SIZE = 100;

	private final PrioritizationArtifactService artifactService;
	private final TestCasePromptRenderer promptRenderer;
	private final LlmClient llmClient;
	private final WeightValidator weightValidator;
	private final AiSortValidator aiSortValidator;
	private final SuggestedTestCycleStorage storage;
	private final ZephyrScaleClient zephyrScaleClient;
	private final ObjectMapper objectMapper;

	public PrioritizationService(
			PrioritizationArtifactService artifactService,
			TestCasePromptRenderer promptRenderer,
			LlmClient llmClient,
			WeightValidator weightValidator,
			AiSortValidator aiSortValidator,
			SuggestedTestCycleStorage storage,
			ZephyrScaleClient zephyrScaleClient,
			ObjectMapper objectMapper) {
		this.artifactService = artifactService;
		this.promptRenderer = promptRenderer;
		this.llmClient = llmClient;
		this.weightValidator = weightValidator;
		this.aiSortValidator = aiSortValidator;
		this.storage = storage;
		this.zephyrScaleClient = zephyrScaleClient;
		this.objectMapper = objectMapper;
	}

	public SuggestedTestCycle prioritize(Project project, List<String> testCaseKeys) {
		return prioritize(project, testCaseKeys, null);
	}

	public SuggestedTestCycle prioritize(
			Project project,
			List<String> testCaseKeys,
			PrioritizationProgressListener progressListener) {
		if (testCaseKeys == null || testCaseKeys.size() < 2) {
			throw new IllegalArgumentException("At least two test cases are required for prioritization.");
		}

		String projectKey = zephyrScaleClient.resolveProjectKey(project);
		int sequence = artifactService.resolveNextSequence(projectKey);
		var artifactFolder = artifactService.createArtifactFolder(projectKey, sequence);

		Map<Long, String> statusNames = loadStatusNames(projectKey);
		Map<Long, String> priorityNames = loadPriorityNames(projectKey);

		List<SuggestedTestCycle.SuggestedTestCaseEntry> entries = new ArrayList<>();
		int totalTests = testCaseKeys.size();
		for (int index = 0; index < testCaseKeys.size(); index++) {
			String testCaseKey = testCaseKeys.get(index);
			entries.add(processTestCase(
					testCaseKey,
					artifactFolder.path(),
					statusNames,
					priorityNames));
			notifyTestCompleted(progressListener, index + 1, totalTests);
		}

		notifyAiSortStarted(progressListener);
		String aiSortPayload = buildAiSortPayload(entries);
		AiSortResponse aiSortResponse = llmClient.requestAiSort(aiSortPayload);
		Map<String, Double> aiSortByKey = aiSortValidator.validateAndNormalize(
				testCaseKeys,
				toAiSortMap(aiSortResponse, testCaseKeys));

		List<SuggestedTestCycle.SuggestedTestCaseEntry> finalEntries = entries.stream()
				.map(entry -> new SuggestedTestCycle.SuggestedTestCaseEntry(
						entry.key(),
						entry.name(),
						entry.weights(),
						entry.metrics(),
						aiSortByKey.getOrDefault(entry.key(), 0.0)))
				.sorted(Comparator.comparingDouble(SuggestedTestCycle.SuggestedTestCaseEntry::aiSort).reversed())
				.toList();

		String cycleName = aiSortResponse.suggestedTestCycleName();
		if (cycleName == null || cycleName.isBlank()) {
			cycleName = "Suggested cycle " + artifactFolder.cycleKey();
		}

		var cycle = SuggestedTestCycle.create(
				artifactFolder.cycleKey(),
				cycleName,
				projectKey,
				artifactFolder.createdAtMillis(),
				artifactFolder.folderName(),
				finalEntries);
		storage.save(cycle);
		log.info(
				"Saved suggested test cycle to {}: key={}, name={}, tests={}",
				storage.cyclesDirectory().resolve(cycle.suggestedTestCycleKey() + ".json"),
				cycle.suggestedTestCycleKey(),
				cycleName,
				finalEntries.size());
		return cycle;
	}

	private SuggestedTestCycle.SuggestedTestCaseEntry processTestCase(
			String testCaseKey,
			java.nio.file.Path artifactFolder,
			Map<Long, String> statusNames,
			Map<Long, String> priorityNames) {
		TestCaseDetailDto testCase = zephyrScaleClient.getTestCase(testCaseKey);
		List<TestExecutionDto> executions = loadAllExecutions(testCaseKey);

		String promptMarkdown = promptRenderer.render(testCase, executions, statusNames, priorityNames);
		artifactService.writePromptFile(artifactFolder, testCaseKey, promptMarkdown);

		Map<String, Double> weights = weightValidator.validateAndNormalize(
				llmClient.requestAttributeWeights(promptMarkdown));

		try {
			artifactService.writeResponseFile(
					artifactFolder,
					testCaseKey,
					objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(weights));
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize attribute weights response.", exception);
		}

		SuggestedTestCycle.TestMetrics metrics = buildMetrics(executions, statusNames);
		return new SuggestedTestCycle.SuggestedTestCaseEntry(
				testCaseKey,
				testCase.name(),
				weights,
				metrics,
				0.0);
	}

	private SuggestedTestCycle.TestMetrics buildMetrics(
			List<TestExecutionDto> executions,
			Map<Long, String> statusNames) {
		int failedExecutionsCount = (int) executions.stream()
				.filter(execution -> isFailedExecution(execution, statusNames))
				.count();
		int failuresCount = (int) executions.stream()
				.filter(execution -> execution.links() != null
						&& execution.links().issues() != null
						&& !execution.links().issues().isEmpty())
				.count();
		TestExecutionDto latest = executions.stream()
				.max(Comparator.comparing(
						TestExecutionDto::actualEndDate,
						Comparator.nullsLast(String::compareTo)))
				.orElse(null);
		String lastStatus = "-";
		if (latest != null && latest.testExecutionStatus() != null) {
			lastStatus = statusNames.getOrDefault(
					latest.testExecutionStatus().id(),
					"Status-" + latest.testExecutionStatus().id());
		}
		long totalExecutionTime = executions.stream()
				.mapToLong(execution -> execution.executionTime() == null ? 0L : execution.executionTime())
				.sum();
		return new SuggestedTestCycle.TestMetrics(
				executions.size(),
				failuresCount,
				failedExecutionsCount,
				lastStatus,
				totalExecutionTime);
	}

	private static boolean isFailedExecution(TestExecutionDto execution, Map<Long, String> statusNames) {
		if (execution.testExecutionStatus() == null) {
			return false;
		}
		String statusName = statusNames.getOrDefault(execution.testExecutionStatus().id(), "").toLowerCase();
		return statusName.contains("fail");
	}

	private static void notifyTestCompleted(
			PrioritizationProgressListener progressListener,
			int completedTests,
			int totalTests) {
		if (progressListener != null) {
			progressListener.onTestResponseCompleted(completedTests, totalTests);
		}
	}

	private static void notifyAiSortStarted(PrioritizationProgressListener progressListener) {
		if (progressListener != null) {
			progressListener.onAiSortStarted();
		}
	}

	private String buildAiSortPayload(List<SuggestedTestCycle.SuggestedTestCaseEntry> entries) {
		try {
			return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize AI sort payload.", exception);
		}
	}

	private Map<String, Double> toAiSortMap(AiSortResponse response, List<String> expectedKeys) {
		Map<String, Double> map = new LinkedHashMap<>();
		if (response.tests() != null) {
			for (AiSortResponse.AiSortTestEntry entry : response.tests()) {
				map.put(entry.key(), entry.aiSort());
			}
		}
		for (String key : expectedKeys) {
			map.putIfAbsent(key, 0.0);
		}
		return map;
	}

	private Map<Long, String> loadStatusNames(String projectKey) {
		Map<Long, String> names = new HashMap<>();
		int startAt = 0;
		boolean isLast;
		do {
			ZephyrPage<StatusDto> page = zephyrScaleClient.getStatuses(projectKey, startAt, EXECUTION_PAGE_SIZE);
			if (page.values() != null) {
				for (StatusDto status : page.values()) {
					names.put(status.id(), status.name());
				}
			}
			isLast = page.isLast();
			startAt += page.maxResults() > 0 ? page.maxResults() : EXECUTION_PAGE_SIZE;
		} while (!isLast);
		return names;
	}

	private Map<Long, String> loadPriorityNames(String projectKey) {
		ZephyrPage<PriorityDto> page = zephyrScaleClient.getPriorities(projectKey);
		if (page.values() == null) {
			return Map.of();
		}
		return page.values().stream()
				.collect(Collectors.toMap(PriorityDto::id, PriorityDto::name, (left, right) -> left));
	}

	private List<TestExecutionDto> loadAllExecutions(String testCaseKey) {
		List<TestExecutionDto> allExecutions = new ArrayList<>();
		int startAt = 0;
		boolean isLast;
		do {
			ZephyrPage<TestExecutionDto> page = zephyrScaleClient.getTestExecutions(
					testCaseKey, startAt, EXECUTION_PAGE_SIZE);
			if (page.values() != null) {
				allExecutions.addAll(page.values());
			}
			isLast = page.isLast();
			startAt += page.maxResults() > 0 ? page.maxResults() : EXECUTION_PAGE_SIZE;
		} while (!isLast);
		return allExecutions;
	}
}
