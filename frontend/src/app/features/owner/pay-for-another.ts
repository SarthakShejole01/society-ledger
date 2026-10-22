import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { NOW } from '../../core/clock';
import { LoadState } from '../../core/load-state';
import { FlatStatus, PaymentIntent } from '../../core/models';
import { formatRupees } from '../../core/money';
import { monthLabel, monthOf } from '../../core/month';

type Payment =
  | { status: 'idle' }
  | { status: 'starting' }
  | { status: 'started'; flatNumber: string; intent: PaymentIntent }
  | { status: 'failed'; message: string };

/** Pays the maintenance of another flat in the society. The server decides the amount and records who paid. */
@Component({
  selector: 'app-pay-for-another',
  imports: [RouterLink],
  template: `
    <header>
      <h1>Pay for another flat</h1>
      <p class="muted">{{ monthName }}</p>
    </header>

    @switch (state().status) {
      @case ('loading') {
        <p class="muted" role="status">Loading the flats…</p>
      }
      @case ('error') {
        <div class="card" role="alert">
          <p>We could not load the flats.</p>
          <button class="btn" type="button" (click)="load()">Try again</button>
        </div>
      }
      @case ('ready') {
        @if (started(); as done) {
          <section class="card" role="status">
            <h2>Payment started</h2>
            <p>Flat {{ done.flatNumber }} · Total {{ money(done.intent.totalPaise) }}</p>
            @if (done.intent.lateFeePaise > 0) {
              <p class="muted">This includes a late fee of {{ money(done.intent.lateFeePaise) }}.</p>
            }
            <p class="muted">The online payment page is not connected yet, so nothing has been charged.</p>
          </section>
          <a class="btn ghost" routerLink="/home">Back to home</a>
        } @else if (others().length === 0) {
          <p class="muted">Every other flat has paid for {{ monthName }}.</p>
          <a class="btn ghost" routerLink="/home">Back to home</a>
        } @else {
          <form class="card" (submit)="$event.preventDefault(); start()" novalidate>
            <p class="notice">Your name is recorded as the payer. You will see the total before anything is charged.</p>
            <div class="field">
              <label for="flat">Flat</label>
              <select id="flat" (change)="chosen.set(read($event))">
                @for (flat of others(); track flat.flatId) {
                  <option [value]="flat.flatId" [selected]="flat.flatId === selected()?.flatId">Flat {{ flat.number }}</option>
                }
              </select>
            </div>
            @if (failure(); as text) {
              <p class="error" role="alert">{{ text }}</p>
            }
            <button class="btn primary" type="submit" [disabled]="payment().status === 'starting'">Start payment</button>
          </form>
        }
      }
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 1rem; }
    h1 { margin: 0; font-size: 1.25rem; }
    h2 { margin: 0 0 0.25rem; font-size: 1rem; }
    form { gap: 1rem; }
    a.btn { display: block; text-align: center; text-decoration: none; }
    select { font: 500 16px var(--f-body); color: var(--ink); background: var(--surface); border: 1px solid var(--line); border-radius: 10px; padding: 11px 12px; width: 100%; }
  `,
})
export class PayForAnother {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  protected readonly month = monthOf(inject(NOW)());
  protected readonly monthName = monthLabel(this.month);
  protected readonly money = formatRupees;
  protected readonly state = signal<LoadState<FlatStatus[]>>({ status: 'loading' });
  protected readonly payment = signal<Payment>({ status: 'idle' });
  protected readonly chosen = signal<string | null>(this.route.snapshot.queryParamMap.get('flat'));
  private readonly ownFlatId = this.auth.session()?.flatId ?? null;

  /** Other flats that have not paid. The owner's own flat is paid from Home. */
  protected readonly others = computed<FlatStatus[]>(() => {
    const s = this.state();
    return s.status === 'ready' ? s.data.filter((f) => !f.paid && f.flatId !== this.ownFlatId) : [];
  });
  protected readonly selected = computed<FlatStatus | null>(
    () => this.others().find((f) => f.flatId === this.chosen()) ?? this.others().at(0) ?? null,
  );

  constructor() {
    this.load();
  }

  protected read(event: Event): string {
    return (event.target as HTMLSelectElement).value;
  }

  protected started(): { flatNumber: string; intent: PaymentIntent } | null {
    const p = this.payment();
    return p.status === 'started' ? p : null;
  }

  protected failure(): string | null {
    const p = this.payment();
    return p.status === 'failed' ? p.message : null;
  }

  protected load(): void {
    this.state.set({ status: 'loading' });
    this.api.board(this.month).subscribe({
      next: (data) => this.state.set({ status: 'ready', data }),
      error: () => this.state.set({ status: 'error' }),
    });
  }

  protected start(): void {
    const flat = this.selected();
    if (!flat) {
      return;
    }
    this.payment.set({ status: 'starting' });
    this.api.startPayment(flat.flatId, this.month).subscribe({
      next: (intent) => this.payment.set({ status: 'started', flatNumber: flat.number, intent }),
      error: (e: unknown) =>
        this.payment.set({
          status: 'failed',
          message:
            e instanceof HttpErrorResponse && e.status === 409
              ? 'That flat has already paid for this month.'
              : 'We could not start the payment. Try again.',
        }),
    });
  }
}
