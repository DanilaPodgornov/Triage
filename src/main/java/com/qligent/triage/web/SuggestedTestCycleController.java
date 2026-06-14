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
import com.qligent.triage.web.api.SaveSortStateRequest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Controller
public class SuggestedTestCycleController {

	private final SuggestedTestCycleService suggestedTestCycleService;
	private final ObjectMapper objectMapper;

	public SuggestedTestCycleController(
			SuggestedTestCycleService suggestedTestCycleService,
			ObjectMapper objectMapper) {
		this.suggestedTestCycleService = suggestedTestCycleService;
		this.objectMapper = objectMapper;
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
		model.addAttribute("testsJson", serializeTests(cycle.tests()));
		return "suggested-cycle-detail";
	}

	private String serializeTests(java.util.List<com.qligent.triage.suggested.SuggestedTestCycle.SuggestedTestCaseEntry> tests) {
		try {
			return objectMapper.writeValueAsString(tests);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize suggested test cycle tests.", exception);
		}
	}

	@PostMapping("/api/suggestedcycles/{cycleKey}/save")
	@ResponseBody
	public void saveSortState(@PathVariable String cycleKey, @RequestBody SaveSortStateRequest request) {
		suggestedTestCycleService.saveSortState(cycleKey, request.sortField(), request.sortDirection());
	}

	@PostMapping("/api/suggestedcycles/{cycleKey}/send")
	@ResponseBody
	public void sendToZephyr(@PathVariable String cycleKey) {
		suggestedTestCycleService.sendToZephyr(cycleKey);
	}

}
