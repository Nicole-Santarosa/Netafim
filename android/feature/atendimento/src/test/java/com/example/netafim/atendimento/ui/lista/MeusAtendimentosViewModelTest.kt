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
        manterColetando(viewModel.estado)

        // Pedido sem nenhum critério: a tela não tem chip nem busca.
        assertEquals(FiltroDeAtendimentos(), repositorio.pedidosRecebidos.single())
        assertEquals(listOf("novo", "meio", "antigo"), ids(prontos(viewModel.estado.value)))
    }

    @Test
    fun semUsuarioNaSessaoAListaFicaCarregandoESemConsultarORepositorio() = runTest {
        repositorio.definirItens(listOf(itemDeTeste("1")))
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

        repositorio.definirItens(listOf(itemDeTeste("novo-aqui")))
        assertEquals(listOf("novo-aqui"), ids(prontos(viewModel.estado.value)))
    }

    @Test
    fun aFabricaCriaOViewModelComAsDependencias() {
        val viewModel = MeusAtendimentosViewModel.fabrica(repositorio, sessao)
            .create(MeusAtendimentosViewModel::class.java, CreationExtras.Empty)
        assertEquals(EstadoDaLista.Carregando, viewModel.estado.value)
    }

    // Os quatro testes abaixo valem para as quatro listas: todas montam o estado por observarListaDoUsuario.
    // Ficam aqui por ser a lista mais simples (sem chip nem busca).

    @Test
    fun antesDaPrimeiraRespostaDoRepositorioOEstadoECarregando() = runTest {
        repositorio.segurarResposta()
        val viewModel = criar()
        manterColetando(viewModel.estado)

        // Já pediu ao repositório, mas ele ainda não respondeu: nada de lista vazia na tela.
        assertEquals(1, repositorio.pedidosRecebidos.size)
        assertEquals(EstadoDaLista.Carregando, viewModel.estado.value)

        repositorio.definirItens(listOf(itemDeTeste("1")))
        assertEquals(listOf("1"), ids(prontos(viewModel.estado.value)))
    }

    @Test
    fun respostaVaziaDoRepositorioViraProntaVazia() = runTest {
        repositorio.definirItens(emptyList())
        val viewModel = criar()
        manterColetando(viewModel.estado)

        assertEquals(EstadoDaLista.Pronta(emptyList()), viewModel.estado.value)
    }

    @Test
    fun aoSairDaContaVoltaACarregandoEAoEntrarFicaPronta() = runTest {
        repositorio.definirItens(listOf(itemDeTeste("1")))
        val viewModel = criar()
        manterColetando(viewModel.estado)
        assertEquals(listOf("1"), ids(prontos(viewModel.estado.value)))

        sessao.entrarComo(null)
        assertEquals(EstadoDaLista.Carregando, viewModel.estado.value)

        sessao.entrarComo(SessaoFalsa.umUsuario(id = 5, tipo = TipoDeOrganizacao.CLIENTE))
        assertEquals(listOf("1"), ids(prontos(viewModel.estado.value)))
    }

    @Test
    fun mudancaNoRepositorioAtualizaAListaPronta() = runTest {
        repositorio.definirItens(listOf(itemDeTeste("1")))
        val viewModel = criar()
        manterColetando(viewModel.estado)

        repositorio.definirItens(listOf(itemDeTeste("1"), itemDeTeste("2", aberto = "2026-11-05T12:00:00Z")))
        assertEquals(listOf("2", "1"), ids(prontos(viewModel.estado.value)))
    }
}
