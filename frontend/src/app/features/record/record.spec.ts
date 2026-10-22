import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ApiService } from '../../core/api.service';
import { NOW } from '../../core/clock';
import { MonthDashboard } from '../../core/models';
import { RecordPage } from './record';

describe('RecordPage', () => {
  const api = { dashboard: vi.fn(), recordOffline: vi.fn() };
  let fixture: ComponentFixture<RecordPage>;
  let page: HTMLElement;
  let now = '2026-10-20T10:00:00Z';

  const pending = [
    { flatId: 'f302', number: '302', floor: 3, amountPaise: 150000, dueNowPaise: 160000 },
    { flatId: 'f303', number: '303', floor: 3, amountPaise: 200000, dueNowPaise: 210000 },
  ];
  const data: MonthDashboard = {
    month: '2026-10', flatCount: 45, paidCount: 43, pendingCount: 2, expectedPaise: 0, collectedPaise: 0,
    onlineCollectedPaise: 0, offlineCollectedPaise: 0, lateFeesCollectedPaise: 0, lateFeesWaivedPaise: 0, pending,
  };
  const onTime = { ...data, pending: pending.map((p) => ({ ...p, dueNowPaise: p.amountPaise })) };

  async function open(flat: string | null = null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [RecordPage],
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: api },
        { provide: NOW, useValue: () => new Date(now) },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(flat ? { flat } : {}) } } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(RecordPage);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  function set(selector: string, value: string, event: 'input' | 'change' = 'input'): void {
    const field = page.querySelector<HTMLInputElement | HTMLSelectElement>(selector);
    if (!field) {
      throw new Error(`No field ${selector}`);
    }
    field.value = value;
    field.dispatchEvent(new Event(event));
    fixture.detectChanges();
  }

  function check(selector: string, checked: boolean): void {
    const box = page.querySelector<HTMLInputElement>(selector);
    if (!box) {
      throw new Error(`No checkbox ${selector}`);
    }
    box.checked = checked;
    box.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function press(label: string): void {
    const button = Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.includes(label));
    if (!button) {
      throw new Error(`No button "${label}"`);
    }
    button.click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.resetAllMocks();
    now = '2026-10-20T10:00:00Z';
    TestBed.resetTestingModule();
  });

  it('says it is loading while it waits', async () => {
    api.dashboard.mockReturnValue(NEVER);

    await open();

    expect(page.textContent).toContain('Loading');
  });

  it('offers the flats that have not paid', async () => {
    api.dashboard.mockReturnValue(of(data));

    await open();

    const options = Array.from(page.querySelectorAll('#flat option')).map((o) => o.textContent?.trim());
    expect(options).toEqual(['Flat 302 · due ₹1,600', 'Flat 303 · due ₹2,100']);
  });

  it('starts on the flat chosen on the dashboard', async () => {
    api.dashboard.mockReturnValue(of(data));

    await open('f303');

    expect(page.querySelector<HTMLSelectElement>('#flat')?.value).toBe('f303');
    expect(page.textContent).toContain('₹2,100');
  });

  it('says when every flat has already paid', async () => {
    api.dashboard.mockReturnValue(of({ ...data, pending: [], pendingCount: 0 }));

    await open();

    expect(page.textContent).toContain('Every flat has paid');
  });

  it('shows a clear error with a way to try again', async () => {
    api.dashboard.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    await open();
    expect(page.textContent).toContain('could not load');

    api.dashboard.mockReturnValue(of(data));
    press('Try again');

    expect(page.querySelector('#flat')).not.toBeNull();
  });

  it('asks for a cheque number only for cheques', async () => {
    api.dashboard.mockReturnValue(of(data));
    await open();
    expect(page.querySelector('#reference')).toBeNull();

    set('#method', 'CHEQUE', 'change');

    expect(page.querySelector('#reference')).not.toBeNull();
  });

  it('offers a waiver only when a late fee applies', async () => {
    api.dashboard.mockReturnValue(of(onTime));

    await open();

    expect(page.querySelector('#waive')).toBeNull();
  });

  it('takes the late fee off the total when it is waived, and asks why', async () => {
    api.dashboard.mockReturnValue(of(data));
    await open();
    expect(page.querySelector('#reason')).toBeNull();

    check('#waive', true);

    expect(page.querySelector('#reason')).not.toBeNull();
    expect(page.textContent).toContain('Total to record');
    expect(page.querySelector('[data-testid="total"]')?.textContent).toContain('₹1,500');
  });

  it('will not record a waiver without a reason', async () => {
    api.dashboard.mockReturnValue(of(data));
    await open();
    check('#waive', true);

    press('Record payment');

    expect(api.recordOffline).not.toHaveBeenCalled();
    expect(page.textContent).toContain('Give a reason');
  });

  it('records the payment with the details chosen and never an amount', async () => {
    api.dashboard.mockReturnValue(of(data));
    api.recordOffline.mockReturnValue(of({ paymentId: 'p1', totalPaise: 150000, lateFeePaise: 0, waivedPaise: 10000 }));
    await open();
    set('#method', 'CHEQUE', 'change');
    set('#reference', '000123');
    check('#waive', true);
    set('#reason', 'Owner was in hospital');

    press('Record payment');

    expect(api.recordOffline).toHaveBeenCalledWith({
      flatId: 'f302', month: '2026-10', method: 'CHEQUE', reference: '000123',
      waiveLateFee: true, waiverReason: 'Owner was in hospital',
    });
  });

  it('confirms what was recorded and updates the list of flats', async () => {
    api.dashboard.mockReturnValueOnce(of(data));
    api.recordOffline.mockReturnValue(of({ paymentId: 'p1', totalPaise: 160000, lateFeePaise: 10000, waivedPaise: 0 }));
    await open();
    api.dashboard.mockReturnValue(of({ ...data, pending: pending.slice(1) }));

    press('Record payment');

    expect(page.textContent).toContain('Recorded');
    expect(page.textContent).toContain('₹1,600');
    const options = Array.from(page.querySelectorAll('#flat option')).map((o) => o.textContent?.trim());
    expect(options).toEqual(['Flat 303 · due ₹2,100']);
  });

  it('explains when the flat turns out to be paid already', async () => {
    api.dashboard.mockReturnValue(of(data));
    api.recordOffline.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    await open();

    press('Record payment');

    expect(page.textContent).toContain('already paid');
  });

  it('shows a plain message for any other failure', async () => {
    api.dashboard.mockReturnValue(of(data));
    api.recordOffline.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    await open();

    press('Record payment');

    expect(page.textContent).toContain('could not record');
  });
});
