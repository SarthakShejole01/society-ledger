const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

const INDIA = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit' });

/** The month a moment falls in, by the Indian calendar, as 2026-10. */
export function monthOf(date: Date): string {
  const parts = INDIA.formatToParts(date);
  const year = parts.find((p) => p.type === 'year')?.value;
  const month = parts.find((p) => p.type === 'month')?.value;
  return `${year}-${month}`;
}

/** 2026-10 as October 2026. */
export function monthLabel(month: string): string {
  const [year, number] = month.split('-').map(Number);
  return `${MONTH_NAMES[(number ?? 1) - 1]} ${year}`;
}
