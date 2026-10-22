import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { NOW } from '../../core/clock';
import { MonthDashboard, Role } from '../../core/models';
import { DashboardPage } from './dashboard';

describe('DashboardPage', () => {
  const api = { dashboard: vi.fn(), exportMonth: vi.fn() };
  let fixture: ComponentFixture<DashboardPage>;
  let page: HTMLElement;

  const data: MonthDashboard = {
    month: '2026-10', flatCount: 45, paidCount: 31, pendingCount: 14,
    expectedPaise: 7000000, collectedPaise: 5000000, onlineCollectedPaise: 3000000, offlineCollectedPaise: 2000000,
    lateFeesCollectedPaise: 40000, lateFeesWaivedPaise: 10000,
    pending: [
      { flatId: 'f302', number: '302', floor: 3, amountPaise: 150000, dueNowPaise: 160000 },
      { flatId: 'f303', number: '303', floor: 3, amountPaise: 200000, dueNowPaise: 210000 },
    ],
  };

  async function openAs(role: Role): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: api },
        { provide: AuthService, useValue: { role: signal(role) } },
        { provide: NOW, useValue: () => new Date('2026-10-20T10:00:00Z') },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DashboardPage);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.resetTestingModule();
  });

  it('asks for the current month', async () => {
    api.dashboard.mockReturnValue(of(data));

    await openAs('TREASURER');

    expect(api.dashboard).toHaveBeenCalledWith('2026-10');
  });

  it('says it is loading while it waits', async () => {
    api.dashboard.mockReturnValue(NEVER);

    await openAs('TREASURER');

    expect(page.textContent).toContain('Loading');
  });

  it('shows a clear error with a way to try again', async () => {
    api.dashboard.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    await openAs('TREASURER');
    expect(page.textContent).toContain('could not load');

    api.dashboard.mockReturnValue(of(data));
    Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.includes('Try again'))?.click();
    fixture.detectChanges();

    expect(page.textContent).toContain('31 of 45');
  });

  it('shows what has been collected against what was expected', async () => {
    api.dashboard.mockReturnValue(of(data));

    await openAs('TREASURER');

    expect(page.textContent).toContain('₹50,000');
    expect(page.textContent).toContain('₹70,000');
    expect(page.textContent).toContain('31 of 45');
  });

  it('splits online from offline and shows late fees collected and waived', async () => {
    api.dashboard.mockReturnValue(of(data));

    await openAs('TREASURER');

    expect(page.textContent).toContain('Online');
    expect(page.textContent).toContain('₹30,000');
    expect(page.textContent).toContain('Offline');
    expect(page.textContent).toContain('₹20,000');
    expect(page.textContent).toContain('Late fees collected');
    expect(page.textContent).toContain('₹400');
    expect(page.textContent).toContain('Late fees waived');
    expect(page.textContent).toContain('₹100');
  });

  it('lists the pending flats with what each owes right now', async () => {
    api.dashboard.mockReturnValue(of(data));

    await openAs('TREASURER');

    expect(page.textContent).toContain('Flat 302');
    expect(page.textContent).toContain('₹1,600');
    expect(page.textContent).toContain('Flat 303');
    expect(page.textContent).toContain('₹2,100');
  });

  it('lets the treasurer record a payment for a pending flat', async () => {
    api.dashboard.mockReturnValue(of(data));

    await openAs('TREASURER');

    const links = Array.from(page.querySelectorAll('a')).filter((a) => a.textContent?.includes('Record'));
    expect(links).toHaveLength(2);
    expect(links[0]?.getAttribute('href')).toContain('flat=f302');
  });

  it('gives the committee a read-only view', async () => {
    api.dashboard.mockReturnValue(of(data));

    await openAs('COMMITTEE');

    expect(Array.from(page.querySelectorAll('a')).some((a) => a.textContent?.includes('Record'))).toBe(false);
    expect(page.textContent).toContain('View only');
    expect(page.textContent).toContain('Flat 302');
  });

  describe('downloading the month as a spreadsheet', () => {
    let click: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
      URL.createObjectURL = vi.fn(() => 'blob:csv');
      URL.revokeObjectURL = vi.fn();
      click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    });

    afterEach(() => click.mockRestore());

    const button = () => Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.includes('Download CSV'));

    it('is offered to the treasurer and the committee', async () => {
      api.dashboard.mockReturnValue(of(data));

      await openAs('COMMITTEE');

      expect(button()).toBeTruthy();
    });

    it('asks the server for this month and saves the file', async () => {
      api.dashboard.mockReturnValue(of(data));
      api.exportMonth.mockReturnValue(of(new Blob(['x'])));
      await openAs('TREASURER');

      button()?.click();
      fixture.detectChanges();

      expect(api.exportMonth).toHaveBeenCalledWith('2026-10');
      expect(URL.createObjectURL).toHaveBeenCalled();
      expect(click).toHaveBeenCalled();
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:csv');
    });

    it('says so when the download fails and lets them try again', async () => {
      api.dashboard.mockReturnValue(of(data));
      api.exportMonth.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      await openAs('TREASURER');

      button()?.click();
      fixture.detectChanges();

      expect(page.textContent).toContain('could not download');
      expect(click).not.toHaveBeenCalled();
      expect(button()?.disabled).toBe(false);
    });
  });

  it('says so when every flat has paid', async () => {
    api.dashboard.mockReturnValue(of({ ...data, paidCount: 45, pendingCount: 0, pending: [] }));

    await openAs('TREASURER');

    expect(page.textContent).toContain('Every flat has paid');
  });
});
