package com.societyledger.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.societyledger.auth.AuthService;
import com.societyledger.auth.AuthSession;
import com.societyledger.auth.InvalidOtpException;
import com.societyledger.auth.Principal;
import com.societyledger.auth.Role;
import com.societyledger.auth.TooManyOtpRequestsException;
import com.societyledger.billing.Dashboard;
import com.societyledger.billing.DashboardService;
import com.societyledger.billing.Dues;
import com.societyledger.billing.DuesService;
import com.societyledger.billing.FlatStatus;
import com.societyledger.billing.PendingFlat;
import com.societyledger.billing.StatusBoardService;
import com.societyledger.common.Money;
import com.societyledger.payment.AlreadyPaidException;
import com.societyledger.payment.OfflineMethod;
import com.societyledger.payment.OfflinePaymentCommand;
import com.societyledger.payment.OfflinePaymentResult;
import com.societyledger.payment.OfflinePaymentService;
import com.societyledger.payment.PaymentIntent;
import com.societyledger.payment.PaymentService;
import com.societyledger.payment.WaiverReasonRequiredException;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
class ApiContractTest {

	private static final UUID SOCIETY = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID OWN_FLAT = UUID.fromString("00000000-0000-0000-0000-0000000003f0");
	private static final UUID OTHER_FLAT = UUID.fromString("00000000-0000-0000-0000-0000000003f1");
	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	private static final Principal OWNER = new Principal(UUID.randomUUID(), SOCIETY, OWN_FLAT, Role.OWNER, "Rajesh K");
	private static final Principal TREASURER = new Principal(UUID.randomUUID(), SOCIETY, null, Role.TREASURER, "Meena T");
	private static final Principal COMMITTEE = new Principal(UUID.randomUUID(), SOCIETY, null, Role.COMMITTEE, "Anil C");

	@Autowired
	private MockMvc mvc;
	@MockitoBean
	private AuthService auth;
	@MockitoBean
	private DuesService dues;
	@MockitoBean
	private StatusBoardService board;
	@MockitoBean
	private PaymentService payments;
	@MockitoBean
	private OfflinePaymentService offline;
	@MockitoBean
	private DashboardService dashboard;

	@BeforeEach
	void knownSessions() {
		given(auth.authenticate("owner-token")).willReturn(Optional.of(OWNER));
		given(auth.authenticate("treasurer-token")).willReturn(Optional.of(TREASURER));
		given(auth.authenticate("committee-token")).willReturn(Optional.of(COMMITTEE));
		given(auth.authenticate("unknown-token")).willReturn(Optional.empty());
	}

	// --- login ---

	@Test
	void requesting_a_code_always_answers_accepted() throws Exception {
		mvc.perform(post("/api/auth/otp").contentType(MediaType.APPLICATION_JSON)
				.content("{\"societyCode\":\"sunrise\",\"mobile\":\"9876543210\"}"))
				.andExpect(status().isAccepted());

		verify(auth).requestOtp("sunrise", "9876543210");
	}

	@Test
	void too_many_code_requests_answer_429_as_a_problem() throws Exception {
		org.mockito.Mockito.doThrow(new TooManyOtpRequestsException()).when(auth).requestOtp(any(), any());

		mvc.perform(post("/api/auth/otp").contentType(MediaType.APPLICATION_JSON)
				.content("{\"societyCode\":\"sunrise\",\"mobile\":\"9876543210\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(429));
	}

	@Test
	void the_right_code_returns_a_token_and_who_you_are() throws Exception {
		given(auth.verifyOtp("sunrise", "9876543210", "482917")).willReturn(new AuthSession("tok", OWNER));

		mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
				.content("{\"societyCode\":\"sunrise\",\"mobile\":\"9876543210\",\"code\":\"482917\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("tok"))
				.andExpect(jsonPath("$.role").value("OWNER"))
				.andExpect(jsonPath("$.flatId").value(OWN_FLAT.toString()))
				.andExpect(jsonPath("$.name").value("Rajesh K"));
	}

	@Test
	void a_wrong_code_answers_401_as_a_problem() throws Exception {
		given(auth.verifyOtp(any(), any(), any())).willThrow(new InvalidOtpException());

		mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
				.content("{\"societyCode\":\"sunrise\",\"mobile\":\"9876543210\",\"code\":\"000000\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void a_blank_society_code_answers_400() throws Exception {
		mvc.perform(post("/api/auth/otp").contentType(MediaType.APPLICATION_JSON)
				.content("{\"societyCode\":\"\",\"mobile\":\"9876543210\"}"))
				.andExpect(status().isBadRequest());

		verify(auth, never()).requestOtp(any(), any());
	}

	@Test
	void a_code_that_is_not_six_digits_answers_400() throws Exception {
		mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
				.content("{\"societyCode\":\"sunrise\",\"mobile\":\"9876543210\",\"code\":\"12\"}"))
				.andExpect(status().isBadRequest());
	}

	// --- who is asking ---

	@Test
	void a_request_without_a_token_answers_401() throws Exception {
		mvc.perform(get("/api/flats/status").param("month", "2026-10"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void a_request_with_an_unknown_token_answers_401() throws Exception {
		mvc.perform(get("/api/flats/status").param("month", "2026-10").header("Authorization", "Bearer unknown-token"))
				.andExpect(status().isUnauthorized());
	}

	// --- dues ---

	@Test
	void an_owner_sees_the_dues_of_their_own_flat() throws Exception {
		given(dues.forFlat(SOCIETY, OWN_FLAT, OCTOBER)).willReturn(
				new Dues("304", OCTOBER, Money.rupees(1500), Money.rupees(100), Money.rupees(1600), false));

		mvc.perform(get("/api/flats/" + OWN_FLAT + "/dues").param("month", "2026-10")
				.header("Authorization", "Bearer owner-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.flatNumber").value("304"))
				.andExpect(jsonPath("$.month").value("2026-10"))
				.andExpect(jsonPath("$.amountPaise").value(150000))
				.andExpect(jsonPath("$.lateFeePaise").value(10000))
				.andExpect(jsonPath("$.totalPaise").value(160000))
				.andExpect(jsonPath("$.paid").value(false));
	}

	@Test
	void an_owner_cannot_see_the_dues_of_another_flat() throws Exception {
		mvc.perform(get("/api/flats/" + OTHER_FLAT + "/dues").param("month", "2026-10")
				.header("Authorization", "Bearer owner-token"))
				.andExpect(status().isForbidden());

		verify(dues, never()).forFlat(any(), any(), any());
	}

	@Test
	void a_treasurer_sees_the_dues_of_any_flat() throws Exception {
		given(dues.forFlat(SOCIETY, OTHER_FLAT, OCTOBER)).willReturn(
				new Dues("305", OCTOBER, Money.rupees(1200), Money.ZERO, Money.rupees(1200), false));

		mvc.perform(get("/api/flats/" + OTHER_FLAT + "/dues").param("month", "2026-10")
				.header("Authorization", "Bearer treasurer-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.flatNumber").value("305"));
	}

	// --- board ---

	@Test
	void any_member_sees_the_status_board_without_amounts() throws Exception {
		given(board.forMonth(SOCIETY, OCTOBER)).willReturn(List.of(new FlatStatus("304", 3, true), new FlatStatus("305", 3, false)));

		mvc.perform(get("/api/flats/status").param("month", "2026-10").header("Authorization", "Bearer owner-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].number").value("304"))
				.andExpect(jsonPath("$[0].paid").value(true))
				.andExpect(jsonPath("$[1].paid").value(false))
				.andExpect(jsonPath("$[0].amountPaise").doesNotExist())
				.andExpect(jsonPath("$[0].amount").doesNotExist());
	}

	// --- payments ---

	@Test
	void an_owner_can_start_a_payment_for_any_flat_and_is_recorded_as_the_payer() throws Exception {
		UUID paymentId = UUID.randomUUID();
		given(payments.start(SOCIETY, OTHER_FLAT, OCTOBER, "Rajesh K")).willReturn(
				new PaymentIntent(paymentId, OTHER_FLAT, OCTOBER, Money.rupees(1300), Money.rupees(100)));

		mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer owner-token")
				.content("{\"flatId\":\"" + OTHER_FLAT + "\",\"month\":\"2026-10\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
				.andExpect(jsonPath("$.totalPaise").value(130000))
				.andExpect(jsonPath("$.lateFeePaise").value(10000));
	}

	@Test
	void a_client_cannot_choose_the_amount() throws Exception {
		given(payments.start(any(), any(), any(), any())).willReturn(
				new PaymentIntent(UUID.randomUUID(), OWN_FLAT, OCTOBER, Money.rupees(1500), Money.ZERO));

		mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer owner-token")
				.content("{\"flatId\":\"" + OWN_FLAT + "\",\"month\":\"2026-10\",\"amountPaise\":1}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.totalPaise").value(150000));
	}

	@Test
	void a_committee_member_cannot_start_a_payment() throws Exception {
		mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer committee-token")
				.content("{\"flatId\":\"" + OWN_FLAT + "\",\"month\":\"2026-10\"}"))
				.andExpect(status().isForbidden());

		verify(payments, never()).start(any(), any(), any(), any());
	}

	@Test
	void paying_a_month_that_is_already_paid_answers_409() throws Exception {
		given(payments.start(eq(SOCIETY), any(), any(), any())).willThrow(new AlreadyPaidException());

		mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer owner-token")
				.content("{\"flatId\":\"" + OWN_FLAT + "\",\"month\":\"2026-10\"}"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void a_month_that_does_not_exist_answers_400() throws Exception {
		mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer owner-token")
				.content("{\"flatId\":\"" + OWN_FLAT + "\",\"month\":\"2026-13\"}"))
				.andExpect(status().isBadRequest());
	}

	// --- treasurer tools ---

	private static final String OFFLINE_BODY = "{\"flatId\":\"%s\",\"month\":\"2026-10\",\"method\":\"CHEQUE\",\"reference\":\"000123\","
			+ "\"waiveLateFee\":true,\"waiverReason\":\"Owner was in hospital\"}";

	@Test
	void a_treasurer_can_record_a_cheque_with_a_waiver() throws Exception {
		UUID paymentId = UUID.randomUUID();
		given(offline.record(eq(SOCIETY), eq(TREASURER.memberId()), eq("Meena T"),
				eq(new OfflinePaymentCommand(OWN_FLAT, OCTOBER, OfflineMethod.CHEQUE, "000123", true, "Owner was in hospital"))))
				.willReturn(new OfflinePaymentResult(paymentId, Money.rupees(1500), Money.ZERO, Money.rupees(100)));

		mvc.perform(post("/api/payments/offline").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer treasurer-token")
				.content(OFFLINE_BODY.formatted(OWN_FLAT)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
				.andExpect(jsonPath("$.totalPaise").value(150000))
				.andExpect(jsonPath("$.waivedPaise").value(10000));
	}

	@Test
	void an_owner_cannot_record_an_offline_payment() throws Exception {
		mvc.perform(post("/api/payments/offline").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer owner-token")
				.content(OFFLINE_BODY.formatted(OWN_FLAT)))
				.andExpect(status().isForbidden());

		verify(offline, never()).record(any(), any(), any(), any());
	}

	@Test
	void a_committee_member_cannot_record_an_offline_payment() throws Exception {
		mvc.perform(post("/api/payments/offline").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer committee-token")
				.content(OFFLINE_BODY.formatted(OWN_FLAT)))
				.andExpect(status().isForbidden());

		verify(offline, never()).record(any(), any(), any(), any());
	}

	@Test
	void waiving_without_a_reason_answers_400() throws Exception {
		given(offline.record(any(), any(), any(), any())).willThrow(new WaiverReasonRequiredException());

		mvc.perform(post("/api/payments/offline").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer treasurer-token")
				.content(OFFLINE_BODY.formatted(OWN_FLAT)))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void an_unknown_payment_method_answers_400() throws Exception {
		mvc.perform(post("/api/payments/offline").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer treasurer-token")
				.content("{\"flatId\":\"" + OWN_FLAT + "\",\"month\":\"2026-10\",\"method\":\"BITCOIN\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void the_treasurer_and_committee_see_the_dashboard_and_owners_do_not() throws Exception {
		given(dashboard.forMonth(SOCIETY, OCTOBER)).willReturn(new Dashboard(OCTOBER, 45, 31, 14, Money.rupees(70000),
				Money.rupees(50000), Money.rupees(30000), Money.rupees(20000), Money.rupees(400), Money.rupees(100),
				List.of(new PendingFlat(OTHER_FLAT, "305", 3, Money.rupees(1500), Money.rupees(1600)))));

		mvc.perform(get("/api/dashboard").param("month", "2026-10").header("Authorization", "Bearer treasurer-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.flatCount").value(45))
				.andExpect(jsonPath("$.paidCount").value(31))
				.andExpect(jsonPath("$.expectedPaise").value(7000000))
				.andExpect(jsonPath("$.collectedPaise").value(5000000))
				.andExpect(jsonPath("$.onlineCollectedPaise").value(3000000))
				.andExpect(jsonPath("$.offlineCollectedPaise").value(2000000))
				.andExpect(jsonPath("$.lateFeesWaivedPaise").value(10000))
				.andExpect(jsonPath("$.pending[0].flatId").value(OTHER_FLAT.toString()))
				.andExpect(jsonPath("$.pending[0].number").value("305"))
				.andExpect(jsonPath("$.pending[0].amountPaise").value(150000))
				.andExpect(jsonPath("$.pending[0].dueNowPaise").value(160000));

		mvc.perform(get("/api/dashboard").param("month", "2026-10").header("Authorization", "Bearer committee-token"))
				.andExpect(status().isOk());
		mvc.perform(get("/api/dashboard").param("month", "2026-10").header("Authorization", "Bearer owner-token"))
				.andExpect(status().isForbidden());
	}
}
