export function requireExternalHttpUrl(value: string, origin = window.location.origin): string {
  let parsed: URL;
  try {
    parsed = new URL(value, origin);
  } catch {
    throw new Error('The application link is invalid.');
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    throw new Error('The application link uses a blocked protocol.');
  }
  return parsed.href;
}
