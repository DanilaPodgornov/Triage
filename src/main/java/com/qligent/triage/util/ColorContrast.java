package com.qligent.triage.util;

public final class ColorContrast {

	private ColorContrast() {
	}

	public static String textColorForBackground(String hexColor) {
		if (hexColor == null || hexColor.isBlank()) {
			return "#111827";
		}
		String normalized = hexColor.trim();
		if (!normalized.startsWith("#")) {
			normalized = "#" + normalized;
		}
		if (normalized.length() != 7 && normalized.length() != 4) {
			return "#111827";
		}
		int red;
		int green;
		int blue;
		if (normalized.length() == 4) {
			red = Integer.parseInt(normalized.substring(1, 2).repeat(2), 16);
			green = Integer.parseInt(normalized.substring(2, 3).repeat(2), 16);
			blue = Integer.parseInt(normalized.substring(3, 4).repeat(2), 16);
		} else {
			red = Integer.parseInt(normalized.substring(1, 3), 16);
			green = Integer.parseInt(normalized.substring(3, 5), 16);
			blue = Integer.parseInt(normalized.substring(5, 7), 16);
		}
		double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0;
		return luminance > 0.6 ? "#111827" : "#ffffff";
	}

}
