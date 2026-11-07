'use client';

import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react';
import { ArrowDown, ArrowRight, CircleAlert, CirclePlay, Layers3, MegaphoneOff, ShieldCheck } from 'lucide-react';
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
import AuroraBackdrop from './AuroraBackdrop';
import DownloadSection from './DownloadSection';
import { AndroidGlyph, LockGlyph } from './HeroGlyphs';
import { useMotionStage } from '@/hooks/useAfter';
import { usePointerParallax } from '@/hooks/usePointerParallax';
import { prefersReducedMotion } from '@/lib/motion-preference';
import { release } from '@/lib/release';
import { ribbonOutline, ribbonRim, type RibbonSpec } from '@/lib/ribbon';

const SUBHEADLINE = 'Defina limites para suas redes, acompanhe seus hábitos e escolha a hora de parar.';
const delayed = (ms: number) => ({ '--d': `${ms}ms` }) as CSSProperties;
const MINT_FLAT = '#2de8b6';
const GLASS_MINT = { '--spotlight-card-surface': 'rgba(10, 34, 32, .42)', '--spotlight-card-border': 'linear-gradient(145deg, rgba(150,255,230,1), rgba(34,227,176,.34) 38%, rgba(34,227,176,.12) 62%, rgba(34,227,176,.85))', '--spotlight-card-shadow': 'inset 0 1.5px 0 rgba(255,255,255,.5), inset 0 0 34px rgba(34,227,176,.16), inset 0 -22px 34px -22px rgba(34,227,176,.4), 0 38px 70px -26px rgba(34,227,176,.8), 0 0 46px -12px rgba(34,227,176,.38), 0 14px 26px -14px rgba(0,0,0,.6)' } as CSSProperties;
const GLASS_CORAL = { '--spotlight-card-surface': 'rgba(46, 20, 28, .44)', '--spotlight-card-border': 'linear-gradient(145deg, rgba(255,170,178,1), rgba(255,107,122,.36) 38%, rgba(255,107,122,.14) 62%, rgba(255,107,122,.9))', '--spotlight-card-shadow': 'inset 0 1.5px 0 rgba(255,255,255,.42), inset 0 0 34px rgba(255,107,122,.18), inset 0 -22px 34px -22px rgba(255,107,122,.45), 0 38px 70px -26px rgba(255,84,97,.85), 0 0 46px -12px rgba(255,84,97,.42), 0 14px 26px -14px rgba(0,0,0,.6)' } as CSSProperties;
// Aurora ribbon behind the device, like the approved hero: two OPEN crescents of one tilted ellipse (wide and bright at the lower left, a softer sweep up the right side),
// each swelling and tapering to a point. Their ends hide behind the phone and the top stays dark, so it never reads as a closed halo.
const RIBBON_LOWER: RibbonSpec = { cx: 380, cy: 410, rx: 365, ry: 240, rotate: -22, from: 230, to: 94, width: 154, peak: 0.38 };
const RIBBON_RIGHT: RibbonSpec = { cx: 380, cy: 410, rx: 392, ry: 244, rotate: -22, from: 48, to: -68, width: 126, peak: 0.62 };
const RIBBONS = [RIBBON_LOWER, RIBBON_RIGHT].map((spec) => ({ outline: ribbonOutline(spec), rim: ribbonRim(spec) }));
const LINE_FROM = { opacity: 1, y: '8px', filter: 'blur(0px)' };
const LINE_TO = { opacity: 1, y: 0, filter: 'blur(0px)' };

/**
 * The server HTML carries the plain copy. React Bits starts from visible text and only adds a short positional highlight after hydration.
 */
function HeadlineLine({ text, startMs, stagger, duration }: { text: string; startMs: number; stagger: number; duration: number }) {
  const stage = useMotionStage(startMs);
  return stage === 'motion'
    ? <SplitText tag="span" text={text} className="h1-fragment" trigger="load" splitType="chars" delay={stagger} duration={duration} textAlign="left" from={LINE_FROM} to={LINE_TO} />
    : <span className="h1-fragment motion-hide">{text}</span>;
}

function Subheadline() {
  const stage = useMotionStage(560);
  return stage === 'motion'
    ? <BlurText text={SUBHEADLINE} className="subheadline" trigger="load" animateBy="words" direction="bottom" delay={34} stepDuration={.5} blur={0} distance={8} animationFrom={{ opacity: 1, y: 8, filter: 'blur(0px)' }} animationTo={[{ opacity: 1, y: -1, filter: 'blur(0px)' }, { opacity: 1, y: 0, filter: 'blur(0px)' }]} />
    : <p className="subheadline motion-hide">{SUBHEADLINE}</p>;
}

export default function Hero() {
  const landing = useRef<HTMLDivElement>(null);
  const [limitReached, setLimitReached] = useState(false);
  const reachLimit = useCallback(() => setLimitReached(true), []);
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
    <AuroraBackdrop/>
    <SiteHeader/>
    <main className="hero" aria-labelledby="hero-title">
      <section className="hero-copy">
        <span className="eyebrow enter" style={delayed(150)}><ShinyText text="SEU TEMPO, SOB SEU CONTROLE" color="#22e3b0" shineColor="#f2fffb" speed={2.2} delay={9} angle={105} shineWidth={55} softness={.9} glow={.3} /></span>
        <h1 id="hero-title" aria-label="Seu feed não tem fim. Seu tempo tem.">
          <span className="h1-line" aria-hidden="true"><HeadlineLine text="Seu feed não tem fim." startMs={0} stagger={22} duration={.8} /></span>
          <span className="h1-line h1-accent" aria-hidden="true"><HeadlineLine text="Seu tempo tem." startMs={270} stagger={26} duration={.85} /></span>
        </h1>
        <Subheadline/>
        <AnimatedContent className="hero-actions" trigger="mount" initialOpacity={1} animateOpacity={false} distance={8} duration={.85} delay={.18}>
          <ClickSpark sparkColor={['#22e3b0', '#7ffbe0', '#ffffff']} sparkCount={12} sparkRadius={34} sparkSize={9} sparkWidth={2.2} duration={.65} easing="ease-out" shape="streak" glow={.6} style={{ display: 'inline-flex', width: 'auto', height: 'auto' }}>
            <Magnet disabled={!finePointer} maxOffset={5} magnetStrength={8} padding={30} wrapperClassName="magnet-wrap">
              <StarBorder as="a" href="#download" className="button button-primary" radius={26} thickness={1.5} duration={5} trailLength={.34} glow={.75} hover="lap" backgroundColor={MINT_FLAT} textColor="#03231c" color="#ffffff" trailColor="#b8fff0" borderColor="rgba(255,255,255,.3)"><AndroidGlyph size={30} color="#03231c"/>Baixar para Android<ArrowRight size={28}/></StarBorder>
            </Magnet>
          </ClickSpark>
          <Magnet disabled={!finePointer} maxOffset={4} magnetStrength={9} padding={25} wrapperClassName="magnet-wrap"><a className="button button-secondary" href="#demo"><span className="play-icon"><CirclePlay size={37}/></span>Ver demonstração</a></Magnet>
        </AnimatedContent>
        {release.status === 'preparing' && <p className="release-note enter" style={delayed(1050)}>Download em preparação. O APK será publicado após verificação.</p>}
        <AnimatedContent className="benefits" trigger="mount" distance={10} duration={.6} delay={1.1} stagger={.07}>
          <span className="benefit"><ShieldCheck aria-hidden="true"/>Sem conta</span><span className="benefit-dot" aria-hidden="true">•</span>
          <span className="benefit"><MegaphoneOff aria-hidden="true"/>Sem anúncios</span><span className="benefit-dot" aria-hidden="true">•</span>
          <span className="benefit"><Layers3 aria-hidden="true"/>100% local</span>
        </AnimatedContent>
      </section>
      <section className="phone-stage" id="demo-phone" aria-label="Demonstração do aplicativo">
        <div className="phone-layer phone-layer-far" data-parallax="4" aria-hidden="true"><div className="phone-halo"/><div className="phone-floor"/></div>
        <div className="phone-layer phone-layer-mid" data-parallax="7" aria-hidden="true">
          <svg className="phone-orbit" viewBox="0 0 740 742" preserveAspectRatio="xMidYMid meet">
            <defs>
              <linearGradient id="ribbon-fill-lower" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stopColor="#10f0be" stopOpacity=".05"/><stop offset=".3" stopColor="#14f5c6" stopOpacity=".6"/><stop offset=".62" stopColor="#09b996" stopOpacity=".46"/><stop offset="1" stopColor="#08937a" stopOpacity=".05"/></linearGradient>
              <linearGradient id="ribbon-fill-right" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stopColor="#10f0be" stopOpacity=".05"/><stop offset=".4" stopColor="#13f2c2" stopOpacity=".95"/><stop offset=".78" stopColor="#09b996" stopOpacity=".6"/><stop offset="1" stopColor="#08937a" stopOpacity=".04"/></linearGradient>
              <linearGradient id="ribbon-rim" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#7dffe3" stopOpacity="0"/><stop offset=".22" stopColor="#7dffe3" stopOpacity=".95"/><stop offset=".78" stopColor="#19ffd0" stopOpacity=".85"/><stop offset="1" stopColor="#19ffd0" stopOpacity="0"/></linearGradient>
              <linearGradient id="ribbon-rim-right" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stopColor="#7dffe3" stopOpacity="0"/><stop offset=".25" stopColor="#7dffe3" stopOpacity=".9"/><stop offset=".8" stopColor="#19ffd0" stopOpacity=".75"/><stop offset="1" stopColor="#19ffd0" stopOpacity="0"/></linearGradient>
              <filter id="ribbon-soft" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="13"/></filter>
              <filter id="ribbon-haze" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="36"/></filter>
            </defs>
            {RIBBONS.map((ribbon, index) => <g key={index} className={index === 0 ? 'ribbon ribbon-lower' : 'ribbon ribbon-right'}>
              <path className="ribbon-haze" fill={`url(#ribbon-fill-${index === 0 ? 'lower' : 'right'})`} filter="url(#ribbon-haze)" d={ribbon.outline}/>
              <path className="ribbon-body" fill={`url(#ribbon-fill-${index === 0 ? 'lower' : 'right'})`} filter="url(#ribbon-soft)" d={ribbon.outline}/>
              <path className="phone-orbit-line" pathLength="1" stroke={`url(#${index === 0 ? 'ribbon-rim' : 'ribbon-rim-right'})`} d={ribbon.rim}/>
            </g>)}
          </svg>
        </div>
        <div className="float-pos lock-pos" data-parallax="10"><div className="float-bob"><div className="float-tilt float-tilt-lock"><SpotlightCard className="float-card lock-card" style={GLASS_MINT} spotlightColor="rgba(34,227,176,.3)" intensity={.4} spotlightSize={240} softness={.55}><LockGlyph/><p>Você decide<br/>quando parar.</p></SpotlightCard></div></div></div>
        <PhoneExperience onLimitReached={reachLimit}/>
        <div className="float-pos alert-pos" data-parallax="8"><div className="float-bob float-bob-alt"><div className="float-tilt float-tilt-alert"><SpotlightCard className={`float-card alert-card${limitReached ? ' is-in' : ''}`} style={GLASS_CORAL} spotlightColor="rgba(255,107,122,.32)" intensity={.45} spotlightSize={260} softness={.5}><span className="alert-icon"><CircleAlert size={48}/></span><span>Limite<br/>alcançado</span></SpotlightCard></div></div></div>
      </section>
    </main>
    <footer className="hero-footer"><span className="footer-caption">RECONQUISTE SEU TEMPO</span><ArrowDown className="footer-chevron" size={27} strokeWidth={3}/></footer>
    <DownloadSection/>
  </div>;
}
