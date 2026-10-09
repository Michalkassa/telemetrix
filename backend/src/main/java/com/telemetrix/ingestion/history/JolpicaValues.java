package com.telemetrix.ingestion.history;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Turns the dump's text values into Java values. Empty means "no value" (null) everywhere.
final class JolpicaValues {

	// hh:mm:ss with 0-3 decimals: "01:31:44.742", "02:13:23.6", "00:00:24"
	private static final Pattern TIME = Pattern.compile("(\\d{2}):([0-5]\\d):([0-5]\\d)(?:\\.(\\d{1,3}))?");

	private JolpicaValues() {
	}

	static Long millis(String time) {
		if (time == null || time.isBlank()) {
			return null;
		}
		Matcher m = TIME.matcher(time.strip());
		if (!m.matches()) {
			throw new IllegalArgumentException("Not a Jolpica time (hh:mm:ss.fff): '" + time + "'");
		}
		String fraction = m.group(4) == null ? "0" : (m.group(4) + "00").substring(0, 3);
		return ((Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2))) * 60 + Long.parseLong(m.group(3)))
				* 1000 + Long.parseLong(fraction);
	}

	static Integer integer(String value) {
		return blank(value) ? null : Integer.valueOf(value.strip());
	}

	static BigDecimal decimal(String value) {
		return blank(value) ? null : new BigDecimal(value.strip());
	}

	static Boolean bool(String value) {
		return blank(value) ? null : "t".equals(value.strip());
	}

	static LocalDate date(String value) {
		return blank(value) ? null : LocalDate.parse(value.strip());
	}

	static LocalTime time(String value) {
		return blank(value) ? null : LocalTime.parse(value.strip());
	}

	static String text(String value) {
		return blank(value) ? null : value.strip();
	}

	static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
