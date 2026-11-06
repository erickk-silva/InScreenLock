'use client';

import { useEffect, useState } from 'react';
import dynamic from 'next/dynamic';
import Image from 'next/image';
import { ArrowDown, ArrowRight, Ban, CircleAlert, CirclePlay, Layers3, LockKeyhole, Menu, ShieldCheck } from 'lucide-react';
import Aurora from './react-bits/Aurora';
import SplitText from './react-bits/SplitText';
import AnimatedContent from './react-bits/AnimatedContent';
import Magnet from './react-bits/Magnet';
import SpotlightCard from './react-bits/SpotlightCard';
import { release } from '@/lib/release';

const PhoneExperience = dynamic(() => import('./phone-3d/PhoneExperience'), { ssr: false, loading: () => <Image className="phone-poster" src="/app-screens/phone-poster.svg" alt="Demonstração fictícia da tela Início do InScreenLock, com 21 minutos usados e meta de 17 minutos." fill unoptimized priority /> });
const links = [['Como funciona', '#como-funciona'], ['Recursos', '#recursos'], ['Privacidade', '#privacidade'], ['FAQ', '#faq']];

function BrandMark() {
  return <svg className="brand-mark" viewBox="0 0 56 56" aria-hidden="true"><rect x="1" y="1" width="54" height="54" rx="16" fill="#07120f" stroke="#12634f" strokeWidth="2"/><circle cx="28" cy="28" r="17" fill="none" stroke="#22e3b0" strokeWidth="5"/><path d="M28 24v14" stroke="#e6eaf0" strokeWidth="4" strokeLinecap="round"/><circle cx="28" cy="17" r="2.4" fill="#e6eaf0"/><path d="M39 12l4-3" stroke="#22e3b0" strokeWidth="2" strokeLinecap="round"/></svg>;
}

function AndroidGlyph({ size = 25 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" fill="currentColor"><path d="M7.1 8.4 5.5 5.7l.9-.5L8 7.9a9 9 0 0 1 8 0l1.6-2.7.9.5-1.6 2.7a7.6 7.6 0 0 1 3.2 5.9H3.9a7.6 7.6 0 0 1 3.2-5.9ZM8 11.5a.8.8 0 1 0 0 1.6.8.8 0 0 0 0-1.6Zm8 0a.8.8 0 1 0 0 1.6.8.8 0 0 0 0-1.6ZM5 15.4h14v4.1a1.5 1.5 0 0 1-1.5 1.5H6.5A1.5 1.5 0 0 1 5 19.5v-4.1ZM2 15.4h2v4.2H2zm18 0h2v4.2h-2zM8 21.5h2v1.8H8zm6 0h2v1.8h-2z"/></svg>;
}

function SiteHeader() {
  const [open, setOpen] = useState(false);
  return <header className="site-header"><a className="brand" href="#inicio" aria-label="InScreenLock — início"><BrandMark/><span>InScreenLock</span></a>
    <nav className="desktop-nav" aria-label="Navegação principal">{links.map(([label, href]) => <a key={href} href={href}>{label}</a>)}</nav>
    <a className="header-download" href="#download"><AndroidGlyph size={22}/> Baixar app</a>
    <button className="menu-toggle" aria-label={open ? 'Fechar menu' : 'Abrir menu'} aria-expanded={open} aria-controls="mobile-navigation" onClick={() => setOpen(!open)}><Menu size={24}/></button>
    {open && <nav className="mobile-nav" id="mobile-navigation" aria-label="Navegação móvel">{links.map(([label, href]) => <a key={href} href={href} onClick={() => setOpen(false)}>{label}</a>)}<a href="#download" onClick={() => setOpen(false)}>Baixar app</a></nav>}
  </header>;
}

function Benefits() {
  return <AnimatedContent className="benefits" trigger="mount" distance={10} duration={.55} stagger={.08}>
    <span className="benefit"><ShieldCheck aria-hidden="true"/>Sem conta</span><span className="benefit-dot" aria-hidden="true">•</span>
    <span className="benefit"><Ban aria-hidden="true"/>Sem anúncios</span><span className="benefit-dot" aria-hidden="true">•</span>
    <span className="benefit"><Layers3 aria-hidden="true"/>100% local</span>
  </AnimatedContent>;
}

export default function Hero() {
  const finePointer = typeof window !== 'undefined' && window.matchMedia('(pointer: fine)').matches;
  useEffect(() => { if (new URLSearchParams(location.search).has('visualTest')) document.documentElement.classList.add('visual-test'); return () => document.documentElement.classList.remove('visual-test'); }, []);
  return <div className="landing" id="inicio">
    <div className="aurora-glow" aria-hidden="true"><Aurora colorStops={['#16c79a', '#22e3b0', '#087f69']} amplitude={0.55} blend={0.16} speed={0.22} height={0.72} scale={1.3} glow={0.14} brightness={0.7} layers={2} direction="bottom" mouseInteraction={false} intro={false} opacity={0.2} paused dpr={1} /></div>
    <SiteHeader/>
    <main className="hero" aria-labelledby="hero-title">
      <section className="hero-copy">
        <AnimatedContent trigger="mount" distance={12} duration={.5}><span className="eyebrow">SEU TEMPO, SOB SEU CONTROLE</span></AnimatedContent>
        <h1 id="hero-title" aria-label="Seu feed não tem fim. Seu tempo tem."><span className="h1-line"><SplitText text="Seu feed não tem fim." className="h1-fragment" trigger="load" splitType="words" delay={20} duration={.55} effect="rise"/></span><span className="accent h1-line"><SplitText text="Seu tempo tem." className="h1-fragment" trigger="load" splitType="words" delay={120} duration={.6} effect="rise" color="#22e3b0"/></span></h1>
        <AnimatedContent trigger="mount" distance={14} duration={.6} delay={.1}><p className="subheadline">Defina limites para suas redes, acompanhe seus hábitos e escolha a hora de parar.</p></AnimatedContent>
        <AnimatedContent className="hero-actions" trigger="mount" distance={10} duration={.55} delay={.18}>
          <Magnet disabled={!finePointer} maxOffset={5} magnetStrength={8} padding={30} wrapperClassName="magnet-wrap"><a className="button button-primary" href="#download"><AndroidGlyph size={28}/>Baixar para Android<ArrowRight size={28}/></a></Magnet>
          <Magnet disabled={!finePointer} maxOffset={4} magnetStrength={9} padding={25} wrapperClassName="magnet-wrap"><a className="button button-secondary" href="#demo"><span className="play-icon"><CirclePlay size={37}/></span>Ver demonstração</a></Magnet>
        </AnimatedContent>
        {release.status === 'preparing' && <p className="release-note" id="download">Download em preparação. O APK será publicado após verificação.</p>}
        <Benefits/>
      </section>
      <section className="phone-stage" id="demo" aria-label="Demonstração do aplicativo">
        <div className="phone-halo" aria-hidden="true"/>
        <svg className="phone-orbit" viewBox="0 0 700 740" preserveAspectRatio="none" aria-hidden="true">
          <defs><linearGradient id="orbit-gradient" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stopColor="#16eaba" stopOpacity=".52"/><stop offset=".42" stopColor="#24ffd0"/><stop offset=".76" stopColor="#04d7ad"/><stop offset="1" stopColor="#15f6c5" stopOpacity=".58"/></linearGradient></defs>
          <path className="phone-orbit-glow" d="M 21 443 C 35 288 211 134 400 126 C 569 119 697 208 683 337 C 670 461 485 592 300 616 C 130 639 6 566 21 443 Z"/>
          <path className="phone-orbit-line" d="M 21 443 C 35 288 211 134 400 126 C 569 119 697 208 683 337 C 670 461 485 592 300 616 C 130 639 6 566 21 443 Z"/>
        </svg>
        <SpotlightCard className="float-card lock-card" spotlightColor="rgba(34,227,176,.18)" intensity={.32} spotlightSize={240} softness={.55}><LockKeyhole className="lock-icon" size={36} fill="currentColor" stroke="#08231e"/><p>Você decide<br/>quando parar.</p></SpotlightCard>
        <PhoneExperience/>
        <SpotlightCard className="float-card alert-card" spotlightColor="rgba(255,107,122,.23)" intensity={.4} spotlightSize={260} softness={.5}><CircleAlert size={48}/><span>Limite<br/>alcançado</span></SpotlightCard>
      </section>
    </main>
    <footer className="hero-footer"><span>RECONQUISTE SEU TEMPO</span><ArrowDown className="footer-chevron" size={27} strokeWidth={3}/></footer>
    <div className="sr-only" id="como-funciona"/><div className="sr-only" id="recursos"/><div className="sr-only" id="privacidade"/><div className="sr-only" id="faq"/>
  </div>;
}
