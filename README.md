# Papelzinho / Pass the Note

App Android pessoal para transformar texto em PNG na memória e compartilhá-lo
com visualização única. Contrato: [SPEC.md](SPEC.md).

## Ambiente

- mise: JDK fixado em `mise.toml`.
- Gradle 9.6.0 via Wrapper; AGP 9.4.0 e Kotlin 2.2.10.
- Android SDK: platform API 37 e Build Tools 36.0.0; Android API 30+ para execução.
- Defina `ANDROID_HOME` no ambiente local ou `sdk.dir` em `local.properties` (ignorado no Git).
- Configure o Gradle JDK do Android Studio para o mesmo JDK escolhido pelo mise.

```bash
mise trust
mise install
mise run build
mise run test
mise run lint
mise run instrumented
```

`instrumented` exige aparelho autorizado no adb. Não há servidor nem permissão INTERNET no APK.
O JDK fixado é Temurin 17.0.20+8.

## Fontes e limites

O fluxo validado é experimental: no Papelzinho, use **Copiar exemplo e armar envio**,
abra uma conversa sua ou autorizada no WhatsApp e cole em até 2 minutos. O serviço
ativa a visualização única, verifica o estado ativo e envia automaticamente.
O teste de ponta a ponta foi confirmado em WhatsApp 2.26.38.73, Android 14, pt-BR.
A opção **Copiar imagem de exemplo** serve somente ao diagnóstico e não arma envio.

O compartilhamento ACTION_SEND original não abriu a visualização única nessa versão.
O fluxo assistido ainda exige abrir a conversa e colar manualmente; a fase 1 completa
permanece pendente. Evidência e limites: [docs/whatsapp-apk-validation.md](docs/whatsapp-apk-validation.md).

Os seletores atuais foram extraídos do WhatsApp 2.26.38.73 em pt-BR.
Outras combinações ficam bloqueadas até terem fixtures verificadas.
Não se usa coordenada nem se lê conversa para tomar decisões de envio.
O diálogo inicial ainda precisa de fixture; um diálogo desconhecido aborta.

As imagens nunca são gravadas pelo app. Configurações e número de versão do
último handoff são os únicos dados persistidos, em Preferences DataStore.
Encerrar a sessão zera o buffer do app e bloqueia novas aberturas da URI;
descritores/cópias já entregues ao WhatsApp ou ao teclado ficam fora do controle do app.

Checklist de validação real, com cenários adiados para retomada: [docs/manual-test.md](docs/manual-test.md).
Capturas: [fixtures/README.md](fixtures/README.md).
