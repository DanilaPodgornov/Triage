package com.qligent.triage.llm.dto;

import java.util.List;

public record OllamaChatRequest(
		String model,
		boolean stream,
		String format,
		List<Message> messages
) {
	public record Message(String role, String content) {
	}
}
