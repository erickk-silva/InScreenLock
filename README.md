# InScreenLock

Aplicativo Android offline para acompanhar o tempo total com Instagram e TikTok abertos. O contador inclui mensagens e outras áreas dos aplicativos; ele não representa tempo assistindo Reels ou vídeos.

## Privacidade e permissões

- O monitoramento começa desativado. A tela inicial explica o uso do serviço e exige uma confirmação explícita antes de abrir as configurações de Acessibilidade.
- O Android concede Acesso de uso e Acessibilidade somente nas próprias configurações do sistema. O aplicativo não tenta ativar, contornar ou ocultar essas permissões.
- O serviço recebe notificações de mudança de janela de qualquer aplicativo apenas para identificar quando Instagram ou TikTok entram ou saem do primeiro plano. Lê somente o nome do pacote, descarta os demais e não guarda nomes de pacote nem notificações. Não acessa a árvore visual.
- A duração diária é recalculada a partir de `UsageStatsManager` e dos limites do dia local. Preferências e o estado numérico do aviso diário ficam no DataStore. Nenhum texto, imagem, mensagem, legenda, perfil ou histórico de eventos dos aplicativos é guardado.
- Com Acesso de uso, o painel “Uso de hoje por app” lê o tempo de primeiro plano de todos os apps do dia apenas para exibi-lo no aparelho; a lista e os ícones ficam somente em memória, nada sai do aparelho nem é salvo.
- A visibilidade de apps no manifesto usa somente a consulta de intent `MAIN`/`LAUNCHER`, necessária para rótulos e ícones de apps com launcher; pacotes não visíveis aparecem pelo nome do pacote. Não declara `QUERY_ALL_PACKAGES`, Internet, leitura de notificações, janela de alerta do sistema ou serviço em primeiro plano.
- O bloqueio voluntário cobre toda a janela do Instagram ou TikTok, inclusive telas internas como login, e oferece “Ir para o início” e “Desbloquear”. A liberação dura até 15 minutos. O Android continua permitindo desativar Acessibilidade, forçar parada ou desinstalar; desativar Acessibilidade nas configurações encerra o bloqueio, intencionalmente, conforme a política do Android.
- O bloqueio exige uma credencial segura de tela do dispositivo ou PIN do InScreenLock. Sem credencial, a tela informa que o bloqueio está indisponível e mostra somente o aviso. O PIN local tem 4–8 dígitos e é guardado apenas como derivação com salt; ajustes que reduzem a proteção pedem autenticação.
- Em tela dividida ou picture-in-picture, sem ler conteúdo de janela ou pedir permissões adicionais, o app não consegue detectar de forma confiável qual parte da tela pertence ao alvo; a sobreposição de bloqueio pode cobrir outro app. É uma limitação conhecida a validar em aparelho físico. “Ir para o início” e a navegação do sistema permanecem disponíveis.
- Desinstalar remove os dados locais do InScreenLock: preferências, PIN, avisos e liberação. Backup e transferência de dispositivo estão desativados. O histórico de Acesso de uso do Android pertence ao sistema e o InScreenLock nunca o copia.
- Este APK privado não foi submetido à revisão de acessibilidade da Google Play; uma distribuição pela loja exigirá declaração e revisão próprias.

## Escopo do MVP

O modo disponível mede tempo total de primeiro plano com a tela ativa e desbloqueada; a meta padrão é 30 minutos por dia. A reação padrão é bloqueio com credencial; “Aviso discreto” também pode ser escolhido nas configurações. O desbloqueio libera o uso por até 15 minutos e exige nova autenticação para ajustes que reduzam a proteção.

O dashboard mostra também os apps com mais tempo em primeiro plano hoje, com os dez primeiros e opção “Ver todos” na mesma tela. Instagram e TikTok continuam sujeitos ao orçamento próprio.

A estimativa de transições para vídeos curtos permanece desativada. Ela só poderá ser habilitada depois de validação física independente no Instagram e no TikTok. Um emulador não comprova essa detecção.

## Compilar e validar

Requisitos: JDK 17, Android SDK e um dispositivo/emulador Android para testes instrumentados.

```bash
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon lintDebug
./gradlew --no-daemon assembleDebug
./gradlew --no-daemon connectedDebugAndroidTest
```

O APK de depuração é gerado em `app/build/outputs/apk/debug/app-debug.apk`. Os testes instrumentados verificam a divulgação e o consentimento da interface. Eles não concedem Acesso de uso nem ativam o serviço de Acessibilidade.

O projeto compila contra Android API 37 e mantém `targetSdk` 36 para corresponder ao runtime estável usado nesta validação; o lint registra esse alvo como aviso até a validação do runtime API 37. O wrapper Gradle está fixado em 9.8.1 com SHA-256 oficial.
