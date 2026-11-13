package com.netafim.posvenda.atendimento.ui.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

// Campo de busca de 48dp: lupa à esquerda e placeholder. Sem botão de funil: o filtro fica nos chips, e
// também não há botão de limpar. Usado pela fila do técnico, pela lista da região do distribuidor e
// pela do Admin, cada uma com o seu texto de placeholder.
//
// Leitor de tela: eu não coloco contentDescription neste campo. O BasicTextField já junta (merge) o que
// está dentro do decorationBox na semântica dele, então com o campo vazio o placeholder é lido como o
// rótulo, e assim que a pessoa digita o placeholder sai da árvore e o leitor passa a ler o texto digitado.
// Um contentDescription fixo faria o contrário: o leitor falaria sempre o rótulo no lugar do que foi
// digitado e a navegação por caractere andaria pelo rótulo, não pelo texto.
@Composable
fun CampoDeBusca(
    busca: String,
    aoMudar: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val forma = RoundedCornerShape(12.dp)
    BasicTextField(
        value = busca,
        onValueChange = aoMudar,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { campo ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(forma)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, forma)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (busca.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    campo()
                }
            }
        },
    )
}
