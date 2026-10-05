# SPEC — Papelzinho / Pass the Note (texto → imagem em memória → WhatsApp em visualização única)

> Documento de especificação para desenvolvimento com Claude Code.
> Coloque este arquivo na raiz do repositório como `SPEC.md`.

## 0. Identidade do app

| Item | Valor |
|---|---|
| Nome em pt-BR | **Papelzinho** |
| Nome em inglês | **Pass the Note** |
| Conceito | O bilhete dobrado passado de mão em mão na aula: a pessoa lê e joga fora |
| `applicationId` / namespace | `com.andersonmoreira.papelzinho` (único para os dois idiomas) |
| Nome do módulo e do repositório | `papelzinho` |
| Idiomas | pt-BR e en |

Regras de nome:

- O nome exibido vem de `app_name`: `Papelzinho` em `values-pt-rBR/strings.xml` e `Pass the Note` em `values/strings.xml` (padrão inglês). Não há nome fixo no código.
- Todo texto visível ao usuário fica em `strings.xml`, nos dois idiomas. Nenhuma string de UI no código Kotlin.
- O nome do app não pode citar "WhatsApp" nem usar a marca ou o ícone dele.
- Identificadores técnicos (pacote, tags de log, nomes de classe) usam `papelzinho` / `Papelzinho`.

## 1. Objetivo

App Android pessoal que faz o fluxo inteiro, sem passos manuais além de escrever o texto e escolher o destino:

1. O usuário escreve (ou compartilha) um texto.
2. O app renderiza o texto em uma imagem PNG **somente em memória**.
3. O app abre o WhatsApp com a imagem na tela de prévia de envio, servindo os bytes por um `ContentProvider` próprio.
4. Um `AccessibilityService` ativa a opção **visualização única** e só então toca em **Enviar**.
5. Ao fim da sessão, os bytes são descartados e a URI deixa de responder.

### Invariantes (não negociáveis)

- **I1.** O app **nunca envia** uma imagem sem ter confirmado que a visualização única está ativa. Na dúvida, aborta.
- **I2.** O app **nunca grava** a imagem nem o texto em disco: nenhum arquivo em `filesDir`, `cacheDir`, armazenamento externo, `DataStore` ou banco.
- **I3.** Depois que a sessão termina, a URI da imagem **não pode mais ser lida** por ninguém.
- **I4.** Sem sessão armada, o serviço de acessibilidade **não faz nada**.
- **I5.** O APK **não declara** a permissão `INTERNET`.

Cada invariante tem pelo menos um teste automatizado (seção 8).

## 2. Por que AccessibilityService

O WhatsApp não expõe a visualização única por Intent nem por API pública. O menu de compartilhamento do Android entrega apenas o conteúdo. A única forma de ativar a opção automaticamente é interagir com a UI do WhatsApp via `AccessibilityService`.

Consequências assumidas:

- **Fragilidade:** os seletores de UI podem quebrar a cada atualização do WhatsApp. Eles ficam isolados, versionados e cobertos por testes com fixtures (seção 8).
- **Distribuição:** uso pessoal, instalação via APK (sideload). As políticas da Play Store restringem `AccessibilityService` para fins que não sejam acessibilidade.
- **Termos do WhatsApp:** automatizar o cliente pode violar os termos de uso. Em uso pessoal e com volume baixo o risco é pequeno, mas existe.

## 3. Escopo da garantia de memória

**Garantido pelo app:**
- A imagem existe apenas na RAM do processo do app (`Bitmap`, `ByteArray` e um `memfd` anônimo criado sob demanda).
- Ao fim da sessão, o `Bitmap` é reciclado, o `ByteArray` é zerado e a URI passa a responder com `FileNotFoundException`.

**Melhor esforço (limitação da JVM/ART):**
- Zerar o `ByteArray` não garante que não existam cópias transitórias feitas pelo runtime (buffers internos do `Bitmap.compress`, cópias do GC). Essas cópias ficam só em RAM e são liberadas com o tempo.

**Fora do controle do app:**
- O WhatsApp do remetente lê os bytes e faz a própria cópia para criptografar e enviar. O tempo de vida dessa cópia é decisão do WhatsApp.

## 4. Stack

- Kotlin 2.x, Android nativo
- Jetpack Compose (Material 3) para a UI
- **`minSdk 30`** (necessário para `android.system.Os.memfd_create`); `targetSdk` e `compileSdk` na versão estável mais recente
- Gradle Kotlin DSL com version catalog (`libs.versions.toml`)
- Sem framework de DI (injeção manual via construtor)
- Testes: JUnit 5 (unit), Robolectric quando necessário, instrumentados com AndroidX Test/UiAutomator
- Lint: ktlint + detekt

### Manifesto

Permissões (lista fechada):
- `POST_NOTIFICATIONS`
- `BIND_ACCESSIBILITY_SERVICE` (declarada no service)
- **Nenhuma** permissão `INTERNET` (I5)

Outros elementos:
- `<queries>` para os pacotes `com.whatsapp` e `com.whatsapp.w4b`
- `MemoryImageProvider` com `android:exported="false"` e `android:grantUriPermissions="true"`, authority `${applicationId}.images`
- **Sem** `FileProvider` e sem `res/xml/file_paths.xml`
- `android:allowBackup="false"` e `dataExtractionRules` vazias, para nada do app ir para backup

## 5. Fluxo

### 5.1 Entradas

- **Tela principal:** campo de texto multilinha + botão "Enviar ver uma vez".
- **Share target:** o app aparece no menu de compartilhamento para `text/plain` e abre a tela principal com o texto preenchido.
- **Quick Settings Tile (fase 2):** abre a tela principal direto no campo de texto.

### 5.2 Sequência

```
[Usuário confirma texto]
      │
      ▼
TextImageRenderer → Bitmap → PNG em ByteArray (ByteArrayOutputStream)
Bitmap da renderização é reciclado (a prévia da UI usa uma miniatura separada)
      │
      ▼
SessionStore arma a sessão: token, bytes, pacote alvo, timestamps
      │
      ▼
Intent ACTION_SEND (image/png)
  EXTRA_STREAM = content://<appId>.images/<token>.png
  ClipData com a mesma URI + FLAG_GRANT_READ_URI_PERMISSION
  setPackage(targetPackage)
      │
      ▼
Usuário escolhe a conversa no WhatsApp
      │
      ▼
WhatsApp abre a prévia → lê a URI → MemoryImageProvider serve um memfd novo a cada abertura
      │
      ▼
AccessibilityService (só com sessão armada):
  detecta prévia → trata diálogo de primeira vez → ativa toggle
  → verifica estado → toca em Enviar → aguarda saída da prévia
      │
      ▼
SessionStore.release(): zera bytes, revoga URI, consome a sessão
Notificação de resultado
```

### 5.3 Escolha de destino

- **MVP:** o usuário escolhe a conversa no seletor do próprio WhatsApp. O serviço só atua na tela de prévia.
- **Fase 2:** favoritos salvos no app (nome + número E.164) usando o extra não oficial `jid` (`<numero>@s.whatsapp.net`). Fica atrás de uma flag, com fallback para o fluxo do MVP. Favoritos são o único dado persistido pelo app, em `DataStore`, e não incluem textos nem imagens.

## 6. Componentes

### 6.1 `TextImageRenderer`

Função pura: `render(text: String, style: RenderStyle): Result<ByteArray>`.

- Desenha com `StaticLayout` sobre um `Canvas` de um `Bitmap` `ARGB_8888`.
- Tamanho padrão 1080×1350. Opção 1080×1920.
- Ajusta o tamanho da fonte por busca binária entre `minSp` e `maxSp` até o texto caber na área útil.
- Se não couber nem no `minSp`, retorna o erro tipado `TextTooLong`. Nunca corta o texto.
- Preserva as quebras de linha do usuário e suporta emoji pela fonte do sistema.
- Comprime para PNG em `ByteArrayOutputStream`, chama `bitmap.recycle()` em `finally` e retorna `toByteArray()`.
- `RenderStyle`: tema (3 a 4 paletas fixas), alinhamento, fonte (do sistema ou 1 a 2 empacotadas em `res/font`).
- Para a prévia na UI, existe uma função separada, `renderPreview(text, style, maxWidthPx)`, que devolve um `Bitmap` pequeno. Esse bitmap nunca é enviado.

### 6.2 `SessionStore`

Fonte única do estado do envio. Vive em memória, no processo do app. Nada é persistido.

```kotlin
class SendSession(
    val token: String,            // UUID aleatório; também é o path da URI
    val targetPackage: String,    // com.whatsapp ou com.whatsapp.w4b
    val createdAt: Long,          // SystemClock.elapsedRealtime()
    private var bytes: ByteArray?,
    var state: State              // ARMED, IN_PROGRESS, SENT, DONE, ABORTED, EXPIRED
)
```

Regras:

- Uma sessão ativa por vez. Armar uma nova enquanto outra existe libera a anterior como `ABORTED`.
- **Validade:** 120 s em `ARMED` (tempo para escolher a conversa). Estourou → `EXPIRED` → `release()`.
- **Consumo único:** depois de `DONE`, `ABORTED` ou `EXPIRED`, a sessão nunca volta a ser usada.
- `bytesFor(token)` devolve os bytes apenas se o token corresponder à sessão ativa e o estado for `ARMED`, `IN_PROGRESS` ou `SENT`.
- `release()`:
  1. `bytes?.fill(0)` e `bytes = null`
  2. `context.revokeUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`
  3. muda o estado para o estado terminal
  4. é idempotente
- Se o processo morrer, a sessão some junto. O efeito é um envio que não acontece, coerente com I1.

### 6.3 `MemoryImageProvider`

`ContentProvider` que serve os bytes da sessão sem tocar no sistema de arquivos.

- `openFile(uri, mode)`:
  - aceita somente o modo `"r"`; qualquer outro → `SecurityException`
  - extrai o token do path e pede `sessionStore.bytesFor(token)`; se vier `null` → `FileNotFoundException`
  - cria um descritor anônimo com `Os.memfd_create("papelzinho", OsConstants.MFD_CLOEXEC)`
  - escreve os bytes, faz `Os.lseek(fd, 0, SEEK_SET)`
  - devolve `ParcelFileDescriptor.dup(fd)` e fecha o descritor original
  - cria um `memfd` **novo a cada chamada**, porque o WhatsApp pode abrir a URI mais de uma vez (prévia e envio). Cada `memfd` é liberado pelo kernel quando o último descritor fecha.
- `query(uri, ...)`: devolve um `MatrixCursor` com `OpenableColumns.DISPLAY_NAME` (`<token>.png`) e `OpenableColumns.SIZE` (tamanho dos bytes), se a sessão for válida; caso contrário, um cursor vazio.
- `getType(uri)`: `image/png`.
- `insert`, `update` e `delete` lançam `UnsupportedOperationException`.

Exemplo de referência:

```kotlin
override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    if (mode != "r") throw SecurityException("read-only")
    val token = uri.lastPathSegment?.removeSuffix(".png") ?: throw FileNotFoundException()
    val bytes = sessionStore.bytesFor(token) ?: throw FileNotFoundException()
    val fd = Os.memfd_create("papelzinho", OsConstants.MFD_CLOEXEC)
    try {
        FileOutputStream(fd).write(bytes)   // não fechar o stream: fecharia o fd
        Os.lseek(fd, 0, OsConstants.SEEK_SET)
        return ParcelFileDescriptor.dup(fd)
    } finally {
        Os.close(fd)
    }
}
```

### 6.4 `WhatsAppTargetResolver`

- Verifica quais pacotes estão instalados (`com.whatsapp`, `com.whatsapp.w4b`).
- Se os dois estiverem, o usuário escolhe, e a escolha fica salva em `DataStore`.
- Se nenhum estiver, mostra erro na tela principal.

### 6.5 `ViewOnceAccessibilityService`

Configuração (`res/xml/accessibility_service_config.xml`):

- `packageNames="com.whatsapp,com.whatsapp.w4b"`
- `accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"`
- `canRetrieveWindowContent="true"`
- `notificationTimeout` baixo (ex.: 100 ms)
- Descrição em `strings.xml` explicando o que o serviço faz e que ele só age em sessões iniciadas pelo app.

Máquina de estados (uma por sessão):

| Estado | Ação | Próximo |
|---|---|---|
| `WAITING_PREVIEW` | Procura a assinatura da tela de prévia; ao achar, a sessão vai para `IN_PROGRESS` | `PREVIEW_FOUND` |
| `PREVIEW_FOUND` | Procura o toggle de visualização única | `TOGGLE_FOUND` ou `ABORT` (timeout 5 s) |
| `TOGGLE_FOUND` | Clica no toggle | `HANDLE_FIRST_TIME_DIALOG` |
| `HANDLE_FIRST_TIME_DIALOG` | Se o diálogo explicativo conhecido aparecer, toca no botão de confirmação; diálogo desconhecido → aborta | `VERIFY_TOGGLE` |
| `VERIFY_TOGGLE` | Confirma que o toggle está ativo (seção 6.6) | `SEND` ou `ABORT` |
| `SEND` | Clica em Enviar; a sessão vai para `SENT` | `AWAIT_HANDOFF` |
| `AWAIT_HANDOFF` | Espera a prévia sumir (a assinatura da tela de prévia deixa de casar) | `DONE` (timeout 8 s também leva a `DONE`) |

**Momento da liberação.** Os bytes só podem ser liberados em `DONE` ou `ABORT`, nunca no instante do clique em Enviar. O WhatsApp pode ler a URI de forma assíncrona logo depois do clique, e liberar cedo demais faria o envio falhar. O timeout de `AWAIT_HANDOFF` (8 s) é um teto e precisa ser validado em teste manual (seção 8.3).

Regras gerais:

- Ignora eventos sem sessão em `ARMED`, `IN_PROGRESS` ou `SENT` (I4).
- Ignora eventos de pacote diferente de `targetPackage`.
- Cliques usam `performAction(ACTION_CLICK)` no nó, ou no primeiro ancestral clicável. Nunca gestos por coordenada.
- Timeout global de 10 s entre `PREVIEW_FOUND` e `SEND`. Estourou → `ABORT`.
- Em `ABORT`: não clica em mais nada, chama `SessionStore.release()` e notifica com `R.string.notify_abort` ("Não consegui ativar a visualização única. A imagem não foi enviada." / "Couldn't turn on view once. The image was not sent.") O usuário fica na prévia, e como os bytes já foram liberados, a imagem pode não aparecer mais ali. Isso é intencional.
- Logs estruturados (tag `Papelzinho`) com estado, seletor que casou e tempos. Nunca registrar o texto nem os bytes.

### 6.6 Seletores (`selectors/`)

Ficam em um único arquivo de dados versionado:

```kotlin
data class NodeSelector(
    val resourceIds: List<String> = emptyList(),
    val contentDescriptions: List<Regex> = emptyList(), // pt-BR e en
    val texts: List<Regex> = emptyList(),
    val className: String? = null
)

data class SelectorSet(
    val version: String,
    val previewScreen: NodeSelector,
    val viewOnceToggle: NodeSelector,
    val viewOnceToggleActive: NodeSelector,
    val firstTimeDialogConfirm: NodeSelector,
    val sendButton: NodeSelector
)
```

Ordem de casamento: `resourceIds`, depois `contentDescriptions`, depois `texts`. O log registra qual estratégia casou.

Verificação do estado ativo, em ordem de preferência:

1. `isChecked == true` no nó do toggle, se ele for `isCheckable`.
2. Mudança de `contentDescription` ou `stateDescription` para um valor que case com `viewOnceToggleActive`.
3. Aparição de um indicador conhecido de modo ativo na tela.

Se nenhuma confirmar, é `ABORT`. Ter clicado no toggle não basta.

**Para o agente:** não invente resource-ids nem textos do WhatsApp. O `SelectorSet` inicial nasce com `TODO`, e os valores reais vêm dos dumps da seção 8.1.

### 6.7 Onboarding e status

- Checklist: WhatsApp instalado, serviço de acessibilidade ativo (botão abre `Settings.ACTION_ACCESSIBILITY_SETTINGS`), notificações permitidas.
- Botão "Testar com imagem de exemplo": arma uma sessão com uma imagem fixa com o texto de `R.string.test_image_text` ("Teste do Papelzinho" / "Pass the Note test"), também gerada em memória.
- Mostra a versão do WhatsApp instalada e a do `SelectorSet`. Se o WhatsApp mudou de versão desde o último envio bem-sucedido, avisa com `R.string.whatsapp_updated_warning` ("WhatsApp atualizado; faça um envio de teste." / "WhatsApp was updated; send a test first."). A última versão bem-sucedida é salva em `DataStore`; é só um número de versão.

## 7. UI

- Tela principal: campo de texto, prévia ao vivo (miniatura de `renderPreview`), seletor de tema, botão primário.
- Contador de caracteres e aviso quando o renderer retornar `TextTooLong`.
- O texto digitado não é salvo: `rememberSaveable` não deve ser usado para ele, para não ir parar no `Bundle` de estado salvo. Ele vive só no `ViewModel`.
- Configurações: tema padrão, tamanho da imagem, conta alvo e, na fase 2, favoritos.
- Modo escuro; strings em `values` (inglês, usado como fallback para qualquer idioma sem tradução) e `values-pt-rBR`.
- `FLAG_SECURE` na tela principal, para o texto não aparecer nas miniaturas de apps recentes nem em prints.

## 8. Testes

### 8.1 Captura de fixtures

Script `tools/capture_ui.sh`:

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml "fixtures/$1.xml" && adb shell rm /sdcard/ui.xml
```

Capturas necessárias, para `com.whatsapp` e, se usado, `com.whatsapp.w4b`:

- `preview_default.xml`: prévia de imagem com o toggle desligado
- `preview_viewonce_on.xml`: mesma tela com o toggle ligado
- `viewonce_first_time_dialog.xml`: diálogo explicativo da primeira vez, se disponível
- `chat_after_send.xml`: conversa logo após o envio, usada em `AWAIT_HANDOFF`
- `chat_list.xml`: seleção de conversa, para garantir que o serviço não age ali

Registrar em `fixtures/README.md` a versão do WhatsApp e o idioma do aparelho de cada captura.

### 8.2 Testes automatizados

**Renderer**
- Dimensões, auto-fit, `TextTooLong`, emoji sem quebrar o layout.
- Depois de `render`, o `Bitmap` interno está reciclado.

**SessionStore**
- Expiração em 120 s, consumo único, nova sessão aborta a anterior.
- Após `release()`, o `ByteArray` original está todo zerado e `bytesFor(token)` devolve `null` (I3).
- `release()` é idempotente.

**MemoryImageProvider** (instrumentado, API 30+)
- Leitura pela URI devolve exatamente os bytes da sessão.
- Duas leituras seguidas funcionam, e o descritor permite `seek`.
- Modo diferente de `"r"` lança `SecurityException`.
- Token desconhecido ou sessão encerrada → `FileNotFoundException` (I3).
- `query` devolve `SIZE` e `DISPLAY_NAME` corretos.

**Sem disco (I2)**
- Teste instrumentado que roda um envio completo com o WhatsApp simulado e, antes e depois, compara a listagem recursiva de `filesDir`, `cacheDir`, `externalCacheDir` e `getExternalFilesDir(null)`. Nenhum arquivo novo pode aparecer, exceto o arquivo do `DataStore` de configurações.

**Idiomas**
- Lint `MissingTranslation` e `ExtraTranslation` configurados como erro: toda chave precisa existir em `values` e `values-pt-rBR`.
- Teste que resolve `app_name` com locale `pt-BR` (espera `Papelzinho`) e `en-US` (espera `Pass the Note`).

**Manifesto (I5)**
- Teste que lê o manifesto mesclado (ou o APK via `aapt2 dump permissions`) e falha se `android.permission.INTERNET` aparecer.

**Seletores e máquina de estados**
- Parse das fixtures XML e casamento de cada seletor; `viewOnceToggleActive` só casa em `preview_viewonce_on.xml`.
- Máquina de estados com uma árvore de nós falsa: caminho feliz, toggle não encontrado, verificação falha, diálogo desconhecido, timeout. Em todos os caminhos de falha, o `sendButton` nunca recebe clique (I1).
- Sem sessão armada, nenhum evento gera clique (I4).
- `release()` só é chamado em `DONE` ou `ABORT`, nunca durante `SEND`.

### 8.3 Teste manual (checklist em `docs/manual-test.md`)

- Envio para conversa individual e para grupo.
- Aparelho em pt-BR e em en.
- WhatsApp e WhatsApp Business.
- Abrir uma prévia de mídia no WhatsApp sem sessão armada: o serviço não faz nada.
- Deixar a sessão expirar no seletor de conversa: nada é enviado.
- Matar o processo do app (`adb shell am kill`) durante a escolha da conversa: nada é enviado e o WhatsApp mostra erro ou prévia vazia.
- Calibrar `AWAIT_HANDOFF`: com rede lenta (modo avião ligado logo após o clique), confirmar que o envio sai quando a rede volta, ou seja, que o WhatsApp já tinha copiado os bytes antes da liberação.

## 9. Fases

**Fase 1 — MVP**
Renderer, `SessionStore`, `MemoryImageProvider`, Intent para o WhatsApp, serviço com máquina de estados, onboarding, notificação de falha e os testes das seções 8.1 e 8.2.

Critérios de aceite:
- Do toque em "Enviar ver uma vez" até a mensagem enviada, a única ação manual é escolher a conversa.
- A mensagem recebida aparece como foto de visualização única.
- As cinco invariantes (I1 a I5) têm testes passando.

**Fase 2**
Favoritos com `jid`, Quick Settings Tile, mais temas e uma tela de diagnóstico mostrando qual seletor casou no último envio e quanto tempo durou cada estado.

**Fase 3 (opcional)**
Perfis de trabalho e apps clonados, se necessário.

## 10. Instruções para o agente (Claude Code)

- Comece pela estrutura do projeto, `TextImageRenderer`, `SessionStore`, `MemoryImageProvider` e seus testes. Nada disso depende do WhatsApp.
- Não use `FileProvider`, `File`, `createTempFile`, `openFileOutput` nem nada que grave a imagem ou o texto em disco. Se alguma API exigir arquivo, pare e me pergunte.
- Não chute resource-ids nem textos da UI do WhatsApp. Gere o `SelectorSet` com `TODO` e me peça os dumps da seção 8.1.
- Qualquer mudança na máquina de estados precisa manter passando os testes de I1, I3 e I4.
- Não adicione dependências de rede, analytics nem crash reporting.
- Mantenha os seletores em um único arquivo, para que uma atualização do WhatsApp exija mudar só ele e as fixtures.

## 11. Fora de escopo

- Envio de vídeo ou áudio.
- Publicação na Play Store.
- Qualquer automação fora da tela de prévia de mídia (ler mensagens, navegar em conversas etc.).
- Controlar a cópia que o WhatsApp faz da imagem (seção 3).
- Substituir o recurso nativo de texto em visualização única, caso o WhatsApp o lance; nesse caso, reavaliar o projeto.
