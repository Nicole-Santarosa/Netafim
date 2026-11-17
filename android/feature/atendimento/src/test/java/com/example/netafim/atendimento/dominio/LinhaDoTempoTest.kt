package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.Atendimento
import com.example.netafim.core.modelo.EventoDoHistorico
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.SituacaoDeSincronizacao
import com.example.netafim.core.modelo.StatusAtendimento
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinhaDoTempoTest {

    private fun etapas(vararg pares: Pair<TipoEtapa, SituacaoEtapa>) = pares.map { EtapaDaLinha(it.first, it.second) }

    private val aberto = etapas(
        TipoEtapa.ABERTO to SituacaoEtapa.ATUAL,
        TipoEtapa.EM_ATENDIMENTO to SituacaoEtapa.PENDENTE,
        TipoEtapa.CONCLUIDO to SituacaoEtapa.PENDENTE,
    )
    private val emAtendimento = etapas(
        TipoEtapa.ABERTO to SituacaoEtapa.FEITA,
        TipoEtapa.EM_ATENDIMENTO to SituacaoEtapa.ATUAL,
        TipoEtapa.CONCLUIDO to SituacaoEtapa.PENDENTE,
    )
    private val concluido = etapas(
        TipoEtapa.ABERTO to SituacaoEtapa.FEITA,
        TipoEtapa.EM_ATENDIMENTO to SituacaoEtapa.FEITA,
        TipoEtapa.CONCLUIDO to SituacaoEtapa.FEITA,
    )
    private val cancelado = etapas(
        TipoEtapa.ABERTO to SituacaoEtapa.FEITA,
        TipoEtapa.CANCELADO to SituacaoEtapa.ATUAL,
    )
    private val desconhecido = etapas(
        TipoEtapa.ABERTO to SituacaoEtapa.PENDENTE,
        TipoEtapa.EM_ATENDIMENTO to SituacaoEtapa.PENDENTE,
        TipoEtapa.CONCLUIDO to SituacaoEtapa.PENDENTE,
    )

    @Test
    fun linhaDoTempoDeCadaUmDosOitoStatus() {
        val esperado = mapOf(
            StatusAtendimento.ABERTO to aberto,
            StatusAtendimento.DESTINADO to aberto,
            StatusAtendimento.AGENDADO to aberto,
            StatusAtendimento.EM_ATENDIMENTO to emAtendimento,
            StatusAtendimento.RESOLVIDO to concluido,
            StatusAtendimento.ENCERRADO to concluido,
            StatusAtendimento.ENCERRADO_SEM_AVALIACAO to concluido,
            StatusAtendimento.CANCELADO to cancelado,
            StatusAtendimento.DESCONHECIDO to desconhecido,
        )
        assertEquals(StatusAtendimento.entries.size, esperado.size)
        esperado.forEach { (status, etapas) -> assertEquals(status.name, etapas, linhaDoTempo(status)) }
    }

    @Test
    fun canceladoTemSoDuasEtapas() {
        assertEquals(2, linhaDoTempo(StatusAtendimento.CANCELADO).size)
        assertEquals(3, linhaDoTempo(StatusAtendimento.ABERTO).size)
    }

    // Cancelado a partir de em atendimento mantém essa etapa como feita.
    private val canceladoAposEmAtendimento = etapas(
        TipoEtapa.ABERTO to SituacaoEtapa.FEITA,
        TipoEtapa.EM_ATENDIMENTO to SituacaoEtapa.FEITA,
        TipoEtapa.CANCELADO to SituacaoEtapa.ATUAL,
    )

    @Test
    fun canceladoVindoDeCadaStatusPossivel() {
        // Só em atendimento muda a linha. Os demais, inclusive os que o banco nem permite antes de cancelar
        // (resolvido e os finais), seguem como se o status anterior não fosse conhecido.
        StatusAtendimento.entries.forEach { anterior ->
            val esperado = if (anterior == StatusAtendimento.EM_ATENDIMENTO) canceladoAposEmAtendimento else cancelado
            assertEquals(anterior.name, esperado, linhaDoTempo(StatusAtendimento.CANCELADO, anterior))
        }
    }

    @Test
    fun canceladoAPartirDeAbertoDestinadoOuAgendadoTemDuasEtapas() {
        listOf(StatusAtendimento.ABERTO, StatusAtendimento.DESTINADO, StatusAtendimento.AGENDADO).forEach {
            assertEquals(it.name, cancelado, linhaDoTempo(StatusAtendimento.CANCELADO, it))
        }
    }

    @Test
    fun statusDesconhecidoDeixaTodasAsEtapasPendentesSemAtualNemFeita() {
        // O app não sabe em que ponto o atendimento está: não pode marcar nenhuma etapa como atual nem concluída.
        val linha = linhaDoTempo(StatusAtendimento.DESCONHECIDO)
        assertEquals(desconhecido, linha)
        assertEquals(3, linha.size)
        assertTrue(linha.all { it.situacao == SituacaoEtapa.PENDENTE })
        // Nem o status anterior do cancelamento muda isso: o parâmetro só vale para o cancelado.
        assertEquals(desconhecido, linhaDoTempo(StatusAtendimento.DESCONHECIDO, StatusAtendimento.EM_ATENDIMENTO))
    }

    @Test
    fun canceladoSemStatusAnteriorMantemOComportamentoAntigo() {
        assertEquals(cancelado, linhaDoTempo(StatusAtendimento.CANCELADO))
        assertEquals(cancelado, linhaDoTempo(StatusAtendimento.CANCELADO, null))
    }

    @Test
    fun canceladoComCodigoAnteriorDesconhecidoMantemOComportamentoAntigo() {
        // A camada de dados converte o código com deCodigo, que devolve DESCONHECIDO para o que o app não conhece.
        val anterior = StatusAtendimento.deCodigo("status_novo_do_servidor")
        assertEquals(StatusAtendimento.DESCONHECIDO, anterior)
        assertEquals(cancelado, linhaDoTempo(StatusAtendimento.CANCELADO, anterior))
    }

    @Test
    fun statusAnteriorEIgnoradoQuandoOStatusNaoECancelado() {
        StatusAtendimento.entries.filter { it != StatusAtendimento.CANCELADO }.forEach { status ->
            assertEquals(
                status.name,
                linhaDoTempo(status),
                linhaDoTempo(status, StatusAtendimento.EM_ATENDIMENTO),
            )
        }
    }

    // ---- statusAntesDoCancelamento(historico)

    private fun evento(
        id: Long,
        tipo: String = "status_alterado",
        anterior: String? = null,
        novo: String? = null,
    ) = EventoDoHistorico(id, tipo, anterior, novo, Instant.parse("2026-11-01T12:00:00Z"))

    @Test
    fun semHistoricoOuSemCancelamentoOStatusAnteriorENulo() {
        assertNull(statusAntesDoCancelamento(emptyList()))
        val semCancelamento = listOf(
            evento(1, tipo = "criado", novo = "aberto"),
            evento(2, anterior = "aberto", novo = "destinado"),
        )
        assertNull(statusAntesDoCancelamento(semCancelamento))
    }

    @Test
    fun umCancelamentoDevolveOStatusAnterior() {
        val historico = listOf(
            evento(1, anterior = "agendado", novo = "em_atendimento"),
            evento(2, anterior = "em_atendimento", novo = "cancelado"),
        )
        assertEquals(StatusAtendimento.EM_ATENDIMENTO, statusAntesDoCancelamento(historico))
        assertEquals(
            StatusAtendimento.ABERTO,
            statusAntesDoCancelamento(listOf(evento(1, anterior = "aberto", novo = "cancelado"))),
        )
    }

    @Test
    fun comVariosCancelamentosVaiOUltimoNaOrdemDaLista() {
        // Não reordena por data nem por id: o repositório já entrega na ordem do servidor.
        val historico = listOf(
            evento(5, anterior = "aberto", novo = "cancelado"),
            evento(2, anterior = "em_atendimento", novo = "cancelado"),
        )
        assertEquals(StatusAtendimento.EM_ATENDIMENTO, statusAntesDoCancelamento(historico))
    }

    @Test
    fun eventoPosteriorQueNaoECancelamentoNaoApagaOCancelamento() {
        val historico = listOf(
            evento(1, anterior = "em_atendimento", novo = "cancelado"),
            evento(2, tipo = "editado_pos_finalizacao", anterior = "x", novo = "y"),
        )
        assertEquals(StatusAtendimento.EM_ATENDIMENTO, statusAntesDoCancelamento(historico))
    }

    @Test
    fun codigoAnteriorQueOAppNaoConheceViraNulo() {
        assertNull(statusAntesDoCancelamento(listOf(evento(1, anterior = "status_novo_do_servidor", novo = "cancelado"))))
    }

    @Test
    fun cancelamentoSemValorAnteriorViraNulo() {
        assertNull(statusAntesDoCancelamento(listOf(evento(1, anterior = null, novo = "cancelado"))))
    }

    @Test
    fun eventoDeOutroTipoComValorNovoCanceladoEIgnorado() {
        // Só status_alterado conta, mesmo que outro tipo de evento traga o texto "cancelado".
        val historico = listOf(evento(1, tipo = "destinado", anterior = "em_atendimento", novo = "cancelado"))
        assertNull(statusAntesDoCancelamento(historico))
    }

    @Test
    fun mudancaDeStatusParaOutroValorNaoContaComoCancelamento() {
        val historico = listOf(evento(1, anterior = "aberto", novo = "destinado"))
        assertNull(statusAntesDoCancelamento(historico))
    }

    // ---- linhaDeLocal

    private fun detalhe(fazenda: String?, cidade: String?) = Atendimento(
        item = ItemDeAtendimento(
            id = "1",
            protocolo = "OS-0001",
            motivo = MotivoAtendimento.DUVIDAS,
            status = StatusAtendimento.ABERTO,
            origem = OrigemAtendimento.CLIENTE_FINAL,
            abertoEm = Instant.parse("2026-11-11T12:00:00Z"),
            nomeCliente = null,
            local = null,
            idTecnicoResponsavel = null,
            sincronizacao = SituacaoDeSincronizacao.SINCRONIZADO,
        ),
        produtor = "Produtor",
        nomeFazenda = fazenda,
        cidade = cidade,
        culturas = emptyList(),
        situacaoProjeto = null,
        tipoVenda = null,
        historico = emptyList(),
        conflito = null,
        erroDeEnvio = null,
    )

    @Test
    fun linhaDeLocalComOsDoisValores() {
        assertEquals("Fazenda Santa Rita · Ribeirão Preto, SP", linhaDeLocal(detalhe("Fazenda Santa Rita", "Ribeirão Preto, SP")))
    }

    @Test
    fun linhaDeLocalComSoUmValor() {
        assertEquals("Fazenda Santa Rita", linhaDeLocal(detalhe("Fazenda Santa Rita", null)))
        assertEquals("Arealva, SP", linhaDeLocal(detalhe(null, "Arealva, SP")))
        assertEquals("Arealva, SP", linhaDeLocal(detalhe("", "Arealva, SP")))
    }

    @Test
    fun linhaDeLocalSemNenhumValorDevolveNulo() {
        assertNull(linhaDeLocal(detalhe(null, null)))
    }

    @Test
    fun linhaDeLocalComSoEspacosDevolveNulo() {
        assertNull(linhaDeLocal(detalhe("   ", "  ")))
    }

    @Test
    fun linhaDeLocalApagaEspacosDasPontas() {
        assertEquals("Fazenda · Cidade", linhaDeLocal(detalhe("  Fazenda ", " Cidade  ")))
    }
}
