import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ApiService } from './api.service';
import { CheckoutDismissed, CheckoutService, CheckoutUnavailable } from './checkout.service';
import { PaymentIntent } from './models';
import { PaymentFlow } from './payment-flow';

describe('PaymentFlow', () => {
  const api = { startPayment: vi.fn(), confirmPayment: vi.fn() };
  const checkout = { open: vi.fn() };
  const intent: PaymentIntent = {
    paymentId: 'p1', flatId: 'f1', month: '2026-10', totalPaise: 160000, lateFeePaise: 10000,
    currency: 'INR', gatewayOrderId: 'order_Abc', checkoutKey: 'rzp_test_key',
  };
  const proof = { gatewayOrderId: 'order_Abc', gatewayPaymentId: 'pay_1', signature: 'sig' };

  function flow(): PaymentFlow {
    TestBed.configureTestingModule({
      providers: [{ provide: ApiService, useValue: api }, { provide: CheckoutService, useValue: checkout }],
    });
    return TestBed.inject(PaymentFlow);
  }

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.resetTestingModule();
    api.startPayment.mockReturnValue(of(intent));
    api.confirmPayment.mockReturnValue(of({ status: 'PAID' }));
    checkout.open.mockResolvedValue(proof);
  });

  it('starts the payment, opens the payment page and confirms the proof', async () => {
    const outcome = await flow().pay('f1', '2026-10', 'Rajesh K');

    expect(api.startPayment).toHaveBeenCalledWith('f1', '2026-10');
    expect(checkout.open).toHaveBeenCalledWith(intent, 'Rajesh K');
    expect(api.confirmPayment).toHaveBeenCalledWith('p1', proof);
    expect(outcome).toEqual({ kind: 'paid', intent });
  });

  it('stops quietly when the payer closes the payment page, and confirms nothing', async () => {
    checkout.open.mockRejectedValue(new CheckoutDismissed());

    const outcome = await flow().pay('f1', '2026-10', 'Rajesh K');

    expect(outcome).toEqual({ kind: 'cancelled' });
    expect(api.confirmPayment).not.toHaveBeenCalled();
  });

  it('reports a payment page that could not be opened', async () => {
    checkout.open.mockRejectedValue(new CheckoutUnavailable());

    expect(await flow().pay('f1', '2026-10', 'Rajesh K')).toEqual({ kind: 'pageUnavailable' });
  });

  it('reports a month that is already paid', async () => {
    api.startPayment.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));

    expect(await flow().pay('f1', '2026-10', 'Rajesh K')).toEqual({ kind: 'alreadyPaid' });
    expect(checkout.open).not.toHaveBeenCalled();
  });

  it('reports a payment provider that is down', async () => {
    api.startPayment.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 502 })));

    expect(await flow().pay('f1', '2026-10', 'Rajesh K')).toEqual({ kind: 'providerDown' });
  });

  it('reports any other failure to start', async () => {
    api.startPayment.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    expect(await flow().pay('f1', '2026-10', 'Rajesh K')).toEqual({ kind: 'failed' });
  });

  it('says the payment is not confirmed yet when paid but the server could not confirm', async () => {
    api.confirmPayment.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    expect(await flow().pay('f1', '2026-10', 'Rajesh K')).toEqual({ kind: 'notConfirmed', intent });
  });

  it('says the payment is not confirmed when the server refuses the proof', async () => {
    api.confirmPayment.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

    expect(await flow().pay('f1', '2026-10', 'Rajesh K')).toEqual({ kind: 'notConfirmed', intent });
  });
});
