package com.qligent.triage.prioritization;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.llm.LlmClient;
import com.qligent.triage.llm.dto.LlmPrioritizationResult;
import com.qligent.triage.model.Project;
import com.qligent.triage.suggested.SuggestedTestCycle;
import com.qligent.triage.suggested.SuggestedTestCycleStorage;
import com.qligent.triage.zephyr.ZephyrScaleClient;

@Service
public class PrioritizationService {

	private final TestCaseEnrichmentService enrichmentService;
	private final LlmClient llmClient;
	private final SuggestedTestCycleStorage storage;
	private final ZephyrScaleClient zephyrScaleClient;
	private final ObjectMapper objectMapper;

	public PrioritizationService(
			TestCaseEnrichmentService enrichmentService,
			LlmClient llmClient,
			SuggestedTestCycleStorage storage,
			ZephyrScaleClient zephyrScaleClient,
			ObjectMapper objectMapper) {
		this.enrichmentService = enrichmentService;
		this.llmClient = llmClient;
		this.storage = storage;
		this.zephyrScaleClient = zephyrScaleClient;
		this.objectMapper = objectMapper;
	}

	public SuggestedTestCycle prioritize(Project project, List<String> testCaseKeys) {
		if (testCaseKeys == null || testCaseKeys.size() < 2) {
			throw new IllegalArgumentException("At least two test cases are required for prioritization.");
		}

		String projectKey = zephyrScaleClient.resolveProjectKey(project);
		List<TestCaseEnrichment> enrichments = testCaseKeys.stream()
				.map(enrichmentService::enrich)
				.toList();

		String promptPayload;
		try {
			promptPayload = objectMapper.writeValueAsString(enrichments);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize test case data for LLM.", exception);
		}

		LlmPrioritizationResult llmResult = llmClient.prioritizeTestCases(promptPayload, testCaseKeys);
		List<String> orderedKeys = validateAndNormalizeOrder(testCaseKeys, llmResult.testCases());

		// Cycle key is always generated server-side (LLM may return placeholders like PROJECT-SR1).
		String cycleKey = storage.nextSuggestedCycleKey(projectKey);

		String cycleName = llmResult.suggestedTestCycleName();
		if (cycleName == null || cycleName.isBlank()) {
			cycleName = "Suggested cycle " + cycleKey;
		}

		var cycle = SuggestedTestCycle.create(cycleKey, cycleName, orderedKeys);
		storage.add(cycle);
		return cycle;
	}

	private List<String> validateAndNormalizeOrder(List<String> inputKeys, List<String> llmOrderedKeys) {
		Set<String> expected = new HashSet<>(inputKeys);
		List<String> result = new ArrayList<>();

		if (llmOrderedKeys != null) {
			for (String key : llmOrderedKeys) {
				if (expected.contains(key) && !result.contains(key)) {
					result.add(key);
				}
			}
		}

		for (String key : inputKeys) {
			if (!result.contains(key)) {
				result.add(key);
			}
		}
		return result;
	}

}
