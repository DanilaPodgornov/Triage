package com.qligent.triage.zephyr.dto;

public record CreateTestCycleRequest(
		String projectKey,
		String name,
		String plannedStartDate,
		String plannedEndDate
) {
}
