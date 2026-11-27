package com.example.netafim.feature.relatorio

import com.example.netafim.feature.relatorio.data.RelatorioRepositorio
import com.example.netafim.feature.relatorio.model.EtapaEvidencia
import com.example.netafim.feature.relatorio.model.EtapaRelatorio
import com.example.netafim.feature.relatorio.model.EvidenciaItem
import com.example.netafim.feature.relatorio.model.MotivoAtendimento
import com.example.netafim.feature.relatorio.model.OrigemCapturaEvidencia
import com.example.netafim.feature.relatorio.model.RelatorioRascunho
import com.example.netafim.feature.relatorio.model.TipoEvidencia
import com.example.netafim.feature.relatorio.ui.RelatorioViewModel
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorioViewModelTest {

    private fun criarVm(repo: RelatorioRepositorio): RelatorioViewModel =
        RelatorioViewModel(
            repositorio = repo,
            escopoAssincrono = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        )

    private class FakeRelatorioRepositorio : RelatorioRepositorio {
        var modo: Modo = Modo.SUCESSO
        var chamadasSalvar: Int = 0
        var ultimoSalvo: RelatorioRascunho? = null
        var bloqueio: CompletableDeferred<Result<Unit>>? = null
        var entrouEmSalvar: CompletableDeferred<Unit>? = null

        enum class Modo { SUCESSO, FALHA_RETORNO, EXCECAO, BLOQUEADO }

        override suspend fun salvarRelatorio(rascunho: RelatorioRascunho): Result<Unit> {
            chamadasSalvar++
            ultimoSalvo = rascunho
            entrouEmSalvar?.complete(Unit)
            return when (modo) {
                Modo.SUCESSO -> Result.success(Unit)
                Modo.FALHA_RETORNO -> Result.failure(IOException("Falha ao gravar localmente"))
                Modo.EXCECAO -> throw RuntimeException("Erro inesperado na persistência")
                Modo.BLOQUEADO -> bloqueio?.await() ?: Result.success(Unit)
            }
        }

        override suspend fun recuperarRelatorio(idAtendimento: String): Result<RelatorioRascunho?> =
            Result.success(ultimoSalvo)
    }

    private fun evidencia(id: String, etapa: EtapaEvidencia, tipo: TipoEvidencia = TipoEvidencia.FOTO) =
        EvidenciaItem(
            id = id,
            etapa = etapa,
            tipo = tipo,
            origemCaptura = if (tipo == TipoEvidencia.DOCUMENTO) {
                OrigemCapturaEvidencia.ANEXO
            } else {
                OrigemCapturaEvidencia.CAMERA_APP
            },
            caminhoLocal = "/data/user/0/com.example.netafim/files/relatorio/$id",
            nomeArquivo = "$id.bin",
            mimeType = if (tipo == TipoEvidencia.DOCUMENTO) "application/pdf" else "image/jpeg",
            tamanhoBytes = 1024L,
            disponivel = true
        )

    @Test
    fun entregaTecnicaNaoExigeConstatacoesEAvancaParaAtividades() {
        val vm = criarVm(FakeRelatorioRepositorio())
        vm.atualizarMotivo(MotivoAtendimento.ENTREGA_TECNICA)

        val avancou = vm.avancarEtapa()

        assertTrue(avancou)
        assertEquals(EtapaRelatorio.ATIVIDADES, vm.uiState.value.etapaAtual)
        assertNull(vm.uiState.value.mensagemErro)
    }

    @Test
    fun programacaoFeirasExigeAssinaturaEspecialistaSemExigirCliente() {
        val repo = FakeRelatorioRepositorio()
        val vm = criarVm(repo)
        vm.atualizarMotivo(MotivoAtendimento.PROGRAMACAO_FEIRAS)
        vm.atualizarConclusaoVisita("Feira concluída")
        vm.adicionarEvidencia(evidencia("ev-feira", EtapaEvidencia.GERAL))
        vm.selecionarEtapa(EtapaRelatorio.ASSINATURA)

        vm.salvarRelatorio()
        assertFalse(vm.uiState.value.salvoComSucesso)
        assertNotNull(vm.uiState.value.mensagemErro)

        vm.confirmarAssinaturaEspecialista("/tmp/assinatura_esp.png", "Especialista")
        vm.salvarRelatorio()

        assertTrue(vm.uiState.value.salvoComSucesso)
        assertNull(vm.uiState.value.mensagemErro)
        assertEquals(1, repo.chamadasSalvar)
    }

    @Test
    fun treinamentoValidaCamposEspecificosEEvidenciaGeral() {
        val vm = criarVm(FakeRelatorioRepositorio())
        vm.atualizarMotivo(MotivoAtendimento.TREINAMENTO)
        vm.atualizarTipoTreinamento("Operação/Manutenção")
        vm.atualizarLocalTreinamento("Fazenda Santa Maria")
        vm.atualizarQtdPessoasTreinadas("12")

        assertTrue(vm.avancarEtapa())
        assertEquals(EtapaRelatorio.ATIVIDADES, vm.uiState.value.etapaAtual)
        assertTrue(vm.avancarEtapa())
        assertEquals(EtapaRelatorio.PLANO_ACAO, vm.uiState.value.etapaAtual)

        vm.atualizarConclusaoVisita("Treinamento finalizado")
        assertFalse(vm.avancarEtapa())
        assertNotNull(vm.uiState.value.mensagemErro)

        vm.adicionarEvidencia(evidencia("ev-trein", EtapaEvidencia.GERAL))
        assertTrue(vm.avancarEtapa())
        assertEquals(EtapaRelatorio.ASSINATURA, vm.uiState.value.etapaAtual)
    }

    @Test
    fun digitalFarmingDuvidasEPosVendaRespeitamRegrasPorMotivo() {
        val vm = criarVm(FakeRelatorioRepositorio())

        vm.atualizarMotivo(MotivoAtendimento.DUVIDAS)
        assertFalse(vm.avancarEtapa())
        vm.atualizarTipoAtendimento("Presencial")
        vm.atualizarDescricaoConstatacoes("Verificado controlador")
        assertFalse(vm.avancarEtapa())
        vm.adicionarEvidencia(evidencia("ev-duv", EtapaEvidencia.CONSTATACAO))
        assertTrue(vm.avancarEtapa())
        assertEquals(EtapaRelatorio.ATIVIDADES, vm.uiState.value.etapaAtual)
        assertTrue(vm.avancarEtapa())
        assertEquals(EtapaRelatorio.PLANO_ACAO, vm.uiState.value.etapaAtual)

        vm.atualizarMotivo(MotivoAtendimento.DIGITAL_FARMING)
        vm.selecionarEtapa(EtapaRelatorio.ATIVIDADES)
        assertFalse(vm.avancarEtapa())
        vm.atualizarAtividadesExecutadas("Teste de telemetria")
        assertTrue(vm.avancarEtapa())
        assertFalse(vm.avancarEtapa())
        vm.atualizarPlanoAcao("Substituir sensor")
        vm.atualizarConclusaoVisita("Concluído")
        assertTrue(vm.avancarEtapa())

        vm.atualizarMotivo(MotivoAtendimento.POS_VENDA)
        vm.selecionarEtapa(EtapaRelatorio.CONSTATACOES)
        vm.atualizarDescricaoConstatacoes("Vazamento em válvula")
        vm.adicionarEvidencia(evidencia("ev-pos", EtapaEvidencia.CONSTATACAO))
        assertTrue(vm.avancarEtapa())
    }

    @Test
    fun persistenciaFalhaExcecaoBloqueioSimultaneoERetentativa() {
        val repo = FakeRelatorioRepositorio()
        val vm = criarVm(repo)
        vm.atualizarMotivo(MotivoAtendimento.PROGRAMACAO_FEIRAS)
        vm.atualizarConclusaoVisita("Concluído")
        vm.adicionarEvidencia(evidencia("ev-1", EtapaEvidencia.GERAL))
        vm.confirmarAssinaturaEspecialista("/tmp/esp.png", "Técnico")
        vm.selecionarEtapa(EtapaRelatorio.ASSINATURA)

        repo.modo = FakeRelatorioRepositorio.Modo.FALHA_RETORNO
        vm.salvarRelatorio()
        assertFalse(vm.uiState.value.salvoComSucesso)
        assertNotNull(vm.uiState.value.mensagemErro)

        repo.modo = FakeRelatorioRepositorio.Modo.EXCECAO
        vm.salvarRelatorio()
        assertFalse(vm.uiState.value.salvoComSucesso)
        assertNotNull(vm.uiState.value.mensagemErro)

        val entrou = CompletableDeferred<Unit>()
        val bloqueio = CompletableDeferred<Result<Unit>>()
        repo.entrouEmSalvar = entrou
        repo.bloqueio = bloqueio
        repo.modo = FakeRelatorioRepositorio.Modo.BLOQUEADO
        val chamadasAntes = repo.chamadasSalvar

        vm.salvarRelatorio()
        assertTrue(entrou.isCompleted)
        vm.salvarRelatorio()

        assertEquals(chamadasAntes + 1, repo.chamadasSalvar)
        assertTrue(vm.uiState.value.salvando)

        runBlocking { bloqueio.complete(Result.success(Unit)) }
        assertFalse(vm.uiState.value.salvando)
        assertTrue(vm.uiState.value.salvoComSucesso)
    }

    @Test
    fun assinaturasSaoIndependentesELimpezaLimpaApenasAEscolhida() {
        val vm = criarVm(FakeRelatorioRepositorio())
        vm.confirmarAssinaturaEspecialista("/tmp/esp.png", "Especialista")
        vm.confirmarAssinaturaCliente("/tmp/cli.png", "Cliente")

        vm.limparAssinaturaCliente()

        assertTrue(vm.uiState.value.rascunho.assinaturaEspecialista.confirmada)
        assertEquals("/tmp/esp.png", vm.uiState.value.rascunho.assinaturaEspecialista.caminhoImagemLocal)
        assertFalse(vm.uiState.value.rascunho.assinaturaCliente.confirmada)
        assertNull(vm.uiState.value.rascunho.assinaturaCliente.caminhoImagemLocal)
    }
}