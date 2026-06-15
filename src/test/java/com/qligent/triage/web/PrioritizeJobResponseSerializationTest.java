package com.qligent.triage.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.web.api.PrioritizeJobResponse;

@SpringBootTest
class PrioritizeJobResponseSerializationTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void shouldSerializeJobIdFieldName() throws Exception {
		JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(new PrioritizeJobResponse("job-1", 2)));

		assertThat(json.has("jobId")).isTrue();
		assertThat(json.get("jobId").asString()).isEqualTo("job-1");
	}
}
