import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NEVER, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ApiService } from '../../core/api.service';
import { NOW } from '../../core/clock';
import { FlatStatus } from '../../core/models';
import { Board } from './board';

describe('Board', () => {
  const api = { board: vi.fn() };
  let fixture: ComponentFixture<Board>;
  let page: HTMLElement;

  const flats: FlatStatus[] = [
    { flatId: 'f101', number: '101', floor: 1, paid: true },
    { flatId: 'f102', number: '102', floor: 1, paid: false },
    { flatId: 'f201', number: '201', floor: 2, paid: true },
  ];

  async function open(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [Board],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: NOW, useValue: () => new Date('2026-10-20T10:00:00Z') },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Board);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.resetTestingModule();
  });

  it('asks for the current month', async () => {
    api.board.mockReturnValue(of(flats));

    await open();

    expect(api.board).toHaveBeenCalledWith('2026-10');
  });

  it('says it is loading while it waits', async () => {
    api.board.mockReturnValue(NEVER);

    await open();

    expect(page.textContent).toContain('Loading');
  });

  it('counts how many flats have paid', async () => {
    api.board.mockReturnValue(of(flats));

    await open();

    expect(page.textContent).toContain('2 of 3 paid');
  });

  it('shows every flat with its status in words for screen readers', async () => {
    api.board.mockReturnValue(of(flats));

    await open();

    expect(page.querySelector('[aria-label="Flat 101, paid"]')).not.toBeNull();
    expect(page.querySelector('[aria-label="Flat 102, pending"]')).not.toBeNull();
  });

  it('shows the top floor first', async () => {
    api.board.mockReturnValue(of(flats));

    await open();

    const floors = Array.from(page.querySelectorAll('[data-floor]')).map((e) => e.getAttribute('data-floor'));
    expect(floors).toEqual(['2', '1']);
  });

  it('never shows amounts', async () => {
    api.board.mockReturnValue(of(flats));

    await open();

    expect(page.textContent).not.toContain('₹');
  });

  it('says so when there are no flats', async () => {
    api.board.mockReturnValue(of([]));

    await open();

    expect(page.textContent).toContain('No flats');
  });

  it('shows a clear error with a way to try again', async () => {
    api.board.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    await open();
    expect(page.textContent).toContain('could not load');

    api.board.mockReturnValue(of(flats));
    Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.includes('Try again'))?.click();
    fixture.detectChanges();

    expect(page.textContent).toContain('2 of 3 paid');
  });
});
