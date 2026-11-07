import type { Metadata } from 'next';
import '@fontsource-variable/inter/index.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';
import './globals.css';

export const metadata: Metadata = {
  title: 'InScreenLock — Controle o tempo nas redes sociais',
  description: 'Defina limites para Instagram e TikTok, acompanhe seus hábitos e escolha a hora de parar.',
  robots: { index: true, follow: true },
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? 'https://erickk-silva.github.io/InScreenLock/'),
  alternates: { canonical: '/' },
  openGraph: { title: 'InScreenLock — Controle o tempo nas redes sociais', description: 'Defina limites para suas redes, acompanhe seus hábitos e escolha a hora de parar.', type: 'website', locale: 'pt_BR', images: [{ url: '/og-image.png', width: 1200, height: 630, alt: 'InScreenLock — Seu tempo, sob seu controle' }] },
};

/**
 * Runs before first paint. Entrance effects may hide text only when this script ran (`js`) AND motion is allowed (`js-motion`),
 * so without JavaScript, or with reduced motion, every piece of copy is simply visible in the server-rendered HTML.
 */
const MOTION_GATE = "(function(){var d=document.documentElement;d.classList.add('js');try{if(!matchMedia('(prefers-reduced-motion: reduce)').matches&&location.search.indexOf('visualTest')<0)d.classList.add('js-motion')}catch(e){}})();";

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const softwareApplication = { '@context': 'https://schema.org', '@type': 'SoftwareApplication', name: 'InScreenLock', operatingSystem: 'Android 9+', applicationCategory: 'UtilitiesApplication', offers: { '@type': 'Offer', price: '0', priceCurrency: 'BRL' } };
  return <html lang="pt-BR" suppressHydrationWarning><head><script dangerouslySetInnerHTML={{ __html: MOTION_GATE }} /><script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(softwareApplication) }} /></head><body>{children}</body></html>;
}
