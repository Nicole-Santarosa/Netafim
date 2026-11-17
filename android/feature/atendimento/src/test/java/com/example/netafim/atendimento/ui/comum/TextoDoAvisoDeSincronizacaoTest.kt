package com.example.netafim.atendimento.ui.comum

import com.example.netafim.atendimento.R
import com.example.netafim.core.modelo.SituacaoDeSincronizacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Um caso por valor de SituacaoDeSincronizacao. Uma situação nova no core quebra a compilação do when de
// textoDoAviso() antes deste teste rodar; o teste guarda qual texto vai com qual situação.
class TextoDoAvisoDeSincronizacaoTest {

    // O caso normal não mostra aviso.
    @Test
    fun sincronizadoNaoTemAviso() {
        assertNull(SituacaoDeSincronizacao.SINCRONIZADO.textoDoAviso())
    }

    @Test
    fun pendenteDeEnvioUsaOTextoDeAguardandoEnvio() {
        assertEquals(
            R.string.atendimento_sincronizacao_aviso_pendente,
            SituacaoDeSincronizacao.PENDENTE_DE_ENVIO.textoDoAviso(),
        )
    }

    @Test
    fun conflitoUsaOTextoDeConflito() {
        assertEquals(R.string.atendimento_sincronizacao_aviso_conflito, SituacaoDeSincronizacao.CONFLITO.textoDoAviso())
    }

    @Test
    fun erroUsaOTextoDeErroDeEnvio() {
        assertEquals(R.string.atendimento_sincronizacao_aviso_erro, SituacaoDeSincronizacao.ERRO.textoDoAviso())
    }
}
