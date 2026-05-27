package com.qligent.triage.web;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.qligent.triage.model.Project;
import com.qligent.triage.service.TestCaseService;

@Controller
public class TestCaseController {

	private final TestCaseService testCaseService;

	public TestCaseController(TestCaseService testCaseService) {
		this.testCaseService = testCaseService;
	}

	@GetMapping("/testcases")
	public String testCases(
			@RequestParam(defaultValue = "B2C") String project,
			@RequestParam(required = false) Long priority,
			@RequestParam(defaultValue = "0") int page,
			Model model) {
		Project selectedProject = Project.fromLabel(project);
		var pageView = testCaseService.getTestCasesPage(selectedProject, Optional.ofNullable(priority), page);

		model.addAttribute("activeTab", "testcases");
		model.addAttribute("project", selectedProject.label());
		model.addAttribute("pageView", pageView);
		return "testcases";
	}

}
