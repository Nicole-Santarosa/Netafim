package com.example.netafim.atendimento.ui.lista

import com.example.netafim.core.modelo.AlteracaoDeAtendimento
import com.example.netafim.core.modelo.Atendimento
import com.example.netafim.core.modelo.AtendimentoRepository
import com.example.netafim.core.modelo.Cultura
import com.example.netafim.core.modelo.Distribuidor
import com.example.netafim.core.modelo.FiltroDeAtendimentos
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.ResultadoDaAcao
import com.example.netafim.core.modelo.SolicitacaoDeAbertura
import com.example.netafim.core.modelo.buscarNosItens
import com.example.netafim.core.modelo.ordenar
import com.example.netafim.core.modelo.sessao.SessaoDoUsuario
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

// Dublê de teste do contrato AtendimentoRepository. NÃO é a implementação real (a do Room e da
// sincronização), só uma versão em memória para os testes dos ViewModels provarem como eles se comportam
// diante de um repositório que cumpre o contrato: a lista sai já filtrada, buscada e ordenada, e muda quando
// os dados mudam.
//
// Nas listas o falso aplica status, origem, somenteMeusComoTecnico (pelo usuário da sessão, que é quem o
// repositório de verdade consultaria), busca e ordem. O critério origemDoRegistro não é aplicado porque o item
// de lista não carrega essa informação. O detalhe devolve o atendimento que o teste definiu, os catálogos
// devolvem o que o teste definiu, e cada escrita devolve o resultado que o teste configurou (Sucesso por padrão).
class RepositorioDeAtendimentosFalso(private val sessao: SessaoDoUsuario) : AtendimentoRepository {

    // Nulo quer dizer "o banco ainda não respondeu": a lista observada não emite nada até definirItens.
    private val itens = MutableStateFlow<List<ItemDeAtendimento>?>(emptyList())

    // Cada pedido de lista que chegou, na ordem. Serve para o teste conferir o que o ViewModel pediu e também
    // que ele NÃO consultou o repositório (lista vazia) quando não havia usuário logado.
    val pedidosRecebidos = mutableListOf<FiltroDeAtendimentos>()

    // Chamadas de escrita recebidas, para os testes conferirem o que foi pedido.
    val aberturas = mutableListOf<SolicitacaoDeAbertura>()
    val iniciados = mutableListOf<String>()
    val alteracoes = mutableListOf<Pair<String, AlteracaoDeAtendimento>>()

    // Resultado que cada escrita devolve. O teste troca para simular cada erro.
    var resultadoDaAbertura: ResultadoDaAcao = ResultadoDaAcao.Sucesso
    var resultadoDoInicio: ResultadoDaAcao = ResultadoDaAcao.Sucesso
    var resultadoDaAlteracao: ResultadoDaAcao = ResultadoDaAcao.Sucesso

    // Quando preenchido, cada escrita registra a chamada e FICA ESPERANDO até o teste completar o portão. Serve
    // para simular uma escrita demorada e provar que o segundo toque, feito enquanto ela roda, é ignorado.
    var portao: CompletableDeferred<Unit>? = null

    private val atendimento = MutableStateFlow<Atendimento?>(null)
    private val culturas = MutableStateFlow<List<Cultura>>(emptyList())
    private val distribuidores = MutableStateFlow<List<Distribuidor>>(emptyList())

    // Define o atendimento que o detalhe vai encontrar (nulo = não existe). Emite de novo ao mudar.
    fun definirAtendimento(novo: Atendimento?) {
        atendimento.value = novo
    }

    fun definirCulturas(novas: List<Cultura>) {
        culturas.value = novas
    }

    fun definirDistribuidores(novos: List<Distribuidor>) {
        distribuidores.value = novos
    }

    // Troca o conteúdo do "banco". Quem está observando recebe a lista nova, como aconteceria com o Room.
    fun definirItens(novos: List<ItemDeAtendimento>) {
        itens.value = novos
    }

    // Faz as listas ficarem sem resposta até o próximo definirItens, como um Room que ainda não leu o disco.
    // Serve para provar que a tela fica em Carregando antes da primeira emissão.
    fun segurarResposta() {
        itens.value = null
    }

    override fun observarAtendimentos(filtro: FiltroDeAtendimentos): Flow<List<ItemDeAtendimento>> {
        pedidosRecebidos += filtro
        return itens.filterNotNull().map { aplicar(it, filtro) }
    }

    // Mesmas regras do contrato: filtra, busca e ordena, nessa ordem.
    private fun aplicar(todos: List<ItemDeAtendimento>, filtro: FiltroDeAtendimentos): List<ItemDeAtendimento> {
        val idDoUsuario = sessao.usuario.value?.id
        val filtrados = todos.filter { item ->
            (filtro.status.isEmpty() || item.status in filtro.status) &&
                (filtro.origem.isEmpty() || item.origem in filtro.origem) &&
                (!filtro.somenteMeusComoTecnico || item.idTecnicoResponsavel == idDoUsuario)
        }
        return ordenar(buscarNosItens(filtrados, filtro.busca), filtro.ordem)
    }

    // Ids que o detalhe e a edição pediram para observar. Serve para provar que, sem usuário logado, o
    // repositório não é consultado.
    val idsObservados = mutableListOf<String>()

    // Só devolve o atendimento definido se o id bate; qualquer outro id "não existe".
    override fun observarAtendimento(id: String): Flow<Atendimento?> {
        idsObservados += id
        return atendimento.map { atual -> atual?.takeIf { it.item.id == id } }
    }

    override suspend fun abrirAtendimento(solicitacao: SolicitacaoDeAbertura): ResultadoDaAcao {
        aberturas += solicitacao
        portao?.await()
        return resultadoDaAbertura
    }

    override suspend fun iniciarAtendimento(id: String): ResultadoDaAcao {
        iniciados += id
        portao?.await()
        return resultadoDoInicio
    }

    override suspend fun alterarAtendimento(id: String, alteracao: AlteracaoDeAtendimento): ResultadoDaAcao {
        alteracoes += id to alteracao
        portao?.await()
        return resultadoDaAlteracao
    }

    override fun observarCulturas(): Flow<List<Cultura>> = culturas

    override fun observarDistribuidores(): Flow<List<Distribuidor>> = distribuidores
}
