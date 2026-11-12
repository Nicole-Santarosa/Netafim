package com.netafim.posvenda.atendimento.dominio

// O que a tela de detalhe do técnico mostra de um atendimento. O local é opcional (o atendimento pode
// ter sido aberto sem cidade nem fazenda) e as culturas podem vir vazias.
data class DetalheDoTecnico(
    val id: String,
    val protocolo: String,
    val produtor: String,
    val local: String?,
    val culturas: List<String>,
    val motivo: MotivoAtendimento,
    val status: StatusAtendimento,
)

// Diz se a tela mostra o botão Iniciar atendimento. A regra espelha o back-end: a mudança só é
// aceita de agendado para em atendimento, e só o técnico responsável pode fazê-la. Quem confere o
// responsável é o servidor; a tela só decide se o botão aparece.
fun podeIniciar(status: StatusAtendimento): Boolean = status == StatusAtendimento.AGENDADO

// Junta os nomes das culturas em uma linha só, separados por vírgula. Apara cada nome e ignora os
// vazios. Devolve null quando não sobra nenhum, para a tela mostrar "Não informado".
fun culturasEmTexto(culturas: List<String>): String? =
    culturas.map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ")
        .ifEmpty { null }
