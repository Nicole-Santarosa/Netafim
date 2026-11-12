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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.netafim.posvenda.R
import com.netafim.posvenda.acesso.ui.comum.AtalhoEmCartao
import com.netafim.posvenda.acesso.ui.comum.AvatarDeIniciais
import com.netafim.posvenda.acesso.ui.comum.CartaoNoVidro
import com.netafim.posvenda.acesso.ui.comum.PaginaComWallpaper
import com.netafim.posvenda.acesso.ui.comum.TituloDaFolha
import com.netafim.posvenda.acesso.ui.comum.TituloDeGrupo
import com.netafim.posvenda.acesso.ui.comum.esc
import com.netafim.posvenda.acesso.ui.comum.estilo
import com.netafim.posvenda.acesso.ui.comum.margensDaFolha
import com.netafim.posvenda.acesso.ui.comum.textoSuaveNoVidro

/**
 * Início sobre o wallpaper da marca. O avatar de iniciais no canto abre o perfil; a folha traz a
 * saudação, os atalhos que a pessoa pode usar (só aparecem os permitidos) e um espaço reservado
 * para os atendimentos, que ainda não existem neste app.
 */
@Composable
fun InicioTela(
    nome: String,
    perfil: String?,
    organizacao: String?,
    podeGerenciarUsuarios: Boolean,
    podeAprovarCadastros: Boolean,
    aoAbrirPerfil: () -> Unit,
    aoAbrirUsuarios: () -> Unit,
    aoAbrirAprovacao: () -> Unit,
) {
    val descricaoPerfil = stringResource(R.string.inicio_meu_perfil)
    PaginaComWallpaper(
        wallpaper = R.drawable.fundo_inicio,
        descricaoDoWallpaper = stringResource(R.string.inicio_marca),
        alturaDoHero = 0.36f,
        // O logo e a frase da marca ficam à direita da arte; em telas estreitas o corte tira o lado esquerdo.
        alinhamentoDoWallpaper = Alignment.TopEnd,
        acaoNoTopo = {
            AvatarDeIniciais(
                nome = nome,
                tamanho = 44,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .semantics { contentDescription = descricaoPerfil }
                    .clickable(role = Role.Button, onClick = aoAbrirPerfil),
            )
        },
    ) {
        Column(Modifier.margensDaFolha()) {
            val primeiroNome = nome.trim().substringBefore(' ')
            val temPerfil = !perfil.isNullOrBlank()
            val temOrganizacao = !organizacao.isNullOrBlank()
            val subtitulo = when {
                temPerfil && temOrganizacao -> stringResource(R.string.inicio_perfil_e_organizacao, perfil.orEmpty(), organizacao.orEmpty())
                temPerfil -> perfil
                temOrganizacao -> organizacao
                else -> null
            }
            TituloDaFolha(stringResource(R.string.inicio_ola, primeiroNome), subtitulo)
            Spacer(Modifier.height(esc(24)))

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

            CartaoNoVidro {
                Column {
                    Text(
                        stringResource(R.string.inicio_atendimentos_titulo),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = estilo(15, FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(esc(4)))
                    Text(
                        stringResource(R.string.inicio_atendimentos_descricao),
                        color = textoSuaveNoVidro(),
                        style = estilo(12),
                    )
                }
            }
        }
    }
}
