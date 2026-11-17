package com.example.netafim.atendimento.ui.lista

import androidx.lifecycle.viewmodel.CreationExtras
import com.example.netafim.core.modelo.FiltroDeAtendimentos
import com.example.netafim.core.modelo.sessao.TipoDeOrganizacao
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
class MeusAtendimentosViewModelTest {

    private val sessao = SessaoFalsa(SessaoFalsa.umUsuario(id = 5, tipo = TipoDeOrganizacao.CLIENTE))
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

    private fun criar() = MeusAtendimentosViewModel(repositorio, sessao)

    @Test
    fun pedeTudoSemFiltroEMostraDoMaisRecenteParaOMaisAntigo() = runTest {
        repositorio.definirItens(
            listOf(
                itemDeTeste("antigo", aberto = "2026-10-01T12:00:00Z"),
                itemDeTeste("novo", aberto = "2026-11-10T12:00:00Z"),
                itemDeTeste("meio", aberto = "2026-11-01T12:00:00Z"),
            ),
        )
        val viewModel = criar()
        manterColetando(viewModel.itens)

        // Pedido sem nenhum critério: a tela não tem chip nem busca.
        assertEquals(FiltroDeAtendimentos(), repositorio.pedidosRecebidos.single())
        assertEquals(listOf("novo", "meio", "antigo"), ids(viewModel.itens.value))
    }

    @Test
    fun semUsuarioNaSessaoAListaSaiVaziaESemConsultarORepositorio() = runTest {
        repositorio.definirItens(listOf(itemDeTeste("1")))
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

        repositorio.definirItens(listOf(itemDeTeste("novo-aqui")))
        assertEquals(listOf("novo-aqui"), ids(viewModel.itens.value))
    }

    @Test
    fun aFabricaCriaOViewModelComAsDependencias() {
        val viewModel = MeusAtendimentosViewModel.fabrica(repositorio, sessao)
            .create(MeusAtendimentosViewModel::class.java, CreationExtras.Empty)
        assertTrue(viewModel.itens.value.isEmpty())
    }
}
