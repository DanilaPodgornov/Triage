package com.qligent.triage.zephyr.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TestExecutionDto(
		long id,
		String key,
		String actualEndDate,
		Long executionTime,
		String comment,
		ExecutionStatusRef testExecutionStatus,
		TestCycleRef testCycle,
		ExecutionLinks links
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ExecutionStatusRef(long id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TestCycleRef(long id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ExecutionLinks(
			String self,
			List<TestExecutionIssueLinkDto> issues
	) {
	}

}
