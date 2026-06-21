package com.qligent.triage.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.qligent.triage.config.RuntimeCredentials;

@Controller
public class HomeController {

	private final RuntimeCredentials runtimeCredentials;

	public HomeController(RuntimeCredentials runtimeCredentials) {
		this.runtimeCredentials = runtimeCredentials;
	}

	@GetMapping("/")
	public String home() {
		if (!runtimeCredentials.isZephyrConfigured()) {
			return "redirect:/setup";
		}
		return "redirect:/testcases?project=B2C";
	}

}
