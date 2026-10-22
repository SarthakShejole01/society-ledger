import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { CheckoutDismissed, CheckoutService } from './checkout.service';
import { PaymentIntent } from './models';

/** Every way paying a month can end. The screens only decide how to word each one. */
export type PayOutcome =
  | { kind: 'paid'; intent: PaymentIntent }
  /** Money may have been taken, but the server has not confirmed it yet. */
  | { kind: 'notConfirmed'; intent: PaymentIntent }
  | { kind: 'cancelled' }
  | { kind: 'pageUnavailable' }
  | { kind: 'alreadyPaid' }
  | { kind: 'providerDown' }
  | { kind: 'failed' };

/** Start the payment, open the provider's page, and have the server confirm the proof. */
@Injectable({ providedIn: 'root' })
export class PaymentFlow {
  private readonly api = inject(ApiService);
  private readonly checkout = inject(CheckoutService);

  async pay(flatId: string, month: string, payerName: string): Promise<PayOutcome> {
    let intent: PaymentIntent;
    try {
      intent = await firstValueFrom(this.api.startPayment(flatId, month));
    } catch (e) {
      const status = e instanceof HttpErrorResponse ? e.status : 0;
      return status === 409 ? { kind: 'alreadyPaid' } : status === 502 ? { kind: 'providerDown' } : { kind: 'failed' };
    }

    let proof;
    try {
      proof = await this.checkout.open(intent, payerName);
    } catch (e) {
      return e instanceof CheckoutDismissed ? { kind: 'cancelled' } : { kind: 'pageUnavailable' };
    }

    try {
      await firstValueFrom(this.api.confirmPayment(intent.paymentId, proof));
      return { kind: 'paid', intent };
    } catch {
      return { kind: 'notConfirmed', intent };
    }
  }
}
