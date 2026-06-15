package com.qligent.triage.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.qligent.triage.config.TriageProperties;
import com.qligent.triage.model.Project;
import com.qligent.triage.prioritization.PrioritizationJobService;
import com.qligent.triage.prioritization.PrioritizationJobService.JobStatusSnapshot;
import com.qligent.triage.prioritization.PrioritizationJobService.PrioritizationJob;
import com.qligent.triage.prioritization.PrioritizationJobService.PrioritizationJobStatus;
import com.qligent.triage.prioritization.PrioritizationService;

@WebMvcTest(controllers = PrioritizationApiController.class)
@Import(WebExceptionHandler.class)
class PrioritizationApiControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PrioritizationJobService prioritizationJobService;

	@MockitoBean
	private PrioritizationService prioritizationService;

	@MockitoBean
	private TriageProperties triageProperties;

	@BeforeEach
	void stubTriageDefaults() {
		org.mockito.Mockito.lenient().when(triageProperties.theme()).thenReturn(new TriageProperties.Theme("system"));
		org.mockito.Mockito.lenient().when(triageProperties.locale()).thenReturn(new TriageProperties.Locale("en"));
	}

	@Test
	void shouldStartPrioritizationJob() throws Exception {
		PrioritizationJob job = org.mockito.Mockito.mock(PrioritizationJob.class);
		given(job.id()).willReturn("job-1");
		given(job.totalTests()).willReturn(2);
		given(prioritizationJobService.start(eq(Project.B2C), any())).willReturn(job);

		mockMvc.perform(post("/api/prioritize")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"project":"B2C","testCaseKeys":["SQM-T1","SQM-T2"]}
								"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.jobId").value("job-1"))
				.andExpect(jsonPath("$.totalTests").value(2));
	}

	@Test
	void shouldReturnCompletedJobWithRedirectUrl() throws Exception {
		PrioritizationJob job = org.mockito.Mockito.mock(PrioritizationJob.class);
		given(job.snapshot()).willReturn(new JobStatusSnapshot(
				PrioritizationJobStatus.COMPLETED,
				2,
				2,
				0,
				null,
				5000L,
				"SQM-SR2",
				"/suggestedcycles/SQM-SR2?project=B2C",
				null));
		given(prioritizationJobService.getJob("job-1")).willReturn(job);

		mockMvc.perform(get("/api/prioritize/jobs/job-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.cycleKey").value("SQM-SR2"))
				.andExpect(jsonPath("$.redirectUrl").value("/suggestedcycles/SQM-SR2?project=B2C"));
	}

	@Test
	void shouldReturnJobStatus() throws Exception {
		PrioritizationJob job = org.mockito.Mockito.mock(PrioritizationJob.class);
		given(job.snapshot()).willReturn(new JobStatusSnapshot(
				PrioritizationJobStatus.RUNNING,
				1,
				2,
				2,
				"SQM-T2",
				5000L,
				null,
				null,
				null));
		given(prioritizationJobService.getJob("job-1")).willReturn(job);

		mockMvc.perform(get("/api/prioritize/jobs/job-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RUNNING"))
				.andExpect(jsonPath("$.completedTests").value(1))
				.andExpect(jsonPath("$.totalTests").value(2));
	}

	@Test
	void shouldCancelRunningJob() throws Exception {
		PrioritizationJob job = org.mockito.Mockito.mock(PrioritizationJob.class);
		given(job.snapshot()).willReturn(new JobStatusSnapshot(
				PrioritizationJobStatus.CANCELLED,
				0,
				2,
				1,
				"SQM-T1",
				3000L,
				null,
				null,
				null));
		given(prioritizationJobService.cancel("job-1")).willReturn(job);

		mockMvc.perform(delete("/api/prioritize/jobs/job-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	void shouldReturnConflictWhenCancellingCompletedJob() throws Exception {
		given(prioritizationJobService.cancel("job-1"))
				.willThrow(new IllegalStateException("Prioritization job cannot be cancelled: COMPLETED"));

		mockMvc.perform(delete("/api/prioritize/jobs/job-1"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Prioritization job cannot be cancelled: COMPLETED"));
	}
}
