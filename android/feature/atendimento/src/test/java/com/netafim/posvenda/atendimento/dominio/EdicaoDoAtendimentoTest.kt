package com.netafim.posvenda.atendimento.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdicaoDoAtendimentoTest {

    // ---- SituacaoDoProjeto.deCodigo

    @Test
    fun deCodigoDevolveCadaSituacaoDoContrato() {
        assertEquals(SituacaoDoProjeto.GARANTIA, SituacaoDoProjeto.deCodigo("garantia"))
        assertEquals(SituacaoDoProjeto.FORA_GARANTIA, SituacaoDoProjeto.deCodigo("fora_garantia"))
        assertEquals(SituacaoDoProjeto.INSTALACAO, SituacaoDoProjeto.deCodigo("instalacao"))
    }

    @Test
    fun deCodigoNuloDevolveNulo() {
        assertNull(SituacaoDoProjeto.deCodigo(null))
    }

    @Test
    fun deCodigoDesconhecidoDevolveNulo() {
        assertNull(SituacaoDoProjeto.deCodigo("vencida"))
        assertNull(SituacaoDoProjeto.deCodigo(""))
        assertNull(SituacaoDoProjeto.deCodigo("GARANTIA"))
    }

    // ---- podeSalvar

    @Test
    fun escolhaIgualAOriginalNaoLibera() {
        SituacaoDoProjeto.entries.forEach {
            assertFalse(it.name, podeSalvar(original = it, escolhida = it, salvando = false))
        }
    }

    @Test
    fun escolhaDiferenteDaOriginalLibera() {
        assertTrue(podeSalvar(SituacaoDoProjeto.GARANTIA, SituacaoDoProjeto.FORA_GARANTIA, salvando = false))
        assertTrue(podeSalvar(SituacaoDoProjeto.INSTALACAO, SituacaoDoProjeto.GARANTIA, salvando = false))
    }

    @Test
    fun semSituacaoGravadaQualquerEscolhaLibera() {
        SituacaoDoProjeto.entries.forEach {
            assertTrue(it.name, podeSalvar(original = null, escolhida = it, salvando = false))
        }
    }

    @Test
    fun semEscolhaNaoLibera() {
        assertFalse(podeSalvar(original = null, escolhida = null, salvando = false))
        assertFalse(podeSalvar(original = SituacaoDoProjeto.GARANTIA, escolhida = null, salvando = false))
    }

    @Test
    fun salvandoNaoLiberaMesmoComEscolhaDiferente() {
        assertFalse(podeSalvar(SituacaoDoProjeto.GARANTIA, SituacaoDoProjeto.FORA_GARANTIA, salvando = true))
        assertFalse(podeSalvar(original = null, escolhida = SituacaoDoProjeto.INSTALACAO, salvando = true))
    }

    // ---- erroAoSalvar

    @Test
    fun semRespostaViraSemConexao() {
        assertEquals(ErroAoSalvar.SemConexao, erroAoSalvar(null, null))
    }

    @Test
    fun cadaStatusDaApiViraSeuErro() {
        assertEquals(ErroAoSalvar.ConflitoDeVersao, erroAoSalvar(409, "texto qualquer"))
        assertEquals(ErroAoSalvar.SemPermissao, erroAoSalvar(403, null))
        assertEquals(ErroAoSalvar.NaoEncontrado, erroAoSalvar(404, null))
    }

    @Test
    fun regraDeNegocioLevaOTextoDaApi() {
        val mensagem = "somente o Netafim Admin altera a situação do projeto"
        assertEquals(ErroAoSalvar.RegraDeNegocio(mensagem), erroAoSalvar(422, mensagem))
    }

    @Test
    fun regraDeNegocioSemTextoFicaComMensagemVazia() {
        assertEquals(ErroAoSalvar.RegraDeNegocio(""), erroAoSalvar(422, null))
    }

    @Test
    fun statusQueAEdicaoNaoTrataDevolveNulo() {
        assertNull(erroAoSalvar(400, "x"))
        assertNull(erroAoSalvar(401, null))
        assertNull(erroAoSalvar(500, null))
    }
}
