# Como contribuir

Contribuições são bem-vindas. Para manter as mudanças fáceis de revisar e alinhadas com o propósito do InScreenLock, siga este fluxo:

1. Consulte as [issues abertas](https://github.com/erickk-silva/InScreenLock/issues) antes de começar. Para mudanças maiores, abra uma issue para alinhar o escopo.
2. Crie uma branch a partir de `main` e mantenha cada pull request focado em uma mudança.
3. Abra o PR com contexto, comportamento esperado, como validar e, para mudanças visuais, capturas sem dados pessoais.
4. Aguarde revisão antes de integrar. Não inclua credenciais, dados de uso reais, conteúdo de apps, traces privados ou arquivos gerados.

## Padrões do projeto

- Kotlin segue o [guia oficial de estilo](https://kotlinlang.org/docs/coding-conventions.html); a formatação básica está em `.editorconfig`.
- Use mensagens de commit curtas, no imperativo e com um objetivo claro, por exemplo: `Documenta instalação do APK`.
- Mudanças de lógica ou interface devem incluir testes reproduzíveis. Antes do PR, rode os testes unitários e instrumentados afetados.
- Privacidade primeiro: não adicione coleta de conteúdo, telemetria, anúncios ou dependência de conta. O app não deve declarar a permissão `INTERNET`.
- Mantenha consentimento explícito e respeite os controles do Android. Nunca bloqueie navegação do sistema, configurações, desinstalação ou a desativação da Acessibilidade.
- Não descreva transições inferidas como vídeos assistidos. O modo de vídeo depende de validação física em ambos os apps suportados.

## Testes do aplicativo Android

Requer JDK 17, Android SDK e, para testes instrumentados, dispositivo ou emulador compatível:

```sh
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
```

Testes instrumentados não substituem a validação manual das permissões especiais no Android.

## Testes da landing page

Requer Node.js e npm:

```sh
cd landing
npm ci
npm run dev
```

Para executar os testes unitários e E2E, e verificar a compilação destinada ao GitHub Pages:

```sh
npm test
npm run e2e
npm run build:pages
```

Descreva no PR os comandos executados e os resultados relevantes. Não inclua `node_modules`, builds, capturas privadas ou evidência local de dispositivo.
