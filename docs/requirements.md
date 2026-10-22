# Requirements: version 1

## Goal

Let flat owners see the maintenance due and pay it online, let everyone see which flats have paid, and give the treasurer tools for exceptions. The first society has 45 flats on 8 floors. The product is built so more societies can be added later.

## In scope

1. **Owner login** with the society code and registered mobile number. The one-time code is emailed to the address on record (see `docs/adr/0002-login-by-emailed-one-time-code.md`).
2. **Per-flat monthly amount**, set by the treasurer. Amounts can differ per flat.
3. **Pay page** for the current month. The server works out the exact amount, including any late fee.
4. **Online payment** through a payment gateway. A flat turns Paid only when the gateway confirms.
5. **Late fee of Rs 100**, added once when the current month is paid from the 11th to month end.
6. **Pay for any flat.** Anyone logged in can pay another flat's maintenance. The payer is recorded.
7. **All-flats board** showing Paid or Pending per flat, with no amounts.
8. **Treasurer tools:** record cash or cheque, waive a late fee with a reason, export Excel or PDF.
9. **Committee dashboard:** flats paid against pending, amount collected against amount due, gateway fees shown separately.
10. **Daily backups.**

## Rules

| Rule | Decision |
|---|---|
| Due window | 1st to 10th of the month, regular amount |
| After the 10th | Regular amount plus Rs 100 late fee |
| Cutoff | 11:59 pm Indian time on the 10th, counted from when the payment is started |
| Gateway fees | Paid by the society, taken from each online payment before settlement |
| Visibility of Paid or Pending | Approved by the committee |
| Flat list | Dummy data for now |

## Roles

| Role | Can do |
|---|---|
| Owner | See own amount and history, pay for any flat, see everyone's Paid or Pending |
| Treasurer | Set amounts, record cash or cheque, waive a late fee, export reports, invite users |
| Committee | View the dashboard and reports |
| Platform admin | Onboard societies, support access (logged, time-limited) |

## Out of scope for version 1

Advance payment buttons (3 and 12 months), automatic reminders, PDF receipts, expense accounting, bank statement import, visitor or complaint modules, store apps.

## Open questions

1. Is the Rs 100 late fee one time, or charged for every month overdue? The code charges it once per overdue month's charge until this is answered.
2. Do earlier unpaid months carry forward with their own late fee?
