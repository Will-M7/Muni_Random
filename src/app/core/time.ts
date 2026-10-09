export function validTime24(value: string | null | undefined): boolean {
  return typeof value === 'string' && /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value.slice(0, 5));
}

export function formatTime12(value: string | null | undefined): string {
  if (!validTime24(value)) return '';
  const [hours, minutes] = value!.slice(0, 5).split(':').map(Number);
  const display = hours % 12 || 12;
  return `${display}:${String(minutes).padStart(2, '0')} ${hours < 12 ? 'a. m.' : 'p. m.'}`;
}

export function formatTimeDual(value: string | null | undefined): string {
  return validTime24(value) ? `${value!.slice(0, 5)} (${formatTime12(value)})` : '';
}
