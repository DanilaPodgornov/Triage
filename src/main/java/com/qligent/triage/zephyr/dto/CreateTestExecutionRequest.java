package com.qligent.triage.zephyr.dto;

public record CreateTestExecutionRequest(
		String projectKey,
		String testCaseKey,
		String testCycleKey,
		String statusName
) {
}
