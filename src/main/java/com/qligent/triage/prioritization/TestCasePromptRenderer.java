package com.qligent.triage.prioritization;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.qligent.triage.zephyr.dto.PriorityDto;
import com.qligent.triage.zephyr.dto.StatusDto;
import com.qligent.triage.zephyr.dto.TestCaseDetailDto;
import com.qligent.triage.zephyr.dto.TestExecutionDto;
import com.qligent.triage.zephyr.dto.TestExecutionIssueLinkDto;

@Component
public class TestCasePromptRenderer {

	private static final int RECENT_EXECUTIONS_LIMIT = 5;

	public String render(
			TestCaseDetailDto testCase,
			List<TestExecutionDto> executions,
			Map<Long, String> statusNames,
			Map<Long, String> priorityNames) {
		StringBuilder builder = new StringBuilder();
		builder.append("## Test case: ").append(testCase.key()).append('\n');
		builder.append("- Name: ").append(nullToDash(testCase.name())).append('\n');
		builder.append("- Objective: ").append(nullToDash(testCase.objective())).append('\n');
		builder.append("- Labels: ").append(formatLabels(testCase.labels())).append('\n');
		builder.append("- Priority: ").append(formatPriority(testCase.priority(), priorityNames)).append('\n');
		builder.append("- Status: ").append(formatStatus(testCase.status(), statusNames)).append('\n');
		builder.append('\n');
		builder.append("## Executions (total: ").append(executions.size()).append(")\n");
		if (executions.isEmpty()) {
			builder.append("No executions found.\n");
			return builder.toString();
		}
		List<TestExecutionDto> recent = executions.stream()
				.sorted(Comparator.comparing(
						TestExecutionDto::actualEndDate,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.limit(RECENT_EXECUTIONS_LIMIT)
				.toList();
		for (TestExecutionDto execution : recent) {
			String statusLabel = formatExecutionStatus(execution.testExecutionStatus(), statusNames);
			boolean failed = isFailedStatus(statusLabel);
			builder.append("\n### Execution ").append(execution.key());
			if (failed) {
				builder.append(" [FAILED]");
			}
			builder.append('\n');
			builder.append("- Status: ").append(statusLabel).append('\n');
			builder.append("- End date: ").append(nullToDash(execution.actualEndDate())).append('\n');
			builder.append("- Execution time (ms): ").append(execution.executionTime() == null ? 0 : execution.executionTime()).append('\n');
			builder.append("- Comment: ").append(nullToDash(execution.comment())).append('\n');
			builder.append("- Issues: ").append(formatIssues(execution)).append('\n');
		}
		return builder.toString();
	}

	private static String formatPriority(TestCaseDetailDto.PriorityRef priority, Map<Long, String> priorityNames) {
		if (priority == null) {
			return "-";
		}
		return priority.id() + " (" + priorityNames.getOrDefault(priority.id(), "Unknown") + ")";
	}

	private static String formatStatus(TestCaseDetailDto.StatusRef status, Map<Long, String> statusNames) {
		if (status == null) {
			return "-";
		}
		return status.id() + " (" + statusNames.getOrDefault(status.id(), "Unknown") + ")";
	}

	private static String formatExecutionStatus(TestExecutionDto.ExecutionStatusRef status, Map<Long, String> statusNames) {
		if (status == null) {
			return "-";
		}
		return status.id() + " (" + statusNames.getOrDefault(status.id(), "Unknown") + ")";
	}

	private static String formatLabels(List<String> labels) {
		if (labels == null || labels.isEmpty()) {
			return "-";
		}
		return String.join(", ", labels);
	}

	private static String formatIssues(TestExecutionDto execution) {
		if (execution.links() == null || execution.links().issues() == null || execution.links().issues().isEmpty()) {
			return "none";
		}
		return execution.links().issues().stream()
				.map(TestExecutionIssueLinkDto::issueKey)
				.collect(Collectors.joining(", "));
	}

	private static String nullToDash(String value) {
		return value == null || value.isBlank() ? "-" : value;
	}

	private static boolean isFailedStatus(String statusLabel) {
		return statusLabel != null && statusLabel.toLowerCase().contains("fail");
	}
}
