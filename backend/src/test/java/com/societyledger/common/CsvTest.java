package com.societyledger.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CsvTest {

	@Test
	void joins_cells_with_commas_and_ends_each_row_with_a_line_break() {
		assertThat(Csv.row(Arrays.asList("a", "b", "c"))).isEqualTo("a,b,c\r\n");
	}

	@Test
	void shows_empty_cells_as_nothing() {
		assertThat(Csv.row(Arrays.asList("a", null, ""))).isEqualTo("a,,\r\n");
	}

	@Test
	void quotes_a_cell_with_a_comma() {
		assertThat(Csv.row(Arrays.asList("Rao, Asha"))).isEqualTo("\"Rao, Asha\"\r\n");
	}

	@Test
	void doubles_quotes_inside_a_quoted_cell() {
		assertThat(Csv.row(Arrays.asList("Asha \"Big\" Rao"))).isEqualTo("\"Asha \"\"Big\"\" Rao\"\r\n");
	}

	@Test
	void quotes_a_cell_with_a_line_break() {
		assertThat(Csv.row(Arrays.asList("line one\nline two"))).isEqualTo("\"line one\nline two\"\r\n");
	}

	@Test
	void stops_a_spreadsheet_running_a_cell_that_starts_with_an_equals_sign() {
		assertThat(Csv.row(Arrays.asList("=HYPERLINK(\"http://evil\",\"x\")"))).startsWith("\"'=HYPERLINK");
	}

	@Test
	void stops_a_spreadsheet_running_cells_that_start_with_plus_minus_at_or_a_tab() {
		assertThat(Csv.row(Arrays.asList("+1+1"))).isEqualTo("'+1+1\r\n");
		assertThat(Csv.row(Arrays.asList("-2+3"))).isEqualTo("'-2+3\r\n");
		assertThat(Csv.row(Arrays.asList("@SUM(A1)"))).isEqualTo("'@SUM(A1)\r\n");
		assertThat(Csv.row(Arrays.asList("\tcmd"))).isEqualTo("'\tcmd\r\n");
	}

	@Test
	void leaves_ordinary_text_and_numbers_alone() {
		assertThat(Csv.row(Arrays.asList("Flat 304", "1500.00", "2026-10-20 15:30"))).isEqualTo("Flat 304,1500.00,2026-10-20 15:30\r\n");
	}

	@Test
	void formats_money_as_rupees_with_two_decimals_for_a_spreadsheet() {
		assertThat(Csv.rupees(Money.ofPaise(160050))).isEqualTo("1600.50");
		assertThat(Csv.rupees(Money.ZERO)).isEqualTo("0.00");
	}
}
