package com.qligent.triage.web.api;

import java.util.List;

public record PrioritizeRequest(
		String project,
		List<String> testCaseKeys
) {
}
