package com.netafim.posvenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.netafim.posvenda.R
import com.netafim.posvenda.acesso.ui.comum.AtalhoEmCartao
import com.netafim.posvenda.acesso.ui.comum.AvatarDeIniciais
import com.netafim.posvenda.acesso.ui.comum.CartaoDoPrototipo
import com.netafim.posvenda.acesso.ui.comum.PaginaComCabecalho
import com.netafim.posvenda.acesso.ui.comum.TituloDeGrupo
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.estilo

/**
 * Início. Não existe no Figma (os desenhos de início são de cada área, de outras pessoas do grupo);
 * usa a base do protótipo: barra azul com a saudação e, à direita, o avatar que abre o perfil. O
 * corpo traz o perfil e a organização, os atalhos que a pessoa pode usar (só aparecem os
 * permitidos) e um espaço reservado para os atendimentos, que ainda não existem neste app.
 */
@Composable
fun InicioTela(
    nome: String,
    perfil: String?,
    organizacao: String?,
    foto: ImageBitmap?,
    podeGerenciarUsuarios: Boolean,
    podeAprovarCadastros: Boolean,
    aoAbrirPerfil: () -> Unit,
    aoAbrirUsuarios: () -> Unit,
    aoAbrirAprovacao: () -> Unit,
) {
    val descricaoPerfil = stringResource(R.string.inicio_meu_perfil)
    val primeiroNome = nome.trim().substringBefore(' ')
    PaginaComCabecalho(
        titulo = stringResource(R.string.inicio_ola, primeiroNome),
        acaoNoTopo = {
            AvatarDeIniciais(
                nome = nome,
                tamanho = 40,
                foto = foto,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .semantics { contentDescription = descricaoPerfil }
                    .clickable(role = Role.Button, onClick = aoAbrirPerfil),
            )
        },
    ) {
        val temPerfil = !perfil.isNullOrBlank()
        val temOrganizacao = !organizacao.isNullOrBlank()
        val subtitulo = when {
            temPerfil && temOrganizacao -> stringResource(R.string.inicio_perfil_e_organizacao, perfil.orEmpty(), organizacao.orEmpty())
            temPerfil -> perfil
            temOrganizacao -> organizacao
            else -> null
        }
        subtitulo?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = estilo(13))
            Spacer(Modifier.height(esc(24)))
        }

        if (podeGerenciarUsuarios || podeAprovarCadastros) {
            TituloDeGrupo(stringResource(R.string.inicio_atalhos_titulo))
            Spacer(Modifier.height(esc(8)))
            Column(verticalArrangement = Arrangement.spacedBy(esc(12))) {
                if (podeGerenciarUsuarios) {
                    AtalhoEmCartao(
                        icone = Icons.Outlined.Person,
                        titulo = stringResource(R.string.inicio_gestao_usuarios),
                        descricao = stringResource(R.string.inicio_gestao_usuarios_descricao),
                        aoClicar = aoAbrirUsuarios,
                    )
                }
                if (podeAprovarCadastros) {
                    AtalhoEmCartao(
                        icone = Icons.Outlined.CheckCircle,
                        titulo = stringResource(R.string.inicio_aprovar_cadastros),
                        descricao = stringResource(R.string.inicio_aprovar_cadastros_descricao),
                        aoClicar = aoAbrirAprovacao,
                    )
                }
            }
            Spacer(Modifier.height(esc(24)))
        }

        CartaoDoPrototipo {
            Column {
                Text(
                    stringResource(R.string.inicio_atendimentos_titulo),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = estilo(15, FontWeight.SemiBold),
                )
                Spacer(Modifier.height(esc(4)))
                Text(
                    stringResource(R.string.inicio_atendimentos_descricao),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = estilo(12),
                )
            }
        }
    }
}
