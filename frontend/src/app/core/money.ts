const INDIAN_GROUPING = new Intl.NumberFormat('en-IN');

/** Shows paise as rupees, for example 160000 as ₹1,600. Paise appear only when there are some. */
export function formatRupees(paise: number): string {
  const rupees = Math.trunc(paise / 100);
  const rest = Math.abs(paise % 100);
  const base = '₹' + INDIAN_GROUPING.format(rupees);
  return rest === 0 ? base : `${base}.${String(rest).padStart(2, '0')}`;
}
