package com.qligent.triage.prioritization;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TestCaseEnrichment(
		String key,
		String name,
		String objective,
		List<String> labels,
		Long priorityId,
		Long statusId,
		List<ExecutionSummary> recentExecutions
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ExecutionSummary(
			String key,
			String actualEndDate,
			long executionStatusId,
			Long testCycleId
	) {
	}

	public static TestCaseEnrichmentBuilder builder(String key) {
		return new TestCaseEnrichmentBuilder(key);
	}

	public static final class TestCaseEnrichmentBuilder {
		private final String key;
		private String name;
		private String objective;
		private List<String> labels = List.of();
		private Long priorityId;
		private Long statusId;
		private List<ExecutionSummary> recentExecutions = new ArrayList<>();

		private TestCaseEnrichmentBuilder(String key) {
			this.key = key;
		}

		public TestCaseEnrichmentBuilder name(String name) {
			this.name = name;
			return this;
		}

		public TestCaseEnrichmentBuilder objective(String objective) {
			this.objective = objective;
			return this;
		}

		public TestCaseEnrichmentBuilder labels(List<String> labels) {
			this.labels = labels;
			return this;
		}

		public TestCaseEnrichmentBuilder priorityId(Long priorityId) {
			this.priorityId = priorityId;
			return this;
		}

		public TestCaseEnrichmentBuilder statusId(Long statusId) {
			this.statusId = statusId;
			return this;
		}

		public TestCaseEnrichmentBuilder recentExecutions(List<ExecutionSummary> recentExecutions) {
			this.recentExecutions = recentExecutions;
			return this;
		}

		public TestCaseEnrichment build() {
			return new TestCaseEnrichment(key, name, objective, labels, priorityId, statusId, recentExecutions);
		}
	}

}
