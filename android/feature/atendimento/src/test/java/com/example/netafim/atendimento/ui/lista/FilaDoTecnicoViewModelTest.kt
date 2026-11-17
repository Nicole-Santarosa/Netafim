package com.example.netafim.atendimento.ui.lista

import androidx.lifecycle.viewmodel.CreationExtras
import com.example.netafim.atendimento.dominio.FiltroDaFila
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FilaDoTecnicoViewModelTest {

    // O técnico logado nos testes tem id 10; o item de outro técnico usa o 11.
    private val sessao = SessaoFalsa(SessaoFalsa.umUsuario(id = 10))
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

    private fun criar() = FilaDoTecnicoViewModel(repositorio, sessao)

    @Test
    fun estadoInicialTemTodosSemBuscaEListaVaziaSemDados() = runTest {
        val viewModel = criar()
        manterColetando(viewModel.estado)

        assertEquals(FiltroDaFila.TODOS, viewModel.filtro.value)
        assertEquals("", viewModel.busca.value)
        assertTrue(prontos(viewModel.estado.value).isEmpty())
        // O primeiro pedido ao repositório é o do chip Todos da fila, do técnico logado, na ordem da fila.
        val pedido = repositorio.pedidosRecebidos.single()
        assertEquals(FiltroDaFila.TODOS.comoFiltroDeAtendimentos(""), pedido)
        assertEquals(OrdemDaLista.FILA_DO_TECNICO, pedido.ordem)
    }

    @Test
    fun soMostraOsAtendimentosDoTecnicoLogadoQueEstaoNaFila() = runTest {
        repositorio.definirItens(
            listOf(
                itemDeTeste("meu", idTecnico = 10),
                itemDeTeste("de-outro", idTecnico = 11),
                itemDeTeste("sem-tecnico", idTecnico = null),
                itemDeTeste("meu-resolvido", status = StatusAtendimento.RESOLVIDO, idTecnico = 10),
                itemDeTeste("meu-aberto", status = StatusAtendimento.ABERTO, idTecnico = 10),
            ),
        )
        val viewModel = criar()
        manterColetando(viewModel.estado)

        assertEquals(listOf("meu"), ids(prontos(viewModel.estado.value)))
    }

    @Test
    fun trocarOChipFiltraPorStatus() = runTest {
        repositorio.definirItens(
            listOf(
                itemDeTeste("agendado", status = StatusAtendimento.AGENDADO, idTecnico = 10),
                itemDeTeste("destinado", status = StatusAtendimento.DESTINADO, idTecnico = 10),
                itemDeTeste("em-curso", status = StatusAtendimento.EM_ATENDIMENTO, idTecnico = 10),
            ),
        )
        val viewModel = criar()
        manterColetando(viewModel.estado)

        viewModel.trocarFiltro(FiltroDaFila.NOVO)
        assertEquals(FiltroDaFila.NOVO, viewModel.filtro.value)
        assertEquals(setOf("agendado", "destinado"), ids(prontos(viewModel.estado.value)).toSet())

        viewModel.trocarFiltro(FiltroDaFila.EM_ATENDIMENTO)
        assertEquals(listOf("em-curso"), ids(prontos(viewModel.estado.value)))

        viewModel.trocarFiltro(FiltroDaFila.TODOS)
        assertEquals(3, prontos(viewModel.estado.value).size)
    }

    @Test
    fun buscarFiltraPeloTextoSemAcentoNemCaixa() = runTest {
        repositorio.definirItens(
            listOf(
                itemDeTeste("1", nomeCliente = "Citrosuco", local = "Boa Esperança do Sul", idTecnico = 10),
                itemDeTeste("2", nomeCliente = "Fazenda Santa Rita", local = "Ribeirão Preto", idTecnico = 10),
            ),
        )
        val viewModel = criar()
        manterColetando(viewModel.estado)

        viewModel.buscar("RIBEIRAO")
        assertEquals("RIBEIRAO", viewModel.busca.value)
        assertEquals(listOf("2"), ids(prontos(viewModel.estado.value)))

        viewModel.buscar("")
        assertEquals(2, prontos(viewModel.estado.value).size)

        viewModel.buscar("zzz")
        assertTrue(prontos(viewModel.estado.value).isEmpty())
    }

    @Test
    fun chipEBuscaValemJuntos() = runTest {
        repositorio.definirItens(
            listOf(
                itemDeTeste("novo-citro", nomeCliente = "Citrosuco", idTecnico = 10),
                itemDeTeste(
                    "curso-citro",
                    status = StatusAtendimento.EM_ATENDIMENTO,
                    nomeCliente = "Citrosuco",
                    idTecnico = 10,
                ),
                itemDeTeste("novo-outro", nomeCliente = "Outro", idTecnico = 10),
            ),
        )
        val viewModel = criar()
        manterColetando(viewModel.estado)

        viewModel.trocarFiltro(FiltroDaFila.NOVO)
        viewModel.buscar("citro")
        assertEquals(listOf("novo-citro"), ids(prontos(viewModel.estado.value)))
    }

    @Test
    fun aFilaSaiComEmAtendimentoPrimeiroEDepoisOsMaisAntigos() = runTest {
        repositorio.definirItens(
            listOf(
                itemDeTeste("novo-recente", aberto = "2026-11-09T12:00:00Z", idTecnico = 10),
                itemDeTeste("novo-antigo", aberto = "2026-10-01T12:00:00Z", idTecnico = 10),
                itemDeTeste(
                    "em-curso-recente",
                    status = StatusAtendimento.EM_ATENDIMENTO,
                    aberto = "2026-11-08T12:00:00Z",
                    idTecnico = 10,
                ),
            ),
        )
        val viewModel = criar()
        manterColetando(viewModel.estado)

        assertEquals(listOf("em-curso-recente", "novo-antigo", "novo-recente"), ids(prontos(viewModel.estado.value)))
    }

    @Test
    fun semUsuarioNaSessaoAListaFicaCarregandoESemConsultarORepositorio() = runTest {
        repositorio.definirItens(listOf(itemDeTeste("meu", idTecnico = 10)))
        sessao.entrarComo(null)
        val viewModel = criar()
        manterColetando(viewModel.estado)

        assertEquals(EstadoDaLista.Carregando, viewModel.estado.value)
        assertTrue(repositorio.pedidosRecebidos.isEmpty())
    }

    @Test
    fun aoEntrarAListaAparecePeloUsuarioEAoSairVoltaACarregando() = runTest {
        repositorio.definirItens(listOf(itemDeTeste("meu", idTecnico = 10)))
        sessao.entrarComo(null)
        val viewModel = criar()
        manterColetando(viewModel.estado)
        assertEquals(EstadoDaLista.Carregando, viewModel.estado.value)

        sessao.entrarComo(SessaoFalsa.umUsuario(id = 10))
        assertEquals(listOf("meu"), ids(prontos(viewModel.estado.value)))

        sessao.entrarComo(null)
        assertEquals(EstadoDaLista.Carregando, viewModel.estado.value)
    }

    @Test
    fun aoTrocarDeContaAFilaPassaAOutroTecnico() = runTest {
        repositorio.definirItens(
            listOf(itemDeTeste("do-10", idTecnico = 10), itemDeTeste("do-11", idTecnico = 11)),
        )
        val viewModel = criar()
        manterColetando(viewModel.estado)
        assertEquals(listOf("do-10"), ids(prontos(viewModel.estado.value)))

        sessao.entrarComo(SessaoFalsa.umUsuario(id = 11))
        assertEquals(listOf("do-11"), ids(prontos(viewModel.estado.value)))
    }

    @Test
    fun aListaReageAMudancasNoRepositorio() = runTest {
        val viewModel = criar()
        manterColetando(viewModel.estado)
        assertTrue(prontos(viewModel.estado.value).isEmpty())

        repositorio.definirItens(listOf(itemDeTeste("chegou", idTecnico = 10)))
        assertEquals(listOf("chegou"), ids(prontos(viewModel.estado.value)))

        repositorio.definirItens(emptyList())
        assertTrue(prontos(viewModel.estado.value).isEmpty())
    }

    @Test
    fun aFabricaCriaOViewModelComAsDependencias() {
        val viewModel = FilaDoTecnicoViewModel.fabrica(repositorio, sessao)
            .create(FilaDoTecnicoViewModel::class.java, CreationExtras.Empty)
        assertEquals(FiltroDaFila.TODOS, viewModel.filtro.value)
    }
}
