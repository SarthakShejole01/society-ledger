import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { NOW } from '../../core/clock';
import { LoadState } from '../../core/load-state';
import { formatRupees } from '../../core/money';
import { monthLabel, monthOf } from '../../core/month';
import { Dues } from '../../core/models';
import { PaymentFlow } from '../../core/payment-flow';

type Payment =
  | { status: 'idle' }
  | { status: 'working' }
  | { status: 'received' }
  | { status: 'alreadyPaid' }
  | { status: 'notConfirmed' }
  | { status: 'failed'; message: string };

@Component({
  selector: 'app-owner-home',
  imports: [RouterLink],
  template: `
    @switch (state().status) {
      @case ('loading') {
        <p class="muted" role="status">Loading your dues…</p>
      }
      @case ('error') {
        <div class="card" role="alert">
          <p>{{ problem() }}</p>
          @if (canRetry()) {
            <button class="btn" type="button" (click)="load()">Try again</button>
          }
        </div>
      }
      @case ('ready') {
        @if (payment().status === 'received') {
          <p class="notice" role="status">Payment received. Thank you.</p>
        }
        @if (payment().status === 'alreadyPaid') {
          <p class="notice" role="status">This month is already paid.</p>
        }
        @if (dues(); as d) {
          <section class="hero" aria-label="Dues for {{ monthName }}">
            <div class="hero-top">
              <span>{{ monthName }}</span>
              @if (d.paid) {
                <span class="pill good">Paid</span>
              } @else {
                <span class="pill warn">Due</span>
              }
            </div>
            <p class="amount mono">{{ money(d.paid ? 0 : d.totalPaise) }}</p>
            <p class="hero-note">
              @if (d.paid) {
                Nothing to pay for {{ monthName }}. Thank you.
              } @else if (d.lateFeePaise > 0) {
                A late fee applies because the 10th has passed.
              } @else {
                Pay by the 10th to avoid a late fee.
              }
            </p>
          </section>

          @if (!d.paid) {
            <section class="card">
              <div class="line"><span>Maintenance</span><span class="mono">{{ money(d.amountPaise) }}</span></div>
              @if (d.lateFeePaise > 0) {
                <div class="line"><span>Late fee</span><span class="mono">{{ money(d.lateFeePaise) }}</span></div>
              }
              <div class="line total"><span>Total due</span><span class="mono">{{ money(d.totalPaise) }}</span></div>
            </section>

            @if (payment().status === 'notConfirmed') {
              <section class="card" role="status">
                <h2>Payment not confirmed yet</h2>
                <p>Your payment is not confirmed yet. If money was taken, it will show as Paid shortly, so do not pay again.</p>
                <button class="btn" type="button" (click)="checkAgain()">Check again</button>
              </section>
            } @else {
              <button class="btn primary" type="button" [disabled]="payment().status === 'working'" (click)="payNow()">
                @if (payment().status === 'working') { Opening payment… } @else { Pay now {{ money(d.totalPaise) }} }
              </button>
              @if (failure(); as text) {
                <p class="error" role="alert">{{ text }}</p>
              }
            }
          }

          <a class="btn ghost" routerLink="/pay-for">Pay for another flat</a>
        }
      }
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 1rem; }
    h2 { margin: 0 0 0.25rem; font-size: 1rem; }
    a.btn { display: block; text-align: center; text-decoration: none; }
  `,
})
export class OwnerHome {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly flow = inject(PaymentFlow);

  protected readonly month = monthOf(inject(NOW)());
  protected readonly monthName = monthLabel(this.month);
  protected readonly state = signal<LoadState<Dues>>({ status: 'loading' });
  protected readonly payment = signal<Payment>({ status: 'idle' });
  protected readonly money = formatRupees;
  private readonly flatId = this.auth.session()?.flatId ?? null;

  constructor() {
    this.load();
  }

  protected dues(): Dues | null {
    const s = this.state();
    return s.status === 'ready' ? s.data : null;
  }

  protected canRetry(): boolean {
    return this.flatId !== null;
  }

  protected problem(): string {
    return this.flatId === null
      ? 'Your login is not linked to a flat. Ask the treasurer to link it.'
      : 'We could not load your dues.';
  }

  protected failure(): string | null {
    const p = this.payment();
    return p.status === 'failed' ? p.message : null;
  }

  protected load(): void {
    if (this.flatId === null) {
      this.state.set({ status: 'error' });
      return;
    }
    this.state.set({ status: 'loading' });
    this.api.dues(this.flatId, this.month).subscribe({
      next: (data) => this.state.set({ status: 'ready', data }),
      error: () => this.state.set({ status: 'error' }),
    });
  }

  protected checkAgain(): void {
    this.payment.set({ status: 'idle' });
    this.load();
  }

  protected async payNow(): Promise<void> {
    if (this.flatId === null) {
      return;
    }
    this.payment.set({ status: 'working' });
    const outcome = await this.flow.pay(this.flatId, this.month, this.auth.session()?.name ?? '');
    switch (outcome.kind) {
      case 'paid':
        this.payment.set({ status: 'received' });
        this.load();
        break;
      case 'notConfirmed':
        this.payment.set({ status: 'notConfirmed' });
        break;
      case 'cancelled':
        this.payment.set({ status: 'failed', message: 'Payment cancelled. Nothing was charged.' });
        break;
      case 'pageUnavailable':
        this.payment.set({ status: 'failed', message: 'We could not open the payment page. Try again.' });
        break;
      case 'providerDown':
        this.payment.set({ status: 'failed', message: 'The payment provider is not available. Try again in a moment.' });
        break;
      case 'alreadyPaid':
        this.payment.set({ status: 'alreadyPaid' });
        this.load();
        break;
      default:
        this.payment.set({ status: 'failed', message: 'We could not start the payment. Try again.' });
    }
  }
}
