package com.qligent.triage.service;

import java.util.Optional;

public record TestCaseListQuery(
		Optional<Long> priorityId,
		Optional<Long> folderId,
		Optional<Long> statusId,
		TestCaseSort sort,
		SortDirection sortDirection,
		int page,
		int pageSize
) {

	public boolean requiresLocalProcessing() {
		return hasActiveFilters()
				|| sort != TestCaseSort.KEY
				|| sortDirection != SortDirection.ASC;
	}

	public boolean hasActiveFilters() {
		return priorityId.isPresent() || folderId.isPresent() || statusId.isPresent();
	}

}
