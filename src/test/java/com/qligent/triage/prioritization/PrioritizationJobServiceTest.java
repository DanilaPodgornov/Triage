package com.qligent.triage.prioritization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.qligent.triage.model.Project;
import com.qligent.triage.suggested.SuggestedTestCycle;

@ExtendWith(MockitoExtension.class)
class PrioritizationJobServiceTest {

	@TempDir
	Path tempDir;

	@Mock
	private PrioritizationService prioritizationService;

	@Mock
	private PrioritizationArtifactService artifactService;

	@Test
	void shouldExposeRedirectUrlWhenJobCompletes() throws Exception {
		var cycle = SuggestedTestCycle.create(
				"SQM-SR2",
				"Cycle",
				"SQM",
				1L,
				"folder",
				List.of());
		given(prioritizationService.prioritize(eq(Project.B2C), any(), any(), any())).willReturn(cycle);

		var service = new PrioritizationJobService(prioritizationService, artifactService);
		var job = service.start(Project.B2C, List.of("SQM-T1", "SQM-T2"));

		awaitCompletion(service, job.id(), PrioritizationJobService.PrioritizationJobStatus.COMPLETED);

		var completed = service.getJob(job.id());
		assertThat(completed.status()).isEqualTo(PrioritizationJobService.PrioritizationJobStatus.COMPLETED);
		assertThat(completed.redirectUrl()).isEqualTo("/suggestedcycles/SQM-SR2?project=B2C");
	}

	@Test
	void shouldCancelRunningJobAndDeleteArtifacts() throws Exception {
		Path artifactFolder = tempDir.resolve("1781542979267_SQM-SR39");
		given(prioritizationService.prioritize(eq(Project.B2C), any(), any(), any())).willAnswer(invocation -> {
			PrioritizationProgressListener listener = invocation.getArgument(2);
			PrioritizationCancellation cancellation = invocation.getArgument(3);
			listener.onArtifactFolderCreated(new PrioritizationArtifactService.ArtifactFolder(
					artifactFolder,
					"1781542979267_SQM-SR39",
					"SQM-SR39",
					1L));
			while (!cancellation.isCancelled()) {
				Thread.sleep(25);
			}
			throw new PrioritizationCancelledException();
		});

		var service = new PrioritizationJobService(prioritizationService, artifactService);
		var job = service.start(Project.B2C, List.of("SQM-T1", "SQM-T2"));

		awaitArtifactFolder(service, job.id(), artifactFolder);

		service.cancel(job.id());
		awaitCompletion(service, job.id(), PrioritizationJobService.PrioritizationJobStatus.CANCELLED);

		assertThat(service.getJob(job.id()).status())
				.isEqualTo(PrioritizationJobService.PrioritizationJobStatus.CANCELLED);
		verify(artifactService).deleteArtifactFolder(artifactFolder);
	}

	@Test
	void shouldRejectCancelForCompletedJob() throws Exception {
		var cycle = SuggestedTestCycle.create(
				"SQM-SR2",
				"Cycle",
				"SQM",
				1L,
				"folder",
				List.of());
		given(prioritizationService.prioritize(eq(Project.B2C), any(), any(), any())).willReturn(cycle);

		var service = new PrioritizationJobService(prioritizationService, artifactService);
		var job = service.start(Project.B2C, List.of("SQM-T1", "SQM-T2"));
		awaitCompletion(service, job.id(), PrioritizationJobService.PrioritizationJobStatus.COMPLETED);

		assertThatThrownBy(() -> service.cancel(job.id()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("cannot be cancelled");
	}

	private static void awaitCompletion(
			PrioritizationJobService service,
			String jobId,
			PrioritizationJobService.PrioritizationJobStatus expectedStatus) throws InterruptedException {
		for (int attempt = 0; attempt < 200; attempt++) {
			var status = service.getJob(jobId).status();
			if (status == expectedStatus) {
				return;
			}
			TimeUnit.MILLISECONDS.sleep(25);
		}
		throw new IllegalStateException("Job did not reach status " + expectedStatus);
	}

	private static void awaitArtifactFolder(
			PrioritizationJobService service,
			String jobId,
			Path artifactFolder) throws InterruptedException {
		for (int attempt = 0; attempt < 200; attempt++) {
			if (artifactFolder.equals(service.getJob(jobId).artifactFolder())) {
				return;
			}
			TimeUnit.MILLISECONDS.sleep(25);
		}
		throw new IllegalStateException("Artifact folder was not registered on job");
	}
}
