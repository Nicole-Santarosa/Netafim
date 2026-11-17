package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.ComparadorDeProtocolo
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento

// Filtros da linha de chips da lista do Admin: todas as origens ou uma só.
enum class FiltroDeOrigem {
    TODAS,
    NETAFIM,
    DISTRIBUIDOR,
    CLIENTE_FINAL,
}

// Diz se uma origem passa pelo filtro. Todas aceita qualquer origem; os outros aceitam só a sua.
fun FiltroDeOrigem.aceita(origem: OrigemAtendimento): Boolean = when (this) {
    FiltroDeOrigem.TODAS -> true
    FiltroDeOrigem.NETAFIM -> origem == OrigemAtendimento.NETAFIM
    FiltroDeOrigem.DISTRIBUIDOR -> origem == OrigemAtendimento.DISTRIBUIDOR
    FiltroDeOrigem.CLIENTE_FINAL -> origem == OrigemAtendimento.CLIENTE_FINAL
}

// Aplica o filtro e devolve o resultado do mais recente para o mais antigo pela data de abertura; no
// empate, pelo número do protocolo. A ordem é uma decisão do Rafael para a lista do Admin e ainda não foi
// confirmada com o grupo. É a mesma regra de ordenarRegiao e, pelo mesmo motivo, não usa o desempate por id
// de ordenarPelosMaisRecentes do core.
fun filtrarListaAdmin(itens: List<ItemDeAtendimento>, filtro: FiltroDeOrigem): List<ItemDeAtendimento> =
    itens.filter { filtro.aceita(it.origem) }
        .sortedWith(
            compareByDescending<ItemDeAtendimento> { it.abertoEm }.thenBy(ComparadorDeProtocolo) { it.protocolo },
        )

// Texto da linha: protocolo e nome do cliente separados por " · ". O separador é pontuação, por isso fica
// no Kotlin e não no strings.xml. Sem protocolo (atendimento criado sem conexão) entra o texto recebido em
// protocoloPendente, que a tela tira do strings.xml; sem nome de cliente a linha mostra só o protocolo.
fun textoDaLinha(item: ItemDeAtendimento, protocoloPendente: String): String =
    listOfNotNull(item.protocolo ?: protocoloPendente, item.nomeCliente?.takeIf { it.isNotBlank() })
        .joinToString(" · ")
