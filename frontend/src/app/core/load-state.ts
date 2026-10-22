/** Every screen that loads data is in exactly one of these states. */
export type LoadState<T> = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: T };
