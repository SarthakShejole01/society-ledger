import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { NOW } from '../../core/clock';
import { LoadState } from '../../core/load-state';
import { MonthDashboard } from '../../core/models';
import { formatRupees } from '../../core/money';
import { monthLabel, monthOf } from '../../core/month';

/** The month at a glance, for the treasurer and the committee. Only the treasurer can act on it. */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  template: `
    <header class="bar">
      <div>
        <h1>Dashboard</h1>
        <p class="muted">{{ monthName }}</p>
      </div>
      @if (data(); as d) {
        <span class="pill good">{{ d.paidCount }} of {{ d.flatCount }} paid</span>
      }
    </header>

    @switch (state().status) {
      @case ('loading') {
        <p class="muted" role="status">Loading the dashboard…</p>
      }
      @case ('error') {
        <div class="card" role="alert">
          <p>We could not load the dashboard.</p>
          <button class="btn" type="button" (click)="load()">Try again</button>
        </div>
      }
      @case ('ready') {
        @if (data(); as d) {
          <section class="hero" aria-label="Collected this month">
            <div class="hero-top"><span>Collected in {{ monthName }}</span></div>
            <p class="amount mono">{{ money(d.collectedPaise) }}</p>
            <p class="hero-note">of {{ money(d.expectedPaise) }} expected</p>
            <div class="meter" aria-hidden="true"><i [style.width.%]="progress()"></i></div>
          </section>

          <section class="card">
            <div class="line"><span>Online</span><span class="mono">{{ money(d.onlineCollectedPaise) }}</span></div>
            <div class="line"><span>Offline (cash and cheque)</span><span class="mono">{{ money(d.offlineCollectedPaise) }}</span></div>
            <div class="line"><span>Late fees collected</span><span class="mono">{{ money(d.lateFeesCollectedPaise) }}</span></div>
            <div class="line"><span>Late fees waived</span><span class="mono">{{ money(d.lateFeesWaivedPaise) }}</span></div>
          </section>

          <section class="card">
            <div class="bar">
              <h2>Pending flats</h2>
              <span class="pill warn">{{ d.pendingCount }}</span>
            </div>
            @if (d.pending.length === 0) {
              <p class="muted">Every flat has paid for {{ monthName }}.</p>
            } @else {
              <ul class="rows">
                @for (flat of d.pending; track flat.flatId) {
                  <li>
                    <span>Flat {{ flat.number }}</span>
                    <span class="mono">{{ money(flat.dueNowPaise) }}</span>
                    @if (isTreasurer()) {
                      <a class="btn sm" routerLink="/record" [queryParams]="{ flat: flat.flatId }">Record</a>
                    }
                  </li>
                }
              </ul>
            }
            @if (!isTreasurer()) {
              <p class="muted small">View only. Recording payments is for the treasurer.</p>
            }
          </section>
        }
      }
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 1rem; }
    h1 { margin: 0; font-size: 1.25rem; }
    h2 { margin: 0; font-size: 1rem; }
    .bar { display: flex; justify-content: space-between; align-items: flex-start; gap: 0.5rem; }
    .meter { height: 8px; border-radius: 99px; background: rgba(255, 255, 255, 0.22); overflow: hidden; margin-top: 12px; }
    .meter i { display: block; height: 100%; background: #8be0b4; border-radius: 99px; }
    .rows { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .rows li { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; padding: 0.55rem 0; border-bottom: 1px solid var(--line); }
    .rows li:last-child { border-bottom: 0; }
    .rows li > span:first-child { flex: 1; }
    .rows a { text-decoration: none; text-align: center; }
  `,
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly month = monthOf(inject(NOW)());
  protected readonly monthName = monthLabel(this.month);
  protected readonly state = signal<LoadState<MonthDashboard>>({ status: 'loading' });
  protected readonly money = formatRupees;
  protected readonly isTreasurer = computed(() => this.auth.role() === 'TREASURER');

  protected readonly data = computed(() => {
    const s = this.state();
    return s.status === 'ready' ? s.data : null;
  });

  protected readonly progress = computed(() => {
    const d = this.data();
    return d && d.expectedPaise > 0 ? Math.min(100, (d.collectedPaise / d.expectedPaise) * 100) : 0;
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set({ status: 'loading' });
    this.api.dashboard(this.month).subscribe({
      next: (data) => this.state.set({ status: 'ready', data }),
      error: () => this.state.set({ status: 'error' }),
    });
  }
}
