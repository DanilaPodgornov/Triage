package com.qligent.triage.service;

import java.util.Optional;

public record TestCaseListQuery(
		Optional<Long> priorityId,
		Optional<Long> folderId,
		Optional<Long> statusId,
		TestCaseSort sort,
		SortDirection sortDirection,
		int page
) {

	public boolean requiresLocalProcessing() {
		return priorityId.isPresent()
				|| folderId.isPresent()
				|| statusId.isPresent()
				|| sort != TestCaseSort.KEY
				|| sortDirection != SortDirection.ASC;
	}

}
