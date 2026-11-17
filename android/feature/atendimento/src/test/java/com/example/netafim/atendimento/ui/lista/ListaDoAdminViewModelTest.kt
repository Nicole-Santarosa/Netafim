package com.example.netafim.atendimento.ui.lista

import androidx.lifecycle.viewmodel.CreationExtras
import com.example.netafim.atendimento.dominio.FiltroDeOrigem
import com.example.netafim.atendimento.dominio.comoFiltroDeAtendimentos
import com.example.netafim.core.modelo.OrigemAtendimento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListaDoAdminViewModelTest {

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

    private fun criar() = ListaDoAdminViewModel(repositorio, sessao)

    private val umDeCadaOrigem = listOf(
        itemDeTeste("netafim", origem = OrigemAtendimento.NETAFIM, aberto = "2026-11-01T12:00:00Z"),
        itemDeTeste("distribuidor", origem = OrigemAtendimento.DISTRIBUIDOR, aberto = "2026-11-02T12:00:00Z"),
        itemDeTeste("cliente", origem = OrigemAtendimento.CLIENTE_FINAL, aberto = "2026-11-03T12:00:00Z"),
    )

    @Test
    fun estadoInicialTemTodasAsOrigensDoMaisRecenteParaOMaisAntigo() = runTest {
        repositorio.definirItens(umDeCadaOrigem)
        val viewModel = criar()
        manterColetando(viewModel.itens)

        assertEquals(FiltroDeOrigem.TODAS, viewModel.filtro.value)
        assertEquals(listOf("cliente", "distribuidor", "netafim"), ids(viewModel.itens.value))
        assertEquals(FiltroDeOrigem.TODAS.comoFiltroDeAtendimentos(), repositorio.pedidosRecebidos.single())
    }

    @Test
    fun trocarOChipFiltraPelaOrigem() = runTest {
        repositorio.definirItens(umDeCadaOrigem)
        val viewModel = criar()
        manterColetando(viewModel.itens)

        viewModel.trocarFiltro(FiltroDeOrigem.NETAFIM)
        assertEquals(listOf("netafim"), ids(viewModel.itens.value))

        viewModel.trocarFiltro(FiltroDeOrigem.DISTRIBUIDOR)
        assertEquals(listOf("distribuidor"), ids(viewModel.itens.value))

        viewModel.trocarFiltro(FiltroDeOrigem.CLIENTE_FINAL)
        assertEquals(listOf("cliente"), ids(viewModel.itens.value))

        viewModel.trocarFiltro(FiltroDeOrigem.TODAS)
        assertEquals(3, viewModel.itens.value.size)
    }

    @Test
    fun empateDeDataDesempataPeloId() = runTest {
        repositorio.definirItens(
            listOf(itemDeTeste("b"), itemDeTeste("a"), itemDeTeste("c", aberto = "2026-11-09T12:00:00Z")),
        )
        val viewModel = criar()
        manterColetando(viewModel.itens)

        assertEquals(listOf("c", "a", "b"), ids(viewModel.itens.value))
    }

    @Test
    fun semUsuarioNaSessaoAListaSaiVaziaESemConsultarORepositorio() = runTest {
        repositorio.definirItens(umDeCadaOrigem)
        sessao.entrarComo(null)
        val viewModel = criar()
        manterColetando(viewModel.itens)

        assertTrue(viewModel.itens.value.isEmpty())
        assertTrue(repositorio.pedidosRecebidos.isEmpty())
    }

    @Test
    fun aListaReageAMudancasNoRepositorio() = runTest {
        val viewModel = criar()
        manterColetando(viewModel.itens)
        assertTrue(viewModel.itens.value.isEmpty())

        repositorio.definirItens(umDeCadaOrigem)
        assertEquals(3, viewModel.itens.value.size)
    }

    @Test
    fun aFabricaCriaOViewModelComAsDependencias() {
        val viewModel = ListaDoAdminViewModel.fabrica(repositorio, sessao)
            .create(ListaDoAdminViewModel::class.java, CreationExtras.Empty)
        assertEquals(FiltroDeOrigem.TODAS, viewModel.filtro.value)
    }
}
