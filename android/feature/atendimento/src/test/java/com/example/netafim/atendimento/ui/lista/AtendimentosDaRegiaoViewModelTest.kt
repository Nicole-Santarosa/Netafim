package com.example.netafim.atendimento.ui.lista

import androidx.lifecycle.viewmodel.CreationExtras
import com.example.netafim.atendimento.dominio.FiltroDaRegiao
import com.example.netafim.atendimento.dominio.comoFiltroDeAtendimentos
import com.example.netafim.core.modelo.OrdemDaLista
import com.example.netafim.core.modelo.StatusAtendimento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AtendimentosDaRegiaoViewModelTest {

    private val sessao = SessaoFalsa()
    private val repositorio = RepositorioDeAtendimentosFalso(sessao)

    @Before
    fun antes() {
        // O viewModelScope roda no Dispatchers.Main, que não existe na JVM: troca por um de teste.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun depois() {
        Dispatchers.resetMain()
    }

    private fun criar() = AtendimentosDaRegiaoViewModel(repositorio, sessao)

    // Um atendimento de cada situação, em técnicos diferentes, para provar que esta lista não filtra por técnico.
    private val umDeCada = listOf(
        itemDeTeste("aberto", status = StatusAtendimento.ABERTO, idTecnico = 1),
        itemDeTeste("em-curso", status = StatusAtendimento.EM_ATENDIMENTO, idTecnico = 2),
        itemDeTeste("resolvido", status = StatusAtendimento.RESOLVIDO, idTecnico = 3),
        itemDeTeste("encerrado", status = StatusAtendimento.ENCERRADO, idTecnico = null),
        itemDeTeste("cancelado", status = StatusAtendimento.CANCELADO, idTecnico = 4),
    )

    @Test
    fun estadoInicialTemTodosSemBuscaTrazTudoSemFiltrarPorTecnico() = runTest {
        repositorio.definirItens(umDeCada)
        val viewModel = criar()
        manterColetando(viewModel.estado)

        assertEquals(FiltroDaRegiao.TODOS, viewModel.filtro.value)
        assertEquals("", viewModel.busca.value)
        assertEquals(5, prontos(viewModel.estado.value).size)
        val pedido = repositorio.pedidosRecebidos.single()
        assertEquals(FiltroDaRegiao.TODOS.comoFiltroDeAtendimentos(""), pedido)
        assertFalse(pedido.somenteMeusComoTecnico)
    }

    @Test
    fun trocarOChipFiltraPorSelo() = runTest {
        repositorio.definirItens(umDeCada)
        val viewModel = criar()
        manterColetando(viewModel.estado)

        viewModel.trocarFiltro(FiltroDaRegiao.EM_ANDAMENTO)
        assertEquals(setOf("aberto", "em-curso"), ids(prontos(viewModel.estado.value)).toSet())

        viewModel.trocarFiltro(FiltroDaRegiao.CONCLUIDOS)
        assertEquals(setOf("resolvido", "encerrado"), ids(prontos(viewModel.estado.value)).toSet())

        // O cancelado só aparece em Todos.
        viewModel.trocarFiltro(FiltroDaRegiao.TODOS)
        assertTrue("cancelado" in ids(prontos(viewModel.estado.value)))
    }

    @Test
    fun buscarFiltraPeloProtocoloOuPeloCliente() = runTest {
        repositorio.definirItens(
            listOf(
                itemDeTeste("1", protocolo = "OS-0506", nomeCliente = "Vanderlei"),
                itemDeTeste("2", protocolo = "OS-0477", nomeCliente = "Citrosuco"),
            ),
        )
        val viewModel = criar()
        manterColetando(viewModel.estado)

        viewModel.buscar("0506")
        assertEquals(listOf("1"), ids(prontos(viewModel.estado.value)))

        viewModel.buscar("CITRO")
        assertEquals(listOf("2"), ids(prontos(viewModel.estado.value)))
    }

    @Test
    fun aListaSaiDoMaisRecenteParaOMaisAntigoComDesempatePeloId() = runTest {
        repositorio.definirItens(
            listOf(
                itemDeTeste("b", aberto = "2026-11-05T12:00:00Z"),
                itemDeTeste("velho", aberto = "2026-10-01T12:00:00Z"),
                itemDeTeste("novo", aberto = "2026-11-10T12:00:00Z"),
                itemDeTeste("a", aberto = "2026-11-05T12:00:00Z"),
            ),
        )
        val viewModel = criar()
        manterColetando(viewModel.estado)

        assertEquals(OrdemDaLista.MAIS_RECENTE_PRIMEIRO, repositorio.pedidosRecebidos.single().ordem)
        assertEquals(listOf("novo", "a", "b", "velho"), ids(prontos(viewModel.estado.value)))
    }

    @Test
    fun semUsuarioNaSessaoAListaFicaCarregandoESemConsultarORepositorio() = runTest {
        repositorio.definirItens(umDeCada)
        sessao.entrarComo(null)
        val viewModel = criar()
        manterColetando(viewModel.estado)

        assertEquals(EstadoDaLista.Carregando, viewModel.estado.value)
        assertTrue(repositorio.pedidosRecebidos.isEmpty())
    }

    @Test
    fun aListaReageAMudancasNoRepositorio() = runTest {
        val viewModel = criar()
        manterColetando(viewModel.estado)
        assertTrue(prontos(viewModel.estado.value).isEmpty())

        repositorio.definirItens(listOf(itemDeTeste("chegou")))
        assertEquals(listOf("chegou"), ids(prontos(viewModel.estado.value)))
    }

    @Test
    fun aFabricaCriaOViewModelComAsDependencias() {
        val viewModel = AtendimentosDaRegiaoViewModel.fabrica(repositorio, sessao)
            .create(AtendimentosDaRegiaoViewModel::class.java, CreationExtras.Empty)
        assertEquals(FiltroDaRegiao.TODOS, viewModel.filtro.value)
    }
}
