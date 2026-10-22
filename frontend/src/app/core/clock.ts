import { InjectionToken } from '@angular/core';

/** The current moment. Replaced in tests so nothing depends on the real clock. */
export const NOW = new InjectionToken<() => Date>('NOW', {
  providedIn: 'root',
  factory: () => () => new Date(),
});
