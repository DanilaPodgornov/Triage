package com.qligent.triage.zephyr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TestCaseDto(
		long id,
		String key,
		String name,
		PriorityRef priority
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PriorityRef(long id) {
	}

}
