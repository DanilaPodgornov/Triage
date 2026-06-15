package com.qligent.triage.suggested;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.qligent.triage.model.Project;
import com.qligent.triage.zephyr.ZephyrScaleClient;

@Service
public class SuggestedTestCycleService {

	private static final Logger log = LoggerFactory.getLogger(SuggestedTestCycleService.class);

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

	public SuggestedTestCycle getByKey(String cycleKey) {
		return storage.findByKey(cycleKey)
				.orElseThrow(() -> new IllegalArgumentException("Suggested test cycle not found: " + cycleKey));
	}

	public void saveSortState(String cycleKey, String sortField, String sortDirection) {
		var existing = getByKey(cycleKey);
		String normalizedField = sortField == null || sortField.isBlank()
				? SuggestedTestCycle.DEFAULT_SORT_FIELD
				: sortField.trim();
		String normalizedDirection = normalizeSortDirection(sortDirection);
		storage.update(existing.withSortState(normalizedField, normalizedDirection));
	}

	private static String normalizeSortDirection(String sortDirection) {
		if (sortDirection != null && sortDirection.trim().equalsIgnoreCase("ASC")) {
			return "ASC";
		}
		return "DESC";
	}

	public void sendToZephyr(String cycleKey) {
		var existing = getByKey(cycleKey);
		log.info("Suggested test cycle {} successfully sent to Zephyr Scale (stub).", cycleKey);
		storage.update(existing.withStatus(SuggestedTestCycleStatus.SENT));
	}

}
