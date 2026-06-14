package com.qligent.triage.prioritization;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import com.qligent.triage.model.Project;

@Service
public class PrioritizationJobService {

	private final PrioritizationService prioritizationService;
	private final Map<String, PrioritizationJob> jobs = new ConcurrentHashMap<>();
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public PrioritizationJobService(PrioritizationService prioritizationService) {
		this.prioritizationService = prioritizationService;
	}

	public PrioritizationJob start(Project project, List<String> testCaseKeys) {
		String jobId = UUID.randomUUID().toString();
		PrioritizationJob job = PrioritizationJob.running(jobId, testCaseKeys.size());
		jobs.put(jobId, job);
		executor.submit(() -> runJob(job, project, testCaseKeys));
		return job;
	}

	public PrioritizationJob getJob(String jobId) {
		PrioritizationJob job = jobs.get(jobId);
		if (job == null) {
			throw new IllegalArgumentException("Prioritization job not found: " + jobId);
		}
		return job;
	}

	private void runJob(PrioritizationJob job, Project project, List<String> testCaseKeys) {
		try {
			var cycle = prioritizationService.prioritize(project, testCaseKeys, new PrioritizationProgressListener() {
				@Override
				public void onTestResponseCompleted(int completedTests, int totalTests) {
					job.updateTestProgress(completedTests, totalTests);
				}

				@Override
				public void onAiSortStarted() {
					job.markAiSortStarted();
				}
			});
			String redirectUrl = "/suggestedcycles/" + cycle.suggestedTestCycleKey()
					+ "?project=" + project.label();
			job.complete(redirectUrl);
		} catch (Exception exception) {
			job.fail(exception.getMessage() == null ? "Prioritization failed." : exception.getMessage());
		}
	}

	public static final class PrioritizationJob {

		private final String id;
		private final int totalTests;
		private final Instant startedAt;
		private volatile PrioritizationJobStatus status;
		private volatile int completedTests;
		private volatile String redirectUrl;
		private volatile String errorMessage;
		private volatile Instant finishedAt;

		private PrioritizationJob(String id, int totalTests, Instant startedAt, PrioritizationJobStatus status) {
			this.id = id;
			this.totalTests = totalTests;
			this.startedAt = startedAt;
			this.status = status;
		}

		static PrioritizationJob running(String id, int totalTests) {
			return new PrioritizationJob(id, totalTests, Instant.now(), PrioritizationJobStatus.RUNNING);
		}

		void updateTestProgress(int completedTests, int totalTests) {
			this.completedTests = completedTests;
			this.status = PrioritizationJobStatus.RUNNING;
		}

		void markAiSortStarted() {
			this.status = PrioritizationJobStatus.AI_SORT;
		}

		void complete(String redirectUrl) {
			this.redirectUrl = redirectUrl;
			this.status = PrioritizationJobStatus.COMPLETED;
			this.finishedAt = Instant.now();
		}

		void fail(String errorMessage) {
			this.errorMessage = errorMessage;
			this.status = PrioritizationJobStatus.FAILED;
			this.finishedAt = Instant.now();
		}

		public String id() {
			return id;
		}

		public int totalTests() {
			return totalTests;
		}

		public PrioritizationJobStatus status() {
			return status;
		}

		public int completedTests() {
			return completedTests;
		}

		public long elapsedMillis() {
			Instant end = finishedAt == null ? Instant.now() : finishedAt;
			return end.toEpochMilli() - startedAt.toEpochMilli();
		}

		public String redirectUrl() {
			return redirectUrl;
		}

		public String errorMessage() {
			return errorMessage;
		}
	}

	public enum PrioritizationJobStatus {
		RUNNING,
		AI_SORT,
		COMPLETED,
		FAILED
	}
}
