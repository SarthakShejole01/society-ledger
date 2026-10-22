import { Injectable, InjectionToken, inject } from '@angular/core';
import { CheckoutProof, PaymentIntent } from './models';

const RAZORPAY_SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js';

/** The payer closed the payment page without paying. */
export class CheckoutDismissed extends Error {
  constructor() {
    super('The payment page was closed');
  }
}

/** The payment page could not be loaded or opened. */
export class CheckoutUnavailable extends Error {
  constructor() {
    super('The payment page could not be opened');
  }
}

/** Loads a script once. Replaced in tests. */
export const LOAD_SCRIPT = new InjectionToken<(src: string) => Promise<void>>('LOAD_SCRIPT', {
  providedIn: 'root',
  factory: () => loadScript,
});

function loadScript(src: string): Promise<void> {
  return new Promise((resolve, reject) => {
    if (document.querySelector(`script[src="${src}"]`)) {
      resolve();
      return;
    }
    const script = document.createElement('script');
    script.src = src;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error(`Could not load ${src}`));
    document.head.appendChild(script);
  });
}

interface RazorpayResponse {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

interface RazorpayOptions {
  key: string;
  order_id: string;
  name: string;
  description: string;
  prefill: { name: string };
  theme: { color: string };
  handler: (response: RazorpayResponse) => void;
  modal: { ondismiss: () => void };
}

type RazorpayConstructor = new (options: RazorpayOptions) => { open(): void };

/** Opens the payment provider's page for an order. The order already carries the amount, so none is passed. */
@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly load = inject(LOAD_SCRIPT);

  async open(intent: PaymentIntent, payerName: string): Promise<CheckoutProof> {
    if (intent.checkoutKey === 'fake') {
      // Development only: the server's fake provider accepts this made-up proof.
      return {
        gatewayOrderId: intent.gatewayOrderId,
        gatewayPaymentId: `pay_fake_${Math.random().toString(36).slice(2, 10)}`,
        signature: 'fake-signature',
      };
    }
    try {
      await this.load(RAZORPAY_SCRIPT);
    } catch {
      throw new CheckoutUnavailable();
    }
    const Razorpay = (window as unknown as { Razorpay?: RazorpayConstructor }).Razorpay;
    if (!Razorpay) {
      throw new CheckoutUnavailable();
    }
    return new Promise<CheckoutProof>((resolve, reject) => {
      try {
        new Razorpay({
          key: intent.checkoutKey,
          order_id: intent.gatewayOrderId,
          name: 'Society Ledger',
          description: `Maintenance ${intent.month}`,
          prefill: { name: payerName },
          theme: { color: '#0b5d58' },
          handler: (response) =>
            resolve({
              gatewayOrderId: response.razorpay_order_id,
              gatewayPaymentId: response.razorpay_payment_id,
              signature: response.razorpay_signature,
            }),
          modal: { ondismiss: () => reject(new CheckoutDismissed()) },
        }).open();
      } catch {
        reject(new CheckoutUnavailable());
      }
    });
  }
}
