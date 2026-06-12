package com.qligent.triage.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import com.qligent.triage.config.TriageProperties;
import com.qligent.triage.model.Project;
import com.qligent.triage.prioritization.PrioritizationService;

@WebMvcTest(controllers = PrioritizationApiController.class)
@Import(WebExceptionHandler.class)
class PrioritizationApiControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PrioritizationService prioritizationService;

	@MockitoBean
	private TriageProperties triageProperties;

	@BeforeEach
	void stubTriageDefaults() {
		lenient().when(triageProperties.theme()).thenReturn(new TriageProperties.Theme("system"));
		lenient().when(triageProperties.locale()).thenReturn(new TriageProperties.Locale("en"));
	}

	@Test
	void shouldReturnJsonErrorWhenOllamaIsUnavailable() throws Exception {
		given(prioritizationService.prioritize(eq(Project.B2C), any()))
				.willThrow(new ResourceAccessException("Connection refused: localhost:11434"));

		mockMvc.perform(post("/api/prioritize")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"project":"B2C","testCaseKeys":["SQM-T1","SQM-T2"]}
								"""))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value(
						"Ollama is not available or did not respond. Start Ollama and ensure model qwen2.5:3b is installed (ollama pull qwen2.5:3b)."));
	}

	@Test
	void shouldReturnJsonErrorWhenLlmResponseCannotBeParsed() throws Exception {
		given(prioritizationService.prioritize(eq(Project.B2C), any()))
				.willThrow(new IllegalStateException("Failed to parse LLM prioritization JSON."));

		mockMvc.perform(post("/api/prioritize")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"project":"B2C","testCaseKeys":["SQM-T1","SQM-T2"]}
								"""))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("Failed to parse LLM prioritization JSON."));
	}

}
