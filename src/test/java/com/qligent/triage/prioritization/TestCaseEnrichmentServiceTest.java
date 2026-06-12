package com.qligent.triage.prioritization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.qligent.triage.jira.JiraClient;
import com.qligent.triage.jira.dto.JiraIssueSummary;
import com.qligent.triage.model.Project;
import com.qligent.triage.zephyr.ZephyrScaleClient;
import com.qligent.triage.zephyr.dto.StatusDto;
import com.qligent.triage.zephyr.dto.TestCaseDetailDto;
import com.qligent.triage.zephyr.dto.TestExecutionDto;
import com.qligent.triage.zephyr.dto.TestExecutionIssueLinkDto;
import com.qligent.triage.zephyr.dto.ZephyrPage;

@ExtendWith(MockitoExtension.class)
class TestCaseEnrichmentServiceTest {

	@Mock
	private ZephyrScaleClient zephyrScaleClient;

	@Mock
	private JiraClient jiraClient;

	private TestCaseEnrichmentService enrichmentService;

	@BeforeEach
	void setUp() {
		enrichmentService = new TestCaseEnrichmentService(zephyrScaleClient, jiraClient);
	}

	@Test
	void shouldIncludeExecutionResultsAndLinkedJiraIssue() {
		when(zephyrScaleClient.resolveProjectKey(Project.B2C)).thenReturn("SQM");
		when(zephyrScaleClient.getStatuses("SQM", 0, 100)).thenReturn(new ZephyrPage<>(
				null, 0, 100, 1, true, List.of(new StatusDto(1137029L, "Fail"))));
		when(zephyrScaleClient.getTestCase("SQM-T1")).thenReturn(new TestCaseDetailDto(
				9815361L,
				"SQM-T1",
				"Login test",
				"Verify login",
				null,
				null,
				List.of("smoke"),
				new TestCaseDetailDto.PriorityRef(1L),
				new TestCaseDetailDto.StatusRef(2L)));
		when(zephyrScaleClient.getTestExecutions("SQM-T1", 0, 100)).thenReturn(new ZephyrPage<>(
				null, 0, 100, 1, true,
				List.of(new TestExecutionDto(
						49385146L,
						"SQM-E9",
						"2021-07-19T06:38:58Z",
						577000L,
						"Assertion failed",
						new TestExecutionDto.ExecutionStatusRef(1137029L),
						new TestExecutionDto.TestCycleRef(1164953L),
						new TestExecutionDto.ExecutionLinks(
								"https://example/links",
								List.of(new TestExecutionIssueLinkDto(10001L, "SQM-42", "COVERAGE")))))));
		when(jiraClient.isAvailable()).thenReturn(true);
		when(jiraClient.getIssue("SQM-42")).thenReturn(Optional.of(
				new JiraIssueSummary("SQM-42", "Login button broken", "Open", "High", "Button does not respond")));

		var enrichments = enrichmentService.enrichAll(Project.B2C, List.of("SQM-T1"));

		assertThat(enrichments).hasSize(1);
		var enrichment = enrichments.getFirst();
		assertThat(enrichment.recentExecutions()).hasSize(1);
		assertThat(enrichment.recentExecutions().getFirst().executionStatusName()).isEqualTo("Fail");
		assertThat(enrichment.recentExecutions().getFirst().comment()).isEqualTo("Assertion failed");
		assertThat(enrichment.linkedIssues()).hasSize(1);
		assertThat(enrichment.linkedIssues().getFirst().issueKey()).isEqualTo("SQM-42");
		assertThat(enrichment.linkedIssues().getFirst().summary()).isEqualTo("Login button broken");
		verify(jiraClient).getIssue("SQM-42");
	}

}
