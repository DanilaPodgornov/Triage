package com.qligent.triage.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponse(
		String model,
		Message message
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Message(String role, String content) {
	}
}
