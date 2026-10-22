package com.societyledger.society;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.societyledger.common.Money;
import com.societyledger.common.tenant.SocietyScope;
import com.societyledger.support.DatabaseTest;
import com.societyledger.support.PostgresTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class FlatTenancyTest {

	private static final UUID SUNRISE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID LAKEVIEW = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	@Autowired
	private SocietyScope scope;
	@Autowired
	private FlatRepository flats;
	@Autowired
	private SocietyRepository societies;

	private final JdbcTemplate admin = PostgresTestSupport.admin();

	@BeforeEach
	void twoSocietiesWithNoFlats() {
		admin.execute("truncate flats, societies cascade");
		admin.update("insert into societies (id, code, name) values (?, 'sunrise', 'Sunrise Apartments')", SUNRISE);
		admin.update("insert into societies (id, code, name) values (?, 'lakeview', 'Lakeview Heights')", LAKEVIEW);
	}

	@Test
	void a_society_only_sees_its_own_flats() {
		scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "101", 1, Money.rupees(1500)));
		scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "102", 1, Money.rupees(1200)));
		scope.inSociety(LAKEVIEW, () -> flats.add(LAKEVIEW, "A-1", 1, Money.rupees(2000)));

		List<Flat> seen = scope.inSociety(SUNRISE, flats::findAll);

		assertThat(seen).extracting(Flat::number).containsExactly("101", "102");
	}

	@Test
	void without_a_society_scope_no_flats_are_visible() {
		scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "101", 1, Money.rupees(1500)));

		assertThat(flats.findAll()).isEmpty();
	}

	@Test
	void a_society_cannot_add_a_flat_to_another_society() {
		assertThatThrownBy(() -> scope.inSociety(SUNRISE, () -> flats.add(LAKEVIEW, "A-1", 1, Money.rupees(2000))))
				.isInstanceOf(DataAccessException.class);

		assertThat(admin.queryForObject("select count(*) from flats", Integer.class)).isZero();
	}

	@Test
	void flat_numbers_are_unique_within_a_society() {
		scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "101", 1, Money.rupees(1500)));

		assertThatThrownBy(() -> scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "101", 1, Money.rupees(1500))))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void the_same_flat_number_can_exist_in_two_societies() {
		scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "101", 1, Money.rupees(1500)));
		scope.inSociety(LAKEVIEW, () -> flats.add(LAKEVIEW, "101", 1, Money.rupees(2000)));

		assertThat(admin.queryForObject("select count(*) from flats where number = '101'", Integer.class)).isEqualTo(2);
	}

	@Test
	void a_society_cannot_see_other_societies() {
		List<Society> seen = scope.inSociety(SUNRISE, societies::findAll);

		assertThat(seen).extracting(Society::code).containsExactly("sunrise");
	}

	@Test
	void a_flat_remembers_its_monthly_amount_in_paise() {
		scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "304", 3, Money.rupees(1500)));

		Flat flat = scope.inSociety(SUNRISE, () -> flats.findAll().get(0));

		assertThat(flat.monthlyAmount()).isEqualTo(Money.ofPaise(150_000));
		assertThat(flat.floor()).isEqualTo(3);
	}
}
