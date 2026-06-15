package com.qligent.triage.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.web.api.PrioritizationJobStatusResponse;

@SpringBootTest
class PrioritizationJobStatusResponseSerializationTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void shouldSerializeRedirectUrlFieldName() throws Exception {
		var response = new PrioritizationJobStatusResponse(
				"COMPLETED", 2, 2, 0, null, 1000L, "SQM-SR2", "/suggestedcycles/SQM-SR2?project=B2C", null);

		JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

		assertThat(json.has("redirectUrl")).isTrue();
		assertThat(json.get("redirectUrl").asString()).isEqualTo("/suggestedcycles/SQM-SR2?project=B2C");
		assertThat(json.has("status")).isTrue();
		assertThat(json.get("status").asString()).isEqualTo("COMPLETED");
	}
}
