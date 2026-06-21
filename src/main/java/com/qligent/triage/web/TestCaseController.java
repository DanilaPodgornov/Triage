package com.qligent.triage.web;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.qligent.triage.config.TriageProperties;
import com.qligent.triage.llm.LlmPromptTemplateService;
import com.qligent.triage.model.Project;
import com.qligent.triage.service.SortDirection;
import com.qligent.triage.service.TestCaseListQuery;
import com.qligent.triage.service.TestCaseService;
import com.qligent.triage.service.TestCaseSort;

@Controller
public class TestCaseController {

	private static final Set<Integer> ALLOWED_PAGE_SIZE_MULTIPLIERS = Set.of(1, 2, 4);

	private final TestCaseService testCaseService;
	private final LlmPromptTemplateService llmPromptTemplateService;
	private final TriageProperties triageProperties;

	public TestCaseController(
			TestCaseService testCaseService,
			LlmPromptTemplateService llmPromptTemplateService,
			TriageProperties triageProperties) {
		this.testCaseService = testCaseService;
		this.llmPromptTemplateService = llmPromptTemplateService;
		this.triageProperties = triageProperties;
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
			@RequestParam(required = false) Integer size,
			@RequestParam(defaultValue = "false") boolean show,
			Model model) {
		Project selectedProject = Project.fromLabel(project);
		int basePageSize = triageProperties.testcasesPageSize();
		int pageSize = normalizePageSize(size, basePageSize);
		var query = new TestCaseListQuery(
				Optional.ofNullable(priority),
				Optional.ofNullable(folder),
				Optional.ofNullable(status),
				TestCaseSort.fromParam(sort),
				SortDirection.fromParam(dir),
				page,
				pageSize);
		var pageView = testCaseService.getTestCasesPage(selectedProject, query, show);

		model.addAttribute("activeTab", "testcases");
		model.addAttribute("project", selectedProject.label());
		model.addAttribute("pageView", pageView);
		model.addAttribute("showTests", show);
		model.addAttribute("defaultLlmPrompt", llmPromptTemplateService.getTemplate());
		model.addAttribute("basePageSize", basePageSize);
		return "testcases";
	}

	private static int normalizePageSize(Integer size, int basePageSize) {
		if (size == null || size <= 0) {
			return basePageSize;
		}
		for (int multiplier : ALLOWED_PAGE_SIZE_MULTIPLIERS) {
			if (size == basePageSize * multiplier) {
				return size;
			}
		}
		return basePageSize;
	}

}
