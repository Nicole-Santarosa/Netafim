package com.netafim.posvenda.atendimento.ui.edicao

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.netafim.posvenda.atendimento.R
import com.netafim.posvenda.atendimento.dominio.DadosDaEdicao
import com.netafim.posvenda.atendimento.dominio.ErroAoSalvar
import com.netafim.posvenda.atendimento.dominio.MotivoAtendimento
import com.netafim.posvenda.atendimento.dominio.SituacaoDoProjeto
import com.netafim.posvenda.atendimento.dominio.podeSalvar
import com.netafim.posvenda.atendimento.ui.comum.BarraDoAtendimento
import com.netafim.posvenda.atendimento.ui.comum.CampoDeLeitura
import com.netafim.posvenda.atendimento.ui.comum.TemaDePreview
import com.netafim.posvenda.atendimento.ui.comum.rotulo

// Edição reduzida do atendimento para o Admin da Netafim: mostra o atendimento em leitura e deixa trocar só
// a situação do projeto. Tela sem estado: a escolha, o "salvando" e o erro chegam por parâmetro e cada ação
// volta por lambda. A tela não olha perfil (quem pode alterar é regra do banco) e não sabe o que acontece
// depois do toque em Salvar: a chamada ao servidor é da camada de dados. Plano de ação, status, destinação e
// técnico ficam fora desta versão.
@Composable
fun EdicaoDoAtendimentoTela(
    dados: DadosDaEdicao,
    escolhida: SituacaoDoProjeto?,
    salvando: Boolean,
    erro: ErroAoSalvar?,
    aoEscolher: (SituacaoDoProjeto) -> Unit,
    aoSalvar: () -> Unit,
    aoRecarregar: () -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BarraDoAtendimento(titulo = stringResource(R.string.atendimento_edicao_titulo), aoVoltar = aoVoltar)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(26.dp))

            // Campo em leitura: número da OS e motivo, como no protótipo ("OS-0477 · Digital Farming").
            // O separador é pontuação, por isso fica aqui e não no strings.xml.
            CampoDeLeitura(
                rotulo = stringResource(R.string.atendimento_edicao_atendimento),
                valor = "${dados.protocolo} · ${stringResource(dados.motivo.rotulo())}",
            )

            // O único campo editável. Com a situação já gravada o seletor abre com ela marcada; sem
            // situação gravada abre sem nada marcado (escolhida nula).
            SeletorDaSituacao(escolhida = escolhida, habilitado = !salvando, aoEscolher = aoEscolher)

            // Erro acima do botão. Some quando o chamador limpa o erro (ao escolher de novo, por exemplo).
            if (erro != null) {
                Spacer(Modifier.height(16.dp))
                AreaDeErro(erro = erro, aoRecarregar = aoRecarregar)
            }

            Spacer(Modifier.height(if (erro != null) 16.dp else 34.dp))
            BotaoSalvar(
                habilitado = podeSalvar(dados.situacaoOriginal, escolhida, salvando),
                salvando = salvando,
                aoSalvar = aoSalvar,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// Rótulo e as três opções. selectableGroup agrupa as opções para o leitor de tela anunciar o conjunto, e
// cada opção expõe o estado selecionado (selectable com Role.RadioButton).
@Composable
private fun SeletorDaSituacao(
    escolhida: SituacaoDoProjeto?,
    habilitado: Boolean,
    aoEscolher: (SituacaoDoProjeto) -> Unit,
) {
    Text(
        text = stringResource(R.string.atendimento_edicao_situacao),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SituacaoDoProjeto.entries.forEach { situacao ->
            OpcaoDaSituacao(
                situacao = situacao,
                selecionada = situacao == escolhida,
                habilitado = habilitado,
                aoClicar = { aoEscolher(situacao) },
            )
        }
    }
}

// Uma opção: rádio à esquerda e o nome. A selecionada ganha borda de 2dp na cor primary; as outras, 1dp em
// outlineVariant (mesmo desenho do cartão de motivo da abertura). Altura mínima de 48dp para o toque.
@Composable
private fun OpcaoDaSituacao(
    situacao: SituacaoDoProjeto,
    selecionada: Boolean,
    habilitado: Boolean,
    aoClicar: () -> Unit,
) {
    val forma = RoundedCornerShape(12.dp)
    val borda = if (selecionada) {
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
            .selectable(selected = selecionada, enabled = habilitado, role = Role.RadioButton, onClick = aoClicar)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // O toque é tratado pela linha inteira; o rádio é só o desenho do estado.
        RadioButton(selected = selecionada, onClick = null, enabled = habilitado)
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(situacao.rotulo()),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

// Mensagem de erro na cor de erro do tema. O liveRegion faz o leitor de tela anunciar a mensagem quando ela
// aparece. No conflito de versão, a ação Atualizar (48dp) pede ao chamador para recarregar o atendimento;
// depois disso o Admin refaz a escolha.
@Composable
private fun AreaDeErro(erro: ErroAoSalvar, aoRecarregar: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = textoDoErro(erro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (erro == ErroAoSalvar.ConflitoDeVersao) {
            // Zero só o padding horizontal do conteúdo, para o texto do botão ficar alinhado com o início
            // do aviso; o vertical continua o do botão e a altura mínima de 48dp garante o toque.
            val paddingPadrao = ButtonDefaults.TextButtonContentPadding
            TextButton(
                onClick = aoRecarregar,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    top = paddingPadrao.calculateTopPadding(),
                    end = 0.dp,
                    bottom = paddingPadrao.calculateBottomPadding(),
                ),
            ) {
                Text(stringResource(R.string.atendimento_edicao_atualizar), fontWeight = FontWeight.Medium)
            }
        }
    }
}

// Texto de cada erro. Na regra de negócio vale o texto que a API devolveu; se vier vazio, a mensagem genérica.
@Composable
private fun textoDoErro(erro: ErroAoSalvar): String = when (erro) {
    ErroAoSalvar.ConflitoDeVersao -> stringResource(R.string.atendimento_edicao_erro_conflito)
    ErroAoSalvar.SemPermissao -> stringResource(R.string.atendimento_edicao_erro_sem_permissao)
    ErroAoSalvar.NaoEncontrado -> stringResource(R.string.atendimento_edicao_erro_nao_encontrado)
    ErroAoSalvar.SemConexao -> stringResource(R.string.atendimento_edicao_erro_sem_conexao)
    is ErroAoSalvar.RegraDeNegocio ->
        erro.mensagem.ifBlank { stringResource(R.string.atendimento_edicao_erro_regra) }
}

// Mesmo padrão dos outros botões principais do módulo: cor primária, versão mais clara quando desabilitado e
// indicador de progresso no lugar do texto enquanto salva.
@Composable
private fun BotaoSalvar(habilitado: Boolean, salvando: Boolean, aoSalvar: () -> Unit) {
    Button(
        onClick = aoSalvar,
        enabled = habilitado,
        shape = RoundedCornerShape(8.dp),
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
        if (salvando) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = stringResource(R.string.atendimento_edicao_salvar),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// Dados fictícios dos previews.
private val dadosDoPreview = DadosDaEdicao(
    id = "1",
    protocolo = "OS-0477",
    motivo = MotivoAtendimento.DIGITAL_FARMING,
    situacaoOriginal = SituacaoDoProjeto.FORA_GARANTIA,
)

@Composable
private fun TelaDePreview(
    dados: DadosDaEdicao = dadosDoPreview,
    escolhida: SituacaoDoProjeto? = dados.situacaoOriginal,
    salvando: Boolean = false,
    erro: ErroAoSalvar? = null,
) {
    TemaDePreview {
        EdicaoDoAtendimentoTela(
            dados = dados,
            escolhida = escolhida,
            salvando = salvando,
            erro = erro,
            aoEscolher = {},
            aoSalvar = {},
            aoRecarregar = {},
            aoVoltar = {},
        )
    }
}

// Situação já gravada e nenhuma mudança: Salvar desabilitado.
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun EdicaoSemMudancaPreview() = TelaDePreview()

// Escolha diferente da gravada: Salvar habilitado.
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun EdicaoAlteradaPreview() = TelaDePreview(escolhida = SituacaoDoProjeto.GARANTIA)

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun EdicaoSalvandoPreview() = TelaDePreview(escolhida = SituacaoDoProjeto.GARANTIA, salvando = true)

// Atendimento sem situação gravada: o seletor abre sem nada marcado.
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun EdicaoSemSituacaoPreview() =
    TelaDePreview(dados = dadosDoPreview.copy(situacaoOriginal = null))

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun EdicaoConflitoDeVersaoPreview() =
    TelaDePreview(escolhida = SituacaoDoProjeto.INSTALACAO, erro = ErroAoSalvar.ConflitoDeVersao)

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun EdicaoRegraDeNegocioPreview() = TelaDePreview(
    escolhida = SituacaoDoProjeto.INSTALACAO,
    erro = ErroAoSalvar.RegraDeNegocio("somente o Netafim Admin altera a situação do projeto"),
)

// Largura de 360dp com fonte ampliada (150%) e o erro mais longo, para conferir que nada corta nem sobrepõe.
@Preview(showBackground = true, widthDp = 360, heightDp = 900, fontScale = 1.5f)
@Composable
private fun EdicaoA360dpComFonteGrandePreview() = TelaDePreview(
    escolhida = SituacaoDoProjeto.INSTALACAO,
    erro = ErroAoSalvar.ConflitoDeVersao,
)
