# WhatsApp UI fixtures

Capturas reais disponíveis para WhatsApp 2.26.38.73 em pt-BR.
Diálogo inicial não apareceu; inglês e Business ainda aguardam captura.
Use imagens e conversas de teste sem informações pessoais.

Com o Android conectado e a depuração USB autorizada:

```bash
bash tools/capture_ui.sh whatsapp/pt-BR/preview_default
bash tools/capture_ui.sh whatsapp/pt-BR/preview_viewonce_on
bash tools/capture_ui.sh whatsapp/pt-BR/viewonce_first_time_dialog
bash tools/capture_ui.sh whatsapp/pt-BR/chat_after_send
bash tools/capture_ui.sh whatsapp/pt-BR/chat_list
```

Repita com `whatsapp/en` e, para Business, `business/pt-BR` e `business/en`.
Omita o diálogo inicial se ele não estiver disponível e registre essa ausência.
O script adiciona versão, pacote, locale e data abaixo. Revise o XML antes de commit.

- `whatsapp/pt-BR/preview_default.xml`: package `com.whatsapp`, version `2.26.38.73`, locale `pt-BR`, captured 2026-10-05T22:57:22Z

- `whatsapp/pt-BR/preview_viewonce_on.xml`: package `com.whatsapp`, version `2.26.38.73`, locale `pt-BR`, captured 2026-10-05T22:59:48Z

- `whatsapp/pt-BR/chat_after_send.xml`: package `com.whatsapp`, version `2.26.38.73`, locale `pt-BR`, captured 2026-10-05T23:02:11Z

- `whatsapp/pt-BR/chat_list.xml`: package `com.whatsapp`, version `2.26.38.73`, locale `pt-BR`, captured 2026-10-05T23:05:56Z

Textos não necessários aos controles foram removidos automaticamente com
`tools/sanitize_fixture.py`. As capturas vieram de prévias e conversas de teste
preparadas manualmente pela pessoa; não comprovam envio automático do Papelzinho.

- `whatsapp/pt-BR/share_preview_observed.xml`: package `com.whatsapp`, version `2.26.38.73`, locale `pt-BR`, captured 2026-10-05T23:40:25Z

- `whatsapp/pt-BR/clipboard_preview_viewonce_on.xml`: package `com.whatsapp`, version `2.26.38.73`, locale `pt-BR`, captured 2026-10-06T01:06:02Z. Active description confirmed after capture; initial default filename corrected.

- `whatsapp/pt-BR/clipboard_preview_default.xml`: package `com.whatsapp`, version `2.26.38.73`, locale `pt-BR`, captured 2026-10-06T01:07:20Z

- `whatsapp/pt-BR/assisted_preview_observed.xml`: package `com.whatsapp`, version `2.26.38.73`, locale `pt-BR`, captured 2026-10-06T01:16:23Z
