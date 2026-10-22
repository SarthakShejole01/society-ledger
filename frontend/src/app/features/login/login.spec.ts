import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { Login } from './login';

describe('Login', () => {
  const auth = { requestOtp: vi.fn(), verify: vi.fn(), rememberedSocietyCode: vi.fn() };
  const navigate = vi.fn();
  let fixture: ComponentFixture<Login>;
  let page: HTMLElement;

  function type(selector: string, value: string): void {
    const input = page.querySelector<HTMLInputElement>(selector);
    if (!input) {
      throw new Error(`No field ${selector}`);
    }
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function press(label: string): void {
    const button = Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
    if (!button) {
      throw new Error(`No button "${label}"`);
    }
    button.click();
    fixture.detectChanges();
  }

  function askForCode(): void {
    auth.requestOtp.mockReturnValue(of(undefined));
    type('#mobile', '9876543210');
    press('Send code');
  }

  beforeEach(async () => {
    vi.resetAllMocks();
    auth.rememberedSocietyCode.mockReturnValue('sunrise');
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: { navigate } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Login);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  it('starts with the society code the person used last time', () => {
    expect(page.querySelector<HTMLInputElement>('#societyCode')?.value).toBe('sunrise');
  });

  it('does not ask for a code until there is a mobile number', () => {
    press('Send code');

    expect(auth.requestOtp).not.toHaveBeenCalled();
    expect(page.textContent).toContain('Enter the mobile number');
  });

  it('asks for a code and then shows the field for it', () => {
    askForCode();

    expect(auth.requestOtp).toHaveBeenCalledWith('sunrise', '9876543210');
    expect(page.querySelector('#code')).not.toBeNull();
    expect(page.textContent).toContain('If this number is registered');
  });

  it('goes into the app after the right code', () => {
    askForCode();
    auth.verify.mockReturnValue(of({ token: 'tok' }));
    type('#code', '482917');

    press('Log in');

    expect(auth.verify).toHaveBeenCalledWith('sunrise', '9876543210', '482917');
    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  it('says so clearly when the code is wrong', () => {
    askForCode();
    auth.verify.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    type('#code', '000000');

    press('Log in');

    expect(page.textContent).toContain('That code is not valid');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('tells the person to wait when too many codes were requested', () => {
    auth.requestOtp.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    type('#mobile', '9876543210');

    press('Send code');

    expect(page.textContent).toContain('Too many codes');
  });

  it('shows a plain message for any other failure', () => {
    auth.requestOtp.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    type('#mobile', '9876543210');

    press('Send code');

    expect(page.textContent).toContain('Something went wrong');
  });

  it('lets the person go back and change the number', () => {
    askForCode();

    press('Use a different number');

    expect(page.querySelector('#code')).toBeNull();
    expect(page.querySelector('#mobile')).not.toBeNull();
  });
});
