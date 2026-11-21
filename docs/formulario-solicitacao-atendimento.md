# Formulário de solicitação de atendimento: campos e regras de obrigatório

> **Para que serve:** fonte versionada da regra de "obrigatório" do app e da API (quem abre uma solicitação preenche o quê, conforme a origem e o motivo).
> **Origem do conteúdo:** transcrição da estrutura do formulário original da Netafim ("Estruturação — Formulário de Solicitação de Atendimento", pasta de documentação do projeto; o arquivo não traz data). O arquivo original **não** é versionado aqui: é um documento interno da Netafim e o repositório é público. Aqui ficam só os nomes dos campos, as opções e se são obrigatórios (`*`).
> **Estado (21/11/2026):** as seções 1 a 3 transcrevem o formulário original; **a regra vigente na API está na seção 4**, que segue o serializer do PR #3 (`OBRIGATORIOS_POR_ORIGEM`) e as decisões de 18/11. A conferir com a Netafim a cada mudança do formulário original. Esta tabela descreve a **solicitação** (a abertura do atendimento); o relatório de resolução (preenchido pelo especialista) é outro documento.

## 1. Campos comuns a todas as origens

| Campo | Obrigatório | Observação |
|---|---|---|
| Nome | não marcado no original | |
| E-mail | não marcado no original | |
| Telefone | não marcado no original | |
| Origem/Solicitante | sim (define o bloco seguinte) | Cliente Final, Distribuidor ou Netafim |

## 2. Bloco por origem

Depois da origem, o formulário pede os campos 5 a 9. **Cultura** e **Motivo do Atendimento** são iguais nas três origens.

| Origem | Campos obrigatórios (`*`) |
|---|---|
| **Cliente Final** | Nome do Distribuidor da sua região (**opcional na API**, ver seção 4), Cidade\*, Nome da Fazenda\*, Cultura\*, Motivo do Atendimento\* |
| **Distribuidor** | Nome do Distribuidor (**opcional na API**, ver seção 4), Nome do Produtor\*, Cidade\*, Cultura\*, Motivo do Atendimento\* |
| **Netafim** | Área/Setor\*, Nome do Produtor\*, Cidade\*, Cultura\*, Motivo do Atendimento\* |

- **Tipo de Venda** (Projeto ou Venda Regular): na API é **obrigatório em todo atendimento**, em todas as origens e motivos. No formulário original ele aparece dentro do Digital Farming, mas **não é só do Digital Farming**.
- **Área/Setor** (só Netafim): Comercial, Engenharia, Instalação, Marketing, Pós Venda.
- **Cultura**: Cacau, Café, Cana-de-açúcar, Citrus, Grãos, Outros.
- **Motivo do Atendimento** (na solicitação são **5**): Digital Farming, Dúvidas, Entrega Técnica, Pós-venda, Treinamento.
- "Programação de Feiras" **não** é motivo de solicitação. Ele existe só no **relatório de resolução** (que lista 6 motivos), com campos próprios (conclusão sobre a visita, fotos/vídeos e assinatura do especialista).

## 3. Campos por motivo

Todos terminam com a pergunta "Formulário atendeu suas expectativas?" (nota de 1 a 5 estrelas, obrigatória).

### Digital Farming
Situação do Projeto\* (Fora da Garantia, Garantia, Instalação) · Tipo de Venda\* (Projeto, Venda Regular) · Número do Projeto (BR) · Estado do Projeto (Operando, Não operando) · Onde ocorreu o problema\* (Controlador, Estação Meteorológica, GS One, Plataforma, RadioNet/NETRTU, cada um com seus problemas típicos) · Data do Início do Problema\* · Descritivo do Problema\* · Enviar fotos e/ou vídeos\*.

### Dúvidas
Descrição da Dúvida\* · Enviar fotos e/ou vídeos\*.

### Entrega Técnica
Situação do Projeto\* (Fora de Garantia, Garantia, Instalação) · Nº Projeto (BR)\* · Estado do Projeto\* (Operando, Não Operando) · Data Sugerida\* · Descreva brevemente a necessidade\*.

### Pós-venda
Situação do Projeto\* (Fora de Garantia, Garantia, Instalação) · Nº Projeto (BR)\* · Estado do Projeto\* (Operando, Não Operando) · Localidade do Problema\* · Data do Início do Problema\* · Descritivo do Problema\* · Enviar fotos e/ou vídeos\*.

- **Localidade do Problema:** Automação, Bombeamento, Cavalete, Emissores, Fertirrigação, Filtragem, Tubulação (cada uma com uma lista de problemas típicos no formulário original) e três opções sem detalhe: Chave de partida, Avaliação de sistema, Cronograma de Garantia.

### Treinamento
Tipo de Treinamento\* (Agronômico, Digital Farming, Manutenção Preventiva, Operação/Manutenção, Padronização de Instalação) · Data Sugerida\* · Descreva brevemente a necessidade\* · Número de participantes\*.

## 4. Regra vigente na API (PR #3)

A validação **está implementada** no serializer de abertura (`AberturaAtendimentoSerializer`, constante `OBRIGATORIOS_POR_ORIGEM`). Vale só na API: os `CHECK` do banco não mudaram (o banco ainda aceita, por exemplo, atendimento sem cidade). Ausente, nulo, vazio e "só espaços" contam igual e voltam como 400 no campo.

| Origem | Obrigatórios na API |
|---|---|
| **Cliente Final** | `id_cliente`, `cidade`, `nome_fazenda_informado` |
| **Distribuidor** | `nome_produtor`, `cidade` |
| **Netafim** | `area_setor`, `nome_produtor`, `cidade` |

Valem em **todas as origens**:
- **Cultura**: ao menos uma (lista não vazia).
- **Motivo**: um dos **5** motivos de abertura (seção 2). `programacao_feiras` não é motivo de abertura.
- **Tipo de Venda** (`projeto` ou `venda_regular`): obrigatório sempre, não só no Digital Farming.
- **Distribuidor de origem**: **opcional** em todas as origens. Para o Cliente Final isso é decisão de 18/11: o nome do distribuidor deixou de ser obrigatório e, sem ele, o atendimento fica `aberto` até o **Netafim Admin** destinar (é a única transição `aberto → destinado`). Quando houver distribuidor, a escolha pode usar `GET /api/v1/distribuidores/opcoes` (distribuidores ativos, exige login).

Continuam valendo:
- **Distribuidor e Netafim:** sem "Nome da Fazenda".
- O motivo define os campos seguintes (seção 3).
- O app não deve depender só da validação da tela: a API é quem decide.
