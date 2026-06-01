package com.qligent.triage.web;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientException;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class WebExceptionHandler {

	@ExceptionHandler(RestClientException.class)
	public String handleZephyrApiError(HttpServletRequest request, Model model) {
		prepareErrorModel(request, model, "Failed to load data from Zephyr Scale API. Check API token and connectivity.");
		return "error";
	}

	@ExceptionHandler(IllegalStateException.class)
	public String handleConfigurationError(IllegalStateException exception, HttpServletRequest request, Model model) {
		prepareErrorModel(request, model, exception.getMessage());
		return "error";
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public String handleBadRequest(IllegalArgumentException exception, HttpServletRequest request, Model model) {
		prepareErrorModel(request, model, exception.getMessage());
		return "error";
	}

	private void prepareErrorModel(HttpServletRequest request, Model model, String message) {
		model.addAttribute("error", message);
		if (!model.containsAttribute("project")) {
			String project = request.getParameter("project");
			model.addAttribute("project", project == null || project.isBlank() ? "B2C" : project);
		}
		if (!model.containsAttribute("activeTab")) {
			model.addAttribute("activeTab", resolveActiveTab(request.getRequestURI()));
		}
	}

	private String resolveActiveTab(String requestUri) {
		if (requestUri.contains("/testcycles")) {
			return "testcycles";
		}
		if (requestUri.contains("/suggestedcycles")) {
			return "suggestedcycles";
		}
		return "testcases";
	}

}
