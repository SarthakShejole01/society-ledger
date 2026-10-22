import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { NOW } from '../../core/clock';
import { Dues } from '../../core/models';
import { OwnerHome } from './owner-home';

describe('OwnerHome', () => {
  const api = { dues: vi.fn(), startPayment: vi.fn() };
  const session = signal({ token: 't', role: 'OWNER' as const, flatId: 'flat-304' as string | null, name: 'Rajesh K', societyCode: 'sunrise' });
  let now = '2026-10-20T10:00:00Z';
  let fixture: ComponentFixture<OwnerHome>;
  let page: HTMLElement;

  const unpaid: Dues = { flatNumber: '304', month: '2026-10', amountPaise: 150000, lateFeePaise: 10000, totalPaise: 160000, paid: false };
  const unpaidOnTime: Dues = { ...unpaid, lateFeePaise: 0, totalPaise: 150000 };
  const paid: Dues = { ...unpaid, lateFeePaise: 0, totalPaise: 0, paid: true };

  async function open(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [OwnerHome],
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: api },
        { provide: AuthService, useValue: { session } },
        { provide: NOW, useValue: () => new Date(now) },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OwnerHome);
    page = fixture.nativeElement as HTMLElement;
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
    session.update((s) => ({ ...s, flatId: 'flat-304' as string | null }));
    now = '2026-10-20T10:00:00Z';
    TestBed.resetTestingModule();
  });

  it('asks the server for this flat and this month', async () => {
    api.dues.mockReturnValue(of(unpaid));

    await open();

    expect(api.dues).toHaveBeenCalledWith('flat-304', '2026-10');
  });

  it('says it is loading while it waits', async () => {
    api.dues.mockReturnValue(NEVER);

    await open();

    expect(page.textContent).toContain('Loading');
  });

  it('shows the amount, the late fee and the total', async () => {
    api.dues.mockReturnValue(of(unpaid));

    await open();

    expect(page.textContent).toContain('October 2026');
    expect(page.textContent).toContain('₹1,500');
    expect(page.textContent).toContain('Late fee');
    expect(page.textContent).toContain('₹100');
    expect(page.textContent).toContain('₹1,600');
  });

  it('does not mention a late fee when there is none', async () => {
    now = '2026-10-05T10:00:00Z';
    api.dues.mockReturnValue(of(unpaidOnTime));

    await open();

    expect(page.textContent).not.toContain('Late fee of');
    expect(page.textContent).toContain('Pay by the 10th');
  });

  it('says there is nothing to pay once the month is paid', async () => {
    api.dues.mockReturnValue(of(paid));

    await open();

    expect(page.textContent).toContain('Paid');
    expect(page.textContent).toContain('Nothing to pay');
    expect(Array.from(page.querySelectorAll('button')).some((b) => b.textContent?.includes('Pay now'))).toBe(false);
  });

  it('shows a clear error with a way to try again', async () => {
    api.dues.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    await open();
    expect(page.textContent).toContain('could not load');

    api.dues.mockReturnValue(of(unpaid));
    press('Try again');

    expect(page.textContent).toContain('₹1,600');
  });

  it('starts a payment for the flat and month, never sending an amount', async () => {
    api.dues.mockReturnValue(of(unpaid));
    api.startPayment.mockReturnValue(of({ paymentId: 'p1', flatId: 'flat-304', month: '2026-10', totalPaise: 160000, lateFeePaise: 10000 }));
    await open();

    press('Pay now');

    expect(api.startPayment).toHaveBeenCalledWith('flat-304', '2026-10');
    expect(page.textContent).toContain('Payment started');
    expect(page.textContent).toContain('₹1,600');
  });

  it('explains when the month turns out to be paid already', async () => {
    api.dues.mockReturnValue(of(unpaid));
    api.startPayment.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    await open();

    press('Pay now');

    expect(page.textContent).toContain('already paid');
  });

  it('shows a plain message when starting the payment fails', async () => {
    api.dues.mockReturnValue(of(unpaid));
    api.startPayment.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    await open();

    press('Pay now');

    expect(page.textContent).toContain('could not start');
  });

  it('says so when the login is not linked to a flat', async () => {
    session.update((s) => ({ ...s, flatId: null }));

    await open();

    expect(page.textContent).toContain('not linked to a flat');
    expect(api.dues).not.toHaveBeenCalled();
  });

  it('offers to pay for another flat', async () => {
    api.dues.mockReturnValue(of(unpaid));

    await open();

    const link = Array.from(page.querySelectorAll('a')).find((a) => a.textContent?.includes('Pay for another flat'));
    expect(link?.getAttribute('href')).toBe('/pay-for');
  });

  it('offers to pay for another flat even when this flat has paid', async () => {
    api.dues.mockReturnValue(of(paid));

    await open();

    expect(Array.from(page.querySelectorAll('a')).some((a) => a.textContent?.includes('Pay for another flat'))).toBe(true);
  });
});
