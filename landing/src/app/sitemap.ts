import type { MetadataRoute } from 'next';

export const dynamic = 'force-static';
export default function sitemap(): MetadataRoute.Sitemap { return [{ url: process.env.NEXT_PUBLIC_SITE_URL ?? 'https://erickk-silva.github.io/InScreenLock/', lastModified: new Date('2026-11-07'), changeFrequency: 'monthly', priority: 1 }]; }
