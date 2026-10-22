import { Component, computed, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { NOW } from '../../core/clock';
import { LoadState } from '../../core/load-state';
import { FlatStatus } from '../../core/models';
import { monthLabel, monthOf } from '../../core/month';

@Component({
  selector: 'app-board',
  template: `
    <header class="bar">
      <div>
        <h1>All flats</h1>
        <p class="muted">{{ monthName }}</p>
      </div>
      @if (summary(); as s) {
        <span class="pill good">{{ s.paid }} of {{ s.total }} paid</span>
      }
    </header>

    @switch (state().status) {
      @case ('loading') {
        <p class="muted" role="status">Loading flats…</p>
      }
      @case ('error') {
        <div class="card" role="alert">
          <p>We could not load the flats.</p>
          <button class="btn" type="button" (click)="load()">Try again</button>
        </div>
      }
      @case ('ready') {
        @if (floors().length === 0) {
          <p class="muted">No flats found.</p>
        } @else {
          <section class="card" aria-label="Flats by floor">
            @for (floor of floors(); track floor.floor) {
              <div class="floor" [attr.data-floor]="floor.floor">
                <span class="floor-label mono">F{{ floor.floor }}</span>
                <div class="chips">
                  @for (flat of floor.flats; track flat.number) {
                    <span class="chip" [class.paid]="flat.paid" [class.pending]="!flat.paid"
                          [attr.aria-label]="'Flat ' + flat.number + ', ' + (flat.paid ? 'paid' : 'pending')">{{ flat.number }}</span>
                  }
                </div>
              </div>
            }
          </section>
          <p class="muted small">Status only. Amounts stay private.</p>
        }
      }
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 1rem; }
    h1 { margin: 0; font-size: 1.25rem; }
    .bar { display: flex; justify-content: space-between; align-items: flex-start; gap: 0.5rem; }
    .floor { display: flex; align-items: flex-start; gap: 0.75rem; margin-bottom: 0.5rem; }
    .floor-label { flex: none; width: 2rem; padding-top: 0.4rem; font-size: 0.75rem; color: var(--muted); }
    .chips { display: flex; flex-wrap: wrap; gap: 0.35rem; }
    .chip { min-width: 2.75rem; padding: 0.4rem 0.3rem; border-radius: 0.6rem; text-align: center; font: 600 0.75rem var(--f-mono); }
    .chip.paid { background: var(--good-bg); color: var(--good); }
    .chip.pending { background: var(--warn-bg); color: var(--warn); }
  `,
})
export class Board {
  private readonly api = inject(ApiService);

  protected readonly month = monthOf(inject(NOW)());
  protected readonly monthName = monthLabel(this.month);
  protected readonly state = signal<LoadState<FlatStatus[]>>({ status: 'loading' });

  protected readonly floors = computed(() => {
    const s = this.state();
    if (s.status !== 'ready') {
      return [];
    }
    const byFloor = new Map<number, FlatStatus[]>();
    for (const flat of s.data) {
      byFloor.set(flat.floor, [...(byFloor.get(flat.floor) ?? []), flat]);
    }
    return [...byFloor.entries()].sort((a, b) => b[0] - a[0]).map(([floor, flats]) => ({ floor, flats }));
  });

  protected readonly summary = computed(() => {
    const s = this.state();
    return s.status === 'ready' && s.data.length > 0
      ? { paid: s.data.filter((f) => f.paid).length, total: s.data.length }
      : null;
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set({ status: 'loading' });
    this.api.board(this.month).subscribe({
      next: (data) => this.state.set({ status: 'ready', data }),
      error: () => this.state.set({ status: 'error' }),
    });
  }
}
