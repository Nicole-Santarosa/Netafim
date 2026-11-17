package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.SolicitacaoDeAbertura
import com.example.netafim.core.modelo.TipoVenda
import java.time.Instant

// Limites de tamanho do contrato da API (schema AberturaAtendimento): cidade aceita até 100
// caracteres, nome_fazenda_informado até 150 e contato_solicitante até 20.
const val LIMITE_CIDADE = 100
const val LIMITE_NOME_FAZENDA = 150
const val LIMITE_CONTATO = 20

// Dados que o cliente preenche na abertura (protótipo do cliente). Todos começam vazios.
// Os campos de identificação (id do atendimento, origem, id do cliente) não são do formulário:
// o app os preenche com o aparelho e a sessão.
// Os obrigatórios seguem o formulário de solicitação da Netafim para o cliente final, que é o
// mesmo que a API passou a exigir: distribuidor, cidade, nome da fazenda, ao menos uma cultura e
// o motivo, além do contato, que o contrato sempre pediu.
data class FormularioAbertura(
    val motivo: MotivoAtendimento? = null,
    // Ids do catálogo de culturas (GET /culturas). Ao menos um.
    val culturas: Set<Int> = emptySet(),
    // Vai no campo cidade da API.
    val cidade: String = "",
    // Vai no campo nome_fazenda_informado da API.
    val nomeFazenda: String = "",
    val contato: String = "",
    // Distribuidor escolhido na lista. Obrigatório para o cliente final.
    val idDistribuidor: Int? = null,
    val tipoVenda: TipoVenda? = null,
)

enum class ErroCampo {
    OBRIGATORIO,
    MUITO_LONGO,
}

// Um erro opcional por campo validado. Culturas e distribuidor só têm o erro de obrigatório,
// porque são escolhas de lista. O tipo de venda não gera erro: sem escolha vale o padrão.
data class ErrosFormulario(
    val motivo: ErroCampo? = null,
    val cidade: ErroCampo? = null,
    val nomeFazenda: ErroCampo? = null,
    val culturas: ErroCampo? = null,
    val distribuidor: ErroCampo? = null,
    val contato: ErroCampo? = null,
) {
    val valido: Boolean
        get() = motivo == null && cidade == null && nomeFazenda == null &&
            culturas == null && distribuidor == null && contato == null
}

fun validar(formulario: FormularioAbertura): ErrosFormulario = ErrosFormulario(
    // O motivo é obrigatório no contrato da API. DESCONHECIDO não é uma escolha possível (o seletor só oferece
    // os motivos do cliente), mas se chegasse aqui contaria como motivo não escolhido.
    motivo = if (formulario.motivo == null || formulario.motivo == MotivoAtendimento.DESCONHECIDO) {
        ErroCampo.OBRIGATORIO
    } else {
        null
    },
    cidade = validarTexto(formulario.cidade, LIMITE_CIDADE, obrigatorio = true),
    nomeFazenda = validarTexto(formulario.nomeFazenda, LIMITE_NOME_FAZENDA, obrigatorio = true),
    culturas = if (formulario.culturas.isEmpty()) ErroCampo.OBRIGATORIO else null,
    distribuidor = if (formulario.idDistribuidor == null) ErroCampo.OBRIGATORIO else null,
    // O contato é obrigatório no contrato (contato_solicitante).
    contato = validarTexto(formulario.contato, LIMITE_CONTATO, obrigatorio = true),
)

// Tipo de venda só vale para Digital Farming. Nesse motivo, se o cliente não escolheu, vale PROJETO; um
// DESCONHECIDO, que o seletor não oferece, também cai no padrão para nunca chegar à solicitação.
// Nos outros motivos devolve null: o app não deve enviar o campo.
fun tipoVendaEfetivo(formulario: FormularioAbertura): TipoVenda? =
    if (formulario.motivo == MotivoAtendimento.DIGITAL_FARMING) {
        formulario.tipoVenda?.takeIf { it != TipoVenda.DESCONHECIDO } ?: TipoVenda.PROJETO
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

// Troca o motivo mantendo o tipo de venda coerente com ele. Tipo de venda só existe em Digital Farming:
// ao entrar nesse motivo sem tipo escolhido vale PROJETO (o mesmo padrão de tipoVendaEfetivo), e ao
// sair dele o tipo é zerado, para não sobrar um valor escondido que seria enviado por engano.
fun FormularioAbertura.comMotivo(motivo: MotivoAtendimento): FormularioAbertura {
    val tipo = if (motivo == MotivoAtendimento.DIGITAL_FARMING) tipoVenda ?: TipoVenda.PROJETO else null
    return copy(motivo = motivo, tipoVenda = tipo)
}

// Marca a cultura se ainda não estava no conjunto e desmarca se já estava (chip de seleção múltipla).
fun FormularioAbertura.comCulturaAlternada(idCultura: Int): FormularioAbertura =
    copy(culturas = if (idCultura in culturas) culturas - idCultura else culturas + idCultura)

// Converte o formulário na solicitação que o repositório recebe. Só converte formulário válido: a própria
// função roda validar antes e devolve nulo se houver qualquer erro, então não existe como obter uma
// solicitação de um formulário que a tela barraria. Como validar também recusa motivo DESCONHECIDO e
// tipoVendaEfetivo nunca devolve DESCONHECIDO, o require do core não chega a recusar o resultado, desde que a
// origem recebida seja um valor real.
// O id (UUID do aparelho), a origem, o idCliente (da sessão) e o abertoEm (relógio do aparelho) vêm de fora: a
// função não gera nenhum deles, para ser previsível nos testes. Campos que o formulário do cliente não
// coleta (nome do produtor e área ou setor) vão vazios, que é como o core representa "não informado"; o texto
// digitado é aparado, que é o que o app deve enviar e o que validar mediu.
fun comoSolicitacao(
    formulario: FormularioAbertura,
    id: String,
    origem: OrigemAtendimento,
    idCliente: Int?,
    abertoEm: Instant,
): SolicitacaoDeAbertura? {
    if (!validar(formulario).valido) return null
    // Depois de validar, o motivo existe; o null aqui é só para o compilador.
    val motivo = formulario.motivo ?: return null
    return SolicitacaoDeAbertura(
        id = id,
        origem = origem,
        motivo = motivo,
        idCliente = idCliente,
        idDistribuidorOrigem = formulario.idDistribuidor,
        nomeProdutor = "",
        nomeFazenda = formulario.nomeFazenda.trim(),
        cidade = formulario.cidade.trim(),
        areaSetor = "",
        contato = formulario.contato.trim(),
        tipoVenda = tipoVendaEfetivo(formulario),
        culturas = formulario.culturas,
        abertoEm = abertoEm,
    )
}
