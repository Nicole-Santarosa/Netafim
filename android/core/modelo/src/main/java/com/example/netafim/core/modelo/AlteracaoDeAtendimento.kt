package com.example.netafim.core.modelo

// Distingue "não mexer no campo" de "limpar o campo", que um simples nulo não consegue dizer.
sealed interface Mudanca<out T> {
    // O campo não entra na alteração.
    data object Manter : Mudanca<Nothing>

    // O campo recebe o valor; Definir(null) limpa o campo.
    data class Definir<out T>(val valor: T?) : Mudanca<T>
}

// Campos que o PATCH aceita. Nulo em status, destinadoA e idDistribuidor quer dizer "não alterar", porque o
// servidor não aceita limpar esses três. Técnico responsável e situação do projeto aceitam limpar, por isso
// usam Mudanca.
data class AlteracaoDeAtendimento(
    val status: StatusAtendimento? = null,
    val destinadoA: DestinadoA? = null,
    val idDistribuidor: Int? = null,
    val idTecnicoResponsavel: Mudanca<Int> = Mudanca.Manter,
    val situacaoProjeto: Mudanca<SituacaoDoProjeto> = Mudanca.Manter,
) {
    init {
        require(
            status != null || destinadoA != null || idDistribuidor != null ||
                idTecnicoResponsavel !is Mudanca.Manter || situacaoProjeto !is Mudanca.Manter,
        ) { "A alteração precisa mudar pelo menos um campo." }
        require(status != StatusAtendimento.DESCONHECIDO) { "O status da alteração não pode ser DESCONHECIDO." }
        require((situacaoProjeto as? Mudanca.Definir)?.valor != SituacaoDoProjeto.DESCONHECIDO) {
            "A situação do projeto da alteração não pode ser DESCONHECIDO."
        }
        // Espelha o CHECK ck_atend_destino do banco: destinar a um distribuidor exige dizer qual. A regra de
        // status contra destino (ck_atend_status_destino) depende da linha atual e fica só no servidor.
        require(destinadoA != DestinadoA.DISTRIBUIDOR || idDistribuidor != null) {
            "Destinar a um distribuidor exige informar o idDistribuidor."
        }
    }
}
