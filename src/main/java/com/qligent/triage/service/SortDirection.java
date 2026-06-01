package com.qligent.triage.service;

public enum SortDirection {

	ASC,
	DESC;

	public static SortDirection fromParam(String value) {
		if ("desc".equalsIgnoreCase(value)) {
			return DESC;
		}
		return ASC;
	}

}
