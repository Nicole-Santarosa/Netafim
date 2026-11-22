# Upload de evidências

> **Status:** fluxo proposto v0.1. O endpoint de upload ainda precisa ser implementado e o mecanismo de transferência depende de decisão de infraestrutura.

## 1. Objetivo

Documentar o envio de evidências do aplicativo Android ao servidor, separando os metadados do arquivo binário. Essa separação permite que o cadastro da evidência seja sincronizado mesmo quando o arquivo ainda não conseguiu ser transferido.

## 2. Fluxo geral

1. O app registra os metadados da evidência localmente.
2. O app envia os metadados pelo PUSH como entidade `evidencia`.
3. O servidor confirma o cadastro e retorna os dados do registro.
4. Depois do cadastro dos metadados, o app envia o binário.
5. O servidor valida tamanho e hash SHA-256.
6. Se válido, armazena o arquivo no Object Storage e atualiza o estado do upload.
7. Em caso de falha, o app incrementa o contador de tentativas e agenda novo envio.

## 3. Metadados

O contrato lista os seguintes campos de metadados:

- `etapa`;
- `tipo_arquivo`;
- `mime_type`;
- `tamanho_bytes`;
- `hash_arquivo` (SHA-256);
- `data_captura`;
- `origem_captura`;
- latitude e longitude, quando aplicáveis;
- `status_upload`.

No envio inicial, o `status_upload` proposto é `pendente`.

O banco também possui campos relacionados ao acompanhamento do envio, como `tentativas_upload`.

## 4. Endpoint proposto para o binário

Após o PUSH dos metadados:

```http
PUT /api/v1/evidencias/{id}/arquivo
```

O corpo da requisição contém os bytes do arquivo. O servidor deverá:

1. localizar a evidência pelo identificador;
2. conferir o tamanho recebido com `tamanho_bytes`;
3. calcular o SHA-256 e comparar com `hash_arquivo`;
4. rejeitar o arquivo se houver divergência;
5. armazenar o arquivo no Object Storage se a validação for aprovada;
6. atualizar `status_upload` para indicar envio concluído;
7. manter `varredura_status = pendente`, conforme o fluxo descrito no contrato.

O nome definitivo dos estados e a resposta detalhada do endpoint precisam ser fechados na especificação da API.

## 5. Reenvio e idempotência

Em caso de falha, o app incrementa `tentativas_upload` e pode reenviar o arquivo. O contrato propõe que o reenvio seja seguro: mesmo identificador e mesmo hash não devem gerar duplicação do conteúdo armazenado.

A regra de idempotência precisa ser implementada no backend e validada com testes, inclusive para o cenário em que o servidor armazenou o arquivo, mas a resposta se perdeu.

## 6. Tipos de arquivo

O contrato estabelece as seguintes restrições:

- fotos e vídeos devem ser capturados pela câmera do aplicativo;
- documentos anexados devem ser PDF;
- o banco possui CHECKs que impõem restrições de tipo, e o app deve validar antes do envio.

Não se deve ampliar os formatos aceitos sem revisar as regras do banco e aprovar a alteração do contrato.

## 7. Armazenamento

O binário será gravado no Object Storage, separado do banco relacional. A decisão entre transferência por proxy da API e URL pré-assinada permanece pendente e depende da definição de infraestrutura.

O banco mantém metadados e referência ao arquivo; o conteúdo binário não deve ser tratado como parte do payload JSON do PUSH.

## 8. Falhas e recuperação

O app deve manter evidências pendentes até receber confirmação. Falhas temporárias podem ser reenviadas; falhas de validação (por exemplo, tamanho ou hash divergente) exigem correção ou descarte, não repetição cega.

A política de tentativas, backoff, tamanho máximo, timeout, antivírus/varredura e limites de armazenamento não está completamente definida no contrato v0.1.

## 9. Pendências

- escolher proxy da API ou URL pré-assinada;
- definir limites de tamanho e timeouts;
- definir resposta e estados finais do upload;
- definir política de varredura/antivírus;
- implementar e testar o endpoint.

## 10. Referência

Baseado no **Contrato de Sincronização — proposta v0.1, 02/11/2026**. O endpoint e parte das regras descritas são propostas, não funcionalidades confirmadas como implementadas.
