package com.qligent.triage.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.qligent.triage.model.Project;
import com.qligent.triage.suggested.SuggestedTestCycleService;
import com.qligent.triage.web.api.SaveOrderRequest;

@Controller
public class SuggestedTestCycleController {

	private final SuggestedTestCycleService suggestedTestCycleService;

	public SuggestedTestCycleController(SuggestedTestCycleService suggestedTestCycleService) {
		this.suggestedTestCycleService = suggestedTestCycleService;
	}

	@GetMapping("/suggestedcycles")
	public String list(@RequestParam(defaultValue = "B2C") String project, Model model) {
		Project selectedProject = Project.fromLabel(project);
		model.addAttribute("activeTab", "suggestedcycles");
		model.addAttribute("project", selectedProject.label());
		model.addAttribute("cycles", suggestedTestCycleService.listForProject(selectedProject));
		return "suggested-cycles";
	}

	@GetMapping("/suggestedcycles/{cycleKey}")
	public String detail(
			@PathVariable String cycleKey,
			@RequestParam(defaultValue = "B2C") String project,
			Model model) {
		Project selectedProject = Project.fromLabel(project);
		var cycle = suggestedTestCycleService.getByKey(cycleKey);
		model.addAttribute("activeTab", "suggestedcycles");
		model.addAttribute("project", selectedProject.label());
		model.addAttribute("cycle", cycle);
		return "suggested-cycle-detail";
	}

	@PostMapping("/api/suggestedcycles/{cycleKey}/save")
	@ResponseBody
	public void saveOrder(@PathVariable String cycleKey, @RequestBody SaveOrderRequest request) {
		suggestedTestCycleService.saveOrder(cycleKey, request.testCases());
	}

	@PostMapping("/api/suggestedcycles/{cycleKey}/send")
	@ResponseBody
	public void sendToZephyr(@PathVariable String cycleKey) {
		suggestedTestCycleService.sendToZephyr(cycleKey);
	}

}
