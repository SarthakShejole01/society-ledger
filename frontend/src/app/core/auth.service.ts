import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { Session, VerifyResponse } from './models';

const SESSION_KEY = 'society-ledger.session';
const SOCIETY_KEY = 'society-ledger.society';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly current = signal<Session | null>(readSession());

  readonly session = this.current.asReadonly();
  readonly isLoggedIn = computed(() => this.current() !== null);
  readonly role = computed(() => this.current()?.role ?? null);

  requestOtp(societyCode: string, mobile: string): Observable<void> {
    return this.http.post<void>('/api/auth/otp', { societyCode, mobile });
  }

  verify(societyCode: string, mobile: string, code: string): Observable<Session> {
    return this.http.post<VerifyResponse>('/api/auth/verify', { societyCode, mobile, code }).pipe(
      map((response): Session => ({ ...response, societyCode })),
      tap((session) => this.store(session)),
    );
  }

  logout(): void {
    this.current.set(null);
    safely(() => sessionStorage.removeItem(SESSION_KEY));
  }

  rememberedSocietyCode(): string {
    return safely(() => localStorage.getItem(SOCIETY_KEY)) ?? '';
  }

  private store(session: Session): void {
    this.current.set(session);
    safely(() => sessionStorage.setItem(SESSION_KEY, JSON.stringify(session)));
    safely(() => localStorage.setItem(SOCIETY_KEY, session.societyCode));
  }
}

function readSession(): Session | null {
  const raw = safely(() => sessionStorage.getItem(SESSION_KEY));
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<Session>;
    return parsed.token && parsed.role && parsed.name && parsed.societyCode ? (parsed as Session) : null;
  } catch {
    return null;
  }
}

/** Browser storage can be missing or blocked, so every use is guarded. */
function safely<T>(action: () => T): T | null {
  try {
    return action();
  } catch {
    return null;
  }
}
