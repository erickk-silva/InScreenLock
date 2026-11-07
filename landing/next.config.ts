import type { NextConfig } from 'next';

const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? '';
const releaseFixture = process.env.NEXT_PUBLIC_ENABLE_RELEASE_FIXTURE === '1' ? '1' : '0';
const nextConfig: NextConfig = { basePath, assetPrefix: basePath || undefined, output: 'export', images: { unoptimized: true }, devIndicators: false, env: { NEXT_PUBLIC_ENABLE_RELEASE_FIXTURE: releaseFixture } };
export default nextConfig;
