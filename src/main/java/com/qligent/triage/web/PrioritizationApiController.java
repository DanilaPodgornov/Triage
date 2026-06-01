package com.qligent.triage.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qligent.triage.model.Project;
import com.qligent.triage.prioritization.PrioritizationService;
import com.qligent.triage.web.api.PrioritizeRequest;
import com.qligent.triage.web.api.PrioritizeResponse;

@RestController
@RequestMapping("/api/prioritize")
public class PrioritizationApiController {

	private final PrioritizationService prioritizationService;

	public PrioritizationApiController(PrioritizationService prioritizationService) {
		this.prioritizationService = prioritizationService;
	}

	@PostMapping
	public ResponseEntity<PrioritizeResponse> prioritize(@RequestBody PrioritizeRequest request) {
		Project project = Project.fromLabel(request.project() == null ? "B2C" : request.project());
		var cycle = prioritizationService.prioritize(project, request.testCaseKeys());
		String redirectUrl = "/suggestedcycles?project=" + project.label();
		return ResponseEntity.ok(new PrioritizeResponse(cycle.suggestedTestCycleKey(), redirectUrl));
	}

}
