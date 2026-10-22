export type Role = 'OWNER' | 'TREASURER' | 'COMMITTEE';

export interface Session {
  token: string;
  role: Role;
  flatId: string | null;
  name: string;
  societyCode: string;
}

/** What the login endpoint returns. The society code comes from what the person typed. */
export interface VerifyResponse {
  token: string;
  role: Role;
  flatId: string | null;
  name: string;
}

export interface Dues {
  flatNumber: string;
  month: string;
  amountPaise: number;
  lateFeePaise: number;
  totalPaise: number;
  paid: boolean;
}

export interface FlatStatus {
  flatId: string;
  number: string;
  floor: number;
  paid: boolean;
}

export interface PaymentIntent {
  paymentId: string;
  flatId: string;
  month: string;
  totalPaise: number;
  lateFeePaise: number;
  currency: string;
  gatewayOrderId: string;
  /** The public key for the payment page. The value "fake" means a development simulation. */
  checkoutKey: string;
}

/** What the payment page hands back after a payment. The server checks it; the browser never decides. */
export interface CheckoutProof {
  gatewayOrderId: string;
  gatewayPaymentId: string;
  signature: string;
}

/** A flat that has not paid. `dueNowPaise` includes any late fee that applies today. */
export interface PendingFlat {
  flatId: string;
  number: string;
  floor: number;
  amountPaise: number;
  dueNowPaise: number;
}

export interface MonthDashboard {
  month: string;
  flatCount: number;
  paidCount: number;
  pendingCount: number;
  expectedPaise: number;
  collectedPaise: number;
  onlineCollectedPaise: number;
  offlineCollectedPaise: number;
  lateFeesCollectedPaise: number;
  lateFeesWaivedPaise: number;
  pending: PendingFlat[];
}

export type OfflineMethod = 'CASH' | 'CHEQUE';

/** There is no amount here on purpose: the server works out what is owed. */
export interface OfflinePaymentRequest {
  flatId: string;
  month: string;
  method: OfflineMethod;
  reference: string | null;
  waiveLateFee: boolean;
  waiverReason: string | null;
}

export interface OfflinePaymentResult {
  paymentId: string;
  totalPaise: number;
  lateFeePaise: number;
  waivedPaise: number;
}
