package com.qligent.triage.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientException;

import com.qligent.triage.web.api.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class WebExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

	@ExceptionHandler(RestClientException.class)
	public Object handleRestClientError(
			RestClientException exception,
			HttpServletRequest request,
			Model model) {
		log.error("External API request failed for {}: {}", request.getRequestURI(), exception.getMessage(), exception);
		String message = resolveRestClientMessage(exception, request);
		if (isApiRequest(request)) {
			return apiError(HttpStatus.BAD_GATEWAY, message);
		}
		prepareErrorModel(request, model, message);
		return "error";
	}

	@ExceptionHandler(IllegalStateException.class)
	public Object handleConfigurationError(
			IllegalStateException exception,
			HttpServletRequest request,
			Model model) {
		if (isApiRequest(request)) {
			return apiError(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
		}
		prepareErrorModel(request, model, exception.getMessage());
		return "error";
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public Object handleBadRequest(
			IllegalArgumentException exception,
			HttpServletRequest request,
			Model model) {
		if (isApiRequest(request)) {
			return apiError(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
		prepareErrorModel(request, model, exception.getMessage());
		return "error";
	}

	private static boolean isApiRequest(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return uri != null && uri.startsWith("/api/");
	}

	private static ResponseEntity<ApiErrorResponse> apiError(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(message));
	}

	private static String resolveRestClientMessage(RestClientException exception, HttpServletRequest request) {
		String rawMessage = exception.getMessage() == null ? "" : exception.getMessage();
		String message = rawMessage.toLowerCase();
		if (request.getRequestURI().contains("/api/prioritize") && isOllamaFailure(message)) {
			return "Ollama is not available or did not respond. Start Ollama and ensure model "
					+ "qwen2.5:3b is installed (ollama pull qwen2.5:3b).";
		}
		if (message.contains("401")) {
			return "Zephyr Scale API rejected the token. Check zephyr.api-token in secrets.yaml.";
		}
		if (message.contains("429")) {
			return "Zephyr Scale API rate limit reached. Try fewer test cases or retry in a minute.";
		}
		if (request.getRequestURI().contains("/api/llm")) {
			return "Ollama is not available or did not respond. Start Ollama and ensure the configured model is installed.";
		}
		if (rawMessage.isBlank()) {
			return "Failed to load data from Zephyr Scale API. Check API token and connectivity.";
		}
		return "Zephyr Scale API error: " + rawMessage;
	}

	private static boolean isOllamaFailure(String message) {
		return message.contains("11434")
				|| message.contains("connection refused")
				|| message.contains("ollama");
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
		if (requestUri.contains("/settings")) {
			return "settings";
		}
		return "testcases";
	}

}
