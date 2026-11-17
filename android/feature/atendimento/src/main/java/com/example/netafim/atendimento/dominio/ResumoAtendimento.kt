package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.ItemDeAtendimento
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
// Status desconhecido: o app não sabe se o atendimento segue em andamento, então não conta dias (daria a
// entender que está pendente) e mostra a data de abertura, que é um fato.
fun apoioDoCartao(item: ItemDeAtendimento, agora: Instant, zona: ZoneId): ApoioDoCartao =
    when (item.status.selo) {
        SeloStatus.ABERTO, SeloStatus.EM_ATENDIMENTO -> {
            val dias = diasEmAberto(item.abertoEm, agora, zona)
            if (dias == 0) ApoioDoCartao.AbertoHoje else ApoioDoCartao.AbertoHaDias(dias)
        }
        SeloStatus.CONCLUIDO, SeloStatus.CANCELADO, SeloStatus.DESCONHECIDO ->
            ApoioDoCartao.AbertoEm(item.abertoEm.atZone(zona).toLocalDate())
    }
