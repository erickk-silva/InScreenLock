# InScreenLock

Aplicativo Android offline para acompanhar o tempo total com Instagram e TikTok abertos. O contador inclui mensagens e outras áreas dos aplicativos; ele não representa tempo assistindo Reels ou vídeos.

## Privacidade e permissões

- O monitoramento começa desativado. A tela inicial explica o uso do serviço e exige uma confirmação explícita antes de abrir as configurações de Acessibilidade.
- O Android concede Acesso de uso e Acessibilidade somente nas próprias configurações do sistema. O aplicativo não tenta ativar, contornar ou ocultar essas permissões.
- O serviço pede apenas eventos de mudança de janela e não acessa a árvore visual. Com monitoramento e Acesso de uso autorizados, usa temporariamente o nome do pacote em primeiro plano para mostrar o contador nos dois aplicativos suportados.
- A duração diária é recalculada a partir de `UsageStatsManager` e dos limites do dia local. Preferências e o estado numérico do aviso diário ficam no DataStore. Nenhum texto, imagem, mensagem, legenda, perfil ou histórico de eventos dos aplicativos é guardado.
- O manifesto não declara Internet, leitura de notificações, janela de alerta do sistema, serviço em primeiro plano ou consulta de todos os pacotes.
- A pausa é voluntária e reversível. A navegação do sistema e o acesso às configurações continuam sob controle do Android e da pessoa usuária.
- Este APK privado não foi submetido à revisão de acessibilidade da Google Play; uma distribuição pela loja exigirá declaração e revisão próprias.

## Escopo do MVP

O modo disponível mede tempo total de primeiro plano com a tela ativa e desbloqueada; a meta padrão é 30 minutos por dia. A reação padrão é um aviso discreto; a pausa em tela cheia pode ser escolhida nas configurações.

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
