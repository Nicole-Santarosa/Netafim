package com.example.netafim

import com.example.netafim.data.repository.RelatorioRepositorio
import com.example.netafim.ui.relatorio.RelatorioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RelatorioViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val fakeRepositorioSucesso = object : RelatorioRepositorio {
        override suspend fun salvarRelatorio(
            motivo: String,
            constatacoes: String,
            atividades: String,
            planoAcao: String,
            conclusao: String,
            fotos: List<String>,
            videos: List<String>,
            anexos: List<String>,
            assinaturaCliente: String?,
            assinaturaEspecialista: String?
        ): Boolean = true
    }

    private val fakeRepositorioFalha = object : RelatorioRepositorio {
        override suspend fun salvarRelatorio(
            motivo: String,
            constatacoes: String,
            atividades: String,
            planoAcao: String,
            conclusao: String,
            fotos: List<String>,
            videos: List<String>,
            anexos: List<String>,
            assinaturaCliente: String?,
            assinaturaEspecialista: String?
        ): Boolean = false
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testValidacaoEtapasPorMotivo() {
        val viewModel = RelatorioViewModel(fakeRepositorioSucesso)

        viewModel.setMotivo("pos_venda")
        val (valido1, _) = viewModel.validarEtapa(1)
        assertFalse(valido1)

        viewModel.atualizarConstatacoes("Constatação de campo preenchida")
        val (valido1Preenchido, _) = viewModel.validarEtapa(1)
        assertTrue(valido1Preenchido)

        viewModel.setMotivo("duvidas")
        val (valido2Duvidas, _) = viewModel.validarEtapa(2)
        assertTrue(valido2Duvidas)
    }

    @Test
    fun testSalvamentoViewModelSucesso() = runTest(testDispatcher) {
        val viewModel = RelatorioViewModel(fakeRepositorioSucesso)
        var resultado = false
        var erroMsg: String? = "nao_chamado"

        viewModel.salvarRelatorio { sucesso, erro ->
            resultado = sucesso
            erroMsg = erro
        }
        advanceUntilIdle()

        assertTrue(resultado)
        assertEquals(null, erroMsg)
    }

    @Test
    fun testSalvamentoViewModelFalhaNaoIndicaSucesso() = runTest(testDispatcher) {
        val viewModel = RelatorioViewModel(fakeRepositorioFalha)
        var resultado = true
        var erroMsg: String? = null

        viewModel.salvarRelatorio { sucesso, erro ->
            resultado = sucesso
            erroMsg = erro
        }
        advanceUntilIdle()

        assertFalse(resultado)
        assertNotNull(erroMsg)
        assertNotNull(viewModel.erroPersistencia.value)
    }
}