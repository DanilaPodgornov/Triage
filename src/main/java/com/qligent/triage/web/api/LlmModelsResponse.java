package com.qligent.triage.web.api;

import java.util.List;

import com.qligent.triage.llm.dto.OllamaTagsResponse;

public record LlmModelsResponse(
		List<OllamaTagsResponse.ModelTag> models,
		String activeModel,
		int numCtx) {
}
