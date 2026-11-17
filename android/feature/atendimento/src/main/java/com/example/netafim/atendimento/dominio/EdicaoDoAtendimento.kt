package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.AlteracaoDeAtendimento
import com.example.netafim.core.modelo.Mudanca
import com.example.netafim.core.modelo.ResultadoDaAcao
import com.example.netafim.core.modelo.SituacaoDoProjeto

// A versão do atendimento não entra na edição: quem manda o PATCH (a camada de dados) guarda a versão lida
// e a envia junto; a tela só desenha o Atendimento e devolve a escolha. A situação original é a que está
// gravada hoje (atendimento.situacaoProjeto), ou nula quando o atendimento ainda não tem situação.

// Regra do botão Salvar alterações: só libera quando há escolha, ela difere da situação gravada e não
// há um salvamento em andamento. A escolha nunca pode ser DESCONHECIDO: esse valor existe só para o que
// veio de um servidor mais novo e o app jamais o envia. A tela não olha perfil: quem pode alterar a
// situação do projeto é regra do banco, e a recusa volta como ErroAoSalvar.SemPermissao ou RegraDeNegocio.
fun podeSalvar(
    original: SituacaoDoProjeto?,
    escolhida: SituacaoDoProjeto?,
    salvando: Boolean,
): Boolean = escolhida != null && escolhida != SituacaoDoProjeto.DESCONHECIDO && escolhida != original && !salvando

// Transforma a escolha da tela na alteração que o repositório recebe: só a situação do projeto muda e os
// outros campos ficam como estão (Manter). Devolve nulo quando não há escolha ou ela é DESCONHECIDO, que são
// os mesmos casos em que podeSalvar não libera o botão; assim nunca se monta uma alteração que o core recusaria.
fun alteracaoDaEscolha(escolhida: SituacaoDoProjeto?): AlteracaoDeAtendimento? =
    if (escolhida == null || escolhida == SituacaoDoProjeto.DESCONHECIDO) {
        null
    } else {
        AlteracaoDeAtendimento(situacaoProjeto = Mudanca.Definir(escolhida))
    }

// Erros possíveis ao salvar, como a tela precisa distinguir cada um.
sealed interface ErroAoSalvar {
    // Outra pessoa alterou o atendimento antes (409 conflito_de_versao): a tela oferece Atualizar.
    data object ConflitoDeVersao : ErroAoSalvar

    // O banco não deixou o perfil alterar (403).
    data object SemPermissao : ErroAoSalvar

    // O atendimento não existe, foi excluído ou não é visível ao usuário (404).
    data object NaoEncontrado : ErroAoSalvar

    // O banco recusou por regra de negócio (422). O texto é o que a API devolve em detail.
    data class RegraDeNegocio(val mensagem: String) : ErroAoSalvar

    // O pedido nem chegou ao servidor.
    data object SemConexao : ErroAoSalvar
}

// Traduz o resultado que o repositório devolve ao salvar no erro que a tela sabe mostrar. Sucesso não é erro e
// devolve nulo. Cada caso vai para o valor mais próximo, sem mensagem nova:
// - ConflitoDeVersao, SemPermissao e NaoEncontrado têm o valor de mesmo nome.
// - FalhaDeRede vira SemConexao.
// - RegraRecusada vira RegraDeNegocio com o texto que o servidor devolveu.
// - DadosInvalidos não tem valor próprio na tela de edição, que só envia a situação do projeto, de um conjunto
//   fechado de opções. Vira RegraDeNegocio com mensagem vazia, e a tela mostra o texto genérico de regra de
//   negócio. Fica como decisão pendente: se um dia a API recusar o campo, vale ter um texto próprio.
fun erroDoResultado(resultado: ResultadoDaAcao): ErroAoSalvar? = when (resultado) {
    ResultadoDaAcao.Sucesso -> null
    ResultadoDaAcao.ConflitoDeVersao -> ErroAoSalvar.ConflitoDeVersao
    ResultadoDaAcao.SemPermissao -> ErroAoSalvar.SemPermissao
    ResultadoDaAcao.NaoEncontrado -> ErroAoSalvar.NaoEncontrado
    ResultadoDaAcao.FalhaDeRede -> ErroAoSalvar.SemConexao
    is ResultadoDaAcao.RegraRecusada -> ErroAoSalvar.RegraDeNegocio(resultado.mensagem)
    is ResultadoDaAcao.DadosInvalidos -> ErroAoSalvar.RegraDeNegocio("")
}
