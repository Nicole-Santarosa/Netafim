package com.netafim.posvenda.atendimento.dominio

// Limites de tamanho do contrato da API (schema AberturaAtendimento): cidade aceita até 100
// caracteres e contato_solicitante até 20. A localidade do formulário vai no campo cidade.
const val LIMITE_LOCALIDADE = 100
const val LIMITE_CONTATO = 20

// Decisão do grupo ainda em confirmação: a localidade é obrigatória na abertura pelo cliente?
// No contrato o campo cidade é opcional, então a regra é só do app. Para mudar, troque só este valor.
const val LOCALIDADE_OBRIGATORIA = true

// Dados que o cliente preenche na abertura (protótipo do cliente). Todos começam vazios.
// Os campos de identificação (id do atendimento, origem, id do cliente) não são do formulário:
// o app os preenche com o aparelho e a sessão.
data class FormularioAbertura(
    val motivo: MotivoAtendimento? = null,
    // Ids do catálogo de culturas (GET /culturas). Opcional.
    val culturas: Set<Int> = emptySet(),
    val localidade: String = "",
    val contato: String = "",
    // Distribuidor escolhido na lista. Opcional.
    val idDistribuidor: Int? = null,
    val tipoVenda: TipoVenda? = null,
)

enum class ErroCampo {
    OBRIGATORIO,
    MUITO_LONGO,
}

// Um erro opcional por campo validado. Distribuidor, culturas e tipo de venda não geram erro.
data class ErrosFormulario(
    val motivo: ErroCampo? = null,
    val localidade: ErroCampo? = null,
    val contato: ErroCampo? = null,
) {
    val valido: Boolean
        get() = motivo == null && localidade == null && contato == null
}

fun validar(formulario: FormularioAbertura): ErrosFormulario = ErrosFormulario(
    // O motivo é obrigatório no contrato da API.
    motivo = if (formulario.motivo == null) ErroCampo.OBRIGATORIO else null,
    localidade = validarTexto(formulario.localidade, LIMITE_LOCALIDADE, LOCALIDADE_OBRIGATORIA),
    // O contato é obrigatório no contrato (contato_solicitante).
    contato = validarTexto(formulario.contato, LIMITE_CONTATO, obrigatorio = true),
)

// Tipo de venda só vale para Digital Farming. Nesse motivo, se o cliente não escolheu, vale PROJETO.
// Nos outros motivos devolve null: o app não deve enviar o campo.
fun tipoVendaEfetivo(formulario: FormularioAbertura): TipoVenda? =
    if (formulario.motivo == MotivoAtendimento.DIGITAL_FARMING) {
        formulario.tipoVenda ?: TipoVenda.PROJETO
    } else {
        null
    }

// O tamanho conta depois de aparar os espaços das pontas, que é o que o app deve enviar.
private fun validarTexto(texto: String, limite: Int, obrigatorio: Boolean): ErroCampo? {
    val aparado = texto.trim()
    return when {
        aparado.isEmpty() -> if (obrigatorio) ErroCampo.OBRIGATORIO else null
        aparado.length > limite -> ErroCampo.MUITO_LONGO
        else -> null
    }
}
