package com.example.netafim.atendimento.ui.edicao

import androidx.lifecycle.viewmodel.CreationExtras
import com.example.netafim.atendimento.dominio.ErroAoSalvar
import com.example.netafim.atendimento.ui.detalhe.EstadoDoDetalhe
import com.example.netafim.atendimento.ui.lista.RepositorioDeAtendimentosFalso
import com.example.netafim.atendimento.ui.lista.SessaoFalsa
import com.example.netafim.atendimento.ui.lista.atendimentoDeTeste
import com.example.netafim.atendimento.ui.lista.manterColetando
import com.example.netafim.core.modelo.AlteracaoDeAtendimento
import com.example.netafim.core.modelo.Mudanca
import com.example.netafim.core.modelo.ResultadoDaAcao
import com.example.netafim.core.modelo.SituacaoDoProjeto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
class EdicaoDoAtendimentoViewModelTest {

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

    // Cria o ViewModel já com o atendimento carregado e as duas coletas que a tela faria (estado e botão).
    private fun TestScope.abrirEdicao(
        situacao: SituacaoDoProjeto? = SituacaoDoProjeto.FORA_GARANTIA,
    ): EdicaoDoAtendimentoViewModel {
        repositorio.definirAtendimento(atendimentoDeTeste(situacaoProjeto = situacao))
        val viewModel = EdicaoDoAtendimentoViewModel("1", repositorio, sessao)
        manterColetando(viewModel.estado)
        manterColetando(viewModel.podeSalvar)
        return viewModel
    }

    @Test
    fun carregaOAtendimentoEMarcaASituacaoGravada() = runTest {
        val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)

        assertTrue(viewModel.estado.value is EstadoDoDetalhe.Pronto)
        assertEquals(SituacaoDoProjeto.FORA_GARANTIA, viewModel.escolhida.value)
        assertFalse(viewModel.podeSalvar.value)
        assertFalse(viewModel.salvando.value)
        assertNull(viewModel.erro.value)
        assertFalse(viewModel.salvo.value)
    }

    @Test
    fun atendimentoQueNaoExisteViraNaoEncontrado() = runTest {
        repositorio.definirAtendimento(null)
        val viewModel = EdicaoDoAtendimentoViewModel("1", repositorio, sessao)
        manterColetando(viewModel.estado)

        assertEquals(EstadoDoDetalhe.NaoEncontrado, viewModel.estado.value)
    }

    @Test
    fun soPodeSalvarQuandoAEscolhaMudaDeFato() = runTest {
        val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)

        viewModel.escolher(SituacaoDoProjeto.GARANTIA)
        assertTrue(viewModel.podeSalvar.value)

        // Voltar para a situação gravada apaga o botão de novo.
        viewModel.escolher(SituacaoDoProjeto.FORA_GARANTIA)
        assertFalse(viewModel.podeSalvar.value)
    }

    @Test
    fun semSituacaoGravadaQualquerEscolhaLibera() = runTest {
        val viewModel = abrirEdicao(situacao = null)
        assertNull(viewModel.escolhida.value)
        assertFalse(viewModel.podeSalvar.value)

        viewModel.escolher(SituacaoDoProjeto.INSTALACAO)

        assertTrue(viewModel.podeSalvar.value)
    }

    @Test
    fun escolhaDesconhecidaNuncaLibera() = runTest {
        val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)

        viewModel.escolher(SituacaoDoProjeto.DESCONHECIDO)

        assertFalse(viewModel.podeSalvar.value)
        viewModel.salvar()
        assertTrue(repositorio.alteracoes.isEmpty())
    }

    @Test
    fun salvarMandaAAlteracaoCertaESinalizaSalvo() = runTest {
        val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)
        viewModel.escolher(SituacaoDoProjeto.GARANTIA)

        viewModel.salvar()

        val esperada = AlteracaoDeAtendimento(situacaoProjeto = Mudanca.Definir(SituacaoDoProjeto.GARANTIA))
        assertEquals(listOf("1" to esperada), repositorio.alteracoes)
        assertTrue(viewModel.salvo.value)
        assertNull(viewModel.erro.value)
        assertFalse(viewModel.salvando.value)
    }

    @Test
    fun depoisDeSalvarASituacaoSalvaPassaAValerComoGravada() = runTest {
        val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)
        viewModel.escolher(SituacaoDoProjeto.GARANTIA)

        viewModel.salvar()

        // Não deixa salvar o mesmo valor duas vezes se a tela continuar aberta.
        assertFalse(viewModel.podeSalvar.value)
        viewModel.salvar()
        assertEquals(1, repositorio.alteracoes.size)
    }

    @Test
    fun cadaResultadoDeFalhaViraOErroCertoESemSinalDeSalvo() = runTest {
        val esperados = mapOf<ResultadoDaAcao, ErroAoSalvar>(
            ResultadoDaAcao.ConflitoDeVersao to ErroAoSalvar.ConflitoDeVersao,
            ResultadoDaAcao.SemPermissao to ErroAoSalvar.SemPermissao,
            ResultadoDaAcao.NaoEncontrado to ErroAoSalvar.NaoEncontrado,
            ResultadoDaAcao.FalhaDeRede to ErroAoSalvar.SemConexao,
            ResultadoDaAcao.RegraRecusada("só o Admin altera") to ErroAoSalvar.RegraDeNegocio("só o Admin altera"),
            ResultadoDaAcao.DadosInvalidos(mapOf("situacao_projeto" to "inválida")) to ErroAoSalvar.RegraDeNegocio(""),
        )
        esperados.forEach { (resultado, erro) ->
            repositorio.resultadoDaAlteracao = resultado
            val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)
            viewModel.escolher(SituacaoDoProjeto.GARANTIA)

            viewModel.salvar()

            assertEquals(resultado.toString(), erro, viewModel.erro.value)
            assertFalse(resultado.toString(), viewModel.salvo.value)
            assertFalse(resultado.toString(), viewModel.salvando.value)
            // Com erro o original não muda: o Admin pode tentar de novo.
            assertTrue(resultado.toString(), viewModel.podeSalvar.value)
        }
    }

    @Test
    fun semAlteracaoValidaORepositorioNaoEChamado() = runTest {
        val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)

        // Nada mudou.
        viewModel.salvar()
        assertTrue(repositorio.alteracoes.isEmpty())
        assertFalse(viewModel.salvo.value)

        // Sem situação gravada e nada escolhido.
        val semSituacao = abrirEdicao(situacao = null)
        semSituacao.salvar()
        assertTrue(repositorio.alteracoes.isEmpty())
    }

    @Test
    fun escolherLimpaOErroAnterior() = runTest {
        repositorio.resultadoDaAlteracao = ResultadoDaAcao.FalhaDeRede
        val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)
        viewModel.escolher(SituacaoDoProjeto.GARANTIA)
        viewModel.salvar()
        assertEquals(ErroAoSalvar.SemConexao, viewModel.erro.value)

        viewModel.escolher(SituacaoDoProjeto.INSTALACAO)

        assertNull(viewModel.erro.value)
    }

    @Test
    fun oOriginalNaoMudaQuandoORepositorioTrazVersaoNovaDuranteAEdicao() = runTest {
        val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)
        viewModel.escolher(SituacaoDoProjeto.GARANTIA)

        // Uma sincronização muda a situação gravada para INSTALACAO enquanto o Admin edita.
        repositorio.definirAtendimento(atendimentoDeTeste(situacaoProjeto = SituacaoDoProjeto.INSTALACAO))

        // A escolha dele e o botão continuam como estavam: o original é o primeiro carregado.
        assertEquals(SituacaoDoProjeto.GARANTIA, viewModel.escolhida.value)
        assertTrue(viewModel.podeSalvar.value)
        // O estado, esse sim, acompanha o dado novo.
        val estado = viewModel.estado.value as EstadoDoDetalhe.Pronto
        assertEquals(SituacaoDoProjeto.INSTALACAO, estado.atendimento.situacaoProjeto)
    }

    @Test
    fun versaoNovaNaoAcendeOBotaoDeQuemNaoMexeu() = runTest {
        val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)
        assertFalse(viewModel.podeSalvar.value)

        repositorio.definirAtendimento(atendimentoDeTeste(situacaoProjeto = SituacaoDoProjeto.INSTALACAO))

        assertEquals(SituacaoDoProjeto.FORA_GARANTIA, viewModel.escolhida.value)
        assertFalse(viewModel.podeSalvar.value)
    }

    @Test
    fun recarregarAdotaAVersaoMaisNovaComoOriginalEComoEscolha() = runTest {
        repositorio.resultadoDaAlteracao = ResultadoDaAcao.ConflitoDeVersao
        val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)
        viewModel.escolher(SituacaoDoProjeto.GARANTIA)
        viewModel.salvar()
        assertEquals(ErroAoSalvar.ConflitoDeVersao, viewModel.erro.value)
        repositorio.definirAtendimento(atendimentoDeTeste(situacaoProjeto = SituacaoDoProjeto.INSTALACAO))

        viewModel.recarregar()

        assertNull(viewModel.erro.value)
        assertEquals(SituacaoDoProjeto.INSTALACAO, viewModel.escolhida.value)
        assertFalse(viewModel.podeSalvar.value)
    }

    @Test
    fun toqueDuploEnquantoSalvaEIgnorado() = runTest {
        val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)
        viewModel.escolher(SituacaoDoProjeto.GARANTIA)
        val portao = CompletableDeferred<Unit>()
        repositorio.portao = portao

        viewModel.salvar()
        assertTrue(viewModel.salvando.value)
        assertFalse(viewModel.podeSalvar.value)
        viewModel.salvar()
        assertEquals(1, repositorio.alteracoes.size)

        portao.complete(Unit)
        assertFalse(viewModel.salvando.value)
        assertTrue(viewModel.salvo.value)
    }

    @Test
    fun semUsuarioNaSessaoNaoSalva() = runTest {
        val viewModel = abrirEdicao(SituacaoDoProjeto.FORA_GARANTIA)
        viewModel.escolher(SituacaoDoProjeto.GARANTIA)
        sessao.entrarComo(null)

        viewModel.salvar()

        assertTrue(repositorio.alteracoes.isEmpty())
        assertFalse(viewModel.salvo.value)
    }

    @Test
    fun aFabricaCriaOViewModelComAsDependencias() {
        val viewModel = EdicaoDoAtendimentoViewModel.fabrica("1", repositorio, sessao)
            .create(EdicaoDoAtendimentoViewModel::class.java, CreationExtras.Empty)
        assertEquals(EstadoDoDetalhe.Carregando, viewModel.estado.value)
    }
}
