package com.qligent.triage.zephyr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PriorityDto(
		long id,
		String name
) {
}
