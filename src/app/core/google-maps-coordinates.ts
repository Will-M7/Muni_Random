export function extractGoogleMapsCoordinates(input: string): [number, number] | null {
  let url: URL;
  try { url = new URL(input.trim()); } catch { return null; }
  const host = url.hostname.toLowerCase();
  if (url.protocol !== 'https:' || !(host === 'maps.app.goo.gl' || host === 'goo.gl' || host === 'google.com' || host.endsWith('.google.com') || host === 'google.com.pe' || host.endsWith('.google.com.pe'))) return null;
  let value: string;
  try { value = decodeURIComponent(url.href); } catch { return null; }
  const pair = [
    value.match(/!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)/),
    value.match(/[?&](?:q|query)=(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)/)
  ].find(Boolean);
  if (!pair) return null;
  const lat = Number(pair[1]), lon = Number(pair[2]);
  return Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180 ? [lat, lon] : null;
}
