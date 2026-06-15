package com.qligent.triage.web.model;

import java.util.List;

import com.qligent.triage.suggested.SuggestedTestCycle;

public record SuggestedCyclesPageView(
		List<SuggestedTestCycle> cycles,
		int currentPage,
		int pageSize,
		int rangeFrom,
		int rangeTo,
		int totalItems,
		int totalPages,
		boolean lastPage
) {
}
