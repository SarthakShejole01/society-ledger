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
}
