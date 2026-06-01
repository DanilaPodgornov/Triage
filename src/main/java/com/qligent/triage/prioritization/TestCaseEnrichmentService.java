package com.qligent.triage.prioritization;

import java.util.List;

import org.springframework.stereotype.Service;

import com.qligent.triage.zephyr.ZephyrScaleClient;

@Service
public class TestCaseEnrichmentService {

	private static final int EXECUTION_HISTORY_LIMIT = 10;

	private final ZephyrScaleClient zephyrScaleClient;

	public TestCaseEnrichmentService(ZephyrScaleClient zephyrScaleClient) {
		this.zephyrScaleClient = zephyrScaleClient;
	}

	public TestCaseEnrichment enrich(String testCaseKey) {
		var detail = zephyrScaleClient.getTestCase(testCaseKey);
		var executionsPage = zephyrScaleClient.getTestExecutions(testCaseKey, 0, EXECUTION_HISTORY_LIMIT);

		List<TestCaseEnrichment.ExecutionSummary> executions = executionsPage.values() == null
				? List.of()
				: executionsPage.values().stream()
						.map(execution -> new TestCaseEnrichment.ExecutionSummary(
								execution.key(),
								execution.actualEndDate(),
								execution.testExecutionStatus() != null ? execution.testExecutionStatus().id() : 0L,
								execution.testCycle() != null ? execution.testCycle().id() : null))
						.toList();

		return TestCaseEnrichment.builder(testCaseKey)
				.name(detail.name())
				.objective(detail.objective())
				.labels(detail.labels() == null ? List.of() : detail.labels())
				.priorityId(detail.priority() != null ? detail.priority().id() : null)
				.statusId(detail.status() != null ? detail.status().id() : null)
				.recentExecutions(executions)
				.build();
	}

}
