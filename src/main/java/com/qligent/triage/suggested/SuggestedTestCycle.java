package com.qligent.triage.suggested;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SuggestedTestCycle(
		String suggestedTestCycleKey,
		String suggestedTestCycleName,
		String suggestedTestCycleStatus,
		String projectKey,
		long createdAt,
		String artifactFolder,
		String llm,
		String aisortPromptVersion,
		String weightsPromptVersion,
		String sortField,
		String sortDirection,
		String zephyrTestCycleKey,
		List<SuggestedTestCaseEntry> tests
) {

	public static final String DEFAULT_SORT_FIELD = "aiSort";
	public static final String DEFAULT_SORT_DIRECTION = "DESC";
	public static final String LEGACY_LLM = "qwen2.5-3b_ctx8192";
	public static final String LEGACY_PROMPT_VERSION = "0.1.0";

	public SuggestedTestCycle withStatus(SuggestedTestCycleStatus status) {
		return new SuggestedTestCycle(
				suggestedTestCycleKey,
				suggestedTestCycleName,
				status.label(),
				projectKey,
				createdAt,
				artifactFolder,
				llm,
				aisortPromptVersion,
				weightsPromptVersion,
				sortField,
				sortDirection,
				zephyrTestCycleKey,
				tests);
	}

	public SuggestedTestCycle withZephyrTestCycleKey(String key) {
		return new SuggestedTestCycle(
				suggestedTestCycleKey,
				suggestedTestCycleName,
				suggestedTestCycleStatus,
				projectKey,
				createdAt,
				artifactFolder,
				llm,
				aisortPromptVersion,
				weightsPromptVersion,
				sortField,
				sortDirection,
				key,
				tests);
	}

	public SuggestedTestCycle withName(String name) {
		return new SuggestedTestCycle(
				suggestedTestCycleKey,
				name,
				suggestedTestCycleStatus,
				projectKey,
				createdAt,
				artifactFolder,
				llm,
				aisortPromptVersion,
				weightsPromptVersion,
				sortField,
				sortDirection,
				zephyrTestCycleKey,
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
				llm,
				aisortPromptVersion,
				weightsPromptVersion,
				field,
				direction,
				zephyrTestCycleKey,
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
		return create(
				cycleKey,
				cycleName,
				projectKey,
				createdAt,
				artifactFolder,
				LEGACY_LLM,
				LEGACY_PROMPT_VERSION,
				LEGACY_PROMPT_VERSION,
				tests);
	}

	public static SuggestedTestCycle create(
			String cycleKey,
			String cycleName,
			String projectKey,
			long createdAt,
			String artifactFolder,
			String llm,
			String aisortPromptVersion,
			String weightsPromptVersion,
			List<SuggestedTestCaseEntry> tests) {
		return new SuggestedTestCycle(
				cycleKey,
				cycleName,
				SuggestedTestCycleStatus.WAITING.label(),
				projectKey,
				createdAt,
				artifactFolder,
				llm,
				aisortPromptVersion,
				weightsPromptVersion,
				DEFAULT_SORT_FIELD,
				DEFAULT_SORT_DIRECTION,
				null,
				new ArrayList<>(tests));
	}

	@JsonCreator
	public static SuggestedTestCycle fromJson(
			@JsonProperty("suggestedTestCycleKey") String suggestedTestCycleKey,
			@JsonProperty("suggestedTestCycleName") String suggestedTestCycleName,
			@JsonProperty("suggestedTestCycleStatus") String suggestedTestCycleStatus,
			@JsonProperty("projectKey") String projectKey,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("artifactFolder") String artifactFolder,
			@JsonProperty(value = "llm", required = false) String llm,
			@JsonProperty(value = "aisortPromptVersion", required = false) String aisortPromptVersion,
			@JsonProperty(value = "weightsPromptVersion", required = false) String weightsPromptVersion,
			@JsonProperty("sortField") String sortField,
			@JsonProperty("sortDirection") String sortDirection,
			@JsonProperty(value = "zephyrTestCycleKey", required = false) String zephyrTestCycleKey,
			@JsonProperty("tests") List<SuggestedTestCaseEntry> tests) {
		return new SuggestedTestCycle(
				suggestedTestCycleKey,
				suggestedTestCycleName,
				suggestedTestCycleStatus,
				projectKey,
				createdAt,
				artifactFolder,
				llm != null ? llm : LEGACY_LLM,
				aisortPromptVersion != null ? aisortPromptVersion : LEGACY_PROMPT_VERSION,
				weightsPromptVersion != null ? weightsPromptVersion : LEGACY_PROMPT_VERSION,
				sortField,
				sortDirection,
				zephyrTestCycleKey,
				tests);
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
			int failedExecutionsCount,
			double failedExecutionsRatio,
			String lastExecutionStatus,
			long executionTimeMs
	) {
		public static double computeFailedExecutionsRatio(int failedExecutionsCount, int executionsCount) {
			if (executionsCount <= 0) {
				return 0.0;
			}
			return (double) failedExecutionsCount / executionsCount;
		}

		@JsonCreator
		public static TestMetrics fromJson(
				@JsonProperty("executionsCount") int executionsCount,
				@JsonProperty("failuresCount") int failuresCount,
				@JsonProperty(value = "failedExecutionsCount", required = false) Integer failedExecutionsCount,
				@JsonProperty(value = "failedExecutionsRatio", required = false) Double failedExecutionsRatio,
				@JsonProperty("lastExecutionStatus") String lastExecutionStatus,
				@JsonProperty("executionTimeMs") long executionTimeMs) {
			int failedCount = failedExecutionsCount != null ? failedExecutionsCount : 0;
			double ratio = failedExecutionsRatio != null
					? failedExecutionsRatio
					: computeFailedExecutionsRatio(failedCount, executionsCount);
			return new TestMetrics(
					executionsCount,
					failuresCount,
					failedCount,
					ratio,
					lastExecutionStatus,
					executionTimeMs);
		}
	}
}
