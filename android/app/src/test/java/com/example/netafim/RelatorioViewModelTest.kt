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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RelatorioViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val fakeRepositorio = object : RelatorioRepositorio {
        override suspend fun salvarRascunho(
            motivo: String, constatacoes: String, atividades: String,
            planoAcao: String, conclusao: String, fotos: List<String>,
            videos: List<String>, anexos: List<String>,
            assinaturaCliente: String?, assinaturaEspecialista: String?
        ): Boolean {
            return true
        }
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
        val viewModel = RelatorioViewModel(fakeRepositorio)

        viewModel.setMotivo("pos_venda")
        val (valido1, _) = viewModel.validarEtapa(1)
        assertFalse(valido1)

        viewModel.atualizarConstatacoes("Constatação de teste preenchida")
        val (valido1Aberto, _) = viewModel.validarEtapa(1)
        assertTrue(valido1Aberto)
    }

    @Test
    fun testSalvamentoViewModel() = runTest(testDispatcher) {
        val viewModel = RelatorioViewModel(fakeRepositorio)
        var resultado = false

        viewModel.salvarRelatorio { sucesso ->
            resultado = sucesso
        }
        advanceUntilIdle()

        assertTrue(resultado)
    }
}