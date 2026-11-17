package com.example.netafim.atendimento.ui.lista

import com.example.netafim.core.modelo.ItemDeAtendimento

// O que uma tela de lista tem para mostrar em cada momento. Existe para separar "ainda não sei" de "não tem
// nada": sem isso a tela começava com lista vazia e mostrava "nenhum atendimento" por um instante, antes de o
// repositório responder pela primeira vez. Os três estados dizem coisas diferentes à pessoa:
// - Carregando: ainda não há resposta, e ela deve esperar;
// - SemOrganizacao: não vai haver resposta, porque a conta não tem empresa, e esperar não adianta;
// - Pronta: há resposta, e uma lista vazia aqui é "nenhum atendimento" de verdade.
sealed interface EstadoDaLista {
    // Ainda não chegou a primeira resposta do repositório (ou não há usuário logado).
    data object Carregando : EstadoDaLista

    // A conta é de distribuidor ou de cliente e ainda não foi vinculada a uma empresa (semOrganizacao do usuário
    // da sessão). O repositório nem é consultado: sem empresa não há atendimento visível, e mostrar "nenhum
    // atendimento" faria a pessoa achar que a conta está pronta e só não tem pedidos.
    data object SemOrganizacao : EstadoDaLista

    // O repositório respondeu. Lista vazia aqui quer dizer, de fato, que não há atendimento para mostrar.
    data class Pronta(val itens: List<ItemDeAtendimento>) : EstadoDaLista
}
