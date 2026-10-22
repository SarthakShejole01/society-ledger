import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Dues, FlatStatus, MonthDashboard, OfflinePaymentRequest, OfflinePaymentResult, PaymentIntent } from './models';

/** The only place the app talks to the server's data endpoints. Months are written 2026-10. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  dues(flatId: string, month: string): Observable<Dues> {
    return this.http.get<Dues>(`/api/flats/${flatId}/dues`, { params: new HttpParams().set('month', month) });
  }

  board(month: string): Observable<FlatStatus[]> {
    return this.http.get<FlatStatus[]>('/api/flats/status', { params: new HttpParams().set('month', month) });
  }

  /** The server decides the amount, so none is sent. */
  startPayment(flatId: string, month: string): Observable<PaymentIntent> {
    return this.http.post<PaymentIntent>('/api/payments', { flatId, month });
  }

  dashboard(month: string): Observable<MonthDashboard> {
    return this.http.get<MonthDashboard>('/api/dashboard', { params: new HttpParams().set('month', month) });
  }

  /** Cash or cheque received by the treasurer. The server works out the amount, so none is sent. */
  recordOffline(request: OfflinePaymentRequest): Observable<OfflinePaymentResult> {
    return this.http.post<OfflinePaymentResult>('/api/payments/offline', request);
  }
}
