import { Download, ShieldCheck, Smartphone } from 'lucide-react';
import { release } from '@/lib/release';

/**
 * Stable target of every "Baixar" link. It is always rendered and only states what is true today: no APK is published yet.
 * When `release.status` changes, this block is where the verified download, version and SHA-256 will appear.
 */
export default function DownloadSection() {
  const preparing = release.status === 'preparing';
  return <section className="download" id="download" aria-labelledby="download-title">
    <div className="download-head">
      <p className="download-status" data-release-status={release.status}><span className="download-pulse" aria-hidden="true" />{preparing ? 'Download em preparação' : 'Download disponível'}</p>
      <h2 id="download-title">Baixar o InScreenLock para Android</h2>
      <p className="download-lead">{preparing ? 'O APK ainda não foi publicado. Quando estiver pronto, o link aparece aqui, junto com tudo o que você precisa para conferir o arquivo.' : 'Confira a versão e o SHA-256 antes de instalar.'}</p>
    </div>
    <div className="download-grid">
      <article className="download-card"><Download aria-hidden="true" /><h3>O que será publicado</h3><ul><li>APK assinado</li><li>Hash SHA-256 para conferir o arquivo</li><li>Número da versão</li></ul></article>
      <article className="download-card"><Smartphone aria-hidden="true" /><h3>Compatibilidade</h3><p>Android 9 ou superior. Não há versão para iOS.</p></article>
      <article className="download-card"><ShieldCheck aria-hidden="true" /><h3>Como instalar</h3><p>O Android pede para permitir a instalação do APK. Depois, as permissões de Acesso de uso e Acessibilidade só são concedidas por você, nas configurações do aparelho.</p></article>
    </div>
    <p className="download-privacy" id="privacidade"><strong>Privacidade:</strong> o app não exige conta e não envia dados para um servidor. As preferências ficam salvas no aparelho.</p>
  </section>;
}
