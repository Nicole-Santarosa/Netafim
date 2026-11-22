# Segurança e permissões — App Netafim

> Registra controles previstos no MER/DER, Regras de Negócio e Documentação Técnica. A presença de uma regra neste arquivo não prova que ela já esteja implementada.

## 1. Princípios

1. Autenticação e regras de negócio ficam na API Django REST Framework.
2. PostgreSQL aplica RLS além das verificações da API.
3. Visibilidade depende de perfil, organização, vínculo e autoria/atribuição.
4. Cliente final só acessa após aprovação da Netafim.
5. Senhas não são armazenadas em texto claro.
6. Tokens são armazenados como hash e podem ser revogados.
7. Arquivos ficam em Object Storage privado.
8. Operações sensíveis são registradas em auditoria.

## 2. Autenticação

A Documentação Técnica determina o cabeçalho:

```http
Authorization: Bearer <access>
```

O MER/DER prevê `TOKEN_ACESSO` com usuário, dispositivo quando aplicável, tipo (`refresh`, `convite`, `reset_senha`), hash SHA-256, expiração, uso, revogação e referência ao token substituto.

Emissão, rotação e revogação são realizadas por `fn_emitir_token`, `fn_rotacionar_token` e `fn_revogar_token`.

### JWT: limite documental

Os documentos confirmam token Bearer para access e refresh tokens armazenados como hash, mas **não especificam explicitamente JWT** nem algoritmo, claims, issuer, audience ou biblioteca. Portanto, JWT não deve ser tratado como decisão confirmada até aprovação técnica. Ver ADR-005.

### Senhas e sessões

- Senha armazenada como hash gerenciado pelo Django.
- Tokens possuem validade e podem ser revogados.
- Bloqueio/inativação da conta revoga tokens conforme gatilhos previstos.
- Dispositivo pode ser revogado; o modelo prevê solicitação e confirmação de limpeza remota.

## 3. Cadastro e aprovação

- Novo usuário nasce `pendente`, sem perfil e organização oficial.
- Netafim Admin aprova/recusa e define perfil e vínculo.
- Usuários Netafim e Distribuidor dependem de domínio corporativo ativo.
- Cliente final passa por validação manual.
- Organização declarada no autocadastro é apenas sugestão para triagem.
- API não deve confiar em `id_cliente`, `id_distribuidor` ou `id_perfil` enviados no autocadastro.

## 4. Perfis e escopos

| Perfil | Atendimentos visíveis |
|---|---|
| Netafim Admin | Todos da plataforma |
| Netafim Usuário | Os que abriu e os atribuídos a ele |
| Distribuidor Admin | Atendimentos destinados ao seu distribuidor e de clientes da região/carteira, conforme vínculo |
| Distribuidor Usuário | Os que abriu e os atribuídos a ele, conforme ajuste do MER/DER |
| Cliente Admin | Todos da própria empresa |
| Cliente Usuário | Somente os que abriu |

A política detalhada está em `../banco-de-dados/politica-integridade-e-acesso.md` e no Anexo B do MER/DER.

## 5. Autorização e isolamento

A autorização deve considerar perfil, organização, vínculo com cliente/distribuidor, autoria, atribuição, destinação e permissão funcional.

Não basta ocultar dados na interface: API e banco devem impedir consultas e alterações não autorizadas.

### RLS
O MER/DER prevê RLS em tabelas de atendimento, conforme perfil, organização, vínculo e autoria. Policies, funções de contexto e papéis de conexão devem ser conferidos nos scripts SQL. A documentação não prova, isoladamente, que RLS esteja habilitada no ambiente.

### Regras de atenção
- Netafim Admin aprova cadastros e destina/reclassifica atendimentos.
- Distribuidor Admin pode vincular técnicos do próprio distribuidor.
- Somente Administrador Netafim edita relatório finalizado.
- Encerramento é feito pelo Netafim Admin ou processo automático, conforme decisão do MER/DER.
- Alcance de “Responder atendimento” para perfis de cliente continua pendente de confirmação.

## 6. Relatórios, assinaturas e evidências

### Relatórios
- Finalização valida campos obrigatórios, especialista responsável, evidências e assinaturas.
- Depois de finalizado, somente Administrador Netafim pode editar.
- Cada edição preserva versão anterior em `RELATORIO_VERSAO`.

### Assinaturas
- Preservam versão e hash do relatório assinado.
- Especialista que assina deve ser o responsável em `RELATORIO_ESPECIALISTA`.
- Cliente pode assinar sem conta.

### Evidências
- Fotos/vídeos capturados pela câmera do aplicativo.
- Documento extra somente PDF e na etapa `anexo_extra`.
- Download somente após `varredura_status = limpo`.
- Arquivos ficam em armazenamento privado separado.

## 7. Auditoria

`LOG_AUDITORIA` registra operações sensíveis. O modelo prevê gravação pelo banco, cadeia SHA-256, hash anterior e hash do evento. Âncora externa periódica é necessária para detectar remoção dos eventos mais recentes.

Eventos incluem login, falha de autenticação, bloqueio, mudança de senha/perfil, acesso negado, download, exportação, revogação de dispositivo e anonimização. A aplicação não deve alterar diretamente a trilha.

## 8. LGPD e privacidade

O MER/DER prevê registro de consentimento/versão do aviso, solicitações de titulares, incidentes, anonimização e arquivos privados.

Dependem de decisão da Netafim/jurídico/DPO:
- base legal por finalidade;
- retenção e descarte definitivos;
- identificação do DPO;
- política corporativa de segurança;
- região de hospedagem e eventual transferência internacional;
- uso de GPS;
- fluxo de exportação/portabilidade.

Prazos no MER/DER são propostas de trabalho, não prazos jurídicos aprovados.

## 9. Segurança do Android e infraestrutura

O MER/DER deixa a segurança da aplicação, infraestrutura e Android para especificação/validação à parte. Itens a detalhar: criptografia local, armazenamento seguro de tokens, TLS, limites de taxa, gestão de segredos, logs sem dados desnecessários, atualizações e resposta a incidentes.

## 10. Checklist

- [ ] Senhas somente como hash.
- [ ] Tokens armazenados como hash no servidor.
- [ ] Expiração, rotação e revogação testadas.
- [ ] Autocadastro não define vínculo/perfil oficial.
- [ ] Permissões validadas na API.
- [ ] RLS habilitada e testada por perfil/organização.
- [ ] Isolamento entre clientes/distribuidores testado.
- [ ] Relatório finalizado protegido.
- [ ] Assinatura e hash conferidos pelo banco.
- [ ] Arquivos privados e download condicionado à varredura.
- [ ] Auditoria protegida contra alteração.
- [ ] Dados fictícios em desenvolvimento/homologação.
- [ ] Segurança do Android e infraestrutura validada.

## 11. Fontes

MER e DER (seções 5 e 6); Regras de Negócio; Documentação Técnica (38, 42, 43, 45–48); Documento de Requisitos.
