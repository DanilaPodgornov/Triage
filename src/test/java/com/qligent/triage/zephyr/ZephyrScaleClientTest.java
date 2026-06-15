package com.qligent.triage.zephyr;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.qligent.triage.config.ZephyrProperties;
import com.qligent.triage.zephyr.dto.CreateTestCycleRequest;
import com.qligent.triage.zephyr.dto.CreateTestExecutionRequest;
import com.qligent.triage.zephyr.dto.TestCycleDto;

class ZephyrScaleClientTest {

	private MockRestServiceServer server;
	private ZephyrScaleClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl("https://api.zephyrscale.smartbear.com/v2");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new ZephyrScaleClient(
				builder.build(),
				new ZephyrProperties("https://api.zephyrscale.smartbear.com/v2", "token", 10, java.util.Map.of()));
	}

	@AfterEach
	void verifyServer() {
		server.verify();
	}

	@Test
	void shouldCreateTestCycle() {
		server.expect(request -> {
					assertThat(request.getURI().getPath()).isEqualTo("/v2/testcycles");
					assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
					assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
				})
				.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
						"""
								{
								  "id": 1001,
								  "key": "SQM-R42"
								}
								""",
						MediaType.APPLICATION_JSON));

		TestCycleDto created = client.createTestCycle(new CreateTestCycleRequest(
				"SQM",
				"[AI] Cycle",
				"2026-06-14T00:00:00Z",
				"2026-06-21T23:59:59Z"));

		assertThat(created.key()).isEqualTo("SQM-R42");
		assertThat(created.id()).isEqualTo(1001L);
	}

	@Test
	void shouldCreateTestExecution() {
		server.expect(request -> {
					assertThat(request.getURI().getPath()).isEqualTo("/v2/testexecutions");
					assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
				})
				.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess());

		client.createTestExecution(new CreateTestExecutionRequest(
				"SQM",
				"SQM-T1",
				"SQM-R42",
				"Not Executed"));
	}
}
