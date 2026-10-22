import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { NOW } from '../../core/clock';
import { FlatStatus } from '../../core/models';
import { PayForAnother } from './pay-for-another';

describe('PayForAnother', () => {
  const api = { board: vi.fn(), startPayment: vi.fn() };
  const session = signal({ token: 't', role: 'OWNER' as const, flatId: 'f304', name: 'Rajesh K', societyCode: 'sunrise' });
  let fixture: ComponentFixture<PayForAnother>;
  let page: HTMLElement;

  const board: FlatStatus[] = [
    { flatId: 'f304', number: '304', floor: 3, paid: false }, // the owner's own flat
    { flatId: 'f305', number: '305', floor: 3, paid: false },
    { flatId: 'f306', number: '306', floor: 3, paid: true },
    { flatId: 'f401', number: '401', floor: 4, paid: false },
  ];

  async function open(flat: string | null = null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PayForAnother],
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: api },
        { provide: AuthService, useValue: { session } },
        { provide: NOW, useValue: () => new Date('2026-10-20T10:00:00Z') },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(flat ? { flat } : {}) } } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PayForAnother);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  function press(label: string): void {
    const button = Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.includes(label));
    if (!button) {
      throw new Error(`No button "${label}"`);
    }
    button.click();
    fixture.detectChanges();
  }

  function choose(value: string): void {
    const select = page.querySelector<HTMLSelectElement>('#flat');
    if (!select) {
      throw new Error('No flat field');
    }
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.resetTestingModule();
  });

  it('asks for the board of the current month', async () => {
    api.board.mockReturnValue(of(board));

    await open();

    expect(api.board).toHaveBeenCalledWith('2026-10');
  });

  it('says it is loading while it waits', async () => {
    api.board.mockReturnValue(NEVER);

    await open();

    expect(page.textContent).toContain('Loading');
  });

  it('offers only the other flats that have not paid', async () => {
    api.board.mockReturnValue(of(board));

    await open();

    const options = Array.from(page.querySelectorAll('#flat option')).map((o) => o.textContent?.trim());
    expect(options).toEqual(['Flat 305', 'Flat 401']);
  });

  it('starts on the flat chosen elsewhere in the app', async () => {
    api.board.mockReturnValue(of(board));

    await open('f401');

    expect(page.querySelector<HTMLSelectElement>('#flat')?.value).toBe('f401');
  });

  it('says so when every other flat has paid', async () => {
    api.board.mockReturnValue(of(board.map((f) => ({ ...f, paid: f.flatId === 'f304' ? false : true }))));

    await open();

    expect(page.textContent).toContain('Every other flat has paid');
    expect(page.querySelector('#flat')).toBeNull();
  });

  it('shows a clear error with a way to try again', async () => {
    api.board.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    await open();
    expect(page.textContent).toContain('could not load');

    api.board.mockReturnValue(of(board));
    press('Try again');

    expect(page.querySelector('#flat')).not.toBeNull();
  });

  it('explains that the payer is recorded', async () => {
    api.board.mockReturnValue(of(board));

    await open();

    expect(page.textContent).toContain('Your name');
  });

  it('starts a payment for the chosen flat and never sends an amount', async () => {
    api.board.mockReturnValue(of(board));
    api.startPayment.mockReturnValue(of({ paymentId: 'p1', flatId: 'f401', month: '2026-10', totalPaise: 130000, lateFeePaise: 10000 }));
    await open();
    choose('f401');

    press('Start payment');

    expect(api.startPayment).toHaveBeenCalledWith('f401', '2026-10');
    expect(page.textContent).toContain('Payment started');
    expect(page.textContent).toContain('Flat 401');
    expect(page.textContent).toContain('₹1,300');
    expect(page.textContent).toContain('late fee');
  });

  it('says plainly that the online payment page is not connected yet', async () => {
    api.board.mockReturnValue(of(board));
    api.startPayment.mockReturnValue(of({ paymentId: 'p1', flatId: 'f305', month: '2026-10', totalPaise: 120000, lateFeePaise: 0 }));
    await open();

    press('Start payment');

    expect(page.textContent).toContain('not connected yet');
  });

  it('explains when the flat turns out to be paid already', async () => {
    api.board.mockReturnValue(of(board));
    api.startPayment.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    await open();

    press('Start payment');

    expect(page.textContent).toContain('already paid');
  });

  it('shows a plain message when starting the payment fails', async () => {
    api.board.mockReturnValue(of(board));
    api.startPayment.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    await open();

    press('Start payment');

    expect(page.textContent).toContain('could not start');
  });
});
