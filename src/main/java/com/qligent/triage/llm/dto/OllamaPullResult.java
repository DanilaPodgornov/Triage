package com.qligent.triage.llm.dto;

import java.util.List;

public record OllamaPullResult(
		String lastStatus,
		List<String> statusLog
) {
}
