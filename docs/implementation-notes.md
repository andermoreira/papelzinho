# Registro de implementação

## Gate de I3 — 2026-10-05

A spec em `SPEC.md` exige URI inacessível após release e usa um memfd novo por abertura.
Revogar a URI impede novas aberturas; não invalida descritores já entregues.
A seção DESCRIPTION de https://man7.org/linux/man-pages/man2/memfd_create.2.html
explica que o objeto continua vivo enquanto referências existirem.
A API Android confirma `Os.memfd_create` a partir da API 30:
https://developer.android.com/reference/android/system/Os#memfd_create(java.lang.String,int).

Recomendação aprovada pela pessoa em 2026-10-05: I3 exige rejeição de novas aberturas
após release, com descritores e cópias já entregues fora do controle do app.
SPEC.md atualizado antes da implementação do provider.

## Ambiente

Bootstrap executado com JDK 17.0.2 já disponível. Em seguida, mise consultou versões
e instalou Temurin 17.0.20+8; esta é a versão fixada em mise.toml.
AGP 9.4.0 requer Gradle 9.6.0 e JDK 17, suporta API 37:
https://developer.android.com/build/releases/agp-9-4-0-release-notes.

## Gate da lista fechada de permissões — 2026-10-05

O manifesto mesclado trouxe `com.andersonmoreira.papelzinho.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
por dependência AndroidX Core. O teste instrumentado da lista fechada falhou com
9 testes executados e uma falha: esperado apenas POST_NOTIFICATIONS, encontrado
POST_NOTIFICATIONS e a permissão interna AndroidX.

O app não registra receptores de broadcast via ContextCompat. Para manter o contrato
fechado da seção 4, o merger remove a declaração e o uso desta permissão interna.
Não introduzir `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` em API 30–32
sem reavaliar esse contrato e a permissão de compatibilidade. Runtime dessas APIs antigas
permanece NOT_RUN; os testes atuais usam Android 14 (API 34).

## Gate da prévia de compartilhamento — 2026-10-05

O envio real por ACTION_SEND não abriu diretamente a prévia prevista na seção 5.2.
A fixture `whatsapp/pt-BR/share_preview_observed.xml` mostra o seletor de contatos
com `inline_media_preview_container`, `thumbnail_with_edit_container`,
`appended_message` e `send`, sem `view_once_toggle` nem `gallery_selected_media`.
O serviço estava conectado; não houve clique automatizado. Essa árvore não autoriza
envio segundo I1 e não equivale às fixtures da prévia de edição anteriormente capturadas.
Verificar manualmente a abertura da imagem antes de alterar seletores ou ampliar
a automação: a seção 11 exclui navegação fora da prévia de mídia.

## Assisted paste experiment — 2026-10-05

After the prototype successfully pasted a memory-backed image and the off/on
editor fixtures were captured, the user authorized the proposed next step with
“siga”: automatic view-once activation and send after manual chat selection and
paste. Add a distinct explicit sample-copy-and-arm control. Preserve the unarmed
diagnostic control. No automation of chat navigation or clipboard paste is authorized
by this step. The original one-manual-step MVP acceptance remains unmet.

Clipboard send sessions must wait until the target package actually opens the
session URI before the service may act. This prevents acting on a preexisting
preview merely because a copy session was armed. Keep I1, I3 and I4 tests intact;
select the captured clipboard editor profile only for the matching session route.

## Runtime clipboard gates — 2026-10-05

The user pasted using the field's Paste menu. Structured logs identified the
configured keyboard UID opening the sample URI immediately after copy. The service
observed toggle/send but `previewPresent=false`; the container present in the
UIAutomator fixture was omitted from its accessibility tree. Android documents
FLAG_INCLUDE_NOT_IMPORTANT_VIEWS as explicitly required for layout containers
omitted from accessibility queries:
https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.

Target-only provider reads are therefore insufficient for the observed clipboard
route. Recognize reads by the configured keyboard UID, while rejecting own-app and
other readers. Before a keyboard-mediated session can trigger, additionally require
an unobstructed target-package observation without preview/toggle/send followed by
the known editor. A keyboard read alone must never trigger actions on a preexisting
preview. This confirms delivery to the configured clipboard consumer, not recipient
identity or pixel identity; only session-scoped view-once verification authorizes
send. Keyboard copies are outside the app's memory/revocation guarantee. Do not
change I1, URI invalidation, package filtering or diagnostic-session isolation.
