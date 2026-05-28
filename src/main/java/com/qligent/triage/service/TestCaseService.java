package com.qligent.triage.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.ArrayList;

import org.springframework.stereotype.Service;

import com.qligent.triage.config.ZephyrProperties;
import com.qligent.triage.model.Project;
import com.qligent.triage.web.model.PriorityOption;
import com.qligent.triage.web.model.TestCaseView;
import com.qligent.triage.web.model.TestCasesPageView;
import com.qligent.triage.zephyr.ZephyrScaleClient;
import com.qligent.triage.zephyr.dto.TestCaseDto;

@Service
public class TestCaseService {

	private final ZephyrScaleClient zephyrScaleClient;
	private final ZephyrProperties properties;

	public TestCaseService(ZephyrScaleClient zephyrScaleClient, ZephyrProperties properties) {
		this.zephyrScaleClient = zephyrScaleClient;
		this.properties = properties;
	}

	public TestCasesPageView getTestCasesPage(Project project, Optional<Long> priorityId, int page) {
		String projectKey = zephyrScaleClient.resolveProjectKey(project);
		List<PriorityOption> priorities = loadPriorities(projectKey);
		Map<Long, String> priorityNames = priorities.stream()
				.collect(Collectors.toMap(PriorityOption::id, PriorityOption::name));

		int pageSize = properties.pageSize();
		List<TestCaseDto> sourceCases;
		int totalItems;
		boolean lastPage;

		if (priorityId.isPresent()) {
			long selectedPriorityId = priorityId.get();
			List<TestCaseDto> filteredCases = loadAllTestCases(projectKey).stream()
					.filter(testCase -> testCase.priority() != null && testCase.priority().id() == selectedPriorityId)
					.toList();

			totalItems = filteredCases.size();
			int fromIndex = Math.min(page * pageSize, totalItems);
			int toIndex = Math.min(fromIndex + pageSize, totalItems);
			sourceCases = filteredCases.subList(fromIndex, toIndex);
			int totalPages = totalItems == 0 ? 1 : (int) Math.ceil((double) totalItems / pageSize);
			lastPage = totalItems == 0 || page >= totalPages - 1;
		} else {
			int startAt = page * pageSize;
			var response = zephyrScaleClient.getTestCases(projectKey, startAt, pageSize, Optional.empty());
			sourceCases = response.values() == null ? Collections.emptyList() : response.values();
			totalItems = response.total();
			lastPage = response.isLast();
		}

		List<TestCaseView> testCases = sourceCases.stream()
				.map(toView(priorityNames))
				.toList();

		int totalPages = totalItems == 0 ? 1 : (int) Math.ceil((double) totalItems / pageSize);

		return new TestCasesPageView(
				project,
				priorities,
				priorityId.orElse(null),
				testCases,
				page,
				pageSize,
				totalItems,
				totalPages,
				lastPage
		);
	}

	private List<TestCaseDto> loadAllTestCases(String projectKey) {
		List<TestCaseDto> allCases = new ArrayList<>();
		int startAt = 0;
		int chunkSize = 1000;
		boolean isLast;

		do {
			var response = zephyrScaleClient.getTestCases(projectKey, startAt, chunkSize, Optional.empty());
			if (response.values() != null) {
				allCases.addAll(response.values());
			}
			isLast = response.isLast();
			startAt += response.maxResults() > 0 ? response.maxResults() : chunkSize;
		} while (!isLast);

		return allCases;
	}

	private List<PriorityOption> loadPriorities(String projectKey) {
		var response = zephyrScaleClient.getPriorities(projectKey);
		if (response.values() == null) {
			return Collections.emptyList();
		}
		return response.values().stream()
				.map(priority -> new PriorityOption(priority.id(), priority.name()))
				.toList();
	}

	private Function<TestCaseDto, TestCaseView> toView(Map<Long, String> priorityNames) {
		return testCase -> {
			long priorityRefId = testCase.priority() != null ? testCase.priority().id() : 0L;
			String priorityName = priorityNames.getOrDefault(priorityRefId, "—");
			return new TestCaseView(testCase.key(), priorityName, testCase.name());
		};
	}

}
