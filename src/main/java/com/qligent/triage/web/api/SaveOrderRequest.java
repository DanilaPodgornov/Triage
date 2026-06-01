package com.qligent.triage.web.api;

import java.util.List;

public record SaveOrderRequest(
		List<String> testCases
) {
}
