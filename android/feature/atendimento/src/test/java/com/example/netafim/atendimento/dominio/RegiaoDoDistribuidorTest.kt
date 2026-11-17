package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.OrdemDaLista
import com.example.netafim.core.modelo.StatusAtendimento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// A filtragem e a ordem em si são aplicadas pelo repositório (testadas no :core:modelo e nos testes do
// ViewModel com o repositório falso). Aqui se confere só a tradução do chip no pedido.
class RegiaoDoDistribuidorTest {

    // Os oito status reais; o desconhecido é tratado à parte.
    private val statusReais = StatusAtendimento.entries.filter { it != StatusAtendimento.DESCONHECIDO }

    @Test
    fun todosNaoLimitaOStatus() {
        // Conjunto vazio quer dizer sem filtro: passam todos, inclusive cancelado e o status desconhecido.
        assertTrue(FiltroDaRegiao.TODOS.comoFiltroDeAtendimentos("").status.isEmpty())
    }

    @Test
    fun emAndamentoPedeOsSelosAbertoEEmAtendimento() {
        val esperados = statusReais.filter {
            it.selo == SeloStatus.ABERTO || it.selo == SeloStatus.EM_ATENDIMENTO
        }.toSet()
        assertEquals(esperados, FiltroDaRegiao.EM_ANDAMENTO.comoFiltroDeAtendimentos("").status)
        assertEquals(
            setOf(
                StatusAtendimento.ABERTO,
                StatusAtendimento.DESTINADO,
                StatusAtendimento.AGENDADO,
                StatusAtendimento.EM_ATENDIMENTO,
            ),
            esperados,
        )
    }

    @Test
    fun concluidosPedeSoOSeloConcluido() {
        val esperados = statusReais.filter { it.selo == SeloStatus.CONCLUIDO }.toSet()
        assertEquals(esperados, FiltroDaRegiao.CONCLUIDOS.comoFiltroDeAtendimentos("").status)
        assertEquals(
            setOf(
                StatusAtendimento.RESOLVIDO,
                StatusAtendimento.ENCERRADO,
                StatusAtendimento.ENCERRADO_SEM_AVALIACAO,
            ),
            esperados,
        )
    }

    @Test
    fun canceladoEODesconhecidoSoApareceEmTodos() {
        listOf(FiltroDaRegiao.EM_ANDAMENTO, FiltroDaRegiao.CONCLUIDOS).forEach { filtro ->
            val status = filtro.comoFiltroDeAtendimentos("").status
            assertFalse(filtro.name, StatusAtendimento.CANCELADO in status)
            assertFalse(filtro.name, StatusAtendimento.DESCONHECIDO in status)
        }
    }

    @Test
    fun aListaSaiDoMaisRecenteParaOMaisAntigoSemFiltrarPorTecnico() {
        FiltroDaRegiao.entries.forEach { filtro ->
            val pedido = filtro.comoFiltroDeAtendimentos("")
            assertEquals(filtro.name, OrdemDaLista.MAIS_RECENTE_PRIMEIRO, pedido.ordem)
            assertFalse(filtro.name, pedido.somenteMeusComoTecnico)
            assertTrue(filtro.name, pedido.origem.isEmpty())
        }
    }

    @Test
    fun aBuscaViajaNoPedidoSemMudar() {
        assertEquals("Ribeirão", FiltroDaRegiao.CONCLUIDOS.comoFiltroDeAtendimentos("Ribeirão").busca)
    }
}
