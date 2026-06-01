package com.qligent.triage.web;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.qligent.triage.model.Project;
import com.qligent.triage.service.SortDirection;
import com.qligent.triage.service.TestCaseListQuery;
import com.qligent.triage.service.TestCaseService;
import com.qligent.triage.service.TestCaseSort;

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
			@RequestParam(required = false) Long folder,
			@RequestParam(required = false) Long status,
			@RequestParam(defaultValue = "key") String sort,
			@RequestParam(defaultValue = "asc") String dir,
			@RequestParam(defaultValue = "0") int page,
			Model model) {
		Project selectedProject = Project.fromLabel(project);
		var query = new TestCaseListQuery(
				Optional.ofNullable(priority),
				Optional.ofNullable(folder),
				Optional.ofNullable(status),
				TestCaseSort.fromParam(sort),
				SortDirection.fromParam(dir),
				page);
		var pageView = testCaseService.getTestCasesPage(selectedProject, query);

		model.addAttribute("activeTab", "testcases");
		model.addAttribute("project", selectedProject.label());
		model.addAttribute("pageView", pageView);
		return "testcases";
	}

}
