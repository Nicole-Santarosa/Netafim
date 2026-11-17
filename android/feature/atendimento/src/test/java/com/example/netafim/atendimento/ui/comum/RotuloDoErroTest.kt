package com.example.netafim.atendimento.ui.comum

import com.example.netafim.atendimento.R
import com.example.netafim.atendimento.dominio.ErroAoSalvar
import org.junit.Assert.assertEquals
import org.junit.Test

// Um caso por valor de ErroAoSalvar. Se o domínio ganhar um valor novo, o when de rotulo() deixa de compilar
// antes deste teste rodar; o teste guarda qual texto vai com qual erro.
class RotuloDoErroTest {

    @Test
    fun conflitoDeVersaoUsaOTextoDeConflito() {
        assertEquals(R.string.atendimento_erro_conflito, ErroAoSalvar.ConflitoDeVersao.rotulo())
    }

    @Test
    fun semPermissaoUsaOTextoDePermissao() {
        assertEquals(R.string.atendimento_erro_sem_permissao, ErroAoSalvar.SemPermissao.rotulo())
    }

    @Test
    fun naoEncontradoUsaOTextoDeNaoEncontrado() {
        assertEquals(R.string.atendimento_erro_nao_encontrado, ErroAoSalvar.NaoEncontrado.rotulo())
    }

    @Test
    fun semConexaoUsaOTextoDeConexao() {
        assertEquals(R.string.atendimento_erro_sem_conexao, ErroAoSalvar.SemConexao.rotulo())
    }

    // Com ou sem mensagem da API o id é o do texto genérico: trocar pela mensagem é decisão da tela.
    @Test
    fun regraDeNegocioUsaOTextoGenericoDeRegra() {
        assertEquals(R.string.atendimento_erro_regra, ErroAoSalvar.RegraDeNegocio("").rotulo())
        assertEquals(R.string.atendimento_erro_regra, ErroAoSalvar.RegraDeNegocio("só o técnico inicia").rotulo())
    }
}
