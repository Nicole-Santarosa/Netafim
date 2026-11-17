package com.example.netafim.atendimento.ui.abrir

import androidx.lifecycle.viewmodel.CreationExtras
import com.example.netafim.atendimento.dominio.ErroAoSalvar
import com.example.netafim.atendimento.dominio.ErroCampo
import com.example.netafim.atendimento.dominio.FormularioAbertura
import com.example.netafim.atendimento.dominio.OrigemDaAbertura
import com.example.netafim.atendimento.dominio.comoSolicitacao
import com.example.netafim.atendimento.ui.lista.RepositorioDeAtendimentosFalso
import com.example.netafim.atendimento.ui.lista.SessaoFalsa
import com.example.netafim.atendimento.ui.lista.manterColetando
import com.example.netafim.core.modelo.AreaDoSetor
import com.example.netafim.core.modelo.Cultura
import com.example.netafim.core.modelo.Distribuidor
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.ResultadoDaAcao
import com.example.netafim.core.modelo.SolicitacaoDeAbertura
import com.example.netafim.core.modelo.sessao.TipoDeOrganizacao
import java.time.Instant
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
class AbrirAtendimentoViewModelTest {

    // Usuário de cliente da empresa 42, que é quem abre atendimento na maioria dos casos.
    private val sessao = SessaoFalsa(
        SessaoFalsa.umUsuario(id = 5, tipo = TipoDeOrganizacao.CLIENTE, idDaOrganizacao = 42),
    )
    private val repositorio = RepositorioDeAtendimentosFalso(sessao)

    // Valores fixos no lugar do UUID sorteado e da hora real, para o teste conferir o que foi enviado.
    private val idFixo = "00000000-0000-4000-8000-000000000001"
    private val instanteFixo = Instant.parse("2026-11-17T12:00:00Z")

    private val formularioValido = FormularioAbertura(
        motivo = MotivoAtendimento.POS_VENDA,
        culturas = setOf(1, 2),
        cidade = "Arealva",
        nomeFazenda = "Fazenda Boa Vista",
        contato = "14999990000",
        idDistribuidor = 3,
    )

    @Before
    fun antes() {
        // O viewModelScope roda no Dispatchers.Main, que não existe na JVM: troca por um de teste.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun depois() {
        Dispatchers.resetMain()
    }

    private fun criar() = AbrirAtendimentoViewModel(
        repositorio = repositorio,
        sessao = sessao,
        gerarId = { idFixo },
        relogio = { instanteFixo },
    )

    @Test
    fun estadoInicialTemFormularioVazioSemErrosNemEnvio() {
        val viewModel = criar()

        assertEquals(FormularioAbertura(), viewModel.formulario.value)
        assertTrue(viewModel.erros.value.valido)
        assertFalse(viewModel.enviando.value)
        assertNull(viewModel.erro.value)
        assertNull(viewModel.aberto.value)
    }

    @Test
    fun exibeOsCatalogosDeCulturasEDistribuidores() = runTest {
        repositorio.definirCulturas(listOf(Cultura(1, "Café"), Cultura(2, "Citrus")))
        repositorio.definirDistribuidores(listOf(Distribuidor(3, "Revenda Centro")))
        val viewModel = criar()
        manterColetando(viewModel.culturas)
        manterColetando(viewModel.distribuidores)

        assertEquals(listOf(Cultura(1, "Café"), Cultura(2, "Citrus")), viewModel.culturas.value)
        assertEquals(listOf(Distribuidor(3, "Revenda Centro")), viewModel.distribuidores.value)
    }

    @Test
    fun alterarAtualizaOFormulario() {
        val viewModel = criar()

        viewModel.alterar(formularioValido)

        assertEquals(formularioValido, viewModel.formulario.value)
    }

    @Test
    fun formularioInvalidoExpoeOsErrosENaoChamaORepositorio() = runTest {
        val viewModel = criar()

        viewModel.enviar()

        val erros = viewModel.erros.value
        assertEquals(ErroCampo.OBRIGATORIO, erros.motivo)
        assertEquals(ErroCampo.OBRIGATORIO, erros.cidade)
        assertEquals(ErroCampo.OBRIGATORIO, erros.nomeFazenda)
        assertEquals(ErroCampo.OBRIGATORIO, erros.culturas)
        assertEquals(ErroCampo.OBRIGATORIO, erros.distribuidor)
        assertEquals(ErroCampo.OBRIGATORIO, erros.contato)
        assertTrue(repositorio.aberturas.isEmpty())
        assertFalse(viewModel.enviando.value)
        assertNull(viewModel.aberto.value)
    }

    @Test
    fun depoisDaPrimeiraTentativaOsErrosSomemConformeACorrecao() = runTest {
        val viewModel = criar()
        viewModel.enviar()
        assertFalse(viewModel.erros.value.valido)

        viewModel.alterar(formularioValido)

        assertTrue(viewModel.erros.value.valido)
    }

    @Test
    fun antesDeTentarEnviarNaoMostraErroNenhum() {
        val viewModel = criar()

        // Mexer no formulário ainda incompleto não acende erro: só depois de tentar enviar.
        viewModel.alterar(FormularioAbertura(cidade = "Arealva"))

        assertTrue(viewModel.erros.value.valido)
    }

    @Test
    fun formularioValidoChamaORepositorioComIdEHoraInjetados() = runTest {
        val viewModel = criar()
        viewModel.alterar(formularioValido)

        viewModel.enviar()

        val esperada = comoSolicitacao(
            formulario = formularioValido,
            id = idFixo,
            origem = OrigemDaAbertura(OrigemAtendimento.CLIENTE_FINAL, idCliente = 42, idDistribuidorOrigem = null),
            abertoEm = instanteFixo,
        )
        assertEquals(listOf(esperada), repositorio.aberturas)
        assertEquals(idFixo, repositorio.aberturas.single().id)
        assertEquals(instanteFixo, repositorio.aberturas.single().abertoEm)
        assertEquals(3, repositorio.aberturas.single().idDistribuidorOrigem)
    }

    // Formulários válidos do distribuidor e da Netafim: cada um só com os campos da sua origem.
    private val formularioDoDistribuidor = FormularioAbertura(
        motivo = MotivoAtendimento.DUVIDAS,
        culturas = setOf(2),
        cidade = "Bauru",
        contato = "14988880000",
        nomeProdutor = "José da Silva",
    )
    private val formularioDaNetafim = formularioDoDistribuidor.copy(
        nomeProdutor = "Maria Souza",
        areaSetor = AreaDoSetor.POS_VENDA,
    )

    @Test
    fun aOrigemExpostaSegueOTipoDoUsuario() = runTest {
        val viewModel = criar()
        manterColetando(viewModel.origem)
        assertEquals(
            OrigemDaAbertura(OrigemAtendimento.CLIENTE_FINAL, idCliente = 42, idDistribuidorOrigem = null),
            viewModel.origem.value,
        )

        sessao.entrarComo(SessaoFalsa.umUsuario(tipo = TipoDeOrganizacao.DISTRIBUIDOR, idDaOrganizacao = 7))
        assertEquals(
            OrigemDaAbertura(OrigemAtendimento.DISTRIBUIDOR, idCliente = null, idDistribuidorOrigem = 7),
            viewModel.origem.value,
        )

        sessao.entrarComo(SessaoFalsa.umUsuario(tipo = TipoDeOrganizacao.NETAFIM, idDaOrganizacao = null))
        assertEquals(
            OrigemDaAbertura(OrigemAtendimento.NETAFIM, idCliente = null, idDistribuidorOrigem = null),
            viewModel.origem.value,
        )
    }

    @Test
    fun aOrigemENulaSemUsuarioOuSemOrganizacao() = runTest {
        sessao.entrarComo(null)
        val viewModel = criar()
        manterColetando(viewModel.origem)
        assertNull(viewModel.origem.value)

        sessao.entrarComo(SessaoFalsa.umUsuario(tipo = TipoDeOrganizacao.CLIENTE, idDaOrganizacao = null))
        assertNull(viewModel.origem.value)

        sessao.entrarComo(SessaoFalsa.umUsuario(tipo = TipoDeOrganizacao.DISTRIBUIDOR, idDaOrganizacao = null))
        assertNull(viewModel.origem.value)
    }

    @Test
    fun usuarioDaNetafimMandaProdutorEArea() = runTest {
        sessao.entrarComo(SessaoFalsa.umUsuario(tipo = TipoDeOrganizacao.NETAFIM, idDaOrganizacao = null))
        val viewModel = criar()
        viewModel.alterar(formularioDaNetafim)

        viewModel.enviar()

        val esperada = SolicitacaoDeAbertura(
            id = idFixo,
            origem = OrigemAtendimento.NETAFIM,
            motivo = MotivoAtendimento.DUVIDAS,
            idCliente = null,
            idDistribuidorOrigem = null,
            nomeProdutor = "Maria Souza",
            nomeFazenda = "",
            cidade = "Bauru",
            areaSetor = AreaDoSetor.POS_VENDA,
            contato = "14988880000",
            tipoVenda = null,
            culturas = setOf(2),
            abertoEm = instanteFixo,
        )
        assertEquals(listOf(esperada), repositorio.aberturas)
    }

    @Test
    fun usuarioDeDistribuidorMandaProdutorEOProprioDistribuidor() = runTest {
        sessao.entrarComo(SessaoFalsa.umUsuario(tipo = TipoDeOrganizacao.DISTRIBUIDOR, idDaOrganizacao = 7))
        val viewModel = criar()
        viewModel.alterar(formularioDoDistribuidor)

        viewModel.enviar()

        val esperada = SolicitacaoDeAbertura(
            id = idFixo,
            origem = OrigemAtendimento.DISTRIBUIDOR,
            motivo = MotivoAtendimento.DUVIDAS,
            idCliente = null,
            idDistribuidorOrigem = 7,
            nomeProdutor = "José da Silva",
            nomeFazenda = "",
            cidade = "Bauru",
            areaSetor = null,
            contato = "14988880000",
            tipoVenda = null,
            culturas = setOf(2),
            abertoEm = instanteFixo,
        )
        assertEquals(listOf(esperada), repositorio.aberturas)
    }

    @Test
    fun formularioIncompletoDeCadaOrigemNaoChamaORepositorio() = runTest {
        // Distribuidor com o formulário do cliente: falta o nome do produtor.
        sessao.entrarComo(SessaoFalsa.umUsuario(tipo = TipoDeOrganizacao.DISTRIBUIDOR, idDaOrganizacao = 7))
        val doDistribuidor = criar()
        doDistribuidor.alterar(formularioValido)
        doDistribuidor.enviar()
        assertEquals(ErroCampo.OBRIGATORIO, doDistribuidor.erros.value.nomeProdutor)
        // A fazenda e o distribuidor não são dele: não aparecem como erro.
        assertNull(doDistribuidor.erros.value.nomeFazenda)

        // Netafim com o formulário do distribuidor: falta a área.
        sessao.entrarComo(SessaoFalsa.umUsuario(tipo = TipoDeOrganizacao.NETAFIM, idDaOrganizacao = null))
        val daNetafim = criar()
        daNetafim.alterar(formularioDoDistribuidor)
        daNetafim.enviar()
        assertEquals(ErroCampo.OBRIGATORIO, daNetafim.erros.value.areaSetor)

        // Cliente com o formulário da Netafim: faltam a fazenda e o distribuidor.
        sessao.entrarComo(SessaoFalsa.umUsuario(tipo = TipoDeOrganizacao.CLIENTE, idDaOrganizacao = 42))
        val doCliente = criar()
        doCliente.alterar(formularioDaNetafim)
        doCliente.enviar()
        assertEquals(ErroCampo.OBRIGATORIO, doCliente.erros.value.nomeFazenda)
        assertEquals(ErroCampo.OBRIGATORIO, doCliente.erros.value.distribuidor)

        assertTrue(repositorio.aberturas.isEmpty())
    }

    @Test
    fun usuarioDeClienteAbreComoClienteFinalComAPropriaEmpresa() = runTest {
        val viewModel = criar()
        viewModel.alterar(formularioValido)

        viewModel.enviar()

        val solicitacao = repositorio.aberturas.single()
        assertEquals(OrigemAtendimento.CLIENTE_FINAL, solicitacao.origem)
        assertEquals(42, solicitacao.idCliente)
    }

    @Test
    fun clienteSemOrganizacaoViraSemPermissaoSemChamarORepositorio() = runTest {
        sessao.entrarComo(SessaoFalsa.umUsuario(tipo = TipoDeOrganizacao.CLIENTE, idDaOrganizacao = null))
        val viewModel = criar()
        viewModel.alterar(formularioValido)

        viewModel.enviar()

        assertEquals(ErroAoSalvar.SemPermissao, viewModel.erro.value)
        assertTrue(repositorio.aberturas.isEmpty())
        assertFalse(viewModel.enviando.value)
    }

    @Test
    fun sucessoSinalizaOAtendimentoAbertoComOIdGerado() = runTest {
        val viewModel = criar()
        viewModel.alterar(formularioValido)

        viewModel.enviar()

        assertEquals(idFixo, viewModel.aberto.value)
        assertNull(viewModel.erro.value)
        assertFalse(viewModel.enviando.value)
    }

    @Test
    fun cadaResultadoDeFalhaViraOErroCertoESemSinalDeAberto() = runTest {
        val esperados = mapOf<ResultadoDaAcao, ErroAoSalvar>(
            ResultadoDaAcao.ConflitoDeVersao to ErroAoSalvar.ConflitoDeVersao,
            ResultadoDaAcao.SemPermissao to ErroAoSalvar.SemPermissao,
            ResultadoDaAcao.NaoEncontrado to ErroAoSalvar.NaoEncontrado,
            ResultadoDaAcao.FalhaDeRede to ErroAoSalvar.SemConexao,
            ResultadoDaAcao.RegraRecusada("distribuidor inativo") to
                ErroAoSalvar.RegraDeNegocio("distribuidor inativo"),
            ResultadoDaAcao.DadosInvalidos(mapOf("cidade" to "inválida")) to ErroAoSalvar.RegraDeNegocio(""),
        )
        esperados.forEach { (resultado, erro) ->
            repositorio.resultadoDaAbertura = resultado
            val viewModel = criar()
            viewModel.alterar(formularioValido)

            viewModel.enviar()

            assertEquals(resultado.toString(), erro, viewModel.erro.value)
            assertNull(resultado.toString(), viewModel.aberto.value)
            assertFalse(resultado.toString(), viewModel.enviando.value)
        }
    }

    @Test
    fun reenvioDepoisDeErroMandaAMesmaSolicitacaoComOMesmoId() = runTest {
        // Um id diferente a cada chamada: se o ViewModel gerasse de novo, o segundo envio sairia com outro id.
        var chamadasDoGerador = 0
        val viewModel = AbrirAtendimentoViewModel(
            repositorio = repositorio,
            sessao = sessao,
            gerarId = { "id-${++chamadasDoGerador}" },
            relogio = { instanteFixo },
        )
        viewModel.alterar(formularioValido)

        // A primeira tentativa falha.
        repositorio.resultadoDaAbertura = ResultadoDaAcao.FalhaDeRede
        viewModel.enviar()
        assertEquals(ErroAoSalvar.SemConexao, viewModel.erro.value)
        assertNull(viewModel.aberto.value)

        // A segunda tentativa dá certo.
        repositorio.resultadoDaAbertura = ResultadoDaAcao.Sucesso
        viewModel.enviar()

        assertEquals(2, repositorio.aberturas.size)
        assertEquals(repositorio.aberturas[0], repositorio.aberturas[1])
        assertEquals("id-1", repositorio.aberturas[1].id)
        assertEquals(1, chamadasDoGerador)
        assertEquals("id-1", viewModel.aberto.value)
    }

    @Test
    fun formularioInvalidoNaoLigaATravaDeEnvio() = runTest {
        val viewModel = criar()

        viewModel.enviar()
        assertFalse(viewModel.enviando.value)
        assertTrue(repositorio.aberturas.isEmpty())

        // Corrigido o formulário, o envio acontece normalmente.
        viewModel.alterar(formularioValido)
        viewModel.enviar()

        assertEquals(1, repositorio.aberturas.size)
        assertEquals(idFixo, viewModel.aberto.value)
    }

    @Test
    fun clienteSemOrganizacaoNaoLigaATravaDeEnvio() = runTest {
        sessao.entrarComo(SessaoFalsa.umUsuario(tipo = TipoDeOrganizacao.CLIENTE, idDaOrganizacao = null))
        val viewModel = criar()
        viewModel.alterar(formularioValido)
        viewModel.enviar()
        assertFalse(viewModel.enviando.value)

        // Entra um usuário com empresa: o envio passa.
        sessao.entrarComo(SessaoFalsa.umUsuario(tipo = TipoDeOrganizacao.CLIENTE, idDaOrganizacao = 42))
        viewModel.enviar()

        assertEquals(1, repositorio.aberturas.size)
    }

    @Test
    fun dispensarErroLimpaOAviso() = runTest {
        repositorio.resultadoDaAbertura = ResultadoDaAcao.FalhaDeRede
        val viewModel = criar()
        viewModel.alterar(formularioValido)
        viewModel.enviar()
        assertEquals(ErroAoSalvar.SemConexao, viewModel.erro.value)

        viewModel.dispensarErro()

        assertNull(viewModel.erro.value)
    }

    @Test
    fun envioDuploEnquantoEnviaEIgnorado() = runTest {
        val portao = CompletableDeferred<Unit>()
        repositorio.portao = portao
        val viewModel = criar()
        viewModel.alterar(formularioValido)

        viewModel.enviar()
        assertTrue(viewModel.enviando.value)
        viewModel.enviar()
        assertEquals(1, repositorio.aberturas.size)

        portao.complete(Unit)
        assertFalse(viewModel.enviando.value)
        assertEquals(idFixo, viewModel.aberto.value)
    }

    @Test
    fun semUsuarioNaSessaoEnviarNaoFazNada() = runTest {
        sessao.entrarComo(null)
        val viewModel = criar()
        viewModel.alterar(formularioValido)

        viewModel.enviar()

        assertTrue(repositorio.aberturas.isEmpty())
        assertNull(viewModel.aberto.value)
        assertNull(viewModel.erro.value)
        assertFalse(viewModel.enviando.value)
    }

    @Test
    fun aFabricaCriaOViewModelComOsPadroesDeProducao() {
        val viewModel = AbrirAtendimentoViewModel.fabrica(repositorio, sessao)
            .create(AbrirAtendimentoViewModel::class.java, CreationExtras.Empty)
        assertEquals(FormularioAbertura(), viewModel.formulario.value)
    }
}
