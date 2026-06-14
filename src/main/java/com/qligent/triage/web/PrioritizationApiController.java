package com.qligent.triage.web;

import org.springframework.http.ResponseEntity;
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

	@GetMapping("/jobs/{jobId}")
	public ResponseEntity<PrioritizationJobStatusResponse> getJobStatus(@PathVariable String jobId) {
		PrioritizationJob job = prioritizationJobService.getJob(jobId);
		return ResponseEntity.ok(new PrioritizationJobStatusResponse(
				job.status().name(),
				job.completedTests(),
				job.totalTests(),
				job.elapsedMillis(),
				job.redirectUrl(),
				job.errorMessage()));
	}
}
