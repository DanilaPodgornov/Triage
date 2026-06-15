package com.qligent.triage.prioritization;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.stereotype.Service;

import com.qligent.triage.model.Project;

@Service
public class PrioritizationJobService {

	private final PrioritizationService prioritizationService;
	private final PrioritizationArtifactService artifactService;
	private final Map<String, PrioritizationJob> jobs = new ConcurrentHashMap<>();
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public PrioritizationJobService(
			PrioritizationService prioritizationService,
			PrioritizationArtifactService artifactService) {
		this.prioritizationService = prioritizationService;
		this.artifactService = artifactService;
	}

	public PrioritizationJob start(Project project, List<String> testCaseKeys) {
		String jobId = UUID.randomUUID().toString();
		PrioritizationCancellation cancellation = new PrioritizationCancellation();
		PrioritizationJob job = PrioritizationJob.running(jobId, testCaseKeys.size(), cancellation);
		jobs.put(jobId, job);
		Future<?> future = executor.submit(() -> runJob(job, project, testCaseKeys, cancellation));
		job.attachFuture(future);
		return job;
	}

	public PrioritizationJob getJob(String jobId) {
		PrioritizationJob job = jobs.get(jobId);
		if (job == null) {
			throw new IllegalArgumentException("Prioritization job not found: " + jobId);
		}
		return job;
	}

	public PrioritizationJob cancel(String jobId) {
		PrioritizationJob job = getJob(jobId);
		if (!job.isCancellable()) {
			throw new IllegalStateException("Prioritization job cannot be cancelled: " + job.status());
		}
		job.cancellation().cancel();
		Future<?> future = job.future();
		if (future != null) {
			future.cancel(true);
		}
		return job;
	}

	private void runJob(
			PrioritizationJob job,
			Project project,
			List<String> testCaseKeys,
			PrioritizationCancellation cancellation) {
		try {
			var cycle = prioritizationService.prioritize(project, testCaseKeys, new PrioritizationProgressListener() {
				@Override
				public void onArtifactFolderCreated(PrioritizationArtifactService.ArtifactFolder folder) {
					job.setArtifactFolder(folder.path());
				}

				@Override
				public void onTestStarted(int currentTest, int totalTests, String testCaseKey) {
					job.markTestProcessing(currentTest, testCaseKey);
				}

				@Override
				public void onTestResponseCompleted(int completedTests, int totalTests) {
					job.updateTestProgress(completedTests, totalTests);
				}

				@Override
				public void onAiSortStarted() {
					job.markAiSortStarted();
				}
			}, cancellation);
			if (cancellation.isCancelled()) {
				cleanupArtifacts(job);
				job.cancel();
				return;
			}
			String cycleKey = cycle.suggestedTestCycleKey();
			String redirectUrl = "/suggestedcycles/" + cycleKey + "?project=" + project.label();
			job.complete(cycleKey, redirectUrl);
		} catch (PrioritizationCancelledException exception) {
			cleanupArtifacts(job);
			job.cancel();
		} catch (Exception exception) {
			if (cancellation.isCancelled()) {
				cleanupArtifacts(job);
				job.cancel();
				return;
			}
			job.fail(exception.getMessage() == null ? "Prioritization failed." : exception.getMessage());
		}
	}

	private void cleanupArtifacts(PrioritizationJob job) {
		Path artifactFolder = job.artifactFolder();
		if (artifactFolder != null) {
			artifactService.deleteArtifactFolder(artifactFolder);
		}
	}

	public static final class PrioritizationJob {

		private final String id;
		private final int totalTests;
		private final Instant startedAt;
		private final PrioritizationCancellation cancellation;
		private volatile PrioritizationJobStatus status;
		private volatile int completedTests;
		private volatile int currentTest;
		private volatile String currentTestCaseKey;
		private volatile Path artifactFolder;
		private volatile Future<?> future;
		private volatile String cycleKey;
		private volatile String redirectUrl;
		private volatile String errorMessage;
		private volatile Instant finishedAt;

		private PrioritizationJob(
				String id,
				int totalTests,
				Instant startedAt,
				PrioritizationJobStatus status,
				PrioritizationCancellation cancellation) {
			this.id = id;
			this.totalTests = totalTests;
			this.startedAt = startedAt;
			this.status = status;
			this.cancellation = cancellation;
		}

		static PrioritizationJob running(String id, int totalTests, PrioritizationCancellation cancellation) {
			return new PrioritizationJob(id, totalTests, Instant.now(), PrioritizationJobStatus.RUNNING, cancellation);
		}

		void attachFuture(Future<?> future) {
			this.future = future;
		}

		void setArtifactFolder(Path artifactFolder) {
			this.artifactFolder = artifactFolder;
		}

		synchronized void markTestProcessing(int currentTest, String testCaseKey) {
			this.currentTest = currentTest;
			this.currentTestCaseKey = testCaseKey;
			this.status = PrioritizationJobStatus.RUNNING;
		}

		synchronized void updateTestProgress(int completedTests, int totalTests) {
			this.completedTests = completedTests;
			this.status = PrioritizationJobStatus.RUNNING;
		}

		synchronized void markAiSortStarted() {
			this.status = PrioritizationJobStatus.AI_SORT;
		}

		synchronized void complete(String cycleKey, String redirectUrl) {
			this.cycleKey = cycleKey;
			this.redirectUrl = redirectUrl;
			this.status = PrioritizationJobStatus.COMPLETED;
			this.finishedAt = Instant.now();
		}

		synchronized void fail(String errorMessage) {
			this.errorMessage = errorMessage;
			this.status = PrioritizationJobStatus.FAILED;
			this.finishedAt = Instant.now();
		}

		synchronized void cancel() {
			this.status = PrioritizationJobStatus.CANCELLED;
			this.finishedAt = Instant.now();
		}

		boolean isCancellable() {
			PrioritizationJobStatus current = status;
			return current == PrioritizationJobStatus.RUNNING || current == PrioritizationJobStatus.AI_SORT;
		}

		public synchronized JobStatusSnapshot snapshot() {
			Instant end = finishedAt == null ? Instant.now() : finishedAt;
			return new JobStatusSnapshot(
					status,
					completedTests,
					totalTests,
					currentTest,
					currentTestCaseKey,
					end.toEpochMilli() - startedAt.toEpochMilli(),
					cycleKey,
					redirectUrl,
					errorMessage);
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

		PrioritizationCancellation cancellation() {
			return cancellation;
		}

		Future<?> future() {
			return future;
		}

		Path artifactFolder() {
			return artifactFolder;
		}
	}

	public record JobStatusSnapshot(
			PrioritizationJobStatus status,
			int completedTests,
			int totalTests,
			int currentTest,
			String currentTestCaseKey,
			long elapsedMillis,
			String cycleKey,
			String redirectUrl,
			String errorMessage
	) {
	}

	public enum PrioritizationJobStatus {
		RUNNING,
		AI_SORT,
		COMPLETED,
		FAILED,
		CANCELLED
	}
}
