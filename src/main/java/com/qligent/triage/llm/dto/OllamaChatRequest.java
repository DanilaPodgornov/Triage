package com.qligent.triage.llm.dto;

import java.util.List;

public record OllamaChatRequest(
		String model,
		boolean stream,
		String format,
		List<Message> messages,
		Options options
) {

	public OllamaChatRequest(String model, boolean stream, String format, List<Message> messages) {
		this(model, stream, format, messages, null);
	}

	public record Message(String role, String content) {
	}

	public record Options(int num_ctx) {
	}
}
