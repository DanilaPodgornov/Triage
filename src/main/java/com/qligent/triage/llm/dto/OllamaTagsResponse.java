package com.qligent.triage.llm.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaTagsResponse(List<ModelTag> models) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ModelTag(
			String name,
			String model,
			Long size,
			String modifiedAt) {
	}
}
