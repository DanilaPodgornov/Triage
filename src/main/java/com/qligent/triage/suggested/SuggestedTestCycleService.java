package com.qligent.triage.suggested;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.qligent.triage.model.Project;
import com.qligent.triage.web.model.SuggestedCyclesPageView;
import com.qligent.triage.zephyr.ZephyrScaleClient;
import com.qligent.triage.zephyr.dto.CreateTestCycleRequest;
import com.qligent.triage.zephyr.dto.CreateTestExecutionRequest;
import com.qligent.triage.zephyr.dto.TestCycleDto;

@Service
public class SuggestedTestCycleService {

	private static final Logger log = LoggerFactory.getLogger(SuggestedTestCycleService.class);

	public static final String ZEPHYR_INITIAL_EXECUTION_STATUS = "Not Executed";

	private final SuggestedTestCycleStorage storage;
	private final ZephyrScaleClient zephyrScaleClient;

	public SuggestedTestCycleService(SuggestedTestCycleStorage storage, ZephyrScaleClient zephyrScaleClient) {
		this.storage = storage;
		this.zephyrScaleClient = zephyrScaleClient;
	}

	public List<SuggestedTestCycle> listForProject(Project project) {
		String projectKey = zephyrScaleClient.resolveProjectKey(project);
		return storage.findByProjectPrefix(projectKey);
	}

	public SuggestedCyclesPageView listPageForProject(Project project, int page, int pageSize) {
		List<SuggestedTestCycle> all = listForProject(project);
		int totalItems = all.size();
		int totalPages = totalItems == 0 ? 1 : (int) Math.ceil((double) totalItems / pageSize);
		int safePage = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
		int fromIndex = Math.min(safePage * pageSize, totalItems);
		int toIndex = Math.min(fromIndex + pageSize, totalItems);
		List<SuggestedTestCycle> cycles = all.subList(fromIndex, toIndex);
		int rangeFrom = totalItems == 0 ? 0 : fromIndex + 1;
		int rangeTo = totalItems == 0 ? 0 : toIndex;
		boolean lastPage = totalItems == 0 || safePage >= totalPages - 1;
		return new SuggestedCyclesPageView(cycles, safePage, pageSize, rangeFrom, rangeTo, totalItems, totalPages, lastPage);
	}

	public SuggestedTestCycle getByKey(String cycleKey) {
		return storage.findByKey(cycleKey)
				.orElseThrow(() -> new IllegalArgumentException("Suggested test cycle not found: " + cycleKey));
	}

	public Path getCycleJsonFile(String cycleKey) {
		getByKey(cycleKey);
		Path file = storage.cycleJsonPath(cycleKey);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("Suggested test cycle JSON file not found: " + cycleKey);
		}
		return file;
	}

	public void saveSortState(String cycleKey, String sortField, String sortDirection, String suggestedTestCycleName) {
		var existing = getByKey(cycleKey);
		String normalizedField = sortField == null || sortField.isBlank()
				? SuggestedTestCycle.DEFAULT_SORT_FIELD
				: sortField.trim();
		String normalizedDirection = normalizeSortDirection(sortDirection);
		var updated = existing.withSortState(normalizedField, normalizedDirection);
		if (suggestedTestCycleName != null) {
			String trimmedName = suggestedTestCycleName.trim();
			if (!trimmedName.isEmpty()) {
				updated = updated.withName(trimmedName);
			}
		}
		storage.update(updated);
	}

	private static String normalizeSortDirection(String sortDirection) {
		if (sortDirection != null && sortDirection.trim().equalsIgnoreCase("ASC")) {
			return "ASC";
		}
		return "DESC";
	}

	public String sendToZephyr(String cycleKey) {
		var existing = getByKey(cycleKey);
		if (SuggestedTestCycleStatus.SENT.label().equalsIgnoreCase(existing.suggestedTestCycleStatus())) {
			throw new IllegalStateException("Suggested test cycle already sent: " + cycleKey);
		}
		if (existing.tests() == null || existing.tests().isEmpty()) {
			throw new IllegalStateException("Suggested test cycle has no tests to send: " + cycleKey);
		}

		CreateTestCycleRequest createCycleRequest = new CreateTestCycleRequest(
				existing.projectKey(),
				ZephyrCycleNaming.formatCycleName(existing.suggestedTestCycleName()),
				ZephyrCycleNaming.plannedStartDateUtc(),
				ZephyrCycleNaming.plannedEndDateUtc());
		TestCycleDto createdCycle = zephyrScaleClient.createTestCycle(createCycleRequest);
		if (createdCycle == null || createdCycle.key() == null || createdCycle.key().isBlank()) {
			throw new IllegalStateException("Zephyr Scale did not return a test cycle key.");
		}
		String zephyrTestCycleKey = createdCycle.key();

		List<SuggestedTestCycle.SuggestedTestCaseEntry> orderedTests = SuggestedTestCaseSorter.sort(
				existing.tests(),
				existing.sortField(),
				existing.sortDirection());
		for (SuggestedTestCycle.SuggestedTestCaseEntry test : orderedTests) {
			zephyrScaleClient.createTestExecution(new CreateTestExecutionRequest(
					existing.projectKey(),
					test.key(),
					zephyrTestCycleKey,
					ZEPHYR_INITIAL_EXECUTION_STATUS));
		}

		storage.update(existing.withZephyrTestCycleKey(zephyrTestCycleKey).withStatus(SuggestedTestCycleStatus.SENT));
		log.info(
				"Suggested test cycle {} sent to Zephyr Scale as {} with {} tests.",
				cycleKey,
				zephyrTestCycleKey,
				orderedTests.size());
		return zephyrTestCycleKey;
	}

}
