# Formulário de solicitação de atendimento: campos e regras de obrigatório

> **Para que serve:** fonte versionada da regra de "obrigatório" do app e da API (quem abre uma solicitação preenche o quê, conforme a origem e o motivo).
> **Origem do conteúdo:** transcrição da estrutura do formulário original da Netafim ("Estruturação — Formulário de Solicitação de Atendimento", pasta de documentação do projeto; o arquivo não traz data). O arquivo original **não** é versionado aqui: é um documento interno da Netafim e o repositório é público. Aqui ficam só os nomes dos campos, as opções e se são obrigatórios (`*`).
> **Estado:** a conferir com a Netafim a cada mudança do formulário original. Esta tabela descreve a **solicitação** (a abertura do atendimento); o relatório de resolução (preenchido pelo especialista) é outro documento.

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
| **Cliente Final** | Nome do Distribuidor da sua região\*, Cidade\*, Nome da Fazenda\*, Cultura\*, Motivo do Atendimento\* |
| **Distribuidor** | Nome do Distribuidor\*, Nome do Produtor\*, Cidade\*, Cultura\*, Motivo do Atendimento\* |
| **Netafim** | Área/Setor\*, Nome do Produtor\*, Cidade\*, Cultura\*, Motivo do Atendimento\* |

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

## 4. Uso previsto no app e na API (a implementar)

Esta seção descreve o que a tabela **deve** garantir; a validação ainda **não** está implementada na API de atendimentos.

- **Cliente Final:** Distribuidor da sua região, Cidade, Nome da Fazenda e Cultura obrigatórios para abrir a solicitação. A escolha do distribuidor pode usar `GET /api/v1/distribuidores/opcoes` (distribuidores ativos, exige login).
- **Distribuidor e Netafim:** sem "Nome da Fazenda"; pedem **Nome do Produtor** e, para a Netafim, a **Área/Setor**.
- O motivo define os campos seguintes (seção 3).
- A API deve validar os obrigatórios por origem e motivo; o app não deve depender só da validação da tela.
