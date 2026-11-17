package com.example.netafim.core.modelo

import java.time.Instant

// Uma linha de qualquer lista de atendimentos (Meus atendimentos, fila do técnico, região do distribuidor,
// lista do Admin). Os campos nulos existem porque o servidor nem sempre entrega o dado.
data class ItemDeAtendimento(
    val id: String,
    // Nulo enquanto um atendimento criado offline ainda não subiu: o protocolo é gerado pelo servidor.
    val protocolo: String?,
    val motivo: MotivoAtendimento,
    val status: StatusAtendimento,
    // Canal do atendimento (quem abriu), diferente de OrigemDoRegistro.
    val origem: OrigemAtendimento,
    // Data de abertura pelo relógio do aparelho (data_abertura da API).
    val abertoEm: Instant,
    // Vem do snapshot do cliente, que o servidor ainda não preenche; até lá pode vir nulo.
    val nomeCliente: String?,
    // Nome da fazenda e cidade já juntos, ou nulo quando o atendimento foi aberto sem nenhum dos dois.
    val local: String?,
    val idTecnicoResponsavel: Int?,
    val sincronizacao: SituacaoDeSincronizacao,
)

// Detalhe completo de um atendimento, usado pelo cliente, pelo técnico, pelo distribuidor e pela edição do
// Admin. Não tem campo de versão: o controle de versão é interno do repositório (versaoBase do Room).
data class Atendimento(
    val item: ItemDeAtendimento,
    val produtor: String,
    val nomeFazenda: String?,
    val cidade: String?,
    val culturas: List<Cultura>,
    val situacaoProjeto: SituacaoDoProjeto?,
    val tipoVenda: TipoVenda?,
    val historico: List<EventoDoHistorico>,
    // Só existe quando a sincronização do item é CONFLITO.
    val conflito: ConflitoDoAtendimento?,
    // Mensagem devolvida pelo servidor; só existe quando a sincronização do item é ERRO.
    val erroDeEnvio: String?,
)

// Conflito de versão descoberto no envio. O registro local continua com a edição do usuário: nada é
// descartado. A cópia do servidor vem sempre sem conflito aninhado. Resolver o conflito (manter a minha
// alteração ou aceitar a do servidor) fica para uma segunda etapa e entra como uma operação nova.
data class ConflitoDoAtendimento(
    // versao_atual que o 409 devolveu, guardada à parte do versaoBase do Room.
    val versaoAtualDoServidor: Int,
    // Nulo quando o servidor não mandou a cópia.
    val atendimentoDoServidor: Atendimento?,
    // A alteração que o usuário fez e que o servidor não aceitou.
    val alteracaoLocal: AlteracaoDeAtendimento,
)

// Um evento da linha do tempo. O id é o id_historico do servidor e serve para o evento não ser duplicado.
// O tipo é o tipo_evento como a API manda (texto livre para o app).
data class EventoDoHistorico(
    val id: Long,
    val tipo: String,
    val valorAnterior: String?,
    val valorNovo: String?,
    val em: Instant,
)
