package com.qligent.triage.zephyr;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.qligent.triage.config.ZephyrProperties;
import com.qligent.triage.zephyr.dto.FolderDto;
import com.qligent.triage.zephyr.dto.PriorityDto;
import com.qligent.triage.zephyr.dto.StatusDto;
import com.qligent.triage.zephyr.dto.TestCaseDetailDto;
import com.qligent.triage.zephyr.dto.TestCaseDto;
import com.qligent.triage.zephyr.dto.TestExecutionDto;
import com.qligent.triage.zephyr.dto.ZephyrPage;

@Component
public class ZephyrScaleClient {

	private final RestClient restClient;
	private final ZephyrProperties properties;

	public ZephyrScaleClient(RestClient zephyrRestClient, ZephyrProperties properties) {
		this.restClient = zephyrRestClient;
		this.properties = properties;
	}

	public ZephyrPage<TestCaseDto> getTestCases(String projectKey, int startAt, int maxResults, Optional<String> zql) {
		return restClient.get()
				.uri(uriBuilder -> {
					var builder = uriBuilder
							.path("/testcases")
							.queryParam("projectKey", projectKey)
							.queryParam("startAt", startAt)
							.queryParam("maxResults", maxResults);
					zql.ifPresent(query -> builder.queryParam("zql", query));
					return builder.build();
				})
				.retrieve()
				.body(new org.springframework.core.ParameterizedTypeReference<>() {
				});
	}

	public TestCaseDetailDto getTestCase(String testCaseKey) {
		return restClient.get()
				.uri("/testcases/{testCaseKey}", testCaseKey)
				.retrieve()
				.body(TestCaseDetailDto.class);
	}

	public ZephyrPage<TestExecutionDto> getTestExecutions(String testCaseKey, int startAt, int maxResults) {
		return restClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/testexecutions")
						.queryParam("testCase", testCaseKey)
						.queryParam("startAt", startAt)
						.queryParam("maxResults", maxResults)
						.build())
				.retrieve()
				.body(new org.springframework.core.ParameterizedTypeReference<>() {
				});
	}

	public ZephyrPage<FolderDto> getFolders(String projectKey, int startAt, int maxResults) {
		return restClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/folders")
						.queryParam("projectKey", projectKey)
						.queryParam("startAt", startAt)
						.queryParam("maxResults", maxResults)
						.build())
				.retrieve()
				.body(new org.springframework.core.ParameterizedTypeReference<>() {
				});
	}

	public ZephyrPage<StatusDto> getStatuses(String projectKey, int startAt, int maxResults) {
		return restClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/statuses")
						.queryParam("projectKey", projectKey)
						.queryParam("startAt", startAt)
						.queryParam("maxResults", maxResults)
						.build())
				.retrieve()
				.body(new org.springframework.core.ParameterizedTypeReference<>() {
				});
	}

	public ZephyrPage<PriorityDto> getPriorities(String projectKey) {
		return restClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/priorities")
						.queryParam("projectKey", projectKey)
						.queryParam("maxResults", 100)
						.build())
				.retrieve()
				.body(new org.springframework.core.ParameterizedTypeReference<>() {
				});
	}

	public String resolveProjectKey(com.qligent.triage.model.Project project) {
		String key = properties.projects().get(project.label());
		if (key == null || key.isBlank()) {
			throw new IllegalStateException("No Zephyr project key configured for " + project.label());
		}
		return key;
	}

}
