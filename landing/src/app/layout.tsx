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

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="pt-BR"><body>{children}<noscript><style>{'.benefits{visibility:visible!important}.split-parent{visibility:visible!important}'}</style></noscript></body></html>;
}
