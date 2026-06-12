package com.qligent.triage.prioritization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.qligent.triage.jira.JiraClient;
import com.qligent.triage.jira.dto.JiraIssueSummary;
import com.qligent.triage.model.Project;
import com.qligent.triage.zephyr.ZephyrScaleClient;
import com.qligent.triage.zephyr.dto.StatusDto;
import com.qligent.triage.zephyr.dto.TestExecutionDto;
import com.qligent.triage.zephyr.dto.TestExecutionIssueLinkDto;
import com.qligent.triage.zephyr.dto.ZephyrPage;

@Service
public class TestCaseEnrichmentService {

	private static final Logger log = LoggerFactory.getLogger(TestCaseEnrichmentService.class);

	private static final int EXECUTION_PAGE_SIZE = 100;

	private final ZephyrScaleClient zephyrScaleClient;
	private final JiraClient jiraClient;

	public TestCaseEnrichmentService(ZephyrScaleClient zephyrScaleClient, JiraClient jiraClient) {
		this.zephyrScaleClient = zephyrScaleClient;
		this.jiraClient = jiraClient;
	}

	public List<TestCaseEnrichment> enrichAll(Project project, List<String> testCaseKeys) {
		String projectKey = zephyrScaleClient.resolveProjectKey(project);
		Map<Long, String> executionStatusNames = loadExecutionStatusNames(projectKey);
		Map<Long, LinkedIssueDetails> linkedIssueCache = new HashMap<>();

		return testCaseKeys.stream()
				.map(testCaseKey -> enrichOne(projectKey, testCaseKey, executionStatusNames, linkedIssueCache))
				.toList();
	}

	private TestCaseEnrichment enrichOne(
			String projectKey,
			String testCaseKey,
			Map<Long, String> executionStatusNames,
			Map<Long, LinkedIssueDetails> linkedIssueCache) {
		var detail = zephyrScaleClient.getTestCase(testCaseKey);
		List<TestExecutionDto> executions = loadAllExecutions(testCaseKey);

		Map<Long, LinkedIssueDetails> issuesForTestCase = new LinkedHashMap<>();
		List<TestCaseEnrichment.ExecutionSummary> executionSummaries = executions.stream()
				.sorted(Comparator.comparing(
						TestExecutionDto::actualEndDate,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.map(execution -> toExecutionSummary(execution, executionStatusNames, issuesForTestCase, linkedIssueCache))
				.toList();

		List<TestCaseEnrichment.LinkedIssueSummary> linkedIssues = issuesForTestCase.values().stream()
				.map(LinkedIssueDetails::toSummary)
				.toList();

		return TestCaseEnrichment.builder(testCaseKey)
				.name(detail.name())
				.objective(detail.objective())
				.labels(detail.labels() == null ? List.of() : detail.labels())
				.priorityId(detail.priority() != null ? detail.priority().id() : null)
				.statusId(detail.status() != null ? detail.status().id() : null)
				.recentExecutions(executionSummaries)
				.linkedIssues(linkedIssues)
				.build();
	}

	private TestCaseEnrichment.ExecutionSummary toExecutionSummary(
			TestExecutionDto execution,
			Map<Long, String> executionStatusNames,
			Map<Long, LinkedIssueDetails> issuesForTestCase,
			Map<Long, LinkedIssueDetails> linkedIssueCache) {
		long statusId = execution.testExecutionStatus() != null ? execution.testExecutionStatus().id() : 0L;
		String statusName = executionStatusNames.getOrDefault(statusId, statusId == 0 ? "Unknown" : "Status-" + statusId);

		List<TestExecutionIssueLinkDto> issueLinks = resolveIssueLinks(execution);
		List<TestCaseEnrichment.ExecutionIssueLink> executionIssueLinks = issueLinks.stream()
				.map(link -> new TestCaseEnrichment.ExecutionIssueLink(
						link.issueId(),
						link.issueKey(),
						link.type()))
				.toList();

		for (TestExecutionIssueLinkDto issueLink : issueLinks) {
			LinkedIssueDetails details = resolveLinkedIssue(issueLink, linkedIssueCache);
			issuesForTestCase.putIfAbsent(issueLink.issueId(), details);
		}

		return new TestCaseEnrichment.ExecutionSummary(
				execution.key(),
				execution.actualEndDate(),
				statusId,
				statusName,
				execution.testCycle() != null ? execution.testCycle().id() : null,
				execution.executionTime(),
				execution.comment(),
				executionIssueLinks);
	}

	private List<TestExecutionIssueLinkDto> resolveIssueLinks(TestExecutionDto execution) {
		if (execution.links() != null && execution.links().issues() != null) {
			return execution.links().issues();
		}
		if (execution.key() == null || execution.key().isBlank()) {
			return List.of();
		}
		try {
			var response = zephyrScaleClient.getTestExecutionIssueLinks(execution.key());
			return response.values() == null ? List.of() : response.values();
		} catch (RestClientException exception) {
			log.warn("Could not load issue links for execution {}: {}", execution.key(), exception.getMessage());
			return List.of();
		}
	}

	private LinkedIssueDetails resolveLinkedIssue(
			TestExecutionIssueLinkDto issueLink,
			Map<Long, LinkedIssueDetails> linkedIssueCache) {
		return linkedIssueCache.computeIfAbsent(issueLink.issueId(), issueId -> {
			Optional<JiraIssueSummary> jiraIssue = fetchJiraIssue(issueLink);
			if (jiraIssue.isPresent()) {
				JiraIssueSummary issue = jiraIssue.get();
				return new LinkedIssueDetails(
						issueId,
						issue.key(),
						issue.summary(),
						issue.status(),
						issue.priority(),
						issue.description());
			}
			if (jiraClient.isAvailable()) {
				log.warn(
						"Jira issue details unavailable for {} (issueId={}): check permissions or issue key",
						issueLink.issueKey(),
						issueId);
			}
			return new LinkedIssueDetails(
					issueId,
					issueLink.issueKey(),
					null,
					null,
					null,
					null);
		});
	}

	private Optional<JiraIssueSummary> fetchJiraIssue(TestExecutionIssueLinkDto issueLink) {
		if (!jiraClient.isAvailable()) {
			return Optional.empty();
		}
		if (issueLink.issueKey() != null && !issueLink.issueKey().isBlank()) {
			Optional<JiraIssueSummary> byKey = jiraClient.getIssue(issueLink.issueKey());
			if (byKey.isPresent()) {
				return byKey;
			}
		}
		return jiraClient.getIssue(issueLink.issueId());
	}

	private Map<Long, String> loadExecutionStatusNames(String projectKey) {
		Map<Long, String> names = new HashMap<>();
		int startAt = 0;
		boolean isLast;
		do {
			ZephyrPage<StatusDto> page = zephyrScaleClient.getStatuses(projectKey, startAt, EXECUTION_PAGE_SIZE);
			if (page.values() != null) {
				for (StatusDto status : page.values()) {
					names.put(status.id(), status.name());
				}
			}
			isLast = page.isLast();
			startAt += page.maxResults() > 0 ? page.maxResults() : EXECUTION_PAGE_SIZE;
		} while (!isLast);
		return names;
	}

	private List<TestExecutionDto> loadAllExecutions(String testCaseKey) {
		List<TestExecutionDto> allExecutions = new ArrayList<>();
		int startAt = 0;
		boolean isLast;
		do {
			var page = zephyrScaleClient.getTestExecutions(testCaseKey, startAt, EXECUTION_PAGE_SIZE);
			if (page.values() != null) {
				allExecutions.addAll(page.values());
			}
			isLast = page.isLast();
			startAt += page.maxResults() > 0 ? page.maxResults() : EXECUTION_PAGE_SIZE;
		} while (!isLast);
		return allExecutions;
	}

	private record LinkedIssueDetails(
			long issueId,
			String issueKey,
			String summary,
			String status,
			String priority,
			String description
	) {

		TestCaseEnrichment.LinkedIssueSummary toSummary() {
			return new TestCaseEnrichment.LinkedIssueSummary(issueId, issueKey, summary, status, priority, description);
		}
	}

}
