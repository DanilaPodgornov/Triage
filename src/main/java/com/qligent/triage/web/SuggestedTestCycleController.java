package com.qligent.triage.web;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.qligent.triage.config.TriageProperties;
import com.qligent.triage.model.Project;
import com.qligent.triage.suggested.SuggestedTestCycleService;
import com.qligent.triage.web.api.SaveSortStateRequest;
import com.qligent.triage.web.api.SendToZephyrResponse;
import com.qligent.triage.web.model.SuggestedCyclesPageView;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

@Controller
public class SuggestedTestCycleController {

	private static final java.util.Set<Integer> ALLOWED_PAGE_SIZE_MULTIPLIERS = java.util.Set.of(1, 2, 4);

	private final SuggestedTestCycleService suggestedTestCycleService;
	private final ObjectMapper objectMapper;
	private final TriageProperties triageProperties;

	public SuggestedTestCycleController(
			SuggestedTestCycleService suggestedTestCycleService,
			ObjectMapper objectMapper,
			TriageProperties triageProperties) {
		this.suggestedTestCycleService = suggestedTestCycleService;
		this.objectMapper = objectMapper;
		this.triageProperties = triageProperties;
	}

	@GetMapping("/suggestedcycles")
	public String list(
			@RequestParam(defaultValue = "B2C") String project,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size,
			Model model) {
		Project selectedProject = Project.fromLabel(project);
		int basePageSize = triageProperties.testcasesPageSize();
		int pageSize = normalizePageSize(size, basePageSize);
		SuggestedCyclesPageView pageView = suggestedTestCycleService.listPageForProject(selectedProject, page, pageSize);
		model.addAttribute("activeTab", "suggestedcycles");
		model.addAttribute("project", selectedProject.label());
		model.addAttribute("pageView", pageView);
		model.addAttribute("basePageSize", basePageSize);
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
		suggestedTestCycleService.saveSortState(
				cycleKey,
				request.sortField(),
				request.sortDirection(),
				request.suggestedTestCycleName());
	}

	@PostMapping("/api/suggestedcycles/{cycleKey}/send")
	@ResponseBody
	public SendToZephyrResponse sendToZephyr(@PathVariable String cycleKey) {
		String zephyrTestCycleKey = suggestedTestCycleService.sendToZephyr(cycleKey);
		return new SendToZephyrResponse(zephyrTestCycleKey);
	}

	@GetMapping("/api/suggestedcycles/{cycleKey}/json")
	public ResponseEntity<Resource> downloadCycleJson(@PathVariable String cycleKey) {
		Path file = suggestedTestCycleService.getCycleJsonFile(cycleKey);
		Resource resource = new FileSystemResource(file);
		String filename = cycleKey + ".json";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.contentType(MediaType.APPLICATION_JSON)
				.body(resource);
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
