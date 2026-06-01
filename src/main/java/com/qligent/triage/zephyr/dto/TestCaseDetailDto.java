package com.qligent.triage.zephyr.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TestCaseDetailDto(
		long id,
		String key,
		String name,
		String objective,
		String precondition,
		Long estimatedTime,
		List<String> labels,
		PriorityRef priority,
		StatusRef status
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PriorityRef(long id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record StatusRef(long id) {
	}

}
