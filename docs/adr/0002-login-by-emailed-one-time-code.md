# 0002. Login by an emailed one-time code

## Context

Owners should log in with their registered mobile number. SMS codes are not free in India: they need sender registration and cost per message, which does not fit a low yearly price for a 45-flat society.

## Options

1. **SMS code.** Matches "mobile login" exactly, but has a setup process and a running cost.
2. **WhatsApp code.** Also costs per message and needs a business account.
3. **Email code (chosen).** Free or nearly free, with no registration step.

## Decision

- The owner enters the society code and the registered mobile number. The code is emailed to the address on that member's record.
- Codes are six digits, valid for 10 minutes, usable once, locked after 5 wrong tries, and limited to 3 requests per 10 minutes.
- The response never says whether a number is registered.
- Codes and session tokens are stored only as keyed hashes.
- Delivery sits behind the `OtpSender` interface, so SMS can be added later without touching login.

## Consequences

- Every member needs an email address on file. The flat list must include one per owner.
- Mobile numbers identify people but do not receive the code.
- Email deliverability needs care. Use a proper sender address, and set up SPF and DKIM once there is a domain.
