package com.qligent.triage.web.model;

import java.util.List;

import com.qligent.triage.model.Project;
import com.qligent.triage.service.SortDirection;
import com.qligent.triage.service.TestCaseSort;

public record TestCasesPageView(
		Project project,
		List<FilterOption> priorities,
		List<FilterOption> folders,
		List<FilterOption> statuses,
		Long selectedPriorityId,
		Long selectedFolderId,
		Long selectedStatusId,
		TestCaseSort sort,
		SortDirection sortDirection,
		List<TestCaseView> testCases,
		List<String> allMatchingTestCaseKeys,
		int currentPage,
		int pageSize,
		int totalItems,
		int totalPages,
		boolean lastPage
) {
}
