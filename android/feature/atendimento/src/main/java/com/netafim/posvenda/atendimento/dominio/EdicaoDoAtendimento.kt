package com.netafim.posvenda.atendimento.dominio

// Situação do projeto do atendimento. Os códigos são os do contrato da API (schema SituacaoProjeto)
// e os mesmos do CHECK do banco. Rótulos ficam na camada de interface.
enum class SituacaoDoProjeto(val codigo: String) {
    GARANTIA("garantia"),
    FORA_GARANTIA("fora_garantia"),
    INSTALACAO("instalacao");

    companion object {
        // Nulo (atendimento sem situação gravada) e código que o app não conhece devolvem nulo, para a
        // tela abrir o seletor sem nada marcado em vez de quebrar.
        fun deCodigo(codigo: String?): SituacaoDoProjeto? = entries.firstOrNull { it.codigo == codigo }
    }
}

// O que a tela de edição mostra em leitura. A versão do atendimento não entra aqui: quem manda o
// PATCH (a camada de dados) guarda a versão lida e a envia junto; a tela só desenha e devolve a escolha.
// A situação original é a que está gravada hoje, ou nula quando o atendimento ainda não tem situação.
data class DadosDaEdicao(
    val id: String,
    val protocolo: String,
    val motivo: MotivoAtendimento,
    val situacaoOriginal: SituacaoDoProjeto?,
)

// Regra do botão Salvar alterações: só libera quando há escolha, ela difere da situação gravada e não
// há um salvamento em andamento. A tela não olha perfil: quem pode alterar a situação do projeto é
// regra do banco, e a recusa volta como ErroAoSalvar.SemPermissao ou RegraDeNegocio.
fun podeSalvar(
    original: SituacaoDoProjeto?,
    escolhida: SituacaoDoProjeto?,
    salvando: Boolean,
): Boolean = escolhida != null && escolhida != original && !salvando

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

// Traduz a resposta do PATCH no erro da tela. statusHttp nulo significa que não houve resposta (sem
// rede). Devolve nulo para um status que a edição não trata (por exemplo 400 e 401, que têm caminho
// próprio na camada de dados), para quem chama decidir o que fazer em vez de a tela mostrar algo errado.
fun erroAoSalvar(statusHttp: Int?, mensagemDaApi: String?): ErroAoSalvar? = when (statusHttp) {
    null -> ErroAoSalvar.SemConexao
    409 -> ErroAoSalvar.ConflitoDeVersao
    403 -> ErroAoSalvar.SemPermissao
    404 -> ErroAoSalvar.NaoEncontrado
    422 -> ErroAoSalvar.RegraDeNegocio(mensagemDaApi.orEmpty())
    else -> null
}
