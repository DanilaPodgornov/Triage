package com.qligent.triage.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.qligent.triage.config.TriageProperties;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class WebModelAdvice {

	private final TriageProperties triageProperties;

	public WebModelAdvice(TriageProperties triageProperties) {
		this.triageProperties = triageProperties;
	}

	@ModelAttribute("currentPath")
	String currentPath(HttpServletRequest request) {
		return request.getRequestURI();
	}

	@ModelAttribute("defaultTheme")
	String defaultTheme() {
		return triageProperties.theme().defaultTheme();
	}

}
