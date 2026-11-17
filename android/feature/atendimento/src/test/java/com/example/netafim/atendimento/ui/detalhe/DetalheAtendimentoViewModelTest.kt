package com.example.netafim.atendimento.ui.detalhe

import androidx.lifecycle.viewmodel.CreationExtras
import com.example.netafim.atendimento.dominio.ErroAoSalvar
import com.example.netafim.atendimento.ui.lista.RepositorioDeAtendimentosFalso
import com.example.netafim.atendimento.ui.lista.SessaoFalsa
import com.example.netafim.atendimento.ui.lista.atendimentoDeTeste
import com.example.netafim.atendimento.ui.lista.manterColetando
import com.example.netafim.core.modelo.ResultadoDaAcao
import com.example.netafim.core.modelo.StatusAtendimento
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetalheAtendimentoViewModelTest {

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

    private fun criar(id: String = "1") = DetalheAtendimentoViewModel(id, repositorio, sessao)

    @Test
    fun antesDeColetarOEstadoEhCarregando() {
        assertEquals(EstadoDoDetalhe.Carregando, criar().estado.value)
    }

    @Test
    fun atendimentoQueNaoExisteViraNaoEncontrado() = runTest {
        repositorio.definirAtendimento(null)
        val viewModel = criar()
        manterColetando(viewModel.estado)

        assertEquals(EstadoDoDetalhe.NaoEncontrado, viewModel.estado.value)
    }

    @Test
    fun idDiferenteDoDefinidoTambemViraNaoEncontrado() = runTest {
        repositorio.definirAtendimento(atendimentoDeTeste(id = "outro"))
        val viewModel = criar(id = "1")
        manterColetando(viewModel.estado)

        assertEquals(EstadoDoDetalhe.NaoEncontrado, viewModel.estado.value)
    }

    @Test
    fun atendimentoEncontradoViraProntoEAcompanhaMudancas() = runTest {
        val agendado = atendimentoDeTeste(status = StatusAtendimento.AGENDADO)
        repositorio.definirAtendimento(agendado)
        val viewModel = criar()
        manterColetando(viewModel.estado)
        assertEquals(EstadoDoDetalhe.Pronto(agendado), viewModel.estado.value)

        // O registro mudou no aparelho (por exemplo, o atendimento foi iniciado): o estado acompanha.
        val emAtendimento = atendimentoDeTeste(status = StatusAtendimento.EM_ATENDIMENTO)
        repositorio.definirAtendimento(emAtendimento)
        assertEquals(EstadoDoDetalhe.Pronto(emAtendimento), viewModel.estado.value)
    }

    @Test
    fun iniciarComSucessoChamaORepositorioSemErro() = runTest {
        val viewModel = criar(id = "1")

        viewModel.iniciar()

        assertEquals(listOf("1"), repositorio.iniciados)
        assertNull(viewModel.erro.value)
        assertFalse(viewModel.iniciando.value)
    }

    @Test
    fun cadaResultadoDeFalhaViraOErroCerto() = runTest {
        val esperados = mapOf<ResultadoDaAcao, ErroAoSalvar>(
            ResultadoDaAcao.ConflitoDeVersao to ErroAoSalvar.ConflitoDeVersao,
            ResultadoDaAcao.SemPermissao to ErroAoSalvar.SemPermissao,
            ResultadoDaAcao.NaoEncontrado to ErroAoSalvar.NaoEncontrado,
            ResultadoDaAcao.FalhaDeRede to ErroAoSalvar.SemConexao,
            ResultadoDaAcao.RegraRecusada("só o técnico inicia") to ErroAoSalvar.RegraDeNegocio("só o técnico inicia"),
            ResultadoDaAcao.DadosInvalidos(mapOf("status" to "inválido")) to ErroAoSalvar.RegraDeNegocio(""),
        )
        esperados.forEach { (resultado, erro) ->
            val viewModel = criar()
            repositorio.resultadoDoInicio = resultado

            viewModel.iniciar()

            assertEquals(resultado.toString(), erro, viewModel.erro.value)
            // Terminou: o botão volta ao normal mesmo com erro.
            assertFalse(resultado.toString(), viewModel.iniciando.value)
        }
    }

    @Test
    fun dispensarErroLimpaOAviso() = runTest {
        repositorio.resultadoDoInicio = ResultadoDaAcao.FalhaDeRede
        val viewModel = criar()
        viewModel.iniciar()
        assertEquals(ErroAoSalvar.SemConexao, viewModel.erro.value)

        viewModel.dispensarErro()

        assertNull(viewModel.erro.value)
    }

    @Test
    fun novoPedidoLimpaOErroAnterior() = runTest {
        repositorio.resultadoDoInicio = ResultadoDaAcao.FalhaDeRede
        val viewModel = criar()
        viewModel.iniciar()
        assertEquals(ErroAoSalvar.SemConexao, viewModel.erro.value)

        repositorio.resultadoDoInicio = ResultadoDaAcao.Sucesso
        viewModel.iniciar()

        assertNull(viewModel.erro.value)
        assertEquals(2, repositorio.iniciados.size)
    }

    @Test
    fun toqueDuploEnquantoIniciaEIgnorado() = runTest {
        val portao = CompletableDeferred<Unit>()
        repositorio.portao = portao
        val viewModel = criar()

        viewModel.iniciar()
        assertTrue(viewModel.iniciando.value)
        // Segundo toque com o primeiro pedido ainda rodando.
        viewModel.iniciar()
        assertEquals(1, repositorio.iniciados.size)

        portao.complete(Unit)
        assertFalse(viewModel.iniciando.value)

        // Depois de terminar, um novo toque volta a valer.
        repositorio.portao = null
        viewModel.iniciar()
        assertEquals(2, repositorio.iniciados.size)
    }

    @Test
    fun semUsuarioNaSessaoNaoConsultaNemIniciaENadaMuda() = runTest {
        repositorio.definirAtendimento(atendimentoDeTeste())
        sessao.entrarComo(null)
        val viewModel = criar()
        manterColetando(viewModel.estado)

        viewModel.iniciar()

        assertEquals(EstadoDoDetalhe.Carregando, viewModel.estado.value)
        assertTrue(repositorio.idsObservados.isEmpty())
        assertTrue(repositorio.iniciados.isEmpty())
        assertFalse(viewModel.iniciando.value)
    }

    @Test
    fun aoEntrarOAtendimentoAparece() = runTest {
        repositorio.definirAtendimento(atendimentoDeTeste())
        sessao.entrarComo(null)
        val viewModel = criar()
        manterColetando(viewModel.estado)
        assertEquals(EstadoDoDetalhe.Carregando, viewModel.estado.value)

        sessao.entrarComo(SessaoFalsa.umUsuario())

        assertTrue(viewModel.estado.value is EstadoDoDetalhe.Pronto)
    }

    @Test
    fun aFabricaCriaOViewModelComAsDependencias() {
        val viewModel = DetalheAtendimentoViewModel.fabrica("1", repositorio, sessao)
            .create(DetalheAtendimentoViewModel::class.java, CreationExtras.Empty)
        assertEquals(EstadoDoDetalhe.Carregando, viewModel.estado.value)
    }
}
