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
  openGraph: { title: 'InScreenLock — Controle o tempo nas redes sociais', description: 'Defina limites para suas redes, acompanhe seus hábitos e escolha a hora de parar.', type: 'website', locale: 'pt_BR' },
};

/**
 * Runs before first paint. Entrance effects may hide text only when this script ran (`js`) AND motion is allowed (`js-motion`),
 * so without JavaScript, or with reduced motion, every piece of copy is simply visible in the server-rendered HTML.
 */
const MOTION_GATE = "(function(){var d=document.documentElement;d.classList.add('js');try{if(!matchMedia('(prefers-reduced-motion: reduce)').matches&&location.search.indexOf('visualTest')<0)d.classList.add('js-motion')}catch(e){}})();";

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="pt-BR" suppressHydrationWarning><head><script dangerouslySetInnerHTML={{ __html: MOTION_GATE }} /></head><body>{children}</body></html>;
}
