package com.qligent.triage.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.qligent.triage.model.Project;

@Controller
public class StubTabController {

	@GetMapping("/testcycles")
	public String testCycles(@RequestParam(defaultValue = "B2C") String project, Model model) {
		return stubPage(model, project, "testcycles", "Test Cycles");
	}

	private String stubPage(Model model, String project, String activeTab, String title) {
		model.addAttribute("activeTab", activeTab);
		model.addAttribute("project", Project.fromLabel(project).label());
		model.addAttribute("pageTitle", title);
		return "stub-tab";
	}

}
