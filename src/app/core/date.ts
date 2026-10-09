export function todayPeru(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return `${values['year']}-${values['month']}-${values['day']}`;
}

// El backend guarda estos instantes como LocalDateTime de America/Lima (sin offset).
export function formatLimaLocalDateTime(value:string|null|undefined):string {
  const match=value?.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  return match?`${match[3]}/${match[2]}/${match[1]} ${match[4]}:${match[5]}`:'—';
}
