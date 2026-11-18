package com.example.netafim.atendimento.ui.abrir

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.ErroAoSalvar
import com.example.netafim.atendimento.dominio.ErroCampo
import com.example.netafim.atendimento.dominio.ErrosFormulario
import com.example.netafim.atendimento.dominio.FormularioAbertura
import com.example.netafim.atendimento.dominio.LIMITE_CIDADE
import com.example.netafim.atendimento.dominio.LIMITE_CONTATO
import com.example.netafim.atendimento.dominio.LIMITE_NOME_FAZENDA
import com.example.netafim.atendimento.dominio.LIMITE_NOME_PRODUTOR
import com.example.netafim.atendimento.dominio.comCulturaAlternada
import com.example.netafim.atendimento.dominio.comMotivo
import com.example.netafim.atendimento.dominio.tipoVendaEfetivo
import com.example.netafim.atendimento.ui.comum.AvisoDeErro
import com.example.netafim.atendimento.ui.comum.BarraDoAtendimento
import com.example.netafim.atendimento.ui.comum.TemaDePreview
import com.example.netafim.atendimento.ui.comum.rotulo
import com.example.netafim.core.modelo.AreaDoSetor
import com.example.netafim.core.modelo.Cultura
import com.example.netafim.core.modelo.Distribuidor
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.TipoVenda

// Formulário de abertura de atendimento. Tela sem estado: mostra o formulário e os erros que receber e
// devolve cada mudança por aoAlterar. Quem valida e quem envia é o chamador.
// "origem" diz quem está abrindo e, com isso, quais campos aparecem (cada origem tem os seus, conforme o
// formulário de solicitação da Netafim). "enviando" desabilita o botão e os campos enquanto a solicitação
// está a caminho. "erro" é o do último envio (nulo quando não há), separado dos erros por campo: é o que o
// repositório ou a sessão recusaram depois que o formulário já estava válido.
@Composable
fun AbrirAtendimentoTela(
    formulario: FormularioAbertura,
    origem: OrigemAtendimento,
    erros: ErrosFormulario,
    culturas: List<Cultura>,
    distribuidores: List<Distribuidor>,
    enviando: Boolean,
    erro: ErroAoSalvar?,
    aoAlterar: (FormularioAbertura) -> Unit,
    aoEnviar: () -> Unit,
    aoDispensarErro: () -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val habilitado = !enviando
    // imePadding levanta o conteúdo quando o teclado abre, para ele não cobrir o botão.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        BarraDoAtendimento(titulo = stringResource(R.string.atendimento_abrir_titulo), aoVoltar = aoVoltar)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // A ordem segue o formulário de solicitação da Netafim de cada origem:
            // - cliente final: distribuidor, cidade, nome da fazenda, cultura e motivo;
            // - distribuidor: nome do produtor, cidade, cultura e motivo;
            // - Netafim: área ou setor, nome do produtor, cidade, cultura e motivo.
            // Contato e tipo de venda vêm depois em todas, como já estavam.
            val ehCliente = origem == OrigemAtendimento.CLIENTE_FINAL
            val ehNetafim = origem == OrigemAtendimento.NETAFIM
            if (ehCliente) {
                CampoDistribuidor(
                    distribuidores = distribuidores,
                    escolhido = formulario.idDistribuidor,
                    habilitado = habilitado,
                    aoEscolher = { aoAlterar(formulario.copy(idDistribuidor = it)) },
                )
            }
            if (ehNetafim) {
                SecaoAreaSetor(
                    escolhida = formulario.areaSetor,
                    erro = erros.areaSetor,
                    habilitado = habilitado,
                    aoEscolher = { aoAlterar(formulario.copy(areaSetor = it)) },
                )
            }
            // O distribuidor e a Netafim abrem em nome de um produtor; o cliente final é o próprio produtor.
            if (ehNetafim || origem == OrigemAtendimento.DISTRIBUIDOR) {
                CampoDeTexto(
                    rotulo = stringResource(R.string.atendimento_abrir_nome_produtor),
                    valor = formulario.nomeProdutor,
                    aoMudar = { aoAlterar(formulario.copy(nomeProdutor = it)) },
                    erro = erros.nomeProdutor,
                    textoObrigatorio = R.string.atendimento_abrir_erro_nome_produtor,
                    limite = LIMITE_NOME_PRODUTOR,
                    teclado = KeyboardType.Text,
                    acao = ImeAction.Next,
                    habilitado = habilitado,
                )
            }
            CampoDeTexto(
                rotulo = stringResource(R.string.atendimento_abrir_cidade),
                valor = formulario.cidade,
                aoMudar = { aoAlterar(formulario.copy(cidade = it)) },
                erro = erros.cidade,
                textoObrigatorio = R.string.atendimento_abrir_erro_cidade,
                limite = LIMITE_CIDADE,
                teclado = KeyboardType.Text,
                acao = ImeAction.Next,
                habilitado = habilitado,
            )
            // Nome da fazenda só no formulário do cliente final.
            if (ehCliente) {
                CampoDeTexto(
                    rotulo = stringResource(R.string.atendimento_abrir_nome_fazenda),
                    valor = formulario.nomeFazenda,
                    aoMudar = { aoAlterar(formulario.copy(nomeFazenda = it)) },
                    erro = erros.nomeFazenda,
                    textoObrigatorio = R.string.atendimento_abrir_erro_nome_fazenda,
                    limite = LIMITE_NOME_FAZENDA,
                    teclado = KeyboardType.Text,
                    acao = ImeAction.Next,
                    habilitado = habilitado,
                )
            }
            SecaoCulturas(
                culturas = culturas,
                marcadas = formulario.culturas,
                erro = erros.culturas,
                habilitado = habilitado,
                aoAlternar = { aoAlterar(formulario.comCulturaAlternada(it)) },
            )
            SecaoMotivo(
                escolhido = formulario.motivo,
                erro = erros.motivo,
                habilitado = habilitado,
                aoEscolher = { aoAlterar(formulario.comMotivo(it)) },
            )
            CampoDeTexto(
                rotulo = stringResource(R.string.atendimento_abrir_contato),
                valor = formulario.contato,
                aoMudar = { aoAlterar(formulario.copy(contato = it)) },
                erro = erros.contato,
                textoObrigatorio = R.string.atendimento_abrir_erro_contato,
                limite = LIMITE_CONTATO,
                teclado = KeyboardType.Phone,
                acao = ImeAction.Done,
                habilitado = habilitado,
            )
            // Tipo de venda aparece em todos os motivos e começa sem nenhuma opção marcada.
            SecaoTipoVenda(
                escolhido = tipoVendaEfetivo(formulario),
                erro = erros.tipoVenda,
                habilitado = habilitado,
                aoEscolher = { aoAlterar(formulario.copy(tipoVenda = it)) },
            )
            // Erro do envio logo acima do botão, depois de todos os campos: aparecer aqui não move nenhum campo.
            if (erro != null) {
                AvisoDeErro(erro = erro, aoDispensar = aoDispensarErro)
            }
            BotaoEnviar(enviando = enviando, aoEnviar = aoEnviar)
        }
    }
}

// Rótulo pequeno acima de cada bloco do formulário. Campo obrigatório leva um asterisco no fim; para o leitor
// de tela o asterisco não é lido como símbolo: o texto lido é o rótulo seguido de "obrigatório".
@Composable
private fun Rotulo(texto: String, obrigatorio: Boolean = true) {
    val descricao = if (obrigatorio) stringResource(R.string.atendimento_abrir_obrigatorio_descricao, texto) else texto
    Text(
        text = if (obrigatorio) "$texto *" else texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = descricao },
    )
}

// Mensagem de erro de campo: ícone e texto, para a informação não depender só da cor.
@Composable
private fun MensagemDeErro(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = texto,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

// Texto do erro de um campo. O limite entra na mensagem de "muito longo".
@Composable
private fun textoDoErro(erro: ErroCampo, @StringRes obrigatorio: Int, limite: Int): String = when (erro) {
    ErroCampo.OBRIGATORIO -> stringResource(obrigatorio)
    ErroCampo.MUITO_LONGO -> stringResource(R.string.atendimento_abrir_erro_muito_longo, limite)
}

// Motivo: lista de cartões de seleção única, um por motivo que o cliente pode escolher.
@Composable
private fun SecaoMotivo(
    escolhido: MotivoAtendimento?,
    erro: ErroCampo?,
    habilitado: Boolean,
    aoEscolher: (MotivoAtendimento) -> Unit,
) {
    Column {
        Rotulo(stringResource(R.string.atendimento_abrir_motivo))
        Spacer(Modifier.size(8.dp))
        // selectableGroup faz o leitor de tela anunciar o conjunto como um grupo de opções.
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MotivoAtendimento.motivosDoCliente.forEach { motivo ->
                CartaoDeMotivo(
                    motivo = motivo,
                    selecionado = motivo == escolhido,
                    habilitado = habilitado,
                    aoClicar = { aoEscolher(motivo) },
                )
            }
        }
        if (erro != null) {
            MensagemDeErro(textoDoErro(erro, R.string.atendimento_abrir_erro_motivo, limite = 0))
        }
    }
}

// Cartão de um motivo: botão de rádio à esquerda e nome em negrito. O selecionado ganha borda de 2dp
// na cor primary; os demais têm borda de 1dp em outlineVariant.
@Composable
private fun CartaoDeMotivo(
    motivo: MotivoAtendimento,
    selecionado: Boolean,
    habilitado: Boolean,
    aoClicar: () -> Unit,
) {
    val forma = RoundedCornerShape(12.dp)
    val borda = if (selecionado) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(borda, forma)
            .clip(forma)
            .selectable(selected = selecionado, enabled = habilitado, role = Role.RadioButton, onClick = aoClicar)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // O toque é tratado pelo cartão inteiro; o rádio é só o desenho do estado.
        RadioButton(selected = selecionado, onClick = null, enabled = habilitado)
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(motivo.rotulo()),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

// Cores dos chips de seleção: cinza claro quando solto, primary quando marcado.
@Composable
private fun coresDoChip() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)

// Chip sem contorno; a diferença entre marcado e solto vem da cor de fundo e do ícone de marcação.
@Composable
private fun ChipDeSelecao(texto: String, marcado: Boolean, habilitado: Boolean, aoClicar: () -> Unit) {
    FilterChip(
        selected = marcado,
        onClick = aoClicar,
        enabled = habilitado,
        label = { Text(texto) },
        // O ícone de marcação reforça o estado para quem não distingue as cores.
        leadingIcon = if (marcado) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        colors = coresDoChip(),
        border = FilterChipDefaults.filterChipBorder(
            enabled = habilitado,
            selected = marcado,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

// Culturas: chips de seleção múltipla, quebrando de linha quando não cabem.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SecaoCulturas(
    culturas: List<Cultura>,
    marcadas: Set<Int>,
    erro: ErroCampo?,
    habilitado: Boolean,
    aoAlternar: (Int) -> Unit,
) {
    Column {
        Rotulo(stringResource(R.string.atendimento_abrir_cultura))
        Spacer(Modifier.size(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            culturas.forEach { cultura ->
                ChipDeSelecao(
                    texto = cultura.nome,
                    marcado = cultura.id in marcadas,
                    habilitado = habilitado,
                    aoClicar = { aoAlternar(cultura.id) },
                )
            }
        }
        // Cultura só tem o erro de obrigatório; o limite não entra na mensagem.
        if (erro != null) {
            MensagemDeErro(textoDoErro(erro, R.string.atendimento_abrir_erro_cultura, limite = 0))
        }
    }
}

// Aparência dos campos de texto: fundo cinza claro, sem sublinhado. O erro aparece pelo ícone e pelo
// texto abaixo do campo, não por linha colorida.
@Composable
private fun coresDoCampo(): TextFieldColors {
    val fundo = MaterialTheme.colorScheme.surfaceVariant
    return TextFieldDefaults.colors(
        focusedContainerColor = fundo,
        unfocusedContainerColor = fundo,
        disabledContainerColor = fundo,
        errorContainerColor = fundo,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
    )
}

private val formaDoCampo = RoundedCornerShape(12.dp)

// Campo de texto de uma linha com o rótulo acima. O rótulo, o campo e a mensagem de erro formam um
// só item para o leitor de tela (mergeDescendants): ele lê o rótulo, o campo de edição e o erro juntos.
@Composable
private fun CampoDeTexto(
    rotulo: String,
    valor: String,
    aoMudar: (String) -> Unit,
    erro: ErroCampo?,
    @StringRes textoObrigatorio: Int,
    limite: Int,
    teclado: KeyboardType,
    acao: ImeAction,
    habilitado: Boolean,
) {
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Rotulo(rotulo)
        Spacer(Modifier.size(8.dp))
        TextField(
            value = valor,
            onValueChange = aoMudar,
            modifier = Modifier.fillMaxWidth(),
            enabled = habilitado,
            isError = erro != null,
            singleLine = true,
            shape = formaDoCampo,
            colors = coresDoCampo(),
            keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = acao),
        )
        if (erro != null) {
            MensagemDeErro(textoDoErro(erro, textoObrigatorio, limite))
        }
    }
}

// Distribuidor: menu suspenso somente leitura com os distribuidores e a opção "Sem distribuidor". O campo é
// opcional (decisão da Netafim de 18/11/2026): quem não tem distribuidor padrão segue sem escolher, e quem já
// escolheu pode voltar atrás com "Sem distribuidor". Por ser opcional não tem erro nem asterisco; um texto de
// apoio abaixo explica para onde vai o atendimento sem distribuidor.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoDistribuidor(
    distribuidores: List<Distribuidor>,
    escolhido: Int?,
    habilitado: Boolean,
    aoEscolher: (Int?) -> Unit,
) {
    // Só o abrir e fechar do menu fica guardado aqui; a escolha em si volta para o chamador.
    var aberto by remember { mutableStateOf(false) }
    // Sem escolha o valor fica vazio e o placeholder mostra "Selecione o distribuidor".
    val nomeExibido = distribuidores.firstOrNull { it.id == escolhido }?.nome.orEmpty()
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Rotulo(stringResource(R.string.atendimento_abrir_distribuidor), obrigatorio = false)
        Spacer(Modifier.size(8.dp))
        ExposedDropdownMenuBox(expanded = aberto && habilitado, onExpandedChange = { aberto = it }) {
            TextField(
                value = nomeExibido,
                onValueChange = {},
                readOnly = true,
                enabled = habilitado,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.atendimento_abrir_distribuidor_selecione)) },
                shape = formaDoCampo,
                colors = coresDoCampo(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aberto) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = aberto && habilitado, onDismissRequest = { aberto = false }) {
                // Volta ao estado sem distribuidor (nulo), que é uma escolha válida.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.atendimento_abrir_distribuidor_nenhum)) },
                    onClick = {
                        aoEscolher(null)
                        aberto = false
                    },
                )
                distribuidores.forEach { distribuidor ->
                    DropdownMenuItem(
                        text = { Text(distribuidor.nome) },
                        onClick = {
                            aoEscolher(distribuidor.id)
                            aberto = false
                        },
                    )
                }
            }
        }
        // Texto de apoio dentro da mesma coluna com mergeDescendants, para o leitor de tela ler junto do campo.
        Text(
            text = stringResource(R.string.atendimento_abrir_distribuidor_apoio),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// Área ou setor da Netafim: um chip de seleção única por área, quebrando de linha quando não cabem. Só
// aparece para a origem Netafim. Usa os mesmos chips das culturas e do tipo de venda.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SecaoAreaSetor(
    escolhida: AreaDoSetor?,
    erro: ErroCampo?,
    habilitado: Boolean,
    aoEscolher: (AreaDoSetor) -> Unit,
) {
    Column {
        Rotulo(stringResource(R.string.atendimento_abrir_area_setor))
        Spacer(Modifier.size(8.dp))
        // selectableGroup faz o leitor de tela anunciar as áreas como um grupo de opções.
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AreaDoSetor.entries.forEach { area ->
                ChipDeSelecao(
                    texto = stringResource(area.rotulo()),
                    marcado = area == escolhida,
                    habilitado = habilitado,
                    aoClicar = { aoEscolher(area) },
                )
            }
        }
        // Área só tem o erro de obrigatório; o limite não entra na mensagem.
        if (erro != null) {
            MensagemDeErro(textoDoErro(erro, R.string.atendimento_abrir_erro_area_setor, limite = 0))
        }
    }
}

// Tipo de venda: dois chips de seleção única, obrigatório e sem opção pré-marcada. "escolhido" vem de
// tipoVendaEfetivo e é nulo até o usuário escolher; sem escolha, o erro aparece depois da tentativa de envio.
@Composable
private fun SecaoTipoVenda(
    escolhido: TipoVenda?,
    erro: ErroCampo?,
    habilitado: Boolean,
    aoEscolher: (TipoVenda) -> Unit,
) {
    Column {
        Rotulo(stringResource(R.string.atendimento_abrir_tipo_venda))
        Spacer(Modifier.size(8.dp))
        // selectableGroup faz o leitor de tela anunciar as opções como um grupo.
        Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipDeSelecao(
                texto = stringResource(TipoVenda.PROJETO.rotulo()),
                marcado = escolhido == TipoVenda.PROJETO,
                habilitado = habilitado,
                aoClicar = { aoEscolher(TipoVenda.PROJETO) },
            )
            ChipDeSelecao(
                texto = stringResource(TipoVenda.VENDA_REGULAR.rotulo()),
                marcado = escolhido == TipoVenda.VENDA_REGULAR,
                habilitado = habilitado,
                aoClicar = { aoEscolher(TipoVenda.VENDA_REGULAR) },
            )
        }
        // Tipo de venda só tem o erro de obrigatório; o limite não entra na mensagem.
        if (erro != null) {
            MensagemDeErro(textoDoErro(erro, R.string.atendimento_abrir_erro_tipo_venda, limite = 0))
        }
    }
}

// Botão de envio em largura total. Enquanto envia, fica desabilitado e mostra um indicador de progresso
// pequeno no lugar do texto.
@Composable
private fun BotaoEnviar(enviando: Boolean, aoEnviar: () -> Unit) {
    Button(
        onClick = aoEnviar,
        enabled = !enviando,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (enviando) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(stringResource(R.string.atendimento_abrir_enviar))
        }
    }
}

// Dados fictícios só para os previews.
private val culturasDoPreview = listOf(
    Cultura(1, "Cacau"),
    Cultura(2, "Café"),
    Cultura(3, "Cana-de-açúcar"),
    Cultura(4, "Citrus"),
    Cultura(5, "Grãos"),
    Cultura(6, "Outros"),
)
private val distribuidoresDoPreview = listOf(
    Distribuidor(10, "Distribuidora Exemplo Norte"),
    Distribuidor(11, "Revenda Exemplo Centro"),
    Distribuidor(12, "Agro Exemplo Sul"),
)
private val formularioPreenchido = FormularioAbertura(
    motivo = MotivoAtendimento.DIGITAL_FARMING,
    culturas = setOf(2, 4),
    cidade = "Arealva",
    nomeFazenda = "Fazenda Boa Vista",
    contato = "14999990000",
    idDistribuidor = 11,
    tipoVenda = TipoVenda.PROJETO,
)

// Formulários preenchidos do distribuidor e da Netafim, cada um só com os campos da sua origem.
private val formularioDoDistribuidor = FormularioAbertura(
    motivo = MotivoAtendimento.POS_VENDA,
    culturas = setOf(1),
    cidade = "Bauru",
    contato = "14988880000",
    nomeProdutor = "José da Silva",
    tipoVenda = TipoVenda.VENDA_REGULAR,
)
private val formularioDaNetafim = FormularioAbertura(
    motivo = MotivoAtendimento.TREINAMENTO,
    culturas = setOf(3, 5),
    cidade = "Ribeirão Preto",
    contato = "16977770000",
    nomeProdutor = "Maria Souza",
    areaSetor = AreaDoSetor.ENGENHARIA,
    tipoVenda = TipoVenda.PROJETO,
)

@Composable
private fun TelaDePreview(
    formulario: FormularioAbertura,
    origem: OrigemAtendimento = OrigemAtendimento.CLIENTE_FINAL,
    erros: ErrosFormulario = ErrosFormulario(),
    enviando: Boolean = false,
    erro: ErroAoSalvar? = null,
) {
    TemaDePreview {
        AbrirAtendimentoTela(
            formulario = formulario,
            origem = origem,
            erros = erros,
            culturas = culturasDoPreview,
            distribuidores = distribuidoresDoPreview,
            enviando = enviando,
            erro = erro,
            aoAlterar = {},
            aoEnviar = {},
            aoDispensarErro = {},
            aoVoltar = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AbrirAtendimentoVazioPreview() {
    TelaDePreview(formulario = FormularioAbertura())
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AbrirAtendimentoPreenchidoPreview() {
    TelaDePreview(formulario = formularioPreenchido)
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AbrirAtendimentoComErrosPreview() {
    TelaDePreview(
        formulario = FormularioAbertura(),
        // O que o validar devolve para o formulário vazio: todos os obrigatórios faltando.
        erros = ErrosFormulario(
            motivo = ErroCampo.OBRIGATORIO,
            cidade = ErroCampo.OBRIGATORIO,
            nomeFazenda = ErroCampo.OBRIGATORIO,
            culturas = ErroCampo.OBRIGATORIO,
            contato = ErroCampo.OBRIGATORIO,
            tipoVenda = ErroCampo.OBRIGATORIO,
        ),
    )
}

// Erros dos campos novos com o resto preenchido: cultura e tipo de venda sem escolha, e cidade e nome
// da fazenda passando do limite, para conferir a mensagem de "muito longo" com 100 e 150. O distribuidor
// fica sem escolha de propósito: é opcional e não gera erro.
@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AbrirAtendimentoErrosDosCamposNovosPreview() {
    TelaDePreview(
        formulario = formularioPreenchido.copy(
            culturas = emptySet(),
            idDistribuidor = null,
            tipoVenda = null,
            cidade = "a".repeat(LIMITE_CIDADE + 1),
            nomeFazenda = "a".repeat(LIMITE_NOME_FAZENDA + 1),
        ),
        erros = ErrosFormulario(
            cidade = ErroCampo.MUITO_LONGO,
            nomeFazenda = ErroCampo.MUITO_LONGO,
            culturas = ErroCampo.OBRIGATORIO,
            tipoVenda = ErroCampo.OBRIGATORIO,
        ),
    )
}

// Formulário válido, mas o envio voltou sem permissão (usuário de cliente sem empresa, por exemplo).
@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AbrirAtendimentoComErroNoEnvioPreview() {
    TelaDePreview(formulario = formularioPreenchido, erro = ErroAoSalvar.SemPermissao)
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AbrirAtendimentoEnviandoPreview() {
    TelaDePreview(formulario = formularioPreenchido, enviando = true)
}

// Abertura pelo distribuidor: nome do produtor no lugar da fazenda e sem escolha de distribuidor.
@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AbrirAtendimentoDistribuidorPreview() {
    TelaDePreview(formulario = formularioDoDistribuidor, origem = OrigemAtendimento.DISTRIBUIDOR)
}

// Abertura pela Netafim: área ou setor e nome do produtor, sem fazenda e sem distribuidor.
@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AbrirAtendimentoNetafimPreview() {
    TelaDePreview(formulario = formularioDaNetafim, origem = OrigemAtendimento.NETAFIM)
}

// O que o validar devolve para o formulário vazio da Netafim: a área e o nome do produtor aparecem com erro
// junto dos campos comuns, e nada de fazenda nem de distribuidor.
@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AbrirAtendimentoNetafimComErrosPreview() {
    TelaDePreview(
        formulario = FormularioAbertura(),
        origem = OrigemAtendimento.NETAFIM,
        erros = ErrosFormulario(
            motivo = ErroCampo.OBRIGATORIO,
            cidade = ErroCampo.OBRIGATORIO,
            culturas = ErroCampo.OBRIGATORIO,
            contato = ErroCampo.OBRIGATORIO,
            nomeProdutor = ErroCampo.OBRIGATORIO,
            areaSetor = ErroCampo.OBRIGATORIO,
            tipoVenda = ErroCampo.OBRIGATORIO,
        ),
    )
}
