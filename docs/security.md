# Security notes

What is protected, how, and what is still open. Update this when a control changes.

## What we protect

- Which flats are Paid or Pending (status only is visible to owners; amounts are not).
- Who paid, how and when (treasurer and committee only).
- Members' mobile numbers and emails.
- That a flat is only ever marked Paid by the payment provider's confirmation or by the treasurer.

## Controls in place

| Threat | Control | Where tested |
|---|---|---|
| One society reading another's data | `society_id` on every tenant table, PostgreSQL row-level security, app role does not own tables (ADR 0001) | tenant isolation tests on real PostgreSQL |
| Guessing login codes | 6 digits, 10 minutes, single use, 5 wrong tries, 3 requests per 10 minutes, codes stored only as HMAC hashes | `AuthServiceTest` |
| Finding out who is registered | Same answer whether or not the mobile number exists | `AuthServiceTest` |
| Stolen database copy | Session tokens and codes are keyed hashes; the secret is not in the database | `SecretHasherTest` |
| Placeholder secrets in production | App refuses to start with a short secret or one that starts with `change-me` | `SecretHasherTest` |
| Client choosing what to pay | Amounts are decided by the server; no endpoint accepts an amount | `ApiContractTest`, `PaymentServiceTest` |
| Faking a payment | Browser proof and webhook are verified with HMAC-SHA256; the order id is matched to the stored one; duplicate callbacks are safe | `RazorpayGatewayTest`, `GatewayPaymentTest` |
| Owners acting as treasurer | Role checked on every endpoint, committee is read-only except paying their own flat | `ApiContractTest` |
| Tampering with the record | `audit_log` is append only for the app role; waivers need a reason | `AuditLogTest` |
| Spreadsheet formula injection in the export | Cells starting with `= + - @` or a tab are prefixed with `'`; quoting per RFC 4180 | `CsvTest`, `ExportServiceTest` |
| Browser attacks | `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, HSTS over HTTPS, `Cache-Control: no-store` on `/api` | `SecurityHeadersTest` |
| Secrets in logs | One-time codes and email addresses are never logged outside the dev profile; mail failures log only the error type | review |
| Vulnerable front-end packages | `npm audit --omit=dev` reported 0 vulnerabilities on 2026-10-22 | manual |
| Dev shortcuts reaching production | The fake payment provider and the code-logging sender exist only in the `dev` profile | profile annotations |

## Open items

1. **No Content-Security-Policy yet.** It needs testing against the real Razorpay checkout (script from `checkout.razorpay.com`, frames and connections to Razorpay). Do this with a Razorpay test account before go-live.
2. **Rate limiting covers only login code requests.** Other endpoints need an edge limit (reverse proxy or host) before the service is public.
3. **Abandoned `PENDING` payments never expire.** They are never counted as paid and cannot block a later attempt; they are kept because a late gateway confirmation must still be matched.
4. **No admin console.** Societies and flats are loaded with `deploy/onboard-society.sql` by someone with database access.
5. **Sessions last 12 hours and cannot be revoked from the app.** Removing a member does not end their open session until it expires.
6. **Not verified against real services:** Razorpay (tested against a local stand-in and the documented signature rules), a real mail provider, HTTPS hosting, and the built container run end to end.
7. **Backups are a script, not a schedule.** Someone has to run `deploy/backup.sh` regularly and test a restore.
