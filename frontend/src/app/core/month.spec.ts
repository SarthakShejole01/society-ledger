import { monthLabel, monthOf } from './month';

describe('monthOf', () => {
  it('uses the Indian date, not UTC', () => {
    // 00:30 IST on 1 November is still 31 October in UTC
    expect(monthOf(new Date('2026-10-31T19:00:00Z'))).toBe('2026-11');
  });

  it('is still the old month in the last second of the month in India', () => {
    expect(monthOf(new Date('2026-10-31T18:29:59Z'))).toBe('2026-10');
  });
});

describe('monthLabel', () => {
  it('names the month in words', () => {
    expect(monthLabel('2026-10')).toBe('October 2026');
  });
});
