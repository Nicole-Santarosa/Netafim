package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.FiltroDeAtendimentos
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.OrdemDaLista
import com.example.netafim.core.modelo.OrigemAtendimento

// Filtros da linha de chips da lista do Admin: todas as origens ou uma só.
enum class FiltroDeOrigem {
    TODAS,
    NETAFIM,
    DISTRIBUIDOR,
    CLIENTE_FINAL,
}

// Traduz o chip no pedido que o repositório entende: Todas não limita a origem e os outros limitam à sua.
// A lista do Admin não tem busca. A ordem é do mais recente para o mais antigo (desempate pelo id, do core):
// decisão do Rafael para a lista do Admin, ainda não confirmada com o grupo.
fun FiltroDeOrigem.comoFiltroDeAtendimentos(): FiltroDeAtendimentos = FiltroDeAtendimentos(
    origem = when (this) {
        FiltroDeOrigem.TODAS -> emptySet()
        FiltroDeOrigem.NETAFIM -> setOf(OrigemAtendimento.NETAFIM)
        FiltroDeOrigem.DISTRIBUIDOR -> setOf(OrigemAtendimento.DISTRIBUIDOR)
        FiltroDeOrigem.CLIENTE_FINAL -> setOf(OrigemAtendimento.CLIENTE_FINAL)
    },
    ordem = OrdemDaLista.MAIS_RECENTE_PRIMEIRO,
)

// Texto da linha: protocolo e nome do cliente separados por " · ". O separador é pontuação, por isso fica
// no Kotlin e não no strings.xml. Sem protocolo (atendimento criado sem conexão) entra o texto recebido em
// protocoloPendente, que a tela tira do strings.xml; sem nome de cliente a linha mostra só o protocolo.
fun textoDaLinha(item: ItemDeAtendimento, protocoloPendente: String): String =
    listOfNotNull(item.protocolo ?: protocoloPendente, item.nomeCliente?.takeIf { it.isNotBlank() })
        .joinToString(" · ")
