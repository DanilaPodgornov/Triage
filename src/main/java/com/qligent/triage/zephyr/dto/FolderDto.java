package com.qligent.triage.zephyr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FolderDto(
		long id,
		Long parentId,
		String name,
		String folderType
) {
}
