package com.qligent.triage.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.qligent.triage.config.ZephyrProperties;
import com.qligent.triage.model.Project;
import com.qligent.triage.web.model.PriorityOption;
import com.qligent.triage.web.model.TestCaseView;
import com.qligent.triage.web.model.TestCasesPageView;
import com.qligent.triage.zephyr.ZephyrScaleClient;
import com.qligent.triage.zephyr.dto.PriorityDto;
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

		Optional<String> zql = priorityId.flatMap(id -> Optional.ofNullable(priorityNames.get(id))
				.map(name -> "priority = \"" + name + "\""));

		int pageSize = properties.pageSize();
		int startAt = page * pageSize;
		var response = zephyrScaleClient.getTestCases(projectKey, startAt, pageSize, zql);

		List<TestCaseView> testCases = response.values() == null
				? Collections.emptyList()
				: response.values().stream()
						.map(toView(priorityNames))
						.toList();

		int totalPages = response.total() == 0 ? 1 : (int) Math.ceil((double) response.total() / pageSize);

		return new TestCasesPageView(
				project,
				priorities,
				priorityId.orElse(null),
				testCases,
				page,
				pageSize,
				response.total(),
				totalPages,
				response.isLast()
		);
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
