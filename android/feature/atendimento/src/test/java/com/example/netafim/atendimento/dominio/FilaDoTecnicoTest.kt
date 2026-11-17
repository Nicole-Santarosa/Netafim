package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.FiltroDeAtendimentos
import com.example.netafim.core.modelo.OrdemDaLista
import com.example.netafim.core.modelo.StatusAtendimento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// A filtragem e a ordem em si são aplicadas pelo repositório (testadas no :core:modelo e nos testes do
// ViewModel com o repositório falso). Aqui se confere só a tradução do chip no pedido.
class FilaDoTecnicoTest {

    private val statusDaFila = setOf(
        StatusAtendimento.DESTINADO,
        StatusAtendimento.AGENDADO,
        StatusAtendimento.EM_ATENDIMENTO,
    )

    @Test
    fun todosPedeOsTresStatusDaFila() {
        assertEquals(statusDaFila, FiltroDaFila.TODOS.comoFiltroDeAtendimentos("").status)
    }

    @Test
    fun novoPedeDestinadoEAgendado() {
        assertEquals(
            setOf(StatusAtendimento.DESTINADO, StatusAtendimento.AGENDADO),
            FiltroDaFila.NOVO.comoFiltroDeAtendimentos("").status,
        )
    }

    @Test
    fun emAtendimentoPedeSoEmAtendimento() {
        assertEquals(
            setOf(StatusAtendimento.EM_ATENDIMENTO),
            FiltroDaFila.EM_ATENDIMENTO.comoFiltroDeAtendimentos("").status,
        )
    }

    @Test
    fun nenhumChipPedeStatusForaDaFila() {
        // Aberto, concluídos, cancelado e o desconhecido nunca entram na fila, em nenhum chip.
        FiltroDaFila.entries.forEach { filtro ->
            val pedidos = filtro.comoFiltroDeAtendimentos("").status
            assertTrue(filtro.name, statusDaFila.containsAll(pedidos))
            assertTrue(filtro.name, pedidos.isNotEmpty())
        }
    }

    @Test
    fun todosOsChipsPedemSoOsDoTecnicoLogadoNaOrdemDaFila() {
        FiltroDaFila.entries.forEach { filtro ->
            val pedido = filtro.comoFiltroDeAtendimentos("")
            assertTrue(filtro.name, pedido.somenteMeusComoTecnico)
            assertEquals(filtro.name, OrdemDaLista.FILA_DO_TECNICO, pedido.ordem)
        }
    }

    @Test
    fun aBuscaViajaNoPedidoSemMudar() {
        assertEquals("  Citro ", FiltroDaFila.TODOS.comoFiltroDeAtendimentos("  Citro ").busca)
        assertEquals("", FiltroDaFila.NOVO.comoFiltroDeAtendimentos("").busca)
    }

    @Test
    fun oChipNaoMexeNosOutrosCriterios() {
        val pedido = FiltroDaFila.TODOS.comoFiltroDeAtendimentos("")
        assertTrue(pedido.origem.isEmpty())
        assertNull(pedido.origemDoRegistro)
        // Os demais campos continuam com o valor padrão do pedido.
        assertEquals(
            FiltroDeAtendimentos(
                status = statusDaFila,
                somenteMeusComoTecnico = true,
                ordem = OrdemDaLista.FILA_DO_TECNICO,
            ),
            pedido,
        )
    }
}
