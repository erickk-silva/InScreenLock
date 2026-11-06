'use client';

import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { ArrowDown, ArrowRight, Ban, CircleAlert, CirclePlay, Layers3, ShieldCheck } from 'lucide-react';
import Aurora from './react-bits/Aurora';
import SplitText from './react-bits/SplitText';
import BlurText from './react-bits/BlurText';
import ShinyText from './react-bits/ShinyText';
import StarBorder from './react-bits/StarBorder';
import ClickSpark from './react-bits/ClickSpark';
import AnimatedContent from './react-bits/AnimatedContent';
import Magnet from './react-bits/Magnet';
import SpotlightCard from './react-bits/SpotlightCard';
import PhoneExperience from './phone-3d/PhoneExperience';
import SiteHeader from './SiteHeader';
import { AndroidGlyph, LockGlyph } from './HeroGlyphs';
import { useAfter } from '@/hooks/useAfter';
import { usePointerParallax } from '@/hooks/usePointerParallax';
import { prefersReducedMotion } from '@/lib/motion-preference';
import { release } from '@/lib/release';

const SUBHEADLINE = 'Defina limites para suas redes, acompanhe seus hábitos e escolha a hora de parar.';
const delayed = (ms: number) => ({ '--d': `${ms}ms` }) as CSSProperties;
const MINT_GRADIENT = 'linear-gradient(135deg, #22e3b0 0%, #16c79a 100%)';
const GLASS_MINT = { '--spotlight-card-surface': 'rgba(12, 28, 27, .3)', '--spotlight-card-border': 'linear-gradient(140deg, rgba(34,227,176,.85), rgba(34,227,176,.12) 55%, rgba(34,227,176,.38))', '--spotlight-card-shadow': 'inset 0 1px 0 rgba(255,255,255,.26), inset 0 -18px 30px -24px rgba(34,227,176,.28), 0 26px 60px -22px rgba(34,227,176,.42), 0 10px 24px -12px rgba(0,0,0,.55)' } as CSSProperties;
const GLASS_CORAL = { '--spotlight-card-surface': 'rgba(40, 20, 26, .32)', '--spotlight-card-border': 'linear-gradient(140deg, rgba(255,107,122,.9), rgba(255,107,122,.14) 55%, rgba(255,107,122,.42))', '--spotlight-card-shadow': 'inset 0 1px 0 rgba(255,255,255,.22), inset 0 -18px 30px -24px rgba(255,107,122,.3), 0 26px 60px -22px rgba(255,84,97,.5), 0 10px 24px -12px rgba(0,0,0,.55)' } as CSSProperties;
const LINE_FROM = { opacity: 0, y: '.5em', filter: 'blur(12px)' };
const LINE_TO = { opacity: 1, y: 0, filter: 'blur(0px)' };

function AccentLine() {
  const ready = useAfter(270);
  const text = 'Seu tempo tem.';
  return ready
    ? <SplitText tag="span" text={text} className="h1-fragment" trigger="load" splitType="chars" delay={26} duration={.85} textAlign="left" from={LINE_FROM} to={LINE_TO} />
    : <span className="h1-fragment" style={{ visibility: 'hidden' }}>{text}</span>;
}

function Subheadline() {
  const ready = useAfter(560);
  return ready
    ? <BlurText text={SUBHEADLINE} className="subheadline" trigger="load" animateBy="words" direction="bottom" delay={34} stepDuration={.5} blur={9} distance={14} />
    : <p className="subheadline" style={{ visibility: 'hidden' }} aria-hidden="true">{SUBHEADLINE}</p>;
}

export default function Hero() {
  const landing = useRef<HTMLDivElement>(null);
  const [limitReached, setLimitReached] = useState(false);
  const [finePointer, setFinePointer] = useState(false);
  useEffect(() => {
    if (new URLSearchParams(location.search).has('visualTest')) document.documentElement.classList.add('visual-test');
    const reduced = prefersReducedMotion();
    // the "Limite alcançado" card follows the ring crossing the goal; this is a safety net so it can never stay hidden
    const safety = window.setTimeout(() => setLimitReached(true), reduced ? 0 : 5000);
    const pointer = window.matchMedia('(pointer: fine)');
    const sync = () => setFinePointer(pointer.matches);
    sync(); pointer.addEventListener('change', sync);
    return () => { document.documentElement.classList.remove('visual-test'); window.clearTimeout(safety); pointer.removeEventListener('change', sync); };
  }, []);
  usePointerParallax(landing);

  return <div className="landing" id="inicio" ref={landing}>
    <div className="aurora-glow" aria-hidden="true"><Aurora colorStops={['#16c79a', '#22e3b0', '#087f69']} amplitude={0.55} blend={0.16} speed={0.22} height={0.72} scale={1.3} glow={0.14} brightness={0.7} layers={2} direction="bottom" mouseInteraction={false} intro={false} opacity={0.2} paused dpr={1} /></div>
    <SiteHeader/>
    <main className="hero" aria-labelledby="hero-title">
      <section className="hero-copy">
        <span className="eyebrow enter" style={delayed(150)}><ShinyText text="SEU TEMPO, SOB SEU CONTROLE" color="#22e3b0" shineColor="#f2fffb" speed={2.2} delay={9} angle={105} shineWidth={55} softness={.9} glow={.3} /></span>
        <h1 id="hero-title" aria-label="Seu feed não tem fim. Seu tempo tem.">
          <span className="h1-line" aria-hidden="true"><SplitText tag="span" text="Seu feed não tem fim." className="h1-fragment" trigger="load" splitType="chars" delay={22} duration={.8} textAlign="left" from={LINE_FROM} to={LINE_TO} /></span>
          <span className="h1-line h1-accent" aria-hidden="true"><AccentLine/></span>
        </h1>
        <Subheadline/>
        <div className="hero-actions enter" style={delayed(950)}>
          <ClickSpark sparkColor={['#22e3b0', '#7ffbe0', '#ffffff']} sparkCount={12} sparkRadius={34} sparkSize={9} sparkWidth={2.2} duration={.65} easing="ease-out" shape="streak" glow={.6} style={{ display: 'inline-flex', width: 'auto', height: 'auto' }}>
            <Magnet disabled={!finePointer} maxOffset={5} magnetStrength={8} padding={30} wrapperClassName="magnet-wrap">
              <StarBorder as="a" href="#download" className="button button-primary" radius={26} thickness={1.5} duration={5} trailLength={.34} glow={.75} hover="lap" backgroundColor={MINT_GRADIENT} textColor="#04211b" color="#ffffff" trailColor="#b8fff0" borderColor="rgba(255,255,255,.34)"><AndroidGlyph size={28}/>Baixar para Android<ArrowRight size={28}/></StarBorder>
            </Magnet>
          </ClickSpark>
          <Magnet disabled={!finePointer} maxOffset={4} magnetStrength={9} padding={25} wrapperClassName="magnet-wrap"><a className="button button-secondary" href="#demo"><span className="play-icon"><CirclePlay size={37}/></span>Ver demonstração</a></Magnet>
        </div>
        {release.status === 'preparing' && <p className="release-note enter" style={delayed(1050)} id="download">Download em preparação. O APK será publicado após verificação.</p>}
        <AnimatedContent className="benefits" trigger="mount" distance={10} duration={.6} delay={1.1} stagger={.07}>
          <span className="benefit"><ShieldCheck aria-hidden="true"/>Sem conta</span><span className="benefit-dot" aria-hidden="true">•</span>
          <span className="benefit"><Ban aria-hidden="true"/>Sem anúncios</span><span className="benefit-dot" aria-hidden="true">•</span>
          <span className="benefit"><Layers3 aria-hidden="true"/>100% local</span>
        </AnimatedContent>
      </section>
      <section className="phone-stage" id="demo" aria-label="Demonstração do aplicativo">
        <div className="phone-layer phone-layer-far" data-parallax="4" aria-hidden="true"><div className="phone-halo"/><div className="phone-floor"/></div>
        <div className="phone-layer phone-layer-mid" data-parallax="7" aria-hidden="true">
          <svg className="phone-orbit" viewBox="0 0 700 740" preserveAspectRatio="none">
            <defs><linearGradient id="orbit-gradient" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stopColor="#16eaba" stopOpacity=".52"/><stop offset=".42" stopColor="#24ffd0"/><stop offset=".76" stopColor="#04d7ad"/><stop offset="1" stopColor="#15f6c5" stopOpacity=".58"/></linearGradient></defs>
            <path className="phone-orbit-glow" pathLength="1" d="M 21 443 C 35 288 211 134 400 126 C 569 119 697 208 683 337 C 670 461 485 592 300 616 C 130 639 6 566 21 443 Z"/>
            <path className="phone-orbit-line" pathLength="1" d="M 21 443 C 35 288 211 134 400 126 C 569 119 697 208 683 337 C 670 461 485 592 300 616 C 130 639 6 566 21 443 Z"/>
          </svg>
        </div>
        <div className="float-pos lock-pos" data-parallax="10"><div className="float-bob"><SpotlightCard className="float-card lock-card" style={GLASS_MINT} spotlightColor="rgba(34,227,176,.3)" intensity={.4} spotlightSize={240} softness={.55}><LockGlyph/><p>Você decide<br/>quando parar.</p></SpotlightCard></div></div>
        <PhoneExperience onLimitReached={() => setLimitReached(true)}/>
        <div className="float-pos alert-pos" data-parallax="8"><div className="float-bob float-bob-alt"><SpotlightCard className={`float-card alert-card${limitReached ? ' is-in' : ''}`} style={GLASS_CORAL} spotlightColor="rgba(255,107,122,.32)" intensity={.45} spotlightSize={260} softness={.5}><span className="alert-icon"><CircleAlert size={48}/></span><span>Limite<br/>alcançado</span></SpotlightCard></div></div>
      </section>
    </main>
    <footer className="hero-footer"><span className="footer-caption">RECONQUISTE SEU TEMPO</span><ArrowDown className="footer-chevron" size={27} strokeWidth={3}/></footer>
    <div className="sr-only" id="como-funciona"/><div className="sr-only" id="recursos"/><div className="sr-only" id="privacidade"/><div className="sr-only" id="faq"/>
  </div>;
}
