package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.Mudanca
import com.example.netafim.core.modelo.ResultadoDaAcao
import com.example.netafim.core.modelo.SituacaoDoProjeto
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
    fun deCodigoDesconhecidoDevolveDesconhecido() {
        assertEquals(SituacaoDoProjeto.DESCONHECIDO, SituacaoDoProjeto.deCodigo("vencida"))
        assertEquals(SituacaoDoProjeto.DESCONHECIDO, SituacaoDoProjeto.deCodigo(""))
        assertEquals(SituacaoDoProjeto.DESCONHECIDO, SituacaoDoProjeto.deCodigo("GARANTIA"))
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
        // As escolhas reais: o seletor nunca oferece DESCONHECIDO.
        SituacaoDoProjeto.entries.filter { it != SituacaoDoProjeto.DESCONHECIDO }.forEach {
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

    @Test
    fun escolhaDesconhecidaNuncaLibera() {
        // DESCONHECIDO existe só para o que veio do servidor: o botão não pode liberar mesmo com situação
        // gravada diferente ou ausente.
        assertFalse(podeSalvar(SituacaoDoProjeto.GARANTIA, SituacaoDoProjeto.DESCONHECIDO, salvando = false))
        assertFalse(podeSalvar(original = null, escolhida = SituacaoDoProjeto.DESCONHECIDO, salvando = false))
    }

    // ---- alteracaoDaEscolha

    @Test
    fun cadaEscolhaRealViraAlteracaoSoDaSituacao() {
        SituacaoDoProjeto.entries.filter { it != SituacaoDoProjeto.DESCONHECIDO }.forEach { situacao ->
            val alteracao = alteracaoDaEscolha(situacao)
            assertEquals(situacao.name, Mudanca.Definir(situacao), alteracao?.situacaoProjeto)
            // Os outros campos ficam como estão.
            assertNull(alteracao?.status)
            assertNull(alteracao?.destinadoA)
            assertNull(alteracao?.idDistribuidor)
            assertEquals(Mudanca.Manter, alteracao?.idTecnicoResponsavel)
        }
    }

    @Test
    fun semEscolhaOuComEscolhaDesconhecidaNaoHaAlteracao() {
        // Os mesmos casos em que podeSalvar não libera o botão; nada é montado para o core recusar.
        assertNull(alteracaoDaEscolha(null))
        assertNull(alteracaoDaEscolha(SituacaoDoProjeto.DESCONHECIDO))
    }

    @Test
    fun podeSalvarELiberaSoQuandoHaAlteracaoParaEnviar() {
        SituacaoDoProjeto.entries.forEach { escolhida ->
            assertEquals(
                escolhida.name,
                alteracaoDaEscolha(escolhida) != null,
                podeSalvar(original = null, escolhida = escolhida, salvando = false),
            )
        }
    }

    // ---- erroDoResultado

    @Test
    fun sucessoNaoEErro() {
        assertNull(erroDoResultado(ResultadoDaAcao.Sucesso))
    }

    @Test
    fun resultadosComValorDeMesmoNomeViramEsseErro() {
        assertEquals(ErroAoSalvar.ConflitoDeVersao, erroDoResultado(ResultadoDaAcao.ConflitoDeVersao))
        assertEquals(ErroAoSalvar.SemPermissao, erroDoResultado(ResultadoDaAcao.SemPermissao))
        assertEquals(ErroAoSalvar.NaoEncontrado, erroDoResultado(ResultadoDaAcao.NaoEncontrado))
    }

    @Test
    fun falhaDeRedeViraSemConexao() {
        assertEquals(ErroAoSalvar.SemConexao, erroDoResultado(ResultadoDaAcao.FalhaDeRede))
    }

    @Test
    fun regraRecusadaLevaOTextoDoServidor() {
        val mensagem = "somente o Netafim Admin altera a situação do projeto"
        assertEquals(ErroAoSalvar.RegraDeNegocio(mensagem), erroDoResultado(ResultadoDaAcao.RegraRecusada(mensagem)))
    }

    @Test
    fun dadosInvalidosViramRegraDeNegocioGenerica() {
        // Sem valor próprio na tela de edição: mensagem vazia faz a tela mostrar o texto genérico.
        val resultado = ResultadoDaAcao.DadosInvalidos(mapOf("situacao_projeto" to "valor inválido"))
        assertEquals(ErroAoSalvar.RegraDeNegocio(""), erroDoResultado(resultado))
    }

    @Test
    fun todoResultadoDaAcaoTemMapeamento() {
        // Um caso por subtipo: se o core ganhar um resultado novo, o when exaustivo de erroDoResultado
        // deixa de compilar; este teste confere que os sete atuais estão cobertos e só o sucesso vira nulo.
        val todos = listOf(
            ResultadoDaAcao.Sucesso,
            ResultadoDaAcao.ConflitoDeVersao,
            ResultadoDaAcao.RegraRecusada("x"),
            ResultadoDaAcao.DadosInvalidos(emptyMap()),
            ResultadoDaAcao.SemPermissao,
            ResultadoDaAcao.NaoEncontrado,
            ResultadoDaAcao.FalhaDeRede,
        )
        assertEquals(listOf(ResultadoDaAcao.Sucesso), todos.filter { erroDoResultado(it) == null })
    }
}
