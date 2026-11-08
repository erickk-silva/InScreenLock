import data from '@/content/release.json';

export type Release = {
  status: 'preparing' | 'available';
  version: string | null;
  url: string | null;
  sha256: string | null;
  sizeBytes: number | null;
  minAndroid: string;
  publishedAt: string | null;
};

export const release = data as Release;

export function releaseIsDownloadable(value: Release) {
  if (value.status !== 'available' || !value.version || !value.url || !value.publishedAt) return false;
  let isSecureUrl = false;
  try { isSecureUrl = new URL(value.url).protocol === 'https:'; } catch { isSecureUrl = false; }
  return isSecureUrl && /^[a-f\d]{64}$/i.test(value.sha256 ?? '') && Number.isSafeInteger(value.sizeBytes) && (value.sizeBytes ?? 0) > 0;
}

