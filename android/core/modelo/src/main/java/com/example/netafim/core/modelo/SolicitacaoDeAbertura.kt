package com.example.netafim.core.modelo

import java.time.Instant

// Dados para abrir um atendimento. Os tipos são os mesmos do formulário de abertura do app: texto não
// informado fica vazio (o repositório não envia campo vazio) e as escolhas de lista vão como ids.
data class SolicitacaoDeAbertura(
    // UUID gerado pelo aparelho. Reenviar o mesmo id não duplica o atendimento.
    val id: String,
    val origem: OrigemAtendimento,
    val motivo: MotivoAtendimento,
    val idCliente: Int?,
    val idDistribuidorOrigem: Int?,
    val nomeProdutor: String,
    val nomeFazenda: String,
    val cidade: String,
    val areaSetor: String,
    val contato: String,
    val tipoVenda: TipoVenda?,
    val culturas: Set<Int>,
    // Relógio do aparelho no momento da abertura.
    val abertoEm: Instant,
) {
    // O app nunca cria um atendimento com um valor que ele mesmo não entende: DESCONHECIDO só existe para
    // registros que vieram do servidor.
    init {
        require(origem != OrigemAtendimento.DESCONHECIDO) { "A origem do atendimento não pode ser DESCONHECIDO." }
        require(motivo != MotivoAtendimento.DESCONHECIDO) { "O motivo do atendimento não pode ser DESCONHECIDO." }
        require(tipoVenda != TipoVenda.DESCONHECIDO) { "O tipo de venda não pode ser DESCONHECIDO." }
    }
}
