package com.qligent.triage.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qligent.triage.model.Project;
import com.qligent.triage.prioritization.PrioritizationJobService;
import com.qligent.triage.prioritization.PrioritizationJobService.PrioritizationJob;
import com.qligent.triage.web.api.PrioritizationJobStatusResponse;
import com.qligent.triage.web.api.PrioritizeJobResponse;
import com.qligent.triage.web.api.PrioritizeRequest;

@RestController
@RequestMapping("/api/prioritize")
public class PrioritizationApiController {

	private final PrioritizationJobService prioritizationJobService;

	public PrioritizationApiController(PrioritizationJobService prioritizationJobService) {
		this.prioritizationJobService = prioritizationJobService;
	}

	@PostMapping
	public ResponseEntity<PrioritizeJobResponse> startPrioritization(@RequestBody PrioritizeRequest request) {
		Project project = Project.fromLabel(request.project() == null ? "B2C" : request.project());
		PrioritizationJob job = prioritizationJobService.start(project, request.testCaseKeys());
		return ResponseEntity.accepted().body(new PrioritizeJobResponse(job.id(), job.totalTests()));
	}

	@DeleteMapping("/jobs/{jobId}")
	public ResponseEntity<?> cancelPrioritization(@PathVariable String jobId) {
		try {
			PrioritizationJob job = prioritizationJobService.cancel(jobId);
			return ResponseEntity.ok(toStatusResponse(job));
		} catch (IllegalStateException exception) {
			return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
					.body(new com.qligent.triage.web.api.ApiErrorResponse(exception.getMessage()));
		}
	}

	@GetMapping("/jobs/{jobId}")
	public ResponseEntity<PrioritizationJobStatusResponse> getJobStatus(@PathVariable String jobId) {
		PrioritizationJob job = prioritizationJobService.getJob(jobId);
		return ResponseEntity.ok(toStatusResponse(job));
	}

	private static PrioritizationJobStatusResponse toStatusResponse(PrioritizationJob job) {
		var snapshot = job.snapshot();
		return new PrioritizationJobStatusResponse(
				snapshot.status().name(),
				snapshot.completedTests(),
				snapshot.totalTests(),
				snapshot.currentTest(),
				snapshot.currentTestCaseKey(),
				snapshot.elapsedMillis(),
				snapshot.cycleKey(),
				snapshot.redirectUrl(),
				snapshot.errorMessage());
	}
}
