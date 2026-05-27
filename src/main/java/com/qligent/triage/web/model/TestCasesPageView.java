package com.qligent.triage.web.model;

import java.util.List;

import com.qligent.triage.model.Project;

public record TestCasesPageView(
		Project project,
		List<PriorityOption> priorities,
		Long selectedPriorityId,
		List<TestCaseView> testCases,
		int currentPage,
		int pageSize,
		int totalItems,
		int totalPages,
		boolean lastPage
) {
}
