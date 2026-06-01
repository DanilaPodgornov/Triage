package com.qligent.triage.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.qligent.triage.config.ZephyrProperties;
import com.qligent.triage.model.Project;
import com.qligent.triage.web.model.FilterOption;
import com.qligent.triage.web.model.TestCaseView;
import com.qligent.triage.web.model.TestCasesPageView;
import com.qligent.triage.zephyr.ZephyrScaleClient;
import com.qligent.triage.zephyr.dto.FolderDto;
import com.qligent.triage.zephyr.dto.PriorityDto;
import com.qligent.triage.zephyr.dto.StatusDto;
import com.qligent.triage.zephyr.dto.TestCaseDto;
import com.qligent.triage.zephyr.dto.ZephyrPage;

@Service
public class TestCaseService {

	private final ZephyrScaleClient zephyrScaleClient;
	private final ZephyrProperties properties;

	public TestCaseService(ZephyrScaleClient zephyrScaleClient, ZephyrProperties properties) {
		this.zephyrScaleClient = zephyrScaleClient;
		this.properties = properties;
	}

	public TestCasesPageView getTestCasesPage(Project project, TestCaseListQuery query) {
		String projectKey = zephyrScaleClient.resolveProjectKey(project);
		List<FilterOption> priorities = loadPriorities(projectKey);
		List<FilterOption> folders = loadFolders(projectKey);
		List<FilterOption> statuses = loadStatuses(projectKey);

		Map<Long, String> priorityNames = toNameMap(priorities);
		Map<Long, String> folderNames = toNameMap(folders);
		Map<Long, String> statusNames = toNameMap(statuses);

		int pageSize = properties.pageSize();
		List<TestCaseDto> allMatching;
		List<TestCaseDto> sourceCases;
		int totalItems;
		boolean lastPage;

		if (query.requiresLocalProcessing()) {
			allMatching = loadAllTestCases(projectKey).stream()
					.filter(testCase -> matchesFilters(testCase, query))
					.sorted(buildComparator(query, priorityNames, folderNames, statusNames))
					.toList();

			totalItems = allMatching.size();
			int fromIndex = Math.min(query.page() * pageSize, totalItems);
			int toIndex = Math.min(fromIndex + pageSize, totalItems);
			sourceCases = allMatching.subList(fromIndex, toIndex);
			lastPage = totalItems == 0 || query.page() >= computeTotalPages(totalItems, pageSize) - 1;
		} else {
			int startAt = query.page() * pageSize;
			var response = zephyrScaleClient.getTestCases(projectKey, startAt, pageSize, java.util.Optional.empty());
			sourceCases = response.values() == null ? Collections.emptyList() : response.values();
			allMatching = sourceCases;
			totalItems = response.total();
			lastPage = response.isLast();
		}

		List<String> allMatchingTestCaseKeys = allMatching.stream().map(TestCaseDto::key).toList();

		List<TestCaseView> testCases = sourceCases.stream()
				.map(toView(priorityNames, folderNames, statusNames))
				.toList();

		int totalPages = computeTotalPages(totalItems, pageSize);

		return new TestCasesPageView(
				project,
				priorities,
				folders,
				statuses,
				query.priorityId().orElse(null),
				query.folderId().orElse(null),
				query.statusId().orElse(null),
				query.sort(),
				query.sortDirection(),
				testCases,
				allMatchingTestCaseKeys,
				query.page(),
				pageSize,
				totalItems,
				totalPages,
				lastPage
		);
	}

	private int computeTotalPages(int totalItems, int pageSize) {
		return totalItems == 0 ? 1 : (int) Math.ceil((double) totalItems / pageSize);
	}

	private boolean matchesFilters(TestCaseDto testCase, TestCaseListQuery query) {
		if (query.priorityId().isPresent()) {
			long id = query.priorityId().get();
			if (testCase.priority() == null || testCase.priority().id() != id) {
				return false;
			}
		}
		if (query.folderId().isPresent()) {
			long id = query.folderId().get();
			if (testCase.folder() == null || testCase.folder().id() != id) {
				return false;
			}
		}
		if (query.statusId().isPresent()) {
			long id = query.statusId().get();
			if (testCase.status() == null || testCase.status().id() != id) {
				return false;
			}
		}
		return true;
	}

	private Comparator<TestCaseDto> buildComparator(
			TestCaseListQuery query,
			Map<Long, String> priorityNames,
			Map<Long, String> folderNames,
			Map<Long, String> statusNames) {
		Comparator<TestCaseDto> comparator = switch (query.sort()) {
			case PRIORITY -> Comparator.comparing(
					testCase -> nameForRef(testCase.priority() != null ? testCase.priority().id() : null, priorityNames),
					String.CASE_INSENSITIVE_ORDER);
			case FOLDER -> Comparator.comparing(
					testCase -> nameForRef(testCase.folder() != null ? testCase.folder().id() : null, folderNames),
					String.CASE_INSENSITIVE_ORDER);
			case STATUS -> Comparator.comparing(
					testCase -> nameForRef(testCase.status() != null ? testCase.status().id() : null, statusNames),
					String.CASE_INSENSITIVE_ORDER);
			case KEY -> Comparator.comparing(TestCaseDto::key, String.CASE_INSENSITIVE_ORDER);
		};
		if (query.sortDirection() == SortDirection.DESC) {
			comparator = comparator.reversed();
		}
		return comparator.thenComparing(TestCaseDto::key, String.CASE_INSENSITIVE_ORDER);
	}

	private String nameForRef(Long id, Map<Long, String> names) {
		if (id == null) {
			return "";
		}
		return names.getOrDefault(id, "");
	}

	private Map<Long, String> toNameMap(List<FilterOption> options) {
		return options.stream().collect(Collectors.toMap(FilterOption::id, FilterOption::name));
	}

	private List<TestCaseDto> loadAllTestCases(String projectKey) {
		List<TestCaseDto> allCases = new ArrayList<>();
		int startAt = 0;
		int chunkSize = 1000;
		boolean isLast;

		do {
			var response = zephyrScaleClient.getTestCases(projectKey, startAt, chunkSize, java.util.Optional.empty());
			if (response.values() != null) {
				allCases.addAll(response.values());
			}
			isLast = response.isLast();
			startAt += response.maxResults() > 0 ? response.maxResults() : chunkSize;
		} while (!isLast);

		return allCases;
	}

	private List<FilterOption> loadPriorities(String projectKey) {
		var response = zephyrScaleClient.getPriorities(projectKey);
		return toFilterOptions(response);
	}

	private List<FilterOption> loadFolders(String projectKey) {
		return loadAllPages(projectKey, zephyrScaleClient::getFolders).stream()
				.filter(folder -> folder.folderType() == null || "TEST_CASE".equals(folder.folderType()))
				.map(folder -> new FilterOption(folder.id(), folder.name()))
				.sorted(Comparator.comparing(FilterOption::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private List<FilterOption> loadStatuses(String projectKey) {
		return loadAllPages(projectKey, zephyrScaleClient::getStatuses).stream()
				.map(status -> new FilterOption(status.id(), status.name()))
				.sorted(Comparator.comparing(FilterOption::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private <T> List<T> loadAllPages(String projectKey, PageLoader<T> loader) {
		List<T> all = new ArrayList<>();
		int startAt = 0;
		int chunkSize = 100;
		boolean isLast;
		do {
			ZephyrPage<T> page = loader.load(projectKey, startAt, chunkSize);
			if (page.values() != null) {
				all.addAll(page.values());
			}
			isLast = page.isLast();
			startAt += page.maxResults() > 0 ? page.maxResults() : chunkSize;
		} while (!isLast);
		return all;
	}

	private List<FilterOption> toFilterOptions(ZephyrPage<PriorityDto> response) {
		if (response.values() == null) {
			return List.of();
		}
		return response.values().stream()
				.map(priority -> new FilterOption(priority.id(), priority.name()))
				.sorted(Comparator.comparing(FilterOption::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private Function<TestCaseDto, TestCaseView> toView(
			Map<Long, String> priorityNames,
			Map<Long, String> folderNames,
			Map<Long, String> statusNames) {
		return testCase -> {
			long priorityRefId = testCase.priority() != null ? testCase.priority().id() : 0L;
			long statusRefId = testCase.status() != null ? testCase.status().id() : 0L;
			long folderRefId = testCase.folder() != null ? testCase.folder().id() : 0L;
			return new TestCaseView(
					testCase.key(),
					priorityNames.getOrDefault(priorityRefId, "—"),
					statusNames.getOrDefault(statusRefId, "—"),
					folderNames.getOrDefault(folderRefId, "—"),
					testCase.name());
		};
	}

	@FunctionalInterface
	private interface PageLoader<T> {
		ZephyrPage<T> load(String projectKey, int startAt, int maxResults);
	}

}
