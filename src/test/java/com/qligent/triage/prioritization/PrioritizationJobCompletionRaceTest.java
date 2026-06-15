package com.qligent.triage.prioritization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PrioritizationJobCompletionRaceTest {

	@Test
	void shouldNotExposeCompletedStatusWithoutRedirectUrl() throws Exception {
		var job = PrioritizationJobService.PrioritizationJob.running("job-1", 2);
		var startGate = new CountDownLatch(1);
		var doneGate = new CountDownLatch(1);
		var violations = new AtomicInteger();

		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < 500; i++) {
				pool.submit(() -> {
					try {
						startGate.await(5, TimeUnit.SECONDS);
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						return;
					}
					for (int read = 0; read < 100; read++) {
						if (job.status() == PrioritizationJobService.PrioritizationJobStatus.COMPLETED
								&& job.redirectUrl() == null) {
							violations.incrementAndGet();
						}
					}
					doneGate.countDown();
				});
			}

			startGate.countDown();
			for (int attempt = 0; attempt < 100; attempt++) {
				job.complete("SQM-SR2", "/suggestedcycles/SQM-SR2?project=B2C");
				if (job.status() == PrioritizationJobService.PrioritizationJobStatus.COMPLETED
						&& job.redirectUrl() != null) {
					break;
				}
			}

			doneGate.await(5, TimeUnit.SECONDS);
		}

		assertThat(violations).hasValue(0);
		assertThat(job.redirectUrl()).isEqualTo("/suggestedcycles/SQM-SR2?project=B2C");
	}
}
