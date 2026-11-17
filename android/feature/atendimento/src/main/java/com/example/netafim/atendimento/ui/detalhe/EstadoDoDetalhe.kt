package com.example.netafim.atendimento.ui.detalhe

import com.example.netafim.core.modelo.Atendimento
import com.example.netafim.core.modelo.AtendimentoRepository
import com.example.netafim.core.modelo.sessao.SessaoDoUsuario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// O que a tela de um atendimento só (detalhe ou edição) tem para mostrar em cada momento.
sealed interface EstadoDoDetalhe {
    // Ainda não chegou a primeira resposta do repositório (ou não há usuário logado).
    data object Carregando : EstadoDoDetalhe

    // O repositório respondeu que o atendimento não existe ou o usuário não o enxerga.
    data object NaoEncontrado : EstadoDoDetalhe

    // Atendimento carregado. Emite de novo a cada mudança local dele.
    data class Pronto(val atendimento: Atendimento) : EstadoDoDetalhe
}

// Parte comum ao detalhe e à edição: acompanha o atendimento pelo id enquanto houver usuário logado.
// Sem usuário na sessão o repositório não é consultado e o estado fica em Carregando, como se ainda não
// houvesse o que mostrar (mostrar "não encontrado" ao deslogar faria a mensagem piscar na saída da conta).
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observarEstadoDoDetalhe(
    id: String,
    sessao: SessaoDoUsuario,
    repositorio: AtendimentoRepository,
): Flow<EstadoDoDetalhe> = sessao.usuario.flatMapLatest { usuario ->
    if (usuario == null) {
        flowOf(EstadoDoDetalhe.Carregando)
    } else {
        repositorio.observarAtendimento(id).map { atendimento ->
            if (atendimento == null) EstadoDoDetalhe.NaoEncontrado else EstadoDoDetalhe.Pronto(atendimento)
        }
    }
}
