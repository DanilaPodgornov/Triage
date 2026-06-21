package com.qligent.triage.web;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.qligent.triage.config.RuntimeCredentials;
import com.qligent.triage.config.SecretsFileWriter;

@Controller
public class SetupController {

	private final RuntimeCredentials runtimeCredentials;
	private final SecretsFileWriter secretsFileWriter;

	public SetupController(RuntimeCredentials runtimeCredentials, SecretsFileWriter secretsFileWriter) {
		this.runtimeCredentials = runtimeCredentials;
		this.secretsFileWriter = secretsFileWriter;
	}

	@GetMapping("/setup")
	public String setup(Model model) {
		model.addAttribute("zephyrApiToken", runtimeCredentials.zephyrApiToken().orElse(""));
		model.addAttribute("jiraApiUrl", runtimeCredentials.jiraApiUrl().orElse(""));
		model.addAttribute("jiraEmail", runtimeCredentials.jiraEmail().orElse(""));
		model.addAttribute("jiraApiToken", runtimeCredentials.jiraApiToken().orElse(""));
		return "startup";
	}

	@PostMapping("/setup")
	public String submit(
			@RequestParam String zephyrApiToken,
			@RequestParam(required = false) String jiraApiUrl,
			@RequestParam(required = false) String jiraEmail,
			@RequestParam(required = false) String jiraApiToken,
			@RequestParam(defaultValue = "false") boolean remember,
			Model model) {
		if (zephyrApiToken == null || zephyrApiToken.isBlank()) {
			model.addAttribute("error", "setup.error.zephyrRequired");
			model.addAttribute("zephyrApiToken", zephyrApiToken != null ? zephyrApiToken : "");
			model.addAttribute("jiraApiUrl", jiraApiUrl != null ? jiraApiUrl : "");
			model.addAttribute("jiraEmail", jiraEmail != null ? jiraEmail : "");
			model.addAttribute("jiraApiToken", jiraApiToken != null ? jiraApiToken : "");
			return "startup";
		}

		runtimeCredentials.update(zephyrApiToken, jiraApiUrl, jiraEmail, jiraApiToken);

		if (remember) {
			try {
				secretsFileWriter.write(zephyrApiToken, jiraApiUrl, jiraEmail, jiraApiToken);
			} catch (IOException exception) {
				model.addAttribute("error", "setup.error.saveFailed");
				model.addAttribute("zephyrApiToken", zephyrApiToken);
				model.addAttribute("jiraApiUrl", jiraApiUrl != null ? jiraApiUrl : "");
				model.addAttribute("jiraEmail", jiraEmail != null ? jiraEmail : "");
				model.addAttribute("jiraApiToken", jiraApiToken != null ? jiraApiToken : "");
				return "startup";
			}
		}

		return "redirect:/testcases?project=B2C";
	}
}
