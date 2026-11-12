package com.netafim.posvenda.atendimento.ui.abrir

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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.ErroCampo
import com.netafim.posvenda.atendimento.dominio.ErrosFormulario
import com.netafim.posvenda.atendimento.dominio.FormularioAbertura
import com.netafim.posvenda.atendimento.dominio.LIMITE_CONTATO
import com.netafim.posvenda.atendimento.dominio.LIMITE_LOCALIDADE
import com.netafim.posvenda.atendimento.dominio.MotivoAtendimento
import com.netafim.posvenda.atendimento.dominio.OpcaoCultura
import com.netafim.posvenda.atendimento.dominio.OpcaoDistribuidor
import com.netafim.posvenda.atendimento.dominio.TipoVenda
import com.netafim.posvenda.atendimento.dominio.comCulturaAlternada
import com.netafim.posvenda.atendimento.dominio.comMotivo
import com.netafim.posvenda.atendimento.dominio.tipoVendaEfetivo
import com.netafim.posvenda.atendimento.ui.comum.BarraDoAtendimento
import com.netafim.posvenda.atendimento.ui.comum.TemaDePreview
import com.netafim.posvenda.atendimento.ui.comum.rotulo

// Formulário de abertura de atendimento do cliente. Tela sem estado: mostra o formulário e os erros
// que receber e devolve cada mudança por aoAlterar. Quem valida e quem envia é o chamador.
// "enviando" desabilita o botão e os campos enquanto a solicitação está a caminho.
@Composable
fun AbrirAtendimentoTela(
    formulario: FormularioAbertura,
    erros: ErrosFormulario,
    culturas: List<OpcaoCultura>,
    distribuidores: List<OpcaoDistribuidor>,
    enviando: Boolean,
    aoAlterar: (FormularioAbertura) -> Unit,
    aoEnviar: () -> Unit,
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
            SecaoMotivo(
                escolhido = formulario.motivo,
                erro = erros.motivo,
                habilitado = habilitado,
                aoEscolher = { aoAlterar(formulario.comMotivo(it)) },
            )
            SecaoCulturas(
                culturas = culturas,
                marcadas = formulario.culturas,
                habilitado = habilitado,
                aoAlternar = { aoAlterar(formulario.comCulturaAlternada(it)) },
            )
            CampoDeTexto(
                rotulo = stringResource(R.string.atendimento_abrir_localidade),
                valor = formulario.localidade,
                aoMudar = { aoAlterar(formulario.copy(localidade = it)) },
                erro = erros.localidade,
                textoObrigatorio = R.string.atendimento_abrir_erro_localidade,
                limite = LIMITE_LOCALIDADE,
                teclado = KeyboardType.Text,
                acao = ImeAction.Next,
                habilitado = habilitado,
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
            CampoDistribuidor(
                distribuidores = distribuidores,
                escolhido = formulario.idDistribuidor,
                habilitado = habilitado,
                aoEscolher = { aoAlterar(formulario.copy(idDistribuidor = it)) },
            )
            // Tipo de venda só existe no motivo Digital Farming.
            if (formulario.motivo == MotivoAtendimento.DIGITAL_FARMING) {
                SecaoTipoVenda(
                    escolhido = tipoVendaEfetivo(formulario),
                    habilitado = habilitado,
                    aoEscolher = { aoAlterar(formulario.copy(tipoVenda = it)) },
                )
            }
            BotaoEnviar(enviando = enviando, aoEnviar = aoEnviar)
        }
    }
}

// Rótulo pequeno acima de cada bloco do formulário.
@Composable
private fun Rotulo(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    culturas: List<OpcaoCultura>,
    marcadas: Set<Int>,
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

// Distribuidor: menu suspenso somente leitura. A primeira opção, "Nenhum", limpa a escolha.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoDistribuidor(
    distribuidores: List<OpcaoDistribuidor>,
    escolhido: Int?,
    habilitado: Boolean,
    aoEscolher: (Int?) -> Unit,
) {
    // Só o abrir e fechar do menu fica guardado aqui; a escolha em si volta para o chamador.
    var aberto by remember { mutableStateOf(false) }
    val nenhum = stringResource(R.string.atendimento_abrir_distribuidor_nenhum)
    val nomeExibido = distribuidores.firstOrNull { it.id == escolhido }?.nome ?: nenhum
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Rotulo(stringResource(R.string.atendimento_abrir_distribuidor))
        Spacer(Modifier.size(8.dp))
        ExposedDropdownMenuBox(expanded = aberto && habilitado, onExpandedChange = { aberto = it }) {
            TextField(
                value = nomeExibido,
                onValueChange = {},
                readOnly = true,
                enabled = habilitado,
                singleLine = true,
                shape = formaDoCampo,
                colors = coresDoCampo(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aberto) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = aberto && habilitado, onDismissRequest = { aberto = false }) {
                DropdownMenuItem(
                    text = { Text(nenhum) },
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
    }
}

// Tipo de venda: dois chips de seleção única. O valor mostrado como marcado já considera o padrão
// (Projeto quando o cliente ainda não escolheu), vindo de tipoVendaEfetivo.
@Composable
private fun SecaoTipoVenda(escolhido: TipoVenda?, habilitado: Boolean, aoEscolher: (TipoVenda) -> Unit) {
    Column {
        Rotulo(stringResource(R.string.atendimento_abrir_tipo_venda))
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipDeSelecao(
                texto = stringResource(R.string.atendimento_abrir_tipo_venda_projeto),
                marcado = escolhido == TipoVenda.PROJETO,
                habilitado = habilitado,
                aoClicar = { aoEscolher(TipoVenda.PROJETO) },
            )
            ChipDeSelecao(
                texto = stringResource(R.string.atendimento_abrir_tipo_venda_regular),
                marcado = escolhido == TipoVenda.VENDA_REGULAR,
                habilitado = habilitado,
                aoClicar = { aoEscolher(TipoVenda.VENDA_REGULAR) },
            )
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
    OpcaoCultura(1, "Cacau"),
    OpcaoCultura(2, "Café"),
    OpcaoCultura(3, "Cana-de-açúcar"),
    OpcaoCultura(4, "Citrus"),
    OpcaoCultura(5, "Grãos"),
    OpcaoCultura(6, "Outros"),
)
private val distribuidoresDoPreview = listOf(
    OpcaoDistribuidor(10, "Distribuidora Exemplo Norte"),
    OpcaoDistribuidor(11, "Revenda Exemplo Centro"),
    OpcaoDistribuidor(12, "Agro Exemplo Sul"),
)
private val formularioPreenchido = FormularioAbertura(
    motivo = MotivoAtendimento.DIGITAL_FARMING,
    culturas = setOf(2, 4),
    localidade = "Arealva, SP",
    contato = "14999990000",
    idDistribuidor = 11,
    tipoVenda = TipoVenda.PROJETO,
)

@Composable
private fun TelaDePreview(
    formulario: FormularioAbertura,
    erros: ErrosFormulario = ErrosFormulario(),
    enviando: Boolean = false,
) {
    TemaDePreview {
        AbrirAtendimentoTela(
            formulario = formulario,
            erros = erros,
            culturas = culturasDoPreview,
            distribuidores = distribuidoresDoPreview,
            enviando = enviando,
            aoAlterar = {},
            aoEnviar = {},
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
        erros = ErrosFormulario(
            motivo = ErroCampo.OBRIGATORIO,
            localidade = ErroCampo.OBRIGATORIO,
            contato = ErroCampo.OBRIGATORIO,
        ),
    )
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AbrirAtendimentoEnviandoPreview() {
    TelaDePreview(formulario = formularioPreenchido, enviando = true)
}
