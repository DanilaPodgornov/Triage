package com.qligent.triage.llm.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiSortResponse(
		String suggestedTestCycleName,
		List<AiSortTestEntry> tests
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AiSortTestEntry(
			String key,
			double aiSort
	) {
	}
}
