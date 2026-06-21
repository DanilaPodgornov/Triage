package com.qligent.triage.web.api;

public record SaveSortStateRequest(
		String sortField,
		String sortDirection,
		String suggestedTestCycleName,
		java.util.List<String> orderedKeys
) {
}
