# Teste manual da fase 1

## Preparação

- Android API 30+ com WhatsApp de versão/idioma cobertos pelas fixtures.
- Instalar APK debug: `adb install -r papelzinho/build/outputs/apk/debug/papelzinho-debug.apk`.
- Abrir Papelzinho, ativar assistência nas configurações de acessibilidade e permitir notificações.
- Usar conta/conversa de teste. Cada envio abaixo é uma mensagem real.

## Matriz para retomar depois

Por solicitação da pessoa, os cenários ainda marcados como NOT_RUN ficam adiados.
O envio da imagem de exemplo por colagem assistida foi validado; os demais cenários
não são considerados aprovados. Retomar primeiro os testes de ausência de sessão,
expiração, morte do processo e rede lenta, depois grupos e outras versões/idiomas.
O fluxo ACTION_SEND original falhou nesta versão; o teste aprovado usa cópia no
Papelzinho e colagem manual no WhatsApp, conforme o registro abaixo.

| Cenário | Resultado esperado | Status |
|---|---|---|
| Colagem assistida, pt-BR | Ativa e verifica visualização única, envia, libera sessão após saída da prévia; destinatário recebe foto de visualização única | PASS: logs do remetente e confirmação da pessoa sobre o recebimento em 2026-10-05 |
| Conversa individual, pt-BR | Foto recebida em visualização única | NOT_RUN |
| Grupo, pt-BR | Foto recebida em visualização única | NOT_RUN |
| Individual e grupo, inglês | Mesmo comportamento após fixtures em inglês | NOT_RUN |
| WhatsApp Business | Mesmo comportamento após fixtures Business | NOT_RUN |
| Prévia aberta sem sessão | Nenhum clique automático | NOT_RUN |
| Escolha da conversa por mais de 120 s | Sessão expira, nada enviado | NOT_RUN |
| Processo morto durante escolha | Nova abertura da URI falha, nada enviado automaticamente | NOT_RUN |
| Primeiro uso com diálogo | Confirmar apenas diálogo coberto por fixture; outros abortam | NOT_RUN |
| Toggle sem indicação ativa | Aborta; botão Enviar não é acionado | NOT_RUN |
| Rede lenta durante handoff | Validar que teto de 8 s não quebra envio após rede voltar | NOT_RUN |
| App recente/captura de tela | Texto oculto por FLAG_SECURE | NOT_RUN |
| Rotação/retorno do app | Texto em RAM no ViewModel, sem restauração após morte do processo | NOT_RUN |

## Calibrar handoff

1. Armar a imagem de exemplo no Papelzinho, abrir a conversa autorizada e colar manualmente em até 2 minutos.
2. Logo após o clique automático em Enviar, desligar a rede.
3. Esperar mais de 8 s e restaurar a rede.
4. Confirmar no destinatário a foto em visualização única.
5. Registrar versão, idioma, aparelho, tempos e resultado sem texto de conversas.

Timeout ou saída da prévia confirma apenas handoff local, não entrega ao destinatário.

## Fontes disponíveis

WhatsApp 2.26.38.73, Android 14, pt-BR: prévia desligada/ligada, chat após
um envio manual e seletor de conversa capturados em 2026-10-05.
Textos de conversa foram removidos. Diálogo de primeira vez não apareceu.
Estas capturas não comprovam um envio automático do Papelzinho.

## Assisted paste runtime — 2026-10-05

The user confirmed successful automatic sending after manually pasting the sample
into WhatsApp 2.26.38.73 (Android 14, pt-BR). Scoped Papelzinho logs confirmed:
configured keyboard read → nonpreview target observation → editor with inactive
toggle → TOGGLE → fresh active-state observation → SEND → AWAIT_HANDOFF → preview
exit → URI release → DONE. Toggle to SEND: 108 ms; SEND to URI release: 289 ms.
This validates this sender-side experiment, not the original ACTION_SEND MVP flow
or slow-network handoff. The user subsequently confirmed that the recipient received
the sample as a view-once photo. Assisted paste sample end-to-end: PASS for this
configuration, supported by sender logs and user-provided receiver confirmation.
