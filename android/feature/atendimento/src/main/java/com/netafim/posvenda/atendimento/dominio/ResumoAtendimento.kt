package com.netafim.posvenda.atendimento.dominio

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Dados de um atendimento que a lista "Meus atendimentos" precisa para montar o cartão.
data class ResumoAtendimento(
    val id: String,
    val protocolo: String,
    val motivo: MotivoAtendimento,
    val status: StatusAtendimento,
    val abertoEm: Instant,
)

// O que a linha de apoio do cartão deve dizer sobre a abertura. O texto em si fica no strings.xml.
sealed interface ApoioDoCartao {
    data object AbertoHoje : ApoioDoCartao
    data class AbertoHaDias(val dias: Int) : ApoioDoCartao
    data class AbertoEm(val data: LocalDate) : ApoioDoCartao
}

// Atendimento ainda em andamento (selo ABERTO ou EM_ATENDIMENTO) mostra há quantos dias está aberto,
// porque o tempo de espera é o que importa para quem acompanha.
// Atendimento já concluído ou cancelado mostra a data de abertura: o relógio parou, então "há N dias"
// continuaria crescendo e daria a impressão de que o atendimento segue pendente.
fun apoioDoCartao(resumo: ResumoAtendimento, agora: Instant, zona: ZoneId): ApoioDoCartao =
    when (resumo.status.selo) {
        SeloStatus.ABERTO, SeloStatus.EM_ATENDIMENTO -> {
            val dias = diasEmAberto(resumo.abertoEm, agora, zona)
            if (dias == 0) ApoioDoCartao.AbertoHoje else ApoioDoCartao.AbertoHaDias(dias)
        }
        SeloStatus.CONCLUIDO, SeloStatus.CANCELADO ->
            ApoioDoCartao.AbertoEm(resumo.abertoEm.atZone(zona).toLocalDate())
    }
