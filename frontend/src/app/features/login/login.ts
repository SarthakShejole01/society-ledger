import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  template: `
    <main class="login">
      <header>
        <p class="eyebrow">Society Ledger</p>
        <h1>Welcome back</h1>
        <p class="muted">Log in with the mobile number registered for your flat.</p>
      </header>

      @if (step() === 'number') {
        <form (submit)="$event.preventDefault(); sendCode()" novalidate>
          <div class="field">
            <label for="societyCode">Society code</label>
            <input id="societyCode" type="text" autocomplete="off" autocapitalize="none"
                   [value]="societyCode()" (input)="societyCode.set(read($event))" />
          </div>
          <div class="field">
            <label for="mobile">Registered mobile number</label>
            <input id="mobile" type="tel" inputmode="numeric" autocomplete="tel"
                   [value]="mobile()" (input)="mobile.set(read($event))" />
          </div>
          <button class="btn primary" type="submit" [disabled]="busy()">Send code</button>
        </form>
      } @else {
        <form (submit)="$event.preventDefault(); logIn()" novalidate>
          <p class="notice" role="status">If this number is registered, a code has been emailed to the address we have for you.</p>
          <div class="field">
            <label for="code">6-digit code from the email</label>
            <input id="code" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6"
                   [value]="code()" (input)="code.set(read($event))" />
          </div>
          <button class="btn primary" type="submit" [disabled]="busy()">Log in</button>
          <button class="btn ghost" type="button" (click)="back()">Use a different number</button>
        </form>
      }

      @if (error(); as text) {
        <p class="error" role="alert">{{ text }}</p>
      }
    </main>
  `,
  styles: `
    .login { max-width: 26rem; margin: 0 auto; padding-block: 3rem; display: flex; flex-direction: column; gap: 1.5rem; }
    form { display: flex; flex-direction: column; gap: 1rem; }
    h1 { margin: 0.25rem 0; }
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly societyCode = signal(this.auth.rememberedSocietyCode());
  protected readonly mobile = signal('');
  protected readonly code = signal('');
  protected readonly step = signal<'number' | 'code'>('number');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected read(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected sendCode(): void {
    if (!this.societyCode().trim()) {
      this.error.set('Enter the society code.');
      return;
    }
    if (!this.mobile().trim()) {
      this.error.set('Enter the mobile number registered for your flat.');
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    this.auth.requestOtp(this.societyCode().trim(), this.mobile().trim()).subscribe({
      next: () => {
        this.busy.set(false);
        this.step.set('code');
      },
      error: (e: unknown) => this.fail(e),
    });
  }

  protected logIn(): void {
    if (!/^\d{6}$/.test(this.code().trim())) {
      this.error.set('Enter the 6-digit code from the email.');
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    this.auth.verify(this.societyCode().trim(), this.mobile().trim(), this.code().trim()).subscribe({
      next: () => {
        this.busy.set(false);
        void this.router.navigate(['/']);
      },
      error: (e: unknown) => this.fail(e),
    });
  }

  protected back(): void {
    this.code.set('');
    this.error.set(null);
    this.step.set('number');
  }

  private fail(error: unknown): void {
    this.busy.set(false);
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 401) {
      this.error.set('That code is not valid. Check it, or go back and ask for a new one.');
    } else if (status === 429) {
      this.error.set('Too many codes were requested. Wait a few minutes and try again.');
    } else {
      this.error.set('Something went wrong. Try again.');
    }
  }
}
