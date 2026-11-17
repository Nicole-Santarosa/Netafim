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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// Dublê de teste do contrato AtendimentoRepository. NÃO é a implementação real (a do Room e da
// sincronização), só uma versão em memória para os testes dos ViewModels provarem como eles se comportam
// diante de um repositório que cumpre o contrato: a lista sai já filtrada, buscada e ordenada, e muda quando
// os dados mudam.
//
// O falso só aplica o que as telas de lista usam: status, origem, somenteMeusComoTecnico (pelo usuário da
// sessão, que é quem o repositório de verdade consultaria), busca e ordem. O critério origemDoRegistro não
// é aplicado porque o item de lista não carrega essa informação. A leitura de um atendimento só (detalhe)
// não é usada pelas listas e devolve sempre nulo.
class RepositorioDeAtendimentosFalso(private val sessao: SessaoDoUsuario) : AtendimentoRepository {

    private val itens = MutableStateFlow<List<ItemDeAtendimento>>(emptyList())

    // Cada pedido de lista que chegou, na ordem. Serve para o teste conferir o que o ViewModel pediu e também
    // que ele NÃO consultou o repositório (lista vazia) quando não havia usuário logado.
    val pedidosRecebidos = mutableListOf<FiltroDeAtendimentos>()

    // Chamadas de escrita recebidas, para os testes conferirem o que foi pedido.
    val aberturas = mutableListOf<SolicitacaoDeAbertura>()
    val iniciados = mutableListOf<String>()
    val alteracoes = mutableListOf<Pair<String, AlteracaoDeAtendimento>>()

    // Troca o conteúdo do "banco". Quem está observando recebe a lista nova, como aconteceria com o Room.
    fun definirItens(novos: List<ItemDeAtendimento>) {
        itens.value = novos
    }

    override fun observarAtendimentos(filtro: FiltroDeAtendimentos): Flow<List<ItemDeAtendimento>> {
        pedidosRecebidos += filtro
        return itens.map { aplicar(it, filtro) }
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

    override fun observarAtendimento(id: String): Flow<Atendimento?> = flowOf(null)

    override suspend fun abrirAtendimento(solicitacao: SolicitacaoDeAbertura): ResultadoDaAcao {
        aberturas += solicitacao
        return ResultadoDaAcao.Sucesso
    }

    override suspend fun iniciarAtendimento(id: String): ResultadoDaAcao {
        iniciados += id
        return ResultadoDaAcao.Sucesso
    }

    override suspend fun alterarAtendimento(id: String, alteracao: AlteracaoDeAtendimento): ResultadoDaAcao {
        alteracoes += id to alteracao
        return ResultadoDaAcao.Sucesso
    }

    override fun observarCulturas(): Flow<List<Cultura>> = flowOf(emptyList())

    override fun observarDistribuidores(): Flow<List<Distribuidor>> = flowOf(emptyList())
}
