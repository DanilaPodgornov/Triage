package com.qligent.triage.web.api;

import java.util.List;

public record PromptTemplateResponse(
		String type,
		String model,
		List<String> versions,
		String activeVersion,
		String content,
		boolean available) {
}
