package com.societyledger.common;

import java.util.List;

/** Writes CSV rows (RFC 4180) that are safe to open in a spreadsheet. */
public final class Csv {

	private Csv() {
	}

	public static String row(List<String> cells) {
		StringBuilder row = new StringBuilder();
		for (int i = 0; i < cells.size(); i++) {
			if (i > 0) {
				row.append(',');
			}
			row.append(cell(cells.get(i)));
		}
		return row.append("\r\n").toString();
	}

	/** Whole rupees and two decimals, for example 1600.50. Not for calculation. */
	public static String rupees(Money money) {
		return "%d.%02d".formatted(money.paise() / 100, money.paise() % 100);
	}

	private static String cell(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		String text = runsAsFormula(value.charAt(0)) ? "'" + value : value;
		if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
			return "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}

	/** A cell starting with one of these is run as a formula by Excel and Sheets. */
	private static boolean runsAsFormula(char first) {
		return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t';
	}
}
