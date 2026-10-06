# Prompt para Claude Design

Copie o conteúdo abaixo e cole no Claude Design.

```text
Crie o design da interface de um app Android chamado Papelzinho em português brasileiro e Pass the Note em inglês.

O conceito é o bilhete dobrado passado de mão em mão na sala de aula: a pessoa lê uma vez e descarta. Quero uma interface acolhedora, discreta e bem acabada, com personalidade de papelaria contemporânea.

CONTEXTO REAL DO PRODUTO

O app transforma um texto em imagem, mantendo o conteúdo apenas na memória do processo. O fluxo atualmente validado é:

1. A pessoa escreve o bilhete.
2. O app gera a imagem e a copia para a área de transferência.
3. A pessoa abre uma conversa no WhatsApp e cola a imagem manualmente.
4. Na prévia, o serviço de acessibilidade ativa a visualização única, confirma que está ativa e só então envia automaticamente.

Não desenhe um fluxo que prometa abrir a conversa, colar ou selecionar o destinatário automaticamente. Essas etapas ainda são manuais.

A automação nunca pode enviar sem confirmar visualização única ativa. Se houver dúvida, ela aborta.

O WhatsApp e o teclado podem fazer suas próprias cópias. Evite promessas como “não deixa rastros”, “ninguém pode salvar” ou “privacidade absoluta”.

DIREÇÃO VISUAL

- Android nativo, Jetpack Compose e Material 3.
- Layout principal vertical, confortável para uso com uma mão.
- Personalidade própria, evitando aparência genérica de template.
- Fundo em tons de papel, tipografia muito legível e detalhes discretos de bilhete dobrado.
- Cor de destaque em verde oliva ou outra combinação sóbria que você justifique.
- Poucos elementos decorativos; a mensagem escrita deve ser o centro da experiência.
- Modo claro e escuro desenhados com o mesmo cuidado.
- Sem logotipo, ícone ou identidade visual do WhatsApp na marca do app.
- Sem fotos, gradientes chamativos ou ilustrações ocupando espaço útil.
- Evite textura que prejudique a leitura.

TELA PRINCIPAL

Organize a tela com hierarquia clara e pouca carga visual:

- Nome Papelzinho e uma frase curta sobre o propósito.
- Campo de texto multilinha com rótulo visível.
- Contador de caracteres.
- Prévia ao vivo do bilhete como uma miniatura separada.
- Seletor compacto de três temas: Papel, Noite e Sálvia.
- Escolha de formato da imagem: 1080 × 1350 ou 1080 × 1920; apresente nomes compreensíveis, como “Padrão” e “Vertical”.
- Ação principal com um rótulo que comunique que copiar também arma o envio automático após a colagem. Explore “Copiar e preparar envio”.
- Antes da ação, explique de forma breve: “Abra a conversa e cole. Na prévia, o app ativa a visualização única e envia.”
- Deixe claro que isso enviará uma mensagem real após a pessoa colar.
- Configurações e ajuda como ações secundárias.

Não mostre uma galeria de imagens salvas nem histórico de bilhetes: o app não persiste textos ou imagens.

ESTADO APÓS COPIAR

Crie um estado evidente de “Envio preparado”:

- Instrução para abrir uma conversa própria ou autorizada e colar.
- Prazo de 2 minutos, com tempo restante acessível.
- Botão para abrir o WhatsApp, sem prometer abrir uma conversa específica.
- Ação “Cancelar envio”.
- Explicação de que a automação só age na prévia de mídia.
- Evite depender apenas de cor ou animação para comunicar o estado.

ONBOARDING E PRÉ-REQUISITOS

Crie uma introdução curta, com divulgação clara do uso de acessibilidade:

- O serviço encontra o controle de visualização única na prévia e toca em Enviar somente após confirmar que está ativo.
- Só age durante uma sessão iniciada explicitamente no Papelzinho.
- Não é usado para ler mensagens ou navegar em conversas.
- A ativação acontece nas configurações do Android, por decisão da pessoa.
- Permissão de notificações explicada pelo benefício de informar o resultado.

Apresente um checklist:
- WhatsApp instalado.
- Assistência de envio ativa.
- Notificações permitidas.
- Versão e idioma compatíveis.

Inclua estados bloqueados com motivo específico e ação para resolver. Não transforme notificações em requisito obrigatório de envio sem necessidade.

CONFIGURAÇÕES

- Tema padrão do bilhete.
- Formato da imagem.
- Conta alvo: WhatsApp ou WhatsApp Business, quando instalados.
- Versão do app de destino e versão do perfil de envio.
- Aviso quando o WhatsApp mudar de versão: “WhatsApp atualizado; faça um envio de teste.”
- Compatibilidade deve aparecer como um estado real. Business e outros idiomas podem estar indisponíveis enquanto não forem validados.

Não inclua favoritos, contatos salvos ou seleção de destinatário dentro do Papelzinho nesta versão.

ÁREA DE TESTES

Mantenha separada da ação cotidiana:

- “Copiar imagem de exemplo”: diagnóstico, sem envio automático.
- “Copiar exemplo e armar envio”: testa a automação após colagem manual.

A diferença entre as duas ações precisa ser inequívoca. A segunda deve explicar que envia uma mensagem real.

ESTADOS QUE PRECISAM SER DESENHADOS

- Primeiro acesso.
- Texto vazio.
- Texto preenchido com prévia.
- Texto grande demais para caber, sem corte silencioso.
- Geração da imagem.
- Sessão preparada, aguardando colagem.
- Sessão expirada.
- Envio cancelado.
- Serviço de acessibilidade desligado ou desconectado.
- Versão/idioma do WhatsApp incompatível.
- WhatsApp ausente.
- Falha ao confirmar visualização única: “Não consegui ativar a visualização única. A imagem não foi enviada.”
- Conclusão do repasse ao WhatsApp.

Não apresente “Entregue” ou “Lido”: o app só confirma a conclusão do fluxo local, não o recebimento pelo destinatário.

ACESSIBILIDADE E IMPLEMENTAÇÃO

- Áreas de toque de pelo menos 48 dp.
- Contraste adequado nos dois modos.
- Layout resiliente a fonte ampliada, teclado aberto, telas pequenas e textos longos.
- Rótulos visíveis, foco lógico e mensagens de estado compreensíveis para TalkBack.
- Não depender apenas de ícones, cor ou gestos.
- Todos os textos devem permitir tradução entre pt-BR e inglês.
- Conteúdo digitado não deve aparecer em capturas de tela ou nas miniaturas de apps recentes; considere essa restrição ao desenhar a experiência.

ENTREGÁVEIS

1. Uma direção visual final, com justificativa breve.
2. Telas principais e todos os estados críticos.
3. Protótipo navegável do fluxo, com simulações claramente identificadas.
4. Componentes reutilizáveis e tokens de cores, tipografia, espaçamento e formas.
5. Microcopy em pt-BR e equivalentes em inglês.
6. Especificações úteis para implementar em Jetpack Compose: medidas em dp, tipografia em sp e comportamento dos componentes.

Se a ferramenta gerar código web para o protótipo, use-o apenas como demonstração do design. Não apresente a integração com WhatsApp, a área de transferência ou a acessibilidade como funcionalidades realmente executadas pelo protótipo.

Priorize uma interface simples, honesta sobre suas etapas manuais e agradável para escrever um bilhete.
```
