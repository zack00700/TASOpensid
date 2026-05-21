export function formatUtcDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString('en-GB', { timeZone: 'UTC' });
}
