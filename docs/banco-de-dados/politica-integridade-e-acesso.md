# Política de integridade e acesso — Netafim

> **Fonte principal:** `MER e DER.pdf`, páginas 19–23 e Anexo B (página 48), complementado pela documentação técnica.
>
> **Objetivo:** registrar as regras de integridade, estados de atendimento, visibilidade por perfil, proteção de relatórios, arquivos e auditoria previstos no modelo de dados.

## 1. Princípios

O modelo prevê que regras essenciais sejam garantidas no PostgreSQL, além das validações feitas pela API Django REST Framework. Assim, uma escrita feita por API, script de manutenção ou painel não deve conseguir violar as regras protegidas por constraints e gatilhos.

A visibilidade dos dados é aplicada por Row Level Security (RLS) no banco e também pela API.

> Este documento descreve o que o MER/DER especifica. A existência efetiva de cada constraint, trigger, policy RLS ou função deve ser confirmada nos scripts SQL aplicados e no ambiente.

## 2. Regras de integridade do banco

| Regra | Tabela(s) | Mecanismo descrito |
|---|---|---|
| Um vínculo organizacional por usuário | USUARIO | CHECK impede `id_cliente` e `id_distribuidor` simultâneos; `tipo_organizacao` deve corresponder ao vínculo |
| Aprovação de cadastro | USUARIO, DOMINIO_EMAIL_CORPORATIVO | CHECK: usuário ativo exige `id_perfil` e `tipo_organizacao`; gatilho exige domínio corporativo ativo para Netafim/Distribuidor |
| Atendimento de cliente final | ATENDIMENTO | CHECK: `origem = cliente_final` exige `id_cliente` |
| Atendimento da Netafim | ATENDIMENTO | CHECK: `origem = netafim` exige `area_setor` de lista permitida |
| Destinação a distribuidor | ATENDIMENTO | CHECK: `destinado_a = distribuidor` exige `id_distribuidor` |
| Propriedade pertence ao cliente | ATENDIMENTO, PROPRIEDADE | FK composta `(id_propriedade, id_cliente)` e CHECK que exige cliente quando há propriedade |
| Protocolo | ATENDIMENTO | Gatilho gera `OS-####`, ignora valor enviado e impede alteração posterior |
| Transições de status | ATENDIMENTO | Gatilho permite apenas transições definidas na máquina de estados |
| Encerramento | ATENDIMENTO, PESQUISA_SATISFACAO | `encerrado` exige data de encerramento e pesquisa respondida; `encerrado_sem_avaliacao` só após vencer prazo |
| Autoria do histórico | HISTORICO_ATENDIMENTO | CHECK condiciona `id_usuario_responsavel` a `origem_evento`; gatilho bloqueia UPDATE/DELETE |
| Um agendamento ativo | AGENDAMENTO | Índice único parcial por `id_atendimento` onde status = ativo |
| Categoria/subcategoria coerentes | ATENDIMENTO_PROBLEMA, PROBLEMA_SUBCATEGORIA | FK composta, índice único para combinação e gatilho de compatibilidade com motivo |
| Referência do envio | ENVIO_COMUNICACAO | CHECK exige exatamente uma FK entre agendamento e PDF, coerente com o tipo |
| Especialista responsável | RELATORIO_ESPECIALISTA | Índice único parcial permite no máximo um responsável; constraint trigger adiado exige pelo menos um na finalização |
| Finalização de relatório | RELATORIO_ATENDIMENTO | Gatilho valida campos exigidos pelo motivo, responsável, evidências e assinaturas |
| Edição após finalização | RELATORIO_ATENDIMENTO, RELATORIO_VERSAO | Somente Administrador Netafim pode editar; versão anterior é copiada para RELATORIO_VERSAO |
| Assinatura de especialista | ASSINATURA, RELATORIO_ESPECIALISTA | CHECK exige usuário para especialista; gatilho valida que é o responsável |
| Integridade da assinatura | ASSINATURA | Banco calcula versão e hash; UNIQUE por atendimento, tipo de assinante e versão |
| Evidências | EVIDENCIA | CHECK de tipo, origem, etapa, tamanho e formato; download só após varredura limpa |
| Respostas únicas | RESPOSTA_SATISFACAO, RESPOSTA_PERGUNTA_FORMULARIO | UNIQUE por pesquisa/pergunta e atendimento/pergunta |
| Uma avaliação por atendimento | PESQUISA_SATISFACAO, AVALIACAO_FORMULARIO | UNIQUE em `id_atendimento` |
| Perguntas configuráveis | PERGUNTA_FORMULARIO, PERGUNTA_SATISFACAO | UNIQUE de código/versão, índice parcial para versão ativa e limite de cinco perguntas de satisfação ativas |
| Sessões e convites | TOKEN_ACESSO | Funções do serviço de login emitem, rotacionam e revogam; token armazenado como hash |
| Auditoria | LOG_AUDITORIA | Banco grava por função de auditoria; cadeia SHA-256 e âncora externa periódica |
| Sincronização | Tabelas com `sync_seq` | Gatilho atribui sequência global e atualiza `data_atualizacao`; exclusão lógica propaga tombstone |
| Domínios fechados | Colunas enumeradas | CHECK em campos com listas fechadas, como status, origem, motivo e tipos |
| Arquivos privados | EVIDENCIA, ASSINATURA, RELATORIO_PDF | Banco guarda `storage_key` e hash, não URL; link é gerado sob demanda |
| Visibilidade | Tabelas de atendimento | RLS conforme perfil, organização, vínculo e autoria |

## 3. Máquina de estados do atendimento

O banco deve recusar transições não previstas. Toda mudança de status gera evento em HISTORICO_ATENDIMENTO.

| Estado atual | Significado | Próximos estados previstos | Evento/condição |
|---|---|---|---|
| `aberto` | Pedido registrado; protocolo e alerta gerados | `destinado`, `cancelado` | Netafim Admin destina |
| `destinado` | Definido se Netafim ou distribuidor atende; técnico alocado quando aplicável | `agendado`, `cancelado` | Registro do agendamento |
| `agendado` | Data marcada e comunicada | `em_atendimento`, `cancelado` | Técnico inicia atendimento |
| `em_atendimento` | Visita em execução, com ou sem internet | `resolvido`, `cancelado` | Relatório finalizado com assinaturas exigidas |
| `resolvido` | Relatório finalizado; aguardando avaliação | `encerrado`, `encerrado_sem_avaliacao` | Resposta da pesquisa ou vencimento do prazo |
| `encerrado` | Ciclo concluído após avaliação | Estado final | Sistema recebe pesquisa respondida |
| `encerrado_sem_avaliacao` | Encerrado automaticamente após prazo sem resposta | Estado final | Processo automático |
| `cancelado` | Interrompido antes da resolução | Estado final | Netafim Admin |

Regras complementares:

- Reagendamento não altera o estado do atendimento: cria novo AGENDAMENTO e evento `reagendado`.
- Edição administrativa de relatório finalizado não reabre atendimento.
- O modelo registra proposta de prazo de sete dias para avaliação sem resposta, mas o prazo depende de confirmação da Netafim.

## 4. Matriz de visibilidade dos atendimentos

A regra é aplicada por RLS e pela API, considerando perfil, organização, vínculo e autoria.

| Perfil | Escopo de atendimentos visíveis |
|---|---|
| Netafim Admin | Todos os atendimentos da plataforma |
| Netafim Usuário | Atendimentos que abriu e os atribuídos a ele |
| Distribuidor Admin | Atendimentos destinados ao seu distribuidor, de clientes da sua região/carteira e aqueles em que seu distribuidor é a origem |
| Distribuidor Usuário | Atendimentos que abriu e os atribuídos a ele |
| Cliente Admin | Todos os atendimentos da sua empresa |
| Cliente Usuário | Somente os atendimentos que abriu |

Observação: o escopo do Distribuidor Usuário foi ajustado no modelo para incluir os atendimentos atribuídos a ele, pois precisa consultar os chamados que deve atender.

## 5. Permissões funcionais

A matriz abaixo reproduz o Anexo B do MER/DER.

| Permissão | Netafim Admin | Netafim Usuário | Distribuidor Admin | Distribuidor Usuário | Cliente Admin | Cliente Usuário |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| Aprovar cadastros | ✔ | – | – | – | – | – |
| Criar usuários | ✔ | – | ✔ | – | ✔ | – |
| Editar usuários | ✔ | – | ✔ | – | ✔ | – |
| Excluir usuários | ✔ | – | ✔ | – | ✔ | – |
| Definir perfil/hierarquia | ✔ | – | – | – | – | – |
| Abrir atendimento | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Vincular técnico ao atendimento | ✔ | – | ✔* | – | – | – |
| Responder atendimento | ✔ | ✔ | ✔ | ✔ | ✔† | ✔† |
| Encerrar atendimento | ✔ | – | – | – | – | – |
| Consultar próprios atendimentos | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Consultar atendimentos da empresa | ✔ | – | ✔ | – | ✔ | – |
| Consultar atendimentos de clientes vinculados | ✔ | – | ✔ | – | – | – |
| Consultar todos os atendimentos da plataforma | ✔ | – | – | – | – | – |
| Dashboard gerencial | ✔ | – | ✔ | – | ✔ | – |
| Destinar atendimento | ✔ | – | – | – | – | – |
| Reclassificar atendimento | ✔ | – | – | – | – | – |
| Editar atendimento finalizado | ✔ | – | – | – | – | – |

\* Distribuidor Admin pode vincular técnicos do próprio distribuidor.

† O alcance da permissão de responder para perfis de cliente está marcado como pendência de confirmação no MER/DER.

A permissão de encerrar atendimento foi ajustada: somente Netafim Admin e processo automático encerram o ciclo.

## 6. Cadastro e autenticação

- Todo novo usuário nasce com situação `pendente`, sem perfil e sem organização.
- O Netafim Admin aprova o usuário e define o vínculo organizacional e o perfil.
- A aprovação é da pessoa (USUARIO), não da organização CLIENTE/DISTRIBUIDOR.
- Usuários Netafim e Distribuidor dependem de domínio corporativo ativo.
- Cliente final passa por validação manual da Netafim.
- Senhas são armazenadas como hash gerenciado pelo Django; não devem ser recuperáveis pela API.
- Tokens são armazenados somente como hash SHA-256.
- Tokens podem ser revogados quando a conta é bloqueada/inativada ou o aparelho é revogado.
- MFA é previsto como opção; segredo não fica na tabela USUARIO.

## 7. Proteção de relatórios e assinaturas

- Relatório nasce em `rascunho`.
- A finalização valida os campos obrigatórios definidos em MOTIVO_CAMPO_RELATORIO.
- A finalização exige especialista responsável, evidências e assinaturas requeridas.
- Depois de finalizado, somente Administrador Netafim pode editar.
- Antes da edição, a versão anterior é preservada em RELATORIO_VERSAO.
- A assinatura preserva a versão e o hash do conteúdo assinado.
- O especialista que assina deve ser o responsável registrado em RELATORIO_ESPECIALISTA.
- O modelo registra a decisão de manter assinaturas anteriores após edição administrativa; a exigência de nova assinatura após edição consta como ponto a confirmar.

## 8. Evidências e armazenamento de arquivos

- Fotos e vídeos devem ser capturados pela câmera do aplicativo, conforme regra do modelo.
- Documento extra só é aceito em PDF e na etapa `anexo_extra`.
- O tamanho previsto é de 1 byte a 1 GiB.
- O banco guarda chave opaca (`storage_key`) e hash, não URL pública.
- O arquivo só deve ser liberado para download quando `varredura_status = limpo`.
- Fotos, vídeos, assinaturas e PDFs ficam em Object Storage privado, separado do banco.
- A origem de captura registrada no banco é uma declaração do aplicativo; o servidor não consegue provar essa origem apenas pelo metadado.

## 9. Auditoria

LOG_AUDITORIA registra eventos como:

- Login bem-sucedido e falho.
- Logout, bloqueio e alteração de senha.
- MFA alterado.
- Cadastro solicitado, aprovado ou recusado.
- Situação, perfil ou permissões alteradas.
- Acesso negado.
- Relatório editado após finalização ou baixado.
- Evidência baixada e dados exportados.
- Dispositivo revogado e limpeza remota.
- Dados anonimizados.
- Solicitação de titular e incidente registrado.
- Configuração alterada.

A trilha é encadeada por SHA-256. Uma âncora externa periódica é necessária para detectar remoção dos eventos mais recentes. A aplicação não deve gravar diretamente na tabela; o banco registra por função própria.

## 10. LGPD e retenção: propostas e decisões pendentes

O MER/DER ressalta que o modelo não substitui validação jurídica. Os itens abaixo ainda dependem de decisão da Netafim, jurídico ou DPO:

- Base legal adequada para cada finalidade.
- Prazo definitivo de retenção e descarte.
- Identificação do encarregado/DPO.
- Política de segurança e homologação corporativa.
- Contratos com provedores e região de hospedagem dos dados.
- Uso de coordenadas GPS e consentimento correspondente.
- Fluxo operacional de acesso e portabilidade.
- Uso exclusivo de dados fictícios em desenvolvimento e homologação.

Prazos de retenção indicados no MER/DER são propostas de trabalho, não prazos aprovados:

| Dados | Prazo proposto | Status |
|---|---|---|
| LOG_AUDITORIA | 5 anos | A validar |
| ATENDIMENTO, relatórios, evidências e assinaturas | 5 anos | A validar |
| SOLICITACAO_TITULAR e INCIDENTE_SEGURANCA | 5 anos após conclusão | A validar |
| TOKEN_ACESSO | 30 dias após expirar/revogar | Proposta de expurgo automático |
| SYNC_CONFLITO | 1 ano após resolução | Proposta de expurgo automático |
| DISPOSITIVO revogado | Enquanto houver auditoria que o referencie | Sem expurgo enquanto houver FK |
| NOTIFICACAO | 90 dias após leitura | Proposta de expurgo automático |

## 11. Pendências registradas no MER/DER

1. Prazo de espera pela avaliação: proposta de sete dias, configurável.
2. Retenção de dados: proposta de cinco anos, a validar.
3. Confirmar uso de prioridade e prazos em dias.
4. Confirmar uso de coordenadas GPS.
5. Confirmar se Programação de Feiras permanece como motivo de solicitação.
6. Confirmar categoria “Avaliação de sistema” em Pós-venda.
7. Confirmar alcance da permissão “Responder atendimento” para perfis de cliente.
8. Confirmar se edição administrativa após assinatura exige nova assinatura.
9. Definir base legal, DPO, política corporativa, localização dos dados e dados de teste.

## 12. Checklist de validação técnica

Antes de considerar esta política implementada, verificar:

- [ ] CHECK constraints do modelo existem nos scripts aplicados.
- [ ] FKs simples e compostas estão criadas.
- [ ] Índices UNIQUE e índices únicos parciais estão criados.
- [ ] Gatilhos de transição de status e finalização estão ativos.
- [ ] RLS está habilitada nas tabelas pertinentes.
- [ ] Policies cobrem os seis perfis e os escopos organizacionais.
- [ ] A API aplica permissões coerentes com a política do banco.
- [ ] Histórico e auditoria não permitem alteração indevida.
- [ ] Arquivos privados não são servidos sem autorização e varredura limpa.
- [ ] Testes automatizados cobrem isolamento entre organizações, estados, relatórios finalizados e concorrência.

> A presença de uma regra neste documento significa que ela está especificada no MER/DER. Não significa, isoladamente, que já foi implementada ou testada no banco em produção.
