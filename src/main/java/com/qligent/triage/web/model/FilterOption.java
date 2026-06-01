package com.qligent.triage.web.model;

public record FilterOption(long id, String name, String color) {

	public static FilterOption of(long id, String name) {
		return new FilterOption(id, name, null);
	}
}
