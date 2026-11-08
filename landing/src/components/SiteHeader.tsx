'use client';

import { useEffect, useState } from 'react';
import { Menu } from 'lucide-react';
import { AndroidGlyph, BrandMark } from './HeroGlyphs';

const links = [['Como funciona', '#como-funciona'], ['Demonstração', '#demo'], ['Recursos', '#recursos'], ['Privacidade', '#privacidade'], ['Resultados', '#resultados'], ['FAQ', '#faq']];

/** Entrance slide on load; turns into frosted glass with a hairline border once the page scrolls past 8 px. */
export default function SiteHeader() {
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);
  return <div className={`site-header-bar${scrolled ? ' is-scrolled' : ''}`} data-scrolled={scrolled}>
    <header className="site-header"><a className="brand" href="#inicio" aria-label="InScreenLock — início"><BrandMark/><span>InScreenLock</span></a>
      <div className="header-right"><nav className="desktop-nav" aria-label="Navegação principal">{links.map(([label, href]) => <a key={label} href={href}>{label}</a>)}</nav>
      <a className="header-download" href="#download"><AndroidGlyph size={22}/> Baixar app</a></div>
      <button className="menu-toggle" aria-label={open ? 'Fechar menu' : 'Abrir menu'} aria-expanded={open} aria-controls="mobile-navigation" onClick={() => setOpen(!open)}><Menu size={24}/></button>
      <nav className="mobile-nav" id="mobile-navigation" aria-label="Navegação móvel" hidden={!open}>{links.map(([label, href]) => <a key={label} href={href} onClick={() => setOpen(false)}>{label}</a>)}<a href="#download" onClick={() => setOpen(false)}>Baixar app</a></nav>
    </header>
  </div>;
}
