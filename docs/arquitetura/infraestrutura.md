# Infraestrutura — App Netafim

> Consolidação das decisões documentadas. A topologia final de implantação ainda depende de validação.

## 1. Decisões registradas

| Camada | Decisão | Observação |
|---|---|---|
| Hospedagem inicial | VPS | Definida no Documento de Requisitos |
| Execução | Docker | Serviços em containers |
| Banco central | PostgreSQL no Supabase | MER/DER indica PostgreSQL gerenciado |
| API | Django REST Framework / Python | Localização física a definir |
| Arquivos | Object Storage privado | Separado do banco |
| Aplicativo | Android | Primeira versão |
| Banco local | Room + SQLite | Operação offline |

**Importante:** VPS e Supabase não devem ser interpretados como alternativas excludentes. O Documento de Requisitos define VPS como hospedagem inicial; o MER/DER posterior especifica o banco PostgreSQL gerenciado no Supabase. A localização da API e do Object Storage será definida na implantação.

## 2. Componentes

### VPS
Infraestrutura inicial definida para hospedagem dos serviços. Os documentos não especificam provedor, sistema operacional, CPU, RAM, disco, região ou configuração de rede. Dimensionamento deve ser validado.

### Docker
Docker foi definido para execução dos serviços em containers. A configuração deve documentar imagens, Dockerfiles, Compose, variáveis de ambiente, volumes, redes, portas, health checks, logs e política de atualização. Não versionar segredos.

### PostgreSQL / Supabase
O MER/DER especifica PostgreSQL gerenciado no Supabase. Autenticação e regras de negócio ficam na API Django REST Framework; o projeto não utiliza o serviço de autenticação do Supabase e nenhuma tabela depende dele. RLS deve ser aplicada e testada.

Região, plano, backup, recuperação, monitoramento e credenciais precisam ser confirmados.

### Object Storage
Fotos, vídeos, assinaturas e PDFs ficam em armazenamento privado separado. O banco guarda `storage_key`, hash e metadados, não URL pública permanente.

Provedor, região, proxy ou URL pré-assinada, antivírus, limites e retenção ainda precisam ser definidos. O Contrato de Sincronização v0.1 marca o mecanismo de storage como pendência.

## 3. Ambientes

O MER/DER prevê banco de desenvolvimento e validação automatizada antes da homologação. Na edição consultada, implantação em produção e testes de carga, desempenho, invasão e todos os cenários de concorrência ainda não haviam sido realizados.

| Ambiente | Finalidade | Situação documental |
|---|---|---|
| Desenvolvimento | Implementação e testes | Banco de desenvolvimento previsto |
| Homologação | Validação integrada | Etapa prevista; topologia não detalhada |
| Produção | Operação real | Ainda não validada na edição do MER/DER |

Cada ambiente deve ter configuração e credenciais próprias. Dados de desenvolvimento/homologação devem ser fictícios, conforme pendência do MER/DER.

## 4. Configuração Android

A Documentação Técnica determina URL base por ambiente via `BuildConfig`.

- Emulador: `http://10.0.2.2:8000`.
- HTTP sem TLS somente em `debug`.
- Ambientes reais devem usar TLS.

## 5. Upload de evidências

Fluxo proposto no Contrato v0.1:
1. Android envia metadados.
2. Binário é enviado separadamente.
3. Servidor confere tamanho e SHA-256.
4. Arquivo é gravado no Object Storage.
5. Varredura de segurança é executada.
6. Download só é liberado com `varredura_status = limpo`.

`PUT /api/v1/evidencias/{id}/arquivo` aparece como endpoint novo no contrato; confirmar implementação antes de documentá-lo como disponível.

## 6. Itens operacionais ainda não definidos

- Provedor, plano, região e dimensionamento da VPS.
- Região e plano do Supabase.
- Provedor e região do Object Storage.
- Backup, retenção e testes de restauração.
- Monitoramento, alertas e logs.
- Atualização, rollback e recuperação de desastre.
- Gestão de segredos e rotação de credenciais.
- TLS, domínio, DNS, proxy reverso e firewall.
- Política de retenção e descarte de arquivos.

## 7. Checklist de implantação

- [ ] VPS provisionada e homologada.
- [ ] Docker/Compose configurados.
- [ ] API implantada com variáveis seguras.
- [ ] PostgreSQL/Supabase acessível apenas por serviços autorizados.
- [ ] RLS habilitada e testada.
- [ ] Object Storage privado configurado.
- [ ] Upload/download autorizados testados.
- [ ] Hash, tamanho e antivírus testados.
- [ ] TLS e domínio configurados.
- [ ] Backups e restauração testados.
- [ ] Logs e monitoramento configurados.
- [ ] Homologação concluída e rollback documentado.

## 8. Fontes

Documento de Requisitos (5.2 e 8); MER e DER (página 3 e seção 6); Documentação Técnica (37); Contrato de Sincronização v0.1 (7 e 9).
