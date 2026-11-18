package com.example.netafim.core.modelo

import java.time.Instant

// Dados para abrir um atendimento. Os tipos são os mesmos do formulário de abertura do app: texto não
// informado fica vazio (o repositório não envia campo vazio), as escolhas de lista vão como ids e a área ou
// setor vai como enum (nula fora da origem Netafim).
data class SolicitacaoDeAbertura(
    // UUID gerado pelo aparelho. Reenviar o mesmo id não duplica o atendimento.
    val id: String,
    val origem: OrigemAtendimento,
    val motivo: MotivoAtendimento,
    val idCliente: Int?,
    // Nulo quando o cliente final não tem distribuidor padrão ou não escolheu um: o atendimento nasce sem
    // distribuidor de origem e vai direto para a fila do Netafim Admin. Quem monta o corpo da API deve omitir
    // a chave id_distribuidor_origem nesse caso.
    val idDistribuidorOrigem: Int?,
    val nomeProdutor: String,
    val nomeFazenda: String,
    val cidade: String,
    // Só a origem Netafim informa a área; nas outras ela é nula.
    val areaSetor: AreaDoSetor?,
    val contato: String,
    // Obrigatório em todo atendimento (decisão da Netafim de 18/11/2026): a API recusa a abertura sem ele.
    val tipoVenda: TipoVenda,
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
        // Mesma regra do banco (ck_atend_area_netafim): a Netafim sempre diz de que área vem. As outras origens
        // não têm área; recusar aqui evita mandar um dado que não pertence ao canal.
        if (origem == OrigemAtendimento.NETAFIM) {
            require(areaSetor != null) { "A origem Netafim exige a área ou setor." }
        } else {
            require(areaSetor == null) { "Só a origem Netafim informa a área ou setor." }
        }
    }
}
