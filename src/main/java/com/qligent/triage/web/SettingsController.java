package com.qligent.triage.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.qligent.triage.model.Project;

@Controller
public class SettingsController {

	@GetMapping("/settings")
	public String settings(@RequestParam(defaultValue = "B2C") String project, Model model) {
		model.addAttribute("activeTab", "settings");
		model.addAttribute("project", Project.fromLabel(project).label());
		model.addAttribute("pageTitle", "Settings");
		return "settings";
	}

}
