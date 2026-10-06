# WhatsApp sharing validation — 2026-10-05

## Artifact and method

The installed WhatsApp 2.26.38.73 base APK was copied from the connected device
with `adb pull`, without downloading a third-party distribution. SHA-256:
`b5288811e5c48bac1a8f5e40bf7efbb76a5e5bfe95b3ff141184ff531e8ceb99`.
The APK and decompiled source remain outside the repository in temporary storage.
Only package code and the sanitized UI fixture were inspected; no conversation
database or account data was accessed.

Commands: `aapt2 dump xmltree <apk> --file AndroidManifest.xml` and jadx 1.5.6
single-class inspection of `ExternalShareContactPicker`, `ContactPicker` and
`ContactPickerFragment`. The last two reported two decompilation errors each;
their output is supporting evidence,
not proof of every executable branch.

## Confirmed evidence

- The exported `ExternalShareAlias` accepts ACTION_SEND and ACTION_SEND_MULTIPLE
  with `*/*`, targeting the non-exported `ExternalShareContactPicker`.
- `ExternalShareContactPicker` extends `ContactPicker` in this APK.
- `MediaComposerActivity` has no exported attribute or intent filter in this
  manifest. It does not expose a public entry point for the app's share Intent.
- The real Papelzinho ACTION_SEND test produced the sanitized fixture
  `fixtures/whatsapp/pt-BR/share_preview_observed.xml`: a recipient picker with
  an inline image, caption and send button, without `view_once_toggle`.
- `thumbnail_with_edit_container` is clickable in that fixture. This proves a
  clickable UI element exists, but does not prove that clicking it offers view once.
- The accessibility service was bound during the test. The app did not send.

## Verdict

FAIL: the implemented share flow does not satisfy the phase 1 acceptance criterion
on this tested configuration. The previously captured editor fixtures do not prove
that ACTION_SEND reaches that editor or supports view once there.

Unverified: whether opening the inline image allows view once, and whether another
supported share route can reach it while preserving I1–I5. Do not add guessed
Intent extras, click the picker send button, or declare the MVP complete.

The string `skip_preview` occurs in a fragment branch handling a URL/number route;
this is not evidence that the external image-share route supports that extra or
view once. No speculative extra was added to Papelzinho.

## Clipboard route

The user reported that copying the photo and pasting it into a WhatsApp chat
opens a preview with view once. This is user-provided manual evidence; it has not
been reproduced using Papelzinho's provider.

Android documents image/data clipboard transfer via a content URI and provider:
https://developer.android.com/develop/ui/views/touch-and-input/copy-paste.
This permits testing the existing memory provider without writing an image file;
it does not establish that WhatsApp accepts this specific provider via paste.
Mark the clipboard content sensitive to suppress the system preview, but do not
interpret that flag as access control or a guarantee against external copies.

Next required runtime check: an explicitly initiated diagnostic copy of the test
image URI, manual paste into an authorized chat, verification that the view-once
control appears, and verification that ending the session rejects new URI opens.
Clipboard copying and chat navigation are outside the current specified flow;
the production flow has not been changed. Automated clipboard-to-WhatsApp
integration with WhatsApp remains NOT_RUN.

The user authorized the diagnostic prototype with “siga”. It adds a sample-copy
button and a separate memory session, never arming the accessibility automation.
The URI expires after 120 seconds or becomes invalid on cancellation. Clipboard
cleanup only removes the app's own URI; if background clipboard access is denied,
cleanup is retried when the activity resumes. URI invalidation does not depend on
clipboard access. External copies and clipboard histories remain outside the app's
control. The production ACTION_SEND flow has not been replaced.

The user confirmed that pasting the prototype sample opened the view-once editor.
Captured `clipboard_preview_viewonce_on.xml` (initially named default, renamed
after inspecting the active description): `media_composer_layout`, `view_once_toggle`
with `Desativar a visualização única`, and `send`. The earlier inline gallery profile
uses `gallery_selected_media` and `send_media_btn`; it therefore does not authorize
send in this editor.

Captured the off state in `clipboard_preview_default.xml`: the same toggle changes
its description to `Ativar a visualização única`. A separate `clipboardPreview`
selector profile now recognizes the editor and distinguishes both states; tests
also reject the external recipient picker and ordinary chat. This profile is not
connected to automatic sending. The diagnostic clipboard session still never
arms the service. Automated sending and a revised production workflow remain pending.

The next authorized experiment adds an explicit assisted sample-copy control.
It arms a CLIPBOARD route in the sending store, uses the captured editor profile,
and waits for a provider opening by the target package UID before automation.
The UID check uses `Binder.getCallingUid()` in the provider transaction:
https://developer.android.com/reference/android/os/Binder#getCallingUid().
App-local provider reads do not satisfy that condition. This read check establishes
that the target obtained the session image; it is not proof of the recipient or
a pixel-level comparison of the preview. Users still select the chat and paste
manually. Clipboard diagnostic copy remains unarmed. Runtime assisted send is pending.

The assisted integration passed 20 unit tests and 12 native tests, lint, ktlint and
detekt. See `docs/validation.md`. Actual WhatsApp automatic send and receiver-side
view-once confirmation remain pending; no complete-MVP claim is made.

The first assisted runtime attempt remained in the off-state editor. The user
confirmed using the armed-copy button. The service was bound and the captured
`assisted_preview_observed.xml` matches the clipboard editor's known controls.
There were no Papelzinho log entries sufficient to establish the blocking stage.
Added content-free arm/release, provider caller role (target/self/other), read-gate
and preview-state observations for a fresh run. Root cause is unverified; do not
remove the read gate or bypass active-state verification based on this attempt.

Fresh logs classified the URI reader as the configured keyboard and showed
`previewPresent=false` while toggle and send matched. The old config's native
regression test failed FLAG_INCLUDE_NOT_IMPORTANT_VIEWS before the XML fix.
The service now requests layout containers, matching the captured UI hierarchy.
The keyboard path requires a same-target nonpreview handoff before entering the
editor; clipboard reading alone cannot trigger on a preexisting preview. The
direct-target read route and view-once verification remain separate guards.

After this correction the user reported successful automatic sending. Scoped logs
confirmed fresh active-state verification before SEND and release only after the
editor disappeared, ending in DONE. The assisted paste route is now validated on
the sender for this configuration. The user also confirmed receiver-side delivery
as a view-once photo. Assisted paste of the sample is validated end-to-end for this
configuration. This does not validate the failed external-share route or satisfy the
original MVP requirement that choosing the conversation be the only manual step.
