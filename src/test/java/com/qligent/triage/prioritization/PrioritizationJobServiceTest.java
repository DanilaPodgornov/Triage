package com.qligent.triage.prioritization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.qligent.triage.model.Project;
import com.qligent.triage.suggested.SuggestedTestCycle;

@ExtendWith(MockitoExtension.class)
class PrioritizationJobServiceTest {

	@Mock
	private PrioritizationService prioritizationService;

	@Test
	void shouldExposeRedirectUrlWhenJobCompletes() throws Exception {
		var cycle = SuggestedTestCycle.create(
				"SQM-SR2",
				"Cycle",
				"SQM",
				1L,
				"folder",
				List.of());
		given(prioritizationService.prioritize(eq(Project.B2C), any(), any())).willReturn(cycle);

		var service = new PrioritizationJobService(prioritizationService);
		var job = service.start(Project.B2C, List.of("SQM-T1", "SQM-T2"));

		awaitCompletion(service, job.id());

		var completed = service.getJob(job.id());
		assertThat(completed.status()).isEqualTo(PrioritizationJobService.PrioritizationJobStatus.COMPLETED);
		assertThat(completed.redirectUrl()).isEqualTo("/suggestedcycles/SQM-SR2?project=B2C");
	}

	private static void awaitCompletion(PrioritizationJobService service, String jobId) throws InterruptedException {
		for (int attempt = 0; attempt < 200; attempt++) {
			var status = service.getJob(jobId).status();
			if (status == PrioritizationJobService.PrioritizationJobStatus.COMPLETED
					|| status == PrioritizationJobService.PrioritizationJobStatus.FAILED) {
				return;
			}
			TimeUnit.MILLISECONDS.sleep(25);
		}
		throw new IllegalStateException("Job did not finish in time");
	}
}
