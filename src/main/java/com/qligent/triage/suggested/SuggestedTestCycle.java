package com.qligent.triage.suggested;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SuggestedTestCycle(
		String suggestedTestCycleKey,
		String suggestedTestCycleName,
		String suggestedTestCycleStatus,
		String projectKey,
		long createdAt,
		String artifactFolder,
		String sortField,
		String sortDirection,
		List<SuggestedTestCaseEntry> tests
) {

	public static final String DEFAULT_SORT_FIELD = "aiSort";
	public static final String DEFAULT_SORT_DIRECTION = "DESC";

	public SuggestedTestCycle withStatus(SuggestedTestCycleStatus status) {
		return new SuggestedTestCycle(
				suggestedTestCycleKey,
				suggestedTestCycleName,
				status.label(),
				projectKey,
				createdAt,
				artifactFolder,
				sortField,
				sortDirection,
				tests);
	}

	public SuggestedTestCycle withSortState(String field, String direction) {
		return new SuggestedTestCycle(
				suggestedTestCycleKey,
				suggestedTestCycleName,
				suggestedTestCycleStatus,
				projectKey,
				createdAt,
				artifactFolder,
				field,
				direction,
				tests);
	}

	public List<String> testCaseKeys() {
		if (tests == null) {
			return List.of();
		}
		return tests.stream().map(SuggestedTestCaseEntry::key).toList();
	}

	public static SuggestedTestCycle create(
			String cycleKey,
			String cycleName,
			String projectKey,
			long createdAt,
			String artifactFolder,
			List<SuggestedTestCaseEntry> tests) {
		return new SuggestedTestCycle(
				cycleKey,
				cycleName,
				SuggestedTestCycleStatus.WAITING.label(),
				projectKey,
				createdAt,
				artifactFolder,
				DEFAULT_SORT_FIELD,
				DEFAULT_SORT_DIRECTION,
				new ArrayList<>(tests));
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SuggestedTestCaseEntry(
			String key,
			String name,
			Map<String, Double> weights,
			TestMetrics metrics,
			double aiSort
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TestMetrics(
			int executionsCount,
			int failuresCount,
			String lastExecutionStatus,
			long executionTimeMs
	) {
	}
}
