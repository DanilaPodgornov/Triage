package com.qligent.triage.suggested;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class SuggestedTestCaseSorter {

	private SuggestedTestCaseSorter() {
	}

	static List<SuggestedTestCycle.SuggestedTestCaseEntry> sort(
			List<SuggestedTestCycle.SuggestedTestCaseEntry> tests,
			String sortField,
			String sortDirection) {
		if (tests == null || tests.isEmpty()) {
			return List.of();
		}
		String field = sortField == null || sortField.isBlank()
				? SuggestedTestCycle.DEFAULT_SORT_FIELD
				: sortField.trim();
		boolean ascending = sortDirection != null && sortDirection.trim().equalsIgnoreCase("ASC");
		Comparator<SuggestedTestCycle.SuggestedTestCaseEntry> comparator = (left, right) -> {
			int result = compareSortValues(resolveSortValue(left, field), resolveSortValue(right, field));
			return ascending ? result : -result;
		};
		return tests.stream().sorted(comparator).toList();
	}

	private static Object resolveSortValue(SuggestedTestCycle.SuggestedTestCaseEntry entry, String field) {
		if ("key".equals(field)) {
			return entry.key() != null ? entry.key() : "";
		}
		if ("name".equals(field)) {
			return entry.name() != null ? entry.name() : "";
		}
		if ("aiSort".equals(field)) {
			return entry.aiSort();
		}
		Map<String, Double> weights = entry.weights();
		if (weights == null) {
			return 0.0;
		}
		return weights.getOrDefault(field, 0.0);
	}

	private static int compareSortValues(Object left, Object right) {
		if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
			return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
		}
		return String.valueOf(left).compareTo(String.valueOf(right));
	}
}
