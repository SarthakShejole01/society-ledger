import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { CheckoutDismissed, CheckoutService, CheckoutUnavailable, LOAD_SCRIPT } from './checkout.service';
import { PaymentIntent } from './models';

interface RazorpayOptions {
  key: string;
  order_id: string;
  name: string;
  currency?: string;
  amount?: number;
  prefill?: { name?: string };
  handler: (response: { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }) => void;
  modal?: { ondismiss?: () => void };
}

describe('CheckoutService', () => {
  const loadScript = vi.fn();
  const intent: PaymentIntent = {
    paymentId: 'p1', flatId: 'f1', month: '2026-10', totalPaise: 160000, lateFeePaise: 10000,
    currency: 'INR', gatewayOrderId: 'order_Abc', checkoutKey: 'rzp_test_key',
  };
  let captured: RazorpayOptions | null;
  let finish: (options: RazorpayOptions) => void;

  function service(): CheckoutService {
    TestBed.configureTestingModule({ providers: [{ provide: LOAD_SCRIPT, useValue: loadScript }] });
    return TestBed.inject(CheckoutService);
  }

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.resetTestingModule();
    captured = null;
    loadScript.mockResolvedValue(undefined);
    finish = () => undefined;
    (window as unknown as { Razorpay?: unknown }).Razorpay = class {
      constructor(private readonly options: RazorpayOptions) {
        captured = options;
      }
      open(): void {
        finish(this.options);
      }
    };
  });

  afterEach(() => {
    delete (window as unknown as { Razorpay?: unknown }).Razorpay;
  });

  it('opens the payment page for the order, with the public key and the payer name', async () => {
    finish = (o) => o.handler({ razorpay_order_id: 'order_Abc', razorpay_payment_id: 'pay_1', razorpay_signature: 'sig' });

    await service().open(intent, 'Rajesh K');

    expect(loadScript).toHaveBeenCalledWith('https://checkout.razorpay.com/v1/checkout.js');
    expect(captured?.key).toBe('rzp_test_key');
    expect(captured?.order_id).toBe('order_Abc');
    expect(captured?.prefill?.name).toBe('Rajesh K');
  });

  it('never gives the payment page an amount, because the order already has it', async () => {
    finish = (o) => o.handler({ razorpay_order_id: 'order_Abc', razorpay_payment_id: 'pay_1', razorpay_signature: 'sig' });

    await service().open(intent, 'Rajesh K');

    expect(captured?.amount).toBeUndefined();
  });

  it('returns the proof the payment page hands back', async () => {
    finish = (o) => o.handler({ razorpay_order_id: 'order_Abc', razorpay_payment_id: 'pay_1', razorpay_signature: 'sig' });

    const proof = await service().open(intent, 'Rajesh K');

    expect(proof).toEqual({ gatewayOrderId: 'order_Abc', gatewayPaymentId: 'pay_1', signature: 'sig' });
  });

  it('reports a closed payment page as dismissed', async () => {
    finish = (o) => o.modal?.ondismiss?.();

    await expect(service().open(intent, 'Rajesh K')).rejects.toBeInstanceOf(CheckoutDismissed);
  });

  it('reports a payment page that cannot be loaded', async () => {
    loadScript.mockRejectedValue(new Error('offline'));

    await expect(service().open(intent, 'Rajesh K')).rejects.toBeInstanceOf(CheckoutUnavailable);
  });

  it('reports a payment page that did not start', async () => {
    delete (window as unknown as { Razorpay?: unknown }).Razorpay;

    await expect(service().open(intent, 'Rajesh K')).rejects.toBeInstanceOf(CheckoutUnavailable);
  });

  it('simulates the payment in development, without loading anything from outside', async () => {
    const proof = await service().open({ ...intent, checkoutKey: 'fake', gatewayOrderId: 'order_fake_1' }, 'Rajesh K');

    expect(loadScript).not.toHaveBeenCalled();
    expect(proof.gatewayOrderId).toBe('order_fake_1');
    expect(proof.signature).toBe('fake-signature');
    expect(proof.gatewayPaymentId).toMatch(/^pay_fake_/);
  });
});
