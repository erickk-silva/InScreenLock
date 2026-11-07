'use client';

import { useEffect, useMemo, useState } from 'react';
import { Activity, AlertTriangle, ArrowDown, ArrowRight, Check, ChevronDown, Copy, Eye, Fingerprint, History, LockKeyhole, Minus, Plus, ShieldCheck, Smartphone, SlidersHorizontal, Sparkles, Timer, WifiOff } from 'lucide-react';
import AnimatedContent from './react-bits/AnimatedContent';
import SpotlightCard from './react-bits/SpotlightCard';
import MagicBento, { type MagicBentoCard } from './react-bits/MagicBento';
import { calculateLimitDemo, calculateUsageProjection } from '@/lib/usage-calculator';
import { release, releaseIsDownloadable } from '@/lib/release';

const featureCards: MagicBentoCard[] = [
  { icon: Timer, title: 'Uma meta combinada', body: 'Defina um limite diário compartilhado entre Instagram e TikTok. O tempo é contado com o app em primeiro plano e a tela ativa.', size: 'wide', label: '01' },
  { icon: AlertTriangle, title: 'Aviso ou bloqueio', body: 'Escolha um aviso ao atingir a meta ou o bloqueio opcional, que depende de PIN local ou credencial segura do aparelho.', size: 'tall', label: '02' },
  { icon: Activity, title: 'Hoje, por aplicativo', body: 'Veja o tempo de uso de hoje por app neste aparelho. A disponibilidade depende dos dados e permissões do Android.', size: 'normal', label: '03' },
  { icon: History, title: 'Até os últimos 7 dias', body: 'Consulte análises calculadas sob demanda do histórico disponibilizado pelo Android. Dias sem dados aparecem como “sem dados”.', size: 'normal', label: '04' },
  { icon: ShieldCheck, title: 'Seus dados no aparelho', body: 'Sem conta, internet ou anúncios. Preferências ficam salvas localmente; dados de uso vêm do Android.', size: 'wide', label: '05' },
  { icon: SlidersHorizontal, title: 'Você mantém o controle', body: 'Permissões e Acessibilidade podem ser alteradas nas configurações do Android. Desativar Acessibilidade encerra o bloqueio.', size: 'normal', label: '06' },
];

const questions = [
  ['O que o InScreenLock mede?', 'Tempo com Instagram ou TikTok em primeiro plano e com a tela ativa. A medição considera o app inteiro, não apenas vídeos ou Reels.'],
  ['O limite é separado para cada rede?', 'Não. A meta é combinada entre Instagram e TikTok; o painel de hoje também mostra a divisão por aplicativo.'],
  ['O que acontece quando atinjo a meta?', 'Você escolhe entre receber um aviso ou usar o bloqueio opcional. O bloqueio exige um PIN local ou uma credencial segura configurada no aparelho.'],
  ['Quais permissões são necessárias?', 'O Acesso de uso consulta os tempos em primeiro plano e alimenta os painéis. Com seu consentimento, a Acessibilidade reconhece mudanças de janela para identificar os apps monitorados.'],
  ['O que a Acessibilidade lê?', 'Ela lê somente o nome do pacote da janela para reconhecer Instagram ou TikTok. Não lê textos, imagens ou conteúdo das telas.'],
  ['Meus dados saem do aparelho?', 'O app não declara permissão INTERNET nem exige conta. Preferências ficam no aparelho; a lista de uso de apps é calculada em memória. A disponibilidade dos tempos depende dos dados fornecidos pelo Android.'],
  ['O histórico sempre estará completo?', 'Não necessariamente. As análises cobrem até os últimos 7 dias e dependem dos eventos que o Android disponibilizar e do Acesso de uso. Dias sem dados são indicados como tal.'],
  ['Como instalo o app?', 'Quando o APK verificado estiver disponível, você poderá baixá-lo nesta página e instalar pelo Android. No Android 13 ou superior, além de permitir “instalar apps desconhecidos” para o navegador ou gerenciador de arquivos, talvez seja necessário abrir as informações do InScreenLock e escolher “Permitir configurações restritas” para ativar a Acessibilidade. Os nomes dos menus podem variar por aparelho.'],
  ['Está na Play Store ou funciona no iPhone?', 'Ainda não. A distribuição prevista é por APK para Android 9 ou superior. Não há versão para iOS nem publicação na Play Store.'],
  ['Posso desinstalar ou desligar o bloqueio?', 'Sim. O InScreenLock não é à prova de adulteração: você pode alterar permissões, desligar a Acessibilidade, forçar a parada ou desinstalar nas configurações do Android.'],
];

function SectionHeading({ eyebrow, title, lead, id }: { eyebrow: string; title: string; lead?: string; id: string }) {
  return <div className="section-heading"><span className="section-eyebrow">{eyebrow}</span><h2 id={id}>{title}</h2>{lead && <p>{lead}</p>}</div>;
}

function DemoLab() {
  const [minutes, setMinutes] = useState(60);
  const [pin, setPin] = useState('');
  const projection = useMemo(() => calculateUsageProjection(minutes), [minutes]);
  const limit = calculateLimitDemo(17, 21);
  function enterDigit(digit: string) { setPin((current) => (current.length < 4 ? `${current}${digit}` : '')); }
  return <section id="demo" className="content-section demo-lab" aria-labelledby="demo-title"><span id="demo-lab" className="anchor-alias" aria-hidden="true"/>
    <SectionHeading id="demo-title" eyebrow="EXPERIMENTE" title="Teste como é recuperar o controle." lead="Uma simulação simples para visualizar o efeito acumulado de alguns minutos por dia."/>
    <div className="demo-grid">
      <SpotlightCard className="glass-panel projection-card" spotlightColor="rgba(34,227,176,.16)" intensity={.2}>
        <div className="panel-topline"><span><Sparkles size={17}/> Cenário hipotético</span><span className="faux-tag">Demonstração</span></div>
        <label className="slider-label" htmlFor="daily-minutes"><span>Minutos por dia</span><strong>{minutes} min</strong></label>
        <input id="daily-minutes" type="range" min="5" max="180" step="5" value={minutes} onChange={(event) => setMinutes(Number(event.target.value))} aria-describedby="rounding-note"/>
        <div className="range-scale"><span>5 min</span><span>3 h</span></div>
        <div className="projection-results"><div><strong>{projection.annualHours.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}</strong><span>horas em um ano</span></div><div><strong>{projection.equivalentDays.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}</strong><span>dias equivalentes</span></div></div>
        <p id="rounding-note" className="fine-print">Cálculo: minutos × 365 ÷ 60 e minutos × 365 ÷ 1.440; arredondado para uma casa decimal. Um ano hipotético de 365 dias.</p>
      </SpotlightCard>
      <div className="demo-side-stack">
        <SpotlightCard className="glass-panel limit-demo-card" spotlightColor="rgba(255,107,122,.18)" intensity={.2}>
          <div className="panel-topline"><span><AlertTriangle size={17}/> Simulação de limite</span><span className="faux-tag">Dados fictícios</span></div>
          <div className="limit-comparison"><div><span>Meta diária</span><strong>17 <small>min</small></strong></div><ArrowRight aria-hidden="true"/><div><span>Uso no cenário</span><strong>21 <small>min</small></strong></div></div>
          <p className={`limit-result${limit.exceeded ? ' is-over' : ''}`}><AlertTriangle size={18}/> {limit.exceeded ? `${limit.excessMinutes} min acima da meta` : 'Dentro da meta'}</p>
        </SpotlightCard>
        <SpotlightCard className="glass-panel pin-demo-card" spotlightColor="rgba(34,227,176,.12)" intensity={.18}>
          <div className="panel-topline"><span><LockKeyhole size={17}/> PIN de demonstração</span></div>
          <p>Toque em dígitos fictícios para experimentar o teclado.</p>
          <div className="pin-dots" aria-label={`${pin.length} de 4 dígitos selecionados`}>{Array.from({ length: 4 }, (_, index) => <span key={index} className={index < pin.length ? 'filled' : ''} aria-hidden="true"/>)}</div>
          <div className="demo-keypad" aria-label="Teclado visual de demonstração">{['1','2','3','4','5','6','7','8','9','apagar','0','ok'].map((key) => <button type="button" key={key} aria-label={key === 'apagar' ? 'Apagar dígito' : key === 'ok' ? 'Limpar PIN de demonstração' : `Dígito ${key}`} onClick={() => key === 'apagar' ? setPin((current) => current.slice(0, -1)) : key === 'ok' ? setPin('') : enterDigit(key)}>{key === 'apagar' ? <Minus size={15}/> : key === 'ok' ? <Check size={15}/> : key}</button>)}</div>
          <p className="demo-warning"><ShieldCheck size={16}/> Demonstração interativa — não configura seu aparelho. Nada é salvo ou enviado.</p>
        </SpotlightCard>
      </div>
    </div>
  </section>;
}

function Features() {
  return <section id="recursos" className="content-section features-section" aria-labelledby="features-title">
    <SectionHeading id="features-title" eyebrow="FEITO PARA ESCOLHER" title="Tudo o que você precisa para usar melhor o seu tempo." lead="Recursos do app, com limites claros sobre o que cada um faz."/>
    <AnimatedContent distance={20} duration={.65}><MagicBento items={featureCards}/></AnimatedContent>
  </section>;
}

function Privacy() {
  return <section id="privacidade" className="content-section privacy-section" aria-labelledby="privacy-title">
    <SectionHeading id="privacy-title" eyebrow="PRIVACIDADE, SEM RODAPÉS ESCONDIDOS" title="Seu tempo é pessoal. Seus dados também." lead="As permissões têm funções diferentes e são ativadas por você nas configurações do Android."/>
      <div className="privacy-layout"><div className="privacy-copy"><article><span className="privacy-icon"><Eye size={21}/></span><div><h3>Acesso de uso</h3><p>Consulta o tempo em primeiro plano por aplicativo, usado no limite combinado e nos painéis de hoje e dos últimos dias. A disponibilidade depende do histórico que o Android fornece.</p></div></article><article><span className="privacy-icon"><Fingerprint size={21}/></span><div><h3>Acessibilidade</h3><p>Com seu consentimento e ativação nas configurações, identifica mudanças de janela. Para outros apps, lê somente o nome do pacote para reconhecer Instagram ou TikTok e descarta os demais; não lê o conteúdo da janela. Nomes internos de telas do InScreenLock ficam só em memória.</p></div></article><article><span className="privacy-icon"><WifiOff size={21}/></span><div><h3>Processamento local</h3><p>O app não declara permissão INTERNET nem exige conta. Preferências ficam salvas localmente; o painel de uso dos apps é calculado em memória. Os tempos vêm do histórico mantido pelo Android.</p></div></article><article><span className="privacy-icon"><ShieldCheck size={21}/></span><div><h3>Verificação do APK 1.0.0</h3><p>O APK assinado declara <code>PACKAGE_USAGE_STATS</code> e a permissão interna <code>com.inscreenlock.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION</code>; o badging não lista permissão de internet. Esta versão ainda aguarda publicação. Evidência: <code>apk-badging-1.0.0.txt</code>.</p></div></article></div>
      <aside className="privacy-caveats"><span className="caveat-label"><ShieldCheck size={18}/> CONTROLE E LIMITES</span><h3>Você continua no comando do aparelho.</h3><ul><li>Não é à prova de adulteração.</li><li>Desativar Acessibilidade encerra o bloqueio.</li><li>O histórico depende dos eventos que o Android disponibilizar.</li><li>Permissões, navegação do sistema e desinstalação continuam sob seu controle.</li></ul><p>O bloqueio opcional requer PIN local ou credencial segura do aparelho e pode liberar por até 15 minutos após autenticação.</p></aside></div>
  </section>;
}

function Results() {
  const [selectedDay, setSelectedDay] = useState(3);
  const heights = [84, 118, 67, 141, 96, 128, 57];
  const days = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'];
  return <section id="resultados" className="content-section results-section" aria-labelledby="results-title"><SectionHeading id="results-title" eyebrow="VISUALIZE SEUS HÁBITOS" title="Pequenas decisões. Mudanças que você consegue enxergar." lead="O app consulta até os últimos 7 dias disponíveis no histórico do Android."/><div className="results-grid"><article className="chart-panel"><div className="chart-title"><div><span>Tempo de uso</span><strong>Exemplo de semana</strong></div><span className="chart-range">7 dias</span></div><svg viewBox="0 0 620 220" role="group" aria-label="Gráfico interativo ilustrativo; selecione um dia, sem dados reais"><defs><linearGradient id="barGlow" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#22e3b0"/><stop offset="1" stopColor="#16c79a" stopOpacity=".24"/></linearGradient></defs><path d="M45 35H600M45 90H600M45 145H600M45 200H600" stroke="#ffffff" strokeOpacity=".08"/><g fill="url(#barGlow)">{heights.map((height, index) => <rect key={days[index]} x={72 + index * 76} y={200 - height} width="42" height={height} rx="12" opacity={selectedDay === index ? 1 : .52} role="button" tabIndex={0} aria-label={`${days[index]}, exemplo ilustrativo`} aria-pressed={selectedDay === index} onClick={() => setSelectedDay(index)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setSelectedDay(index); } }}/>)}</g><g fill="#9aa4b2" fontSize="12" textAnchor="middle">{days.map((day, index) => <text key={day} x={93 + index * 76} y="216">{day}</text>)}</g></svg><p className="chart-selection">Dia em foco: {days[selectedDay]} · valor apenas ilustrativo</p><p className="illustrative-label">Exemplo ilustrativo, não são dados reais.</p></article><article className="chart-panel trend-panel"><div className="chart-title"><div><span>Distribuição do uso</span><strong>Instagram + TikTok</strong></div><span className="chart-range">Ilustrativo</span></div><svg viewBox="0 0 420 220" role="img" aria-label="Gráfico ilustrativo da distribuição entre dois apps, sem dados reais"><circle cx="112" cy="110" r="68" fill="none" stroke="#252c35" strokeWidth="24"/><circle cx="112" cy="110" r="68" fill="none" stroke="#8b5cf6" strokeWidth="24" strokeDasharray="174 427" transform="rotate(-90 112 110)"/><circle cx="112" cy="110" r="68" fill="none" stroke="#22e3b0" strokeWidth="24" strokeDasharray="253 427" strokeDashoffset="-174" transform="rotate(-90 112 110)"/><text x="112" y="105" textAnchor="middle" fill="#e6eaf0" fontSize="17" fontWeight="700">Exemplo</text><text x="112" y="127" textAnchor="middle" fill="#9aa4b2" fontSize="12">sem percentuais</text><g fontSize="13" fill="#c9d0db"><circle cx="236" cy="84" r="5" fill="#8b5cf6"/><text x="250" y="89">Instagram</text><circle cx="236" cy="120" r="5" fill="#22e3b0"/><text x="250" y="125">TikTok</text></g></svg><p className="illustrative-label">Exemplo ilustrativo, não são dados reais.</p></article></div><p className="results-note">A análise é calculada sob demanda a partir do histórico disponibilizado pelo Android. Dias sem dados aparecem como “sem dados”.</p></section>;
}

function FAQ() {
  const [open, setOpen] = useState<number | null>(0);
  return <section id="faq" className="content-section faq-section" aria-labelledby="faq-title"><SectionHeading id="faq-title" eyebrow="RESPOSTAS DIRETAS" title="Tudo claro antes de começar."/><div className="faq-list">{questions.map(([question, answer], index) => <article className={`faq-item${open === index ? ' is-open' : ''}`} key={question}><h3><button type="button" aria-expanded={open === index} aria-controls={`faq-answer-${index}`} onClick={() => setOpen((current) => current === index ? null : index)}>{question}<ChevronDown size={20} aria-hidden="true"/></button></h3><div id={`faq-answer-${index}`} className="faq-answer" hidden={open !== index}><p>{answer}</p></div></article>)}</div></section>;
}

function SiteFooter() {
  const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? '';
  return <footer className="site-footer"><a className="footer-brand" href="#inicio">InScreenLock</a><span>Seu tempo, sob seu controle.</span><nav aria-label="Links do rodapé"><a href="#privacidade">Privacidade</a><a href="#faq">FAQ</a><a href="https://github.com/erickk-silva/InScreenLock" target="_blank" rel="noreferrer">GitHub</a><a href={`${basePath}/NOTICE-REACT-BITS.md`}>Licenças e NOTICE React Bits</a></nav><small>© {new Date().getFullYear()} InScreenLock</small></footer>;
}

export default function ContentSections() {
  const [copied, setCopied] = useState(false);
  const [currentRelease, setCurrentRelease] = useState(release);
  useEffect(() => {
    // Fake release data is compiled into the E2E server only, never a normal or Pages build.
    if (process.env.NEXT_PUBLIC_ENABLE_RELEASE_FIXTURE === '1' && new URLSearchParams(location.search).get('releaseFixture') === 'available') {
      window.setTimeout(() => setCurrentRelease({ status: 'available', version: '0.1.0-demo', url: 'https://example.invalid/InScreenLock-demo.apk', sha256: 'a'.repeat(64), sizeBytes: 1024 * 1024 * 12, minAndroid: '9', publishedAt: '2026-11-07' }), 0);
    }
    const heroDemo = document.querySelector<HTMLElement>('.phone-stage#demo');
    if (heroDemo) heroDemo.id = 'demo-phone';
  }, []);
  const available = releaseIsDownloadable(currentRelease);
  async function copyHash() { if (!currentRelease.sha256) return; await navigator.clipboard.writeText(currentRelease.sha256); setCopied(true); window.setTimeout(() => setCopied(false), 1800); }
  return <>
    <section id="como-funciona" className="content-section story-section" aria-labelledby="story-title"><SectionHeading id="story-title" eyebrow="UM CICLO QUE SE REPETE" title="Você abriu por cinco minutos. O feed tinha outros planos." lead="Três momentos familiares. Sem prender a rolagem: avance no seu ritmo."/><StoryBeats/></section>
    <DemoLab/><Features/><Privacy/><Results/><FAQ/>
    <section id="download" className="content-section final-cta" aria-labelledby="download-title"><div className="cta-orb" aria-hidden="true"/><span className="section-eyebrow">RECUPERE SEU TEMPO</span><h2 id="download-title">O próximo vídeo pode esperar.<br/><em>Seu tempo, não.</em></h2><p>InScreenLock para Android 9 ou superior.</p>
      {!available ? <><div className="release-state" data-release-status={currentRelease.status}><span className="download-pulse" aria-hidden="true"/>Download em preparação. O APK será publicado depois de verificado.</div><div className="release-prep-details"><strong>Antes de baixar</strong><p>A publicação incluirá o APK assinado, número da versão, tamanho do arquivo e hash SHA-256 para conferência.</p><p>Compatibilidade: Android 9 ou superior. Ainda não há versão para iOS nem distribuição pela Play Store.</p><p>Para instalar, o Android poderá pedir autorização para instalar apps desconhecidos e você concede Acesso de uso e Acessibilidade nas configurações do aparelho.</p></div></> : <div className="release-ready" data-release-status={currentRelease.status}><a className="button button-primary" href={currentRelease.url!} download><Smartphone size={21}/>Baixar para Android<ArrowDown size={19}/></a><p>Versão {currentRelease.version} · Android {currentRelease.minAndroid}+</p><p>SHA-256 <code>{currentRelease.sha256}</code> <button className="copy-hash" type="button" onClick={copyHash} aria-label="Copiar hash SHA-256">{copied ? <Check size={16}/> : <Copy size={16}/>} {copied ? 'Copiado' : 'Copiar'}</button></p><p>{currentRelease.sizeBytes ? `${(currentRelease.sizeBytes / 1024 / 1024).toFixed(1)} MB` : ''}{currentRelease.publishedAt ? ` · Publicado em ${new Date(currentRelease.publishedAt).toLocaleDateString('pt-BR')}` : ''}</p><ol><li>Baixe o APK deste endereço.</li><li>Confira o SHA-256 antes de instalar.</li><li>Autorize a instalação para o app usado para abrir o arquivo.</li></ol></div>}
      <p className="cta-note">Sem Play Store ou versão para iOS neste momento. A instalação e as permissões são sempre controladas por você nas configurações do Android.</p>
    </section>
    <SiteFooter/>
  </>;
}

function StoryBeats() {
  const [active, setActive] = useState(0);
  const beats = [
    { number: '01', title: 'Só mais um vídeo', copy: 'Você abre a rede por alguns minutos. O próximo vídeo aparece antes de você decidir parar.', icon: Smartphone },
    { number: '02', title: 'O tempo se estende', copy: 'A rolagem continua. Mensagens e outras áreas também contam como tempo no app.', icon: Timer },
    { number: '03', title: 'A escolha volta para você', copy: 'Com uma meta clara e um aviso ou bloqueio opcional, fica mais fácil perceber a hora de sair.', icon: Check },
  ];
  const current = beats[active]; const Icon = current.icon;
  return <div className="story-stage"><div className="story-visual" aria-hidden="true"><span className="story-ring story-ring-a"/><span className="story-ring story-ring-b"/><span className="story-phone"><Icon size={42}/></span><span className="story-counter">{current.number}</span></div><div className="story-content"><AnimatedContent key={active} distance={18} duration={.45} direction="vertical"><span className="story-step">MOMENTO {current.number} / 03</span><h3>{current.title}</h3><p>{current.copy}</p></AnimatedContent><div className="story-controls"><div className="story-dots" role="group" aria-label="Momentos da história">{beats.map((beat, index) => <button key={beat.number} type="button" aria-label={`Mostrar momento ${index + 1}: ${beat.title}`} aria-current={active === index ? 'step' : undefined} onClick={() => setActive(index)}/>)}</div><div><button type="button" aria-label="Momento anterior" disabled={active === 0} onClick={() => setActive((value) => Math.max(0, value - 1))}><Minus size={17}/></button><button type="button" aria-label="Próximo momento" disabled={active === beats.length - 1} onClick={() => setActive((value) => Math.min(beats.length - 1, value + 1))}><Plus size={17}/></button></div></div></div></div>;
}
