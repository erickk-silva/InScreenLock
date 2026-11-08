import { describe, expect, it } from 'vitest';
import { releaseIsDownloadable, type Release } from '@/lib/release';

const available: Release = { status: 'available', version: '1.0.0', url: 'https://example.test/app.apk', sha256: 'a'.repeat(64), sizeBytes: 1200, minAndroid: '9', publishedAt: '2026-11-07' };

describe('release state', () => {
  it('does not expose an APK while preparation is incomplete', () => {
    expect(releaseIsDownloadable({ ...available, status: 'preparing' })).toBe(false);
  });

  it('requires every verification field for an available release', () => {
    expect(releaseIsDownloadable(available)).toBe(true);
    expect(releaseIsDownloadable({ ...available, sha256: null })).toBe(false);
    expect(releaseIsDownloadable({ ...available, url: '/relative.apk' })).toBe(false);
    expect(releaseIsDownloadable({ ...available, sizeBytes: 0 })).toBe(false);
  });
});
