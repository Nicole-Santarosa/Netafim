package com.example.netafim.atendimento.ui.lista

import com.example.netafim.core.modelo.AtendimentoRepository
import com.example.netafim.core.modelo.FiltroDeAtendimentos
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.sessao.SessaoDoUsuario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

// Tempo que o ViewModel continua escutando o repositório depois que a tela some. Cobre a rotação da tela sem
// recomeçar a consulta, e solta o recurso se a tela ficar muito tempo fora.
internal const val ESPERA_SEM_TELA_MS = 5_000L

// Parte comum às quatro listas: acompanha o repositório com o pedido mais recente e some com a lista quando
// ninguém está logado. Cada ViewModel só monta o fluxo de pedidos a partir do chip e da busca.
// - Sem usuário na sessão (deslogado) a lista sai vazia e o repositório nem é consultado, porque sem usuário
//   ele não saberia o que mostrar.
// - Quando o usuário muda (sair e entrar com outra conta) a consulta recomeça, e quando o pedido muda (chip ou
//   busca) a consulta anterior é cancelada: o flatMapLatest garante que só vale a mais nova.
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observarListaDoUsuario(
    sessao: SessaoDoUsuario,
    repositorio: AtendimentoRepository,
    pedidos: Flow<FiltroDeAtendimentos>,
): Flow<List<ItemDeAtendimento>> = sessao.usuario.flatMapLatest { usuario ->
    if (usuario == null) {
        flowOf(emptyList())
    } else {
        pedidos.flatMapLatest { pedido -> repositorio.observarAtendimentos(pedido) }
    }
}
