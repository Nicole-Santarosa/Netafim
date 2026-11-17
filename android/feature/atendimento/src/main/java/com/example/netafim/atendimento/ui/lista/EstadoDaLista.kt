package com.example.netafim.atendimento.ui.lista

import com.example.netafim.core.modelo.ItemDeAtendimento

// O que uma tela de lista tem para mostrar em cada momento. Existe para separar "ainda não sei" de "não tem
// nada": sem isso a tela começava com lista vazia e mostrava "nenhum atendimento" por um instante, antes de o
// repositório responder pela primeira vez.
sealed interface EstadoDaLista {
    // Ainda não chegou a primeira resposta do repositório (ou não há usuário logado).
    data object Carregando : EstadoDaLista

    // O repositório respondeu. Lista vazia aqui quer dizer, de fato, que não há atendimento para mostrar.
    data class Pronta(val itens: List<ItemDeAtendimento>) : EstadoDaLista
}
