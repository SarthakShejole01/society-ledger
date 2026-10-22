import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { NOW } from '../../core/clock';
import { LoadState } from '../../core/load-state';
import { MonthDashboard, OfflineMethod, PendingFlat } from '../../core/models';
import { formatRupees } from '../../core/money';
import { monthLabel, monthOf } from '../../core/month';

interface Recorded {
  flatNumber: string;
  method: OfflineMethod;
  totalPaise: number;
  waivedPaise: number;
}

/** The treasurer records cash or cheque received. The server works out the amount; this screen only previews it. */
@Component({
  selector: 'app-record',
  template: `
    <header>
      <h1>Record a payment</h1>
      <p class="muted">Cash or cheque received for {{ monthName }}</p>
    </header>

    @switch (state().status) {
      @case ('loading') {
        <p class="muted" role="status">Loading the flats that still owe…</p>
      }
      @case ('error') {
        <div class="card" role="alert">
          <p>We could not load the flats that still owe.</p>
          <button class="btn" type="button" (click)="load()">Try again</button>
        </div>
      }
      @case ('ready') {
        @if (recorded(); as done) {
          <section class="card" role="status">
            <h2>Recorded</h2>
            <p>Flat {{ done.flatNumber }} · {{ done.method === 'CASH' ? 'Cash' : 'Cheque' }} · {{ money(done.totalPaise) }}</p>
            @if (done.waivedPaise > 0) {
              <p class="muted">Late fee of {{ money(done.waivedPaise) }} waived.</p>
            }
          </section>
        }

        @if (pending().length === 0) {
          <p class="muted">Every flat has paid for {{ monthName }}.</p>
        } @else {
          <form class="card" (submit)="$event.preventDefault(); save()" novalidate>
            <div class="field">
              <label for="flat">Flat</label>
              <select id="flat" (change)="chosen.set(read($event))">
                @for (flat of pending(); track flat.flatId) {
                  <option [value]="flat.flatId" [selected]="flat.flatId === selected()?.flatId">Flat {{ flat.number }} · due {{ money(flat.dueNowPaise) }}</option>
                }
              </select>
            </div>

            <div class="field">
              <label for="method">Received as</label>
              <select id="method" (change)="method.set(asMethod($event))">
                <option value="CASH" [selected]="method() === 'CASH'">Cash</option>
                <option value="CHEQUE" [selected]="method() === 'CHEQUE'">Cheque</option>
              </select>
            </div>

            @if (method() === 'CHEQUE') {
              <div class="field">
                <label for="reference">Cheque number</label>
                <input id="reference" type="text" inputmode="numeric" [value]="reference()" (input)="reference.set(read($event))" />
              </div>
            }

            @if (lateFeePaise() > 0) {
              <label class="check">
                <input id="waive" type="checkbox" [checked]="waive()" (change)="waive.set(checked($event))" />
                <span>Waive the late fee of {{ money(lateFeePaise()) }}</span>
              </label>
              @if (waive()) {
                <div class="field">
                  <label for="reason">Reason for waiving</label>
                  <input id="reason" type="text" [value]="reason()" (input)="reason.set(read($event))" />
                </div>
              }
            }

            <div class="line total">
              <span>Total to record</span>
              <span class="mono" data-testid="total">{{ money(total()) }}</span>
            </div>

            @if (error(); as text) {
              <p class="error" role="alert">{{ text }}</p>
            }
            <button class="btn primary" type="submit" [disabled]="saving()">Record payment</button>
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
    select { font: 500 16px var(--f-body); color: var(--ink); background: var(--surface); border: 1px solid var(--line); border-radius: 10px; padding: 11px 12px; width: 100%; }
    .check { display: flex; align-items: center; gap: 0.6rem; font-weight: 600; }
    .check input { width: 1.2rem; height: 1.2rem; }
  `,
})
export class RecordPage {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  protected readonly month = monthOf(inject(NOW)());
  protected readonly monthName = monthLabel(this.month);
  protected readonly money = formatRupees;
  protected readonly state = signal<LoadState<MonthDashboard>>({ status: 'loading' });

  protected readonly chosen = signal<string | null>(this.route.snapshot.queryParamMap.get('flat'));
  protected readonly method = signal<OfflineMethod>('CASH');
  protected readonly reference = signal('');
  protected readonly waive = signal(false);
  protected readonly reason = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly recorded = signal<Recorded | null>(null);

  protected readonly pending = computed<PendingFlat[]>(() => {
    const s = this.state();
    return s.status === 'ready' ? s.data.pending : [];
  });
  protected readonly selected = computed<PendingFlat | null>(
    () => this.pending().find((p) => p.flatId === this.chosen()) ?? this.pending().at(0) ?? null,
  );
  protected readonly lateFeePaise = computed(() => {
    const flat = this.selected();
    return flat ? flat.dueNowPaise - flat.amountPaise : 0;
  });
  protected readonly total = computed(() => {
    const flat = this.selected();
    return flat ? flat.dueNowPaise - (this.waive() ? this.lateFeePaise() : 0) : 0;
  });

  constructor() {
    this.load();
  }

  protected read(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected asMethod(event: Event): OfflineMethod {
    return this.read(event) === 'CHEQUE' ? 'CHEQUE' : 'CASH';
  }

  protected checked(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
  }

  protected load(): void {
    this.state.set({ status: 'loading' });
    this.api.dashboard(this.month).subscribe({
      next: (data) => this.state.set({ status: 'ready', data }),
      error: () => this.state.set({ status: 'error' }),
    });
  }

  protected save(): void {
    const flat = this.selected();
    if (!flat) {
      this.error.set('Choose a flat.');
      return;
    }
    const waiving = this.waive() && this.lateFeePaise() > 0;
    if (waiving && !this.reason().trim()) {
      this.error.set('Give a reason for waiving the late fee.');
      return;
    }
    this.error.set(null);
    this.saving.set(true);
    const method = this.method();
    this.api
      .recordOffline({
        flatId: flat.flatId,
        month: this.month,
        method,
        reference: method === 'CHEQUE' && this.reference().trim() ? this.reference().trim() : null,
        waiveLateFee: waiving,
        waiverReason: waiving ? this.reason().trim() : null,
      })
      .subscribe({
        next: (result) => {
          this.saving.set(false);
          this.recorded.set({ flatNumber: flat.number, method, totalPaise: result.totalPaise, waivedPaise: result.waivedPaise });
          this.waive.set(false);
          this.reason.set('');
          this.reference.set('');
          this.chosen.set(null);
          this.load();
        },
        error: (e: unknown) => {
          this.saving.set(false);
          this.error.set(
            e instanceof HttpErrorResponse && e.status === 409
              ? 'That flat has already paid for this month.'
              : 'We could not record the payment. Try again.',
          );
        },
      });
  }
}
