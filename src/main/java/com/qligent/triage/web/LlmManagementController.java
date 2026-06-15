package com.qligent.triage.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qligent.triage.llm.LlmPromptTemplateType;
import com.qligent.triage.llm.LlmRuntimeSettings;
import com.qligent.triage.llm.OllamaModelService;
import com.qligent.triage.llm.PromptTemplateStore;
import com.qligent.triage.web.api.LlmModelsResponse;
import com.qligent.triage.web.api.LlmRuntimeConfigRequest;
import com.qligent.triage.web.api.PromptContentResponse;
import com.qligent.triage.web.api.PromptTemplateResponse;
import com.qligent.triage.web.api.PullModelRequest;
import com.qligent.triage.web.api.SavePromptRequest;
import com.qligent.triage.web.api.SavePromptResponse;
import com.qligent.triage.web.api.SetActiveModelRequest;
import com.qligent.triage.web.api.SetActivePromptVersionRequest;

@RestController
@RequestMapping("/api/llm")
public class LlmManagementController {

	private final OllamaModelService ollamaModelService;
	private final LlmRuntimeSettings runtimeSettings;
	private final PromptTemplateStore promptTemplateStore;

	public LlmManagementController(
			OllamaModelService ollamaModelService,
			LlmRuntimeSettings runtimeSettings,
			PromptTemplateStore promptTemplateStore) {
		this.ollamaModelService = ollamaModelService;
		this.runtimeSettings = runtimeSettings;
		this.promptTemplateStore = promptTemplateStore;
	}

	@GetMapping("/models")
	public ResponseEntity<LlmModelsResponse> listModels() {
		String activeModel = runtimeSettings.activeModel();
		if (activeModel != null && !activeModel.isBlank() && !ollamaModelService.isInstalled(activeModel)) {
			activeModel = null;
		}
		return ResponseEntity.ok(new LlmModelsResponse(
				ollamaModelService.listInstalledOrEmptyOnFailure(),
				activeModel,
				runtimeSettings.numCtx()));
	}

	@PutMapping("/models/active")
	public ResponseEntity<LlmModelsResponse> setActiveModel(@RequestBody SetActiveModelRequest request) {
		String model = request.model();
		if (model == null || model.isBlank()) {
			runtimeSettings.setActiveModel(null);
			return listModels();
		}
		if (!ollamaModelService.isInstalled(model)) {
			throw new IllegalArgumentException("Model is not installed: " + model);
		}
		runtimeSettings.setActiveModel(model);
		return listModels();
	}

	@PostMapping("/models/pull")
	public ResponseEntity<LlmModelsResponse> pullModel(@RequestBody PullModelRequest request) {
		String model = request.model();
		if (model == null || model.isBlank()) {
			throw new IllegalArgumentException("Model name is required.");
		}
		ollamaModelService.pull(model.trim());
		runtimeSettings.setActiveModel(model.trim());
		return listModels();
	}

	@PutMapping("/config")
	public ResponseEntity<LlmModelsResponse> updateConfig(@RequestBody LlmRuntimeConfigRequest request) {
		if (request.numCtx() != null) {
			runtimeSettings.setNumCtx(request.numCtx());
		}
		return listModels();
	}

	@GetMapping("/prompts/{type}")
	public ResponseEntity<PromptTemplateResponse> getPrompt(@PathVariable String type) {
		LlmPromptTemplateType templateType = parseType(type);
		String model = requireActiveModel();
		int numCtx = runtimeSettings.numCtx();
		List<String> versions = promptTemplateStore.listVersions(model, numCtx, templateType);
		String activeVersion = versions.isEmpty()
				? "0.1.0"
				: promptTemplateStore.resolveActiveVersion(
						model,
						numCtx,
						templateType,
						runtimeSettings.activeVersion(templateType));
		if (!activeVersion.equals(runtimeSettings.activeVersion(templateType))) {
			runtimeSettings.setActiveVersion(templateType, activeVersion);
		}
		String content = versions.isEmpty()
				? ""
				: promptTemplateStore.read(model, numCtx, templateType, activeVersion);
		return ResponseEntity.ok(new PromptTemplateResponse(
				templateType.fileSuffix(),
				model,
				versions,
				activeVersion,
				content,
				!versions.isEmpty()));
	}

	@GetMapping("/prompts/{type}/versions/{version}")
	public ResponseEntity<PromptContentResponse> getPromptVersion(
			@PathVariable String type,
			@PathVariable String version) {
		LlmPromptTemplateType templateType = parseType(type);
		String model = requireActiveModel();
		return ResponseEntity.ok(new PromptContentResponse(
				promptTemplateStore.read(runtimeSettings.resolveModel(), runtimeSettings.numCtx(), templateType, version)));
	}

	@PostMapping("/prompts/{type}")
	public ResponseEntity<SavePromptResponse> savePrompt(
			@PathVariable String type,
			@RequestBody SavePromptRequest request) {
		LlmPromptTemplateType templateType = parseType(type);
		String model = requireActiveModel();
		if (request.content() == null) {
			throw new IllegalArgumentException("Prompt content is required.");
		}
		String newVersion = promptTemplateStore.saveNewVersion(
				model,
				runtimeSettings.numCtx(),
				templateType,
				request.content());
		runtimeSettings.setActiveVersion(templateType, newVersion);
		return ResponseEntity.ok(new SavePromptResponse(newVersion));
	}

	@PutMapping("/prompts/{type}/active")
	public ResponseEntity<PromptTemplateResponse> setActivePromptVersion(
			@PathVariable String type,
			@RequestBody SetActivePromptVersionRequest request) {
		LlmPromptTemplateType templateType = parseType(type);
		String model = requireActiveModel();
		String version = request.version();
		if (version == null || version.isBlank()) {
			throw new IllegalArgumentException("Version is required.");
		}
		List<String> versions = promptTemplateStore.listVersions(model, runtimeSettings.numCtx(), templateType);
		if (!versions.contains(version)) {
			throw new IllegalArgumentException("Unknown prompt version: " + version);
		}
		runtimeSettings.setActiveVersion(templateType, version);
		return getPrompt(type);
	}

	private String requireActiveModel() {
		String model = runtimeSettings.activeModel();
		if (model == null || model.isBlank()) {
			throw new IllegalStateException("No active LLM model selected.");
		}
		return model;
	}

	private static LlmPromptTemplateType parseType(String type) {
		if (LlmPromptTemplateType.WEIGHTS.fileSuffix().equalsIgnoreCase(type)) {
			return LlmPromptTemplateType.WEIGHTS;
		}
		if (LlmPromptTemplateType.AI_SORT.fileSuffix().equalsIgnoreCase(type)) {
			return LlmPromptTemplateType.AI_SORT;
		}
		throw new IllegalArgumentException("Unknown prompt type: " + type);
	}
}
