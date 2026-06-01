package com.qligent.triage.zephyr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TestCaseDto(
		long id,
		String key,
		String name,
		PriorityRef priority,
		StatusRef status,
		FolderRef folder
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PriorityRef(long id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record StatusRef(long id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record FolderRef(long id) {
	}

}
