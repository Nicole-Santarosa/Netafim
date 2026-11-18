package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.AreaDoSetor
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.SolicitacaoDeAbertura
import com.example.netafim.core.modelo.TipoVenda
import java.time.Instant

// Limites de tamanho do contrato da API (schema AberturaAtendimento): cidade aceita até 100
// caracteres, nome_fazenda_informado e nome_produtor até 150 e contato_solicitante até 20.
const val LIMITE_CIDADE = 100
const val LIMITE_NOME_FAZENDA = 150
const val LIMITE_NOME_PRODUTOR = 150
const val LIMITE_CONTATO = 20

// Dados que o usuário preenche na abertura. Todos começam vazios.
// Os campos de identificação (id do atendimento, origem, id do cliente, distribuidor de origem do
// distribuidor) não são do formulário: o app os preenche com o aparelho e a sessão.
// O mesmo formulário serve às três origens, mas cada uma só preenche (e só é cobrada por) uma parte dele,
// conforme o formulário de solicitação da Netafim:
// - cliente final: cidade, nome da fazenda, cultura e motivo, mais o distribuidor da região, que é opcional;
// - distribuidor: nome do produtor, cidade, cultura e motivo;
// - Netafim: área ou setor, nome do produtor, cidade, cultura e motivo.
// Em todas o contato também é obrigatório (contato_solicitante não aceita nulo no banco), e o tipo de venda
// também, sem valor pré-selecionado (decisão da Netafim de 18/11/2026).
data class FormularioAbertura(
    val motivo: MotivoAtendimento? = null,
    // Ids do catálogo de culturas (GET /culturas). Ao menos um.
    val culturas: Set<Int> = emptySet(),
    // Vai no campo cidade da API.
    val cidade: String = "",
    // Vai no campo nome_fazenda_informado da API.
    val nomeFazenda: String = "",
    val contato: String = "",
    // Distribuidor escolhido na lista. Só o cliente final escolhe, e é opcional: o cliente final sem
    // distribuidor padrão abre o atendimento sem ele, que vai direto para a fila do Netafim Admin.
    val idDistribuidor: Int? = null,
    // Obrigatório em todas as origens e em todos os motivos. Começa nulo: nenhuma opção vem marcada.
    val tipoVenda: TipoVenda? = null,
    // Vai no campo nome_produtor da API. Só o distribuidor e a Netafim informam.
    val nomeProdutor: String = "",
    // Vai no campo area_setor da API. Só a Netafim informa.
    val areaSetor: AreaDoSetor? = null,
)

enum class ErroCampo {
    OBRIGATORIO,
    MUITO_LONGO,
}

// Um erro opcional por campo validado. Culturas, área e tipo de venda só têm o erro de obrigatório,
// porque são escolhas de lista. O distribuidor não tem erro: é opcional em todas as origens.
data class ErrosFormulario(
    val motivo: ErroCampo? = null,
    val cidade: ErroCampo? = null,
    val nomeFazenda: ErroCampo? = null,
    val culturas: ErroCampo? = null,
    val contato: ErroCampo? = null,
    val nomeProdutor: ErroCampo? = null,
    val areaSetor: ErroCampo? = null,
    val tipoVenda: ErroCampo? = null,
) {
    val valido: Boolean
        get() = motivo == null && cidade == null && nomeFazenda == null && culturas == null &&
            contato == null && nomeProdutor == null && areaSetor == null && tipoVenda == null
}

// Valida o formulário para a origem de quem está abrindo. Motivo, cidade, cultura, contato e tipo de venda valem
// para todas as origens; o resto é cobrado só da origem a que pertence. O distribuidor não é cobrado de
// nenhuma: é opcional, e o cliente final sem ele abre o atendimento normalmente. Campo que não é da origem é ignorado, mesmo que
// tenha sobrado algum valor nele (inclusive um texto longo demais): ele não é desenhado na tela nem enviado,
// então não pode travar o envio.
// Uma origem DESCONHECIDO não tem campos próprios: só os comuns são cobrados (e comoSolicitacao a recusa).
fun validar(formulario: FormularioAbertura, origem: OrigemAtendimento): ErrosFormulario {
    val ehCliente = origem == OrigemAtendimento.CLIENTE_FINAL
    val ehNetafim = origem == OrigemAtendimento.NETAFIM
    val pedeProdutor = ehNetafim || origem == OrigemAtendimento.DISTRIBUIDOR
    return ErrosFormulario(
        // O motivo é obrigatório no contrato da API. DESCONHECIDO não é uma escolha possível (o seletor só
        // oferece os motivos da abertura), mas se chegasse aqui contaria como motivo não escolhido.
        motivo = if (formulario.motivo == null || formulario.motivo == MotivoAtendimento.DESCONHECIDO) {
            ErroCampo.OBRIGATORIO
        } else {
            null
        },
        cidade = validarTexto(formulario.cidade, LIMITE_CIDADE, obrigatorio = true),
        culturas = if (formulario.culturas.isEmpty()) ErroCampo.OBRIGATORIO else null,
        // O contato é obrigatório no contrato (contato_solicitante).
        contato = validarTexto(formulario.contato, LIMITE_CONTATO, obrigatorio = true),
        // O tipo de venda é obrigatório em todo atendimento (a API responde 400 sem ele). DESCONHECIDO só
        // existe para registros vindos do servidor e não é uma escolha possível, então conta como não escolhido.
        tipoVenda = if (formulario.tipoVenda == null || formulario.tipoVenda == TipoVenda.DESCONHECIDO) {
            ErroCampo.OBRIGATORIO
        } else {
            null
        },
        // Só do cliente final.
        nomeFazenda = if (ehCliente) {
            validarTexto(formulario.nomeFazenda, LIMITE_NOME_FAZENDA, obrigatorio = true)
        } else {
            null
        },
        // Do distribuidor e da Netafim.
        nomeProdutor = if (pedeProdutor) {
            validarTexto(formulario.nomeProdutor, LIMITE_NOME_PRODUTOR, obrigatorio = true)
        } else {
            null
        },
        // Só da Netafim.
        areaSetor = if (ehNetafim && formulario.areaSetor == null) ErroCampo.OBRIGATORIO else null,
    )
}

// Tipo de venda escolhido pelo usuário, e só ele: não há valor padrão nem regra por motivo (decisão da Netafim
// de 18/11/2026, o tipo de venda é obrigatório em todo atendimento). Devolve nulo enquanto não houver escolha.
// Um DESCONHECIDO, que o seletor não oferece, também conta como não escolhido para nunca chegar à solicitação.
fun tipoVendaEfetivo(formulario: FormularioAbertura): TipoVenda? =
    formulario.tipoVenda?.takeIf { it != TipoVenda.DESCONHECIDO }

// O tamanho conta depois de aparar os espaços das pontas, que é o que o app deve enviar.
private fun validarTexto(texto: String, limite: Int, obrigatorio: Boolean): ErroCampo? {
    val aparado = texto.trim()
    return when {
        aparado.isEmpty() -> if (obrigatorio) ErroCampo.OBRIGATORIO else null
        aparado.length > limite -> ErroCampo.MUITO_LONGO
        else -> null
    }
}

// Troca o motivo. O tipo de venda não depende do motivo (vale para todos), então a escolha já feita é
// mantida e, se ainda não houve escolha, continua nulo: nenhum valor é pré-selecionado.
fun FormularioAbertura.comMotivo(motivo: MotivoAtendimento): FormularioAbertura = copy(motivo = motivo)

// Zera os campos que não pertencem à origem informada, para nada escolhido numa origem anterior ficar
// escondido no estado e valer numa origem que não o pede (por exemplo, um distribuidor escolhido como cliente
// final seguir no formulário depois que a sessão passa a ser de distribuidor ou da Netafim). Cada origem
// guarda só o que é dela: cliente final, o distribuidor escolhido e o nome da fazenda; distribuidor e
// Netafim, o nome do produtor; só a Netafim, a área ou setor. Origem nula (ninguém logado) ou desconhecida
// não guarda nenhum desses campos. Os campos comuns (motivo, culturas, cidade, contato e tipo de venda)
// ficam como estão.
fun FormularioAbertura.limparParaOrigem(origem: OrigemAtendimento?): FormularioAbertura {
    val ehCliente = origem == OrigemAtendimento.CLIENTE_FINAL
    val ehNetafim = origem == OrigemAtendimento.NETAFIM
    val pedeProdutor = ehNetafim || origem == OrigemAtendimento.DISTRIBUIDOR
    return copy(
        idDistribuidor = if (ehCliente) idDistribuidor else null,
        nomeFazenda = if (ehCliente) nomeFazenda else "",
        nomeProdutor = if (pedeProdutor) nomeProdutor else "",
        areaSetor = if (ehNetafim) areaSetor else null,
    )
}

// Marca a cultura se ainda não estava no conjunto e desmarca se já estava (chip de seleção múltipla).
fun FormularioAbertura.comCulturaAlternada(idCultura: Int): FormularioAbertura =
    copy(culturas = if (idCultura in culturas) culturas - idCultura else culturas + idCultura)

// Converte o formulário na solicitação que o repositório recebe. Só converte formulário válido para a
// origem: a própria função roda validar antes e devolve nulo se houver qualquer erro, então não existe como
// obter uma solicitação de um formulário que a tela barraria. Como validar também recusa motivo e tipo de venda
// DESCONHECIDO, o tipo de venda da solicitação é sempre uma escolha real (nunca nulo) e a área só vai (e sempre
// vai) na origem Netafim, o require do core não chega a recusar o resultado. Origem DESCONHECIDO devolve nulo
// pelo mesmo motivo.
// O id (UUID do aparelho), a origem com os ids que vêm da sessão e o abertoEm (relógio do aparelho) vêm de
// fora: a função não gera nenhum deles, para ser previsível nos testes.
// Cada origem manda só o que é dela; o resto vai vazio (texto) ou nulo, que é como o core representa "não
// informado", mesmo que o formulário tenha sobrado com algum valor:
// - cliente final: nome da fazenda e o distribuidor escolhido no formulário, que pode não ter sido escolhido
//   (nulo): o atendimento nasce sem distribuidor de origem e vai para a fila do Netafim Admin;
// - distribuidor: nome do produtor e o distribuidor de origem que veio da sessão;
// - Netafim: nome do produtor e área ou setor.
// O texto digitado é aparado, que é o que o app deve enviar e o que validar mediu.
fun comoSolicitacao(
    formulario: FormularioAbertura,
    id: String,
    origem: OrigemDaAbertura,
    abertoEm: Instant,
): SolicitacaoDeAbertura? {
    if (origem.origem == OrigemAtendimento.DESCONHECIDO) return null
    if (!validar(formulario, origem.origem).valido) return null
    // Depois de validar, o motivo existe; o null aqui é só para o compilador.
    val motivo = formulario.motivo ?: return null
    val tipoVenda = tipoVendaEfetivo(formulario) ?: return null
    val ehCliente = origem.origem == OrigemAtendimento.CLIENTE_FINAL
    val ehNetafim = origem.origem == OrigemAtendimento.NETAFIM
    return SolicitacaoDeAbertura(
        id = id,
        origem = origem.origem,
        motivo = motivo,
        idCliente = origem.idCliente,
        // O cliente escolhe na lista; o distribuidor é ele mesmo (sessão); a Netafim não tem.
        idDistribuidorOrigem = if (ehCliente) formulario.idDistribuidor else origem.idDistribuidorOrigem,
        nomeProdutor = if (ehCliente) "" else formulario.nomeProdutor.trim(),
        nomeFazenda = if (ehCliente) formulario.nomeFazenda.trim() else "",
        cidade = formulario.cidade.trim(),
        areaSetor = if (ehNetafim) formulario.areaSetor else null,
        contato = formulario.contato.trim(),
        tipoVenda = tipoVenda,
        culturas = formulario.culturas,
        abertoEm = abertoEm,
    )
}
