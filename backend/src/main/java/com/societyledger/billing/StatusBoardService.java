package com.societyledger.billing;

import com.societyledger.common.tenant.SocietyScope;
import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Paid or Pending for every flat in a society, worked out in SQL. */
@Service
public class StatusBoardService {

	private final SocietyScope scope;
	private final JdbcClient jdbc;

	public StatusBoardService(SocietyScope scope, JdbcClient jdbc) {
		this.scope = scope;
		this.jdbc = jdbc;
	}

	public List<FlatStatus> forMonth(UUID societyId, YearMonth month) {
		return scope.inSociety(societyId, () -> jdbc.sql("""
				select f.id, f.number, f.floor,
					exists (
						select 1 from charges c
						join payments p on p.charge_id = c.id and p.status = 'CONFIRMED'
						where c.flat_id = f.id and c.month = :month) as paid
				from flats f
				order by f.floor, f.number""")
				.param("month", Date.valueOf(month.atDay(1)))
				.query((rs, row) -> new FlatStatus(rs.getObject("id", UUID.class), rs.getString("number"), rs.getInt("floor"), rs.getBoolean("paid")))
				.list());
	}
}
