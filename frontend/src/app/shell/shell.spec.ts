import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from '../core/auth.service';
import { Role } from '../core/models';
import { Shell } from './shell';

describe('Shell', () => {
  const logout = vi.fn();
  let fixture: ComponentFixture<Shell>;
  let page: HTMLElement;

  async function openAs(role: Role, name: string): Promise<void> {
    const session = signal({ token: 't', role, flatId: null, name, societyCode: 'sunrise' });
    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [provideRouter([]), { provide: AuthService, useValue: { session, role: signal(role), logout } }],
    }).compileComponents();
    fixture = TestBed.createComponent(Shell);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  function links(): string[] {
    return Array.from(page.querySelectorAll('nav a')).map((a) => a.textContent?.trim() ?? '');
  }

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.resetTestingModule();
  });

  it('gives an owner Home and Flats', async () => {
    await openAs('OWNER', 'Rajesh K');

    expect(links()).toEqual(['Home', 'Flats']);
  });

  it('gives the treasurer only the screens that exist so far', async () => {
    await openAs('TREASURER', 'Meena T');

    expect(links()).toEqual(['Flats']);
  });

  it('gives the committee only the screens that exist so far', async () => {
    await openAs('COMMITTEE', 'Anil C');

    expect(links()).toEqual(['Flats']);
  });

  it('shows who is logged in', async () => {
    await openAs('OWNER', 'Rajesh K');

    expect(page.textContent).toContain('Rajesh K');
  });

  it('logs out and goes to the login page', async () => {
    await openAs('OWNER', 'Rajesh K');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.includes('Log out'))?.click();

    expect(logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
