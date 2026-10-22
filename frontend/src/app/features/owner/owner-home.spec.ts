import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ApiService } from '../../core/api.service';
import { PaymentFlow, PayOutcome } from '../../core/payment-flow';
import { AuthService } from '../../core/auth.service';
import { NOW } from '../../core/clock';
import { Dues } from '../../core/models';
import { OwnerHome } from './owner-home';

describe('OwnerHome', () => {
  const api = { dues: vi.fn() };
  const flow = { pay: vi.fn() };
  const intent = { paymentId: 'p1', flatId: 'flat-304', month: '2026-10', totalPaise: 160000, lateFeePaise: 10000, currency: 'INR', gatewayOrderId: 'o1', checkoutKey: 'k' };
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
        { provide: PaymentFlow, useValue: flow },
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

  async function payWith(outcome: PayOutcome): Promise<void> {
    flow.pay.mockResolvedValue(outcome);
    press('Pay now');
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('pays this flat and month and records the owner as the payer', async () => {
    api.dues.mockReturnValue(of(unpaid));
    await open();

    await payWith({ kind: 'paid', intent });

    expect(flow.pay).toHaveBeenCalledWith('flat-304', '2026-10', 'Rajesh K');
  });

  it('shows the month as paid after a payment goes through', async () => {
    api.dues.mockReturnValueOnce(of(unpaid));
    await open();
    api.dues.mockReturnValue(of(paid));

    await payWith({ kind: 'paid', intent });

    expect(page.textContent).toContain('Payment received');
    expect(page.textContent).toContain('Nothing to pay');
  });

  it('cannot be pressed twice while a payment is under way', async () => {
    api.dues.mockReturnValue(of(unpaid));
    await open();
    flow.pay.mockReturnValue(new Promise(() => undefined));

    press('Pay now');

    const button = Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.includes('Opening'));
    expect(button?.disabled).toBe(true);
  });

  it('says nothing was charged when the payment page is closed', async () => {
    api.dues.mockReturnValue(of(unpaid));
    await open();

    await payWith({ kind: 'cancelled' });

    expect(page.textContent).toContain('Payment cancelled');
    expect(page.textContent).toContain('Nothing was charged');
    expect(Array.from(page.querySelectorAll('button')).some((b) => b.textContent?.includes('Pay now'))).toBe(true);
  });

  it('says so when the payment page cannot be opened', async () => {
    api.dues.mockReturnValue(of(unpaid));
    await open();

    await payWith({ kind: 'pageUnavailable' });

    expect(page.textContent).toContain('could not open the payment page');
  });

  it('says so when the payment provider is down', async () => {
    api.dues.mockReturnValue(of(unpaid));
    await open();

    await payWith({ kind: 'providerDown' });

    expect(page.textContent).toContain('payment provider is not available');
  });

  it('explains when the month turns out to be paid already, and shows the real state', async () => {
    api.dues.mockReturnValueOnce(of(unpaid));
    await open();
    api.dues.mockReturnValue(of(paid));

    await payWith({ kind: 'alreadyPaid' });

    expect(page.textContent).toContain('already paid');
    expect(page.textContent).toContain('Nothing to pay');
  });

  it('shows a plain message when starting the payment fails', async () => {
    api.dues.mockReturnValue(of(unpaid));
    await open();

    await payWith({ kind: 'failed' });

    expect(page.textContent).toContain('could not start');
  });

  it('asks the owner to check again when money may have been taken but is not confirmed', async () => {
    api.dues.mockReturnValueOnce(of(unpaid));
    await open();

    await payWith({ kind: 'notConfirmed', intent });

    expect(page.textContent).toContain('not confirmed yet');
    expect(page.textContent).toContain('do not pay again');
    api.dues.mockReturnValue(of(paid));
    press('Check again');
    expect(page.textContent).toContain('Nothing to pay');
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
