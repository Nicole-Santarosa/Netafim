package com.netafim.posvenda.acesso.ui.cadastro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netafim.posvenda.acesso.R
import com.netafim.posvenda.acesso.ui.comum.BotaoCheio
import com.netafim.posvenda.acesso.ui.comum.CampoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.CampoSenhaDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.MensagemErro
import com.netafim.posvenda.acesso.ui.comum.PaginaComCabecalho
import com.netafim.posvenda.acesso.ui.comum.esc

@Composable
fun CadastroRota(viewModel: CadastroViewModel, aoVoltar: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    CadastroTela(
        estado = estado,
        aoMudar = viewModel::aoMudar,
        aoMudarAceite = viewModel::aoMudarAceite,
        aoEnviar = viewModel::enviar,
        aoVoltar = aoVoltar,
    )
}

/**
 * Cadastro conforme o protótipo (Figma, "03 Cadastro — cliente final"): barra azul com o título e os
 * campos na ordem do desenho (Nome completo, Nome da propriedade, Telefone / WhatsApp, E-mail e
 * Distribuidor / revenda), 16 dp entre eles, e o botão "Enviar cadastro" 26 dp abaixo do último.
 *
 * Fora do protótipo, mas exigidos pela API e pela LGPD: senha, confirmação e o aceite do aviso de
 * privacidade (no mesmo estilo, entre o último campo e o botão). Propriedade e distribuidor ainda
 * não são enviados: a API de cadastro não os recebe.
 */
@Composable
fun CadastroTela(
    estado: CadastroEstado,
    aoMudar: (CampoCadastro, String) -> Unit,
    aoMudarAceite: (Boolean) -> Unit,
    aoEnviar: () -> Unit,
    aoVoltar: () -> Unit,
) {
    if (estado.enviado != null) {
        AguardandoValidacaoTela(aoVoltar)
        return
    }
    PaginaComCabecalho(stringResource(R.string.acesso_cadastro_titulo)) {
        Formulario(estado, aoMudar, aoMudarAceite, aoEnviar)
    }
}

@Composable
private fun Formulario(
    estado: CadastroEstado,
    aoMudar: (CampoCadastro, String) -> Unit,
    aoMudarAceite: (Boolean) -> Unit,
    aoEnviar: () -> Unit,
) {
    var senhaVisivel by rememberSaveable { mutableStateOf(false) }
    val bloqueado = estado.carregando
    val descricaoMostrar = stringResource(R.string.acesso_mostrar_senha)
    val descricaoOcultar = stringResource(R.string.acesso_ocultar_senha)

    estado.erroGeral?.let {
        MensagemErro(it)
        Spacer(Modifier.height(esc(16)))
    }
    CampoDoPrototipo(
        valor = estado.nome,
        aoMudar = { aoMudar(CampoCadastro.NOME, it) },
        rotulo = stringResource(R.string.acesso_nome),
        erro = estado.erros[CampoCadastro.NOME],
        habilitado = !bloqueado,
    )
    Spacer(Modifier.height(esc(16)))
    CampoDoPrototipo(
        valor = estado.propriedade,
        aoMudar = { aoMudar(CampoCadastro.PROPRIEDADE, it) },
        rotulo = stringResource(R.string.acesso_propriedade),
        habilitado = !bloqueado,
    )
    Spacer(Modifier.height(esc(16)))
    CampoDoPrototipo(
        valor = estado.telefone,
        aoMudar = { aoMudar(CampoCadastro.TELEFONE, it) },
        rotulo = stringResource(R.string.acesso_telefone),
        erro = estado.erros[CampoCadastro.TELEFONE],
        tipoTeclado = KeyboardType.Phone,
        habilitado = !bloqueado,
    )
    Spacer(Modifier.height(esc(16)))
    CampoDoPrototipo(
        valor = estado.email,
        aoMudar = { aoMudar(CampoCadastro.EMAIL, it) },
        rotulo = stringResource(R.string.acesso_email),
        erro = estado.erros[CampoCadastro.EMAIL],
        tipoTeclado = KeyboardType.Email,
        habilitado = !bloqueado,
    )
    Spacer(Modifier.height(esc(16)))
    CampoDoPrototipo(
        valor = estado.distribuidor,
        aoMudar = { aoMudar(CampoCadastro.DISTRIBUIDOR, it) },
        rotulo = stringResource(R.string.acesso_distribuidor),
        habilitado = !bloqueado,
    )
    Spacer(Modifier.height(esc(16)))
    CampoSenhaDoPrototipo(
        valor = estado.senha,
        aoMudar = { aoMudar(CampoCadastro.SENHA, it) },
        rotulo = stringResource(R.string.acesso_senha),
        visivel = senhaVisivel,
        aoAlternarVisibilidade = { senhaVisivel = !senhaVisivel },
        descricaoMostrar = descricaoMostrar,
        descricaoOcultar = descricaoOcultar,
        erro = estado.erros[CampoCadastro.SENHA],
        dica = stringResource(R.string.acesso_dica_senha),
        acaoIme = ImeAction.Next,
        habilitado = !bloqueado,
    )
    Spacer(Modifier.height(esc(16)))
    CampoSenhaDoPrototipo(
        valor = estado.confirmacao,
        aoMudar = { aoMudar(CampoCadastro.CONFIRMACAO, it) },
        rotulo = stringResource(R.string.acesso_confirmar_senha),
        visivel = senhaVisivel,
        aoAlternarVisibilidade = { senhaVisivel = !senhaVisivel },
        descricaoMostrar = descricaoMostrar,
        descricaoOcultar = descricaoOcultar,
        erro = estado.erros[CampoCadastro.CONFIRMACAO],
        habilitado = !bloqueado,
    )
    Spacer(Modifier.height(esc(12)))
    AceiteTermo(
        aceito = estado.aceiteTermo,
        versao = estado.versaoTermo,
        erro = estado.erros[CampoCadastro.TERMO],
        aoMudar = aoMudarAceite,
        habilitado = !bloqueado,
    )
    Spacer(Modifier.height(esc(26)))
    BotaoCheio(
        texto = stringResource(R.string.acesso_enviar_cadastro),
        aoClicar = aoEnviar,
        carregando = estado.carregando,
    )
}

@Composable
internal fun AceiteTermo(
    aceito: Boolean,
    versao: String?,
    erro: String?,
    aoMudar: (Boolean) -> Unit,
    habilitado: Boolean,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = aceito,
                    enabled = habilitado,
                    role = Role.Checkbox,
                    onValueChange = aoMudar,
                ),
        ) {
            Checkbox(checked = aceito, onCheckedChange = null, enabled = habilitado)
            Text(
                text = if (versao != null) {
                    stringResource(R.string.acesso_aceite_termo, versao)
                } else {
                    stringResource(R.string.acesso_aceite_termo_carregando)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        erro?.let { MensagemErro(it) }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 828)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun CadastroTelaPreview() {
    MaterialTheme {
        CadastroTela(
            estado = CadastroEstado(
                nome = "João Produtor",
                versaoTermo = "1.0",
                erros = mapOf(CampoCadastro.EMAIL to "E-mail inválido."),
            ),
            aoMudar = { _, _ -> }, aoMudarAceite = {}, aoEnviar = {}, aoVoltar = {},
        )
    }
}
