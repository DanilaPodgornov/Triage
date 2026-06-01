package com.qligent.triage.zephyr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TestExecutionDto(
		long id,
		String key,
		String actualEndDate,
		Long executionTime,
		ExecutionStatusRef testExecutionStatus,
		TestCycleRef testCycle
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ExecutionStatusRef(long id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TestCycleRef(long id) {
	}

}
