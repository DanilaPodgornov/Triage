package com.qligent.triage.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class WebModelAdvice {

	@ModelAttribute("currentPath")
	String currentPath(HttpServletRequest request) {
		return request.getRequestURI();
	}

}
