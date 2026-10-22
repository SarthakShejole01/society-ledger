import { formatRupees } from './money';

describe('formatRupees', () => {
  it('shows whole rupees without decimals', () => {
    expect(formatRupees(160000)).toBe('₹1,600');
  });

  it('shows paise when there are some', () => {
    expect(formatRupees(150050)).toBe('₹1,500.50');
  });

  it('shows zero', () => {
    expect(formatRupees(0)).toBe('₹0');
  });

  it('groups digits the Indian way', () => {
    expect(formatRupees(12345600)).toBe('₹1,23,456');
  });
});
