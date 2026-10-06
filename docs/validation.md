# Recibo de validação — fase 1

Estado avaliado: base `6a70c31` + alterações da implementação registradas neste documento.
Data: 2026-10-05. JDK Temurin 17.0.20+8 via mise, Gradle 9.6.0,
AGP 9.4.0, Kotlin 2.2.10. SDK compile/target 37, min 30.
Aparelho dos testes: Android 14 / API 34, pt-BR.

Resultado atual: 21 testes unitários e 13 instrumentados passaram, além de build,
lint, ktlint e detekt. Colagem assistida da imagem de exemplo aprovada de ponta a
ponta por logs do remetente e confirmação da pessoa sobre o recebimento.
Os demais cenários manuais ficam adiados por solicitação da pessoa; ver
[docs/manual-test.md](manual-test.md). Abaixo, as execuções iniciais e incrementais
preservam o histórico; seus resultados pendentes foram atualizados nas seções posteriores.

## Esperado congelado

- I1: nenhum clique em Enviar sem estado ativo confirmado; falhas/diálogos desconhecidos abortam.
- I2: renderer/provider/sessão e envio simulado não criam arquivos de conteúdo nos diretórios do app.
- I3 aprovado: release zera o buffer e rejeita novas aberturas da URI; descritores já entregues não são revogáveis.
- I4: sem sessão, ou em outro pacote, nenhum clique.
- I5: INTERNET ausente no APK; lista de uses-permission limitada a POST_NOTIFICATIONS.
- APK compila; traduções completas; ktlint e detekt sem achados.

## Execução inicial

Com `ANDROID_HOME` apontando ao SDK instalado:

```bash
mise exec -- ./gradlew :papelzinho:testDebugUnitTest :papelzinho:assembleDebug \
  :papelzinho:assembleDebugAndroidTest :papelzinho:lintDebug \
  :papelzinho:ktlintCheck :papelzinho:detekt --continue
```

**PASS**, exit 0: `BUILD SUCCESSFUL in 10s`, 91 tarefas, 17 testes JUnit 5,
zero falhas e zero testes pulados. Os linters foram executados após a formatação.
Relatórios gerados sob `papelzinho/build/reports/` e resultados JUnit sob
`papelzinho/build/test-results/testDebugUnitTest/` (ignorados no Git).

O APK e o APK de testes foram instalados com `adb install -r`; para preservar a
instalação/configuração de acessibilidade, a execução final usou:

```bash
adb shell am instrument -w -r \
  com.andersonmoreira.papelzinho.test/androidx.test.runner.AndroidJUnitRunner
```

**PASS**, exit 0: `OK (9 tests)`, todos com `INSTRUMENTATION_STATUS_CODE: 0`.
O código final de instrumentação `-1` indica término normal do runner, não falha.
Também houve execução via `connectedDebugAndroidTest` antes da última alteração
no reconhecimento da assinatura da prévia.

## Cobertura e limites por invariante

| Critério | Evidência | Veredito |
|---|---|---|
| I1 | AutomationTest: ativo falso, diálogo desconhecido, timeout, pacote errado; service atualiza a árvore antes de SEND | PASS nos testes; envio real pendente |
| I2 | PrivacyTest: renderer → SessionStore → leitura real pelo provider em envio simulado → release, listagens iguais | PASS no fluxo simulado |
| I3 | SessionStoreTest e ProviderTest: zero no buffer original, URI encerrada/unknown rejeitadas, release idempotente | PASS para novas aberturas |
| I4 | AutomationTest: zero ações sem sessão e com pacote diferente; guarda antes de consultar a árvore no service | PASS nos testes; cenário real pendente |
| I5 | PrivacyTest sobre PackageManager do APK instalado; manifesto mesclado inspecionado | PASS |
| Renderer | PNG 1080×1350/1920, emoji, erro por excesso, reciclagem, miniatura separada | PASS |
| Entrada/estado | ActivityTest: texto recebido no ViewModel, EXTRA_TEXT removido, texto ausente no Bundle, FLAG_SECURE e recriação | PASS |
| Provider | Reaberturas, bytes exatos, seek, modos inválidos, query e consumo | PASS |
| Intent | Pacote escolhido, URI igual em EXTRA_STREAM/ClipData, grant apenas de leitura | PASS |
| Seletores | XML reais pt-BR; off/on, chat e seletor de conversa; ambiguidade rejeitada; timeout sem toggle | PASS nas fixtures disponíveis |
| UI/idiomas | Android Lint MissingTranslation/ExtraTranslation como erro, nomes resolvidos pt-BR/en-US | PASS |

## Histórico de vermelho

- Primeiras compilações dos testes de sessão/máquina, renderer, provider e Activity
  falharam pela ausência das classes antes da implementação (não eram asserções de runtime).
- Teste de diálogo conhecido substituindo a prévia: 16 testes, uma falha de asserção;
  máquina corrigida e teste preservado.
- Lista fechada de permissões: 9 instrumentados, uma falha pela permissão interna
  AndroidX; manifesto corrigido mantendo o esperado fechado.
- Prévia sem toggle: 17 testes, uma falha de asserção; assinatura desacoplada do toggle.
- Falhas de compilação e lint intermediárias foram corrigidas; nenhum teste foi removido ou pulado.

## Auditorias estáticas

- Contrato: comparado ao SPEC.md e ao esclarecimento aprovado de I3. `renderPreview`
  usa `Result<Bitmap>` para sinalizar erro tipado à UI. Não há consumidores preexistentes.
- Dependências: catálogos e POMs resolvidos inspecionados; sem biblioteca de analytics,
  rede ou crash reporting. DataStore guarda somente configuração e versão; nenhuma
  conclusão sobre CVEs foi feita. Lockfile/checksums de todas as transitivas: NOT_RUN.
- Integridade: TODOs limitados a seletores sem fixtures reais (diálogo inicial, inglês,
  Business); estas combinações permanecem bloqueadas. Nenhuma evidência de `.skip`,
  `.only`, @Ignore ou @Disabled nos testes.
- UI: controles Material com rótulos, estado de seleção, headings e erro em live region;
  testes com TalkBack, teclado, fonte ampliada e contraste medido: NOT_RUN.

## Aceite que permanece pendente

O MVP completo ainda não está declarado concluído. Ver `docs/manual-test.md`:
recebimento real em visualização única, grupos, inglês, Business se usado, expiração
real, morte do processo e calibração do handoff com rede lenta são NOT_RUN.
Fixtures presentes: WhatsApp 2.26.38.73, pt-BR. Diálogo inicial não apareceu.

APK debug: `papelzinho/build/outputs/apk/debug/papelzinho-debug.apk`.

## Reconexão após testes instrumentados

No teste manual, o botão de imagem de exemplo permaneceu desabilitado: o serviço
estava habilitado nas configurações, mas não conectado ao AccessibilityManager.
`adb shell dumpsys activity exit-info com.andersonmoreira.papelzinho` registrou
`USER REQUESTED / FORCE STOP`, com descrição `due to finished inst`, após o runner;
também registrou o encerramento anterior por atualização do APK. Não foi registrado
crash Java nesses eventos. O sistema listou o serviço entre os serviços desconectados
(`Crashed services`). A reconexão manual, desligando e ligando o serviço nas
configurações, está pendente de confirmação. Executar os testes instrumentados antes
de habilitar o serviço para o teste manual de envio.

## Clipboard diagnostic prototype

Added an explicitly initiated sample-copy experiment with an independent memory
session. It does not arm the accessibility service. Added native tests for a
sensitive image URI, exact provider bytes, zeroing and URI invalidation on release,
and preserving a replacement clipboard entry.

The test-first compilation failed with unresolved `clipboardDiagnostic` references
before production code was added. Final build command:
`ANDROID_HOME=… mise exec -- ./gradlew :papelzinho:testDebugUnitTest :papelzinho:assembleDebug :papelzinho:assembleDebugAndroidTest :papelzinho:lintDebug :papelzinho:detekt :papelzinho:ktlintCheck`.
Result: BUILD SUCCESSFUL, 91 tasks, 17 unit tests. First native run: 11 tests,
one ActivityScenario recreation timeout while the device was dozing. Both new
clipboard tests passed. After the user unlocked the device, the same native runner
was repeated: `OK (11 tests)`, 2.121 seconds. No assertion or timeout was weakened.
WhatsApp paste compatibility and appearance of its view-once control: NOT_RUN.

## Assisted paste integration

User-provided manual validation confirmed the pasted sample and the view-once
control. Off/on editor fixtures were captured. A separate assisted control now
arms a CLIPBOARD session; the service selects that route's observed selectors.
Provider openings are checked against the installed target UID. Own-app provider
reads do not unlock automation. The diagnostic copy button remains unarmed.

Test-first run failed compilation on the absent SendRoute, markTargetRead and
copyForSending APIs before production changes. New cases cover waiting for the
target read, fresh active-state verification, retention until handoff, sensitive
clipboard copying and zeroing/clipboard cleanup on cancellation.

Executed `:papelzinho:testDebugUnitTest :papelzinho:assembleDebug
:papelzinho:assembleDebugAndroidTest :papelzinho:lintDebug :papelzinho:detekt
:papelzinho:ktlintCheck`: BUILD SUCCESSFUL in 11 seconds; 20 unit tests, zero
failures/errors/skips. Native runner: `OK (12 tests)`, 2.991 seconds. Total 32 tests.
`git diff --check`: PASS. APK installed and MainActivity opened.

Manual assisted activation/send and received view-once photo: NOT_RUN. As before,
instrumentation termination disconnected the accessibility service; the user must
disable/enable it once before the assisted runtime test. Do not rerun instrumentation
between reconnecting and the manual send test. Original MVP acceptance remains unmet.

## Clipboard runtime correction

The first assisted manual attempt did not send. Scoped logs confirmed the selected
keyboard opened the URI, and the service observed toggle/send but no preview
container. The new AccessibilityConfigTest ran against the old APK and failed the
FLAG_INCLUDE_NOT_IMPORTANT_VIEWS assertion (1 test, 1 failure). The keyboard-handoff
unit test initially failed compilation on missing markKeyboardRead before production
changes. Added the flag and a keyboard read path requiring an unobstructed nonpreview
target observation before the new editor; preexisting previews, foreign-package
handoffs and inactive view once cannot trigger send in the new unit test.

Executed full build/unit/lint/detekt/ktlint checks: BUILD SUCCESSFUL in 8 seconds,
91 tasks; 21 unit tests, zero failures/errors/skips. Native runner: `OK (13 tests)`,
2.947 seconds. Total 34 tests. Corrected APK installed and opened. The received
view-once photo and real automated send remain pending. Keyboard copies are external
to the app's memory guarantee; no content or package inventory is logged.

## Successful assisted runtime

After the correction, the user reported successful automatic send. Scoped logs
confirmed a keyboard-mediated CLIPBOARD session, nonpreview handoff, known editor,
TOGGLE, fresh `active=true`, SEND, AWAIT_HANDOFF, preview disappearance, URI release
and DONE, in that order. SEND followed active-state confirmation and release
followed preview exit (289 ms after SEND). Sender-side assisted execution: PASS
for WhatsApp 2.26.38.73, Android 14, pt-BR. The user subsequently confirmed receipt
as a view-once photo: assisted paste sample end-to-end PASS for this configuration.
Receiver confirmation is user-provided evidence, not a device inspection by the
agent. Slow-network calibration, group/Business/English matrix and the original
one-manual-step MVP remain pending. No new code was needed after this successful
runtime observation.
