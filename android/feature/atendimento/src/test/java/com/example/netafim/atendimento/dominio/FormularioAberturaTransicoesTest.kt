package com.example.netafim.atendimento.dominio

import com.example.netafim.core.modelo.AreaDoSetor
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.TipoVenda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormularioAberturaTransicoesTest {

    @Test
    fun digitalFarmingSemTipoContinuaSemTipo() {
        // Nenhum valor é pré-selecionado: entrar no Digital Farming não define mais Projeto.
        val f = FormularioAbertura().comMotivo(MotivoAtendimento.DIGITAL_FARMING)
        assertEquals(MotivoAtendimento.DIGITAL_FARMING, f.motivo)
        assertNull(f.tipoVenda)
    }

    @Test
    fun digitalFarmingComVendaRegularJaEscolhidaMantem() {
        val f = FormularioAbertura(tipoVenda = TipoVenda.VENDA_REGULAR).comMotivo(MotivoAtendimento.DIGITAL_FARMING)
        assertEquals(TipoVenda.VENDA_REGULAR, f.tipoVenda)
    }

    @Test
    fun trocarDeMotivoMantemOTipoDeVendaEscolhido() {
        // O tipo de venda vale para todos os motivos: sair do Digital Farming não o zera mais.
        val f = FormularioAbertura()
            .comMotivo(MotivoAtendimento.DIGITAL_FARMING)
            .copy(tipoVenda = TipoVenda.PROJETO)
            .comMotivo(MotivoAtendimento.DUVIDAS)
        assertEquals(MotivoAtendimento.DUVIDAS, f.motivo)
        assertEquals(TipoVenda.PROJETO, f.tipoVenda)
    }

    @Test
    fun outroMotivoSemTipoContinuaSemTipo() {
        assertNull(FormularioAbertura().comMotivo(MotivoAtendimento.POS_VENDA).tipoVenda)
    }

    // ---- limparParaOrigem: troca de origem não deixa estado escondido

    // Formulário com os campos de todas as origens preenchidos, como ficaria depois de passar por todas.
    private val cheio = FormularioAbertura(
        motivo = MotivoAtendimento.POS_VENDA,
        culturas = setOf(1),
        cidade = "Arealva",
        nomeFazenda = "Fazenda Boa Vista",
        contato = "14999990000",
        idDistribuidor = 3,
        tipoVenda = TipoVenda.VENDA_REGULAR,
        nomeProdutor = "José da Silva",
        areaSetor = AreaDoSetor.ENGENHARIA,
    )

    @Test
    fun deClienteFinalParaDistribuidorLimpaDistribuidorFazendaEArea() {
        val f = cheio.limparParaOrigem(OrigemAtendimento.DISTRIBUIDOR)
        assertNull(f.idDistribuidor)
        assertEquals("", f.nomeFazenda)
        assertNull(f.areaSetor)
        assertEquals("José da Silva", f.nomeProdutor)
    }

    @Test
    fun deClienteFinalParaNetafimLimpaDistribuidorEFazendaMasMantemProdutorEArea() {
        val f = cheio.limparParaOrigem(OrigemAtendimento.NETAFIM)
        assertNull(f.idDistribuidor)
        assertEquals("", f.nomeFazenda)
        assertEquals("José da Silva", f.nomeProdutor)
        assertEquals(AreaDoSetor.ENGENHARIA, f.areaSetor)
    }

    @Test
    fun paraClienteFinalMantemDistribuidorEFazendaMasLimpaProdutorEArea() {
        val f = cheio.limparParaOrigem(OrigemAtendimento.CLIENTE_FINAL)
        assertEquals(3, f.idDistribuidor)
        assertEquals("Fazenda Boa Vista", f.nomeFazenda)
        assertEquals("", f.nomeProdutor)
        assertNull(f.areaSetor)
    }

    @Test
    fun semOrigemOuOrigemDesconhecidaLimpaTodosOsCamposDeOrigem() {
        for (origem in listOf(null, OrigemAtendimento.DESCONHECIDO)) {
            val f = cheio.limparParaOrigem(origem)
            assertNull(f.idDistribuidor)
            assertEquals("", f.nomeFazenda)
            assertEquals("", f.nomeProdutor)
            assertNull(f.areaSetor)
        }
    }

    @Test
    fun limparParaOrigemNaoMexeNosCamposComuns() {
        for (origem in OrigemAtendimento.entries) {
            val f = cheio.limparParaOrigem(origem)
            assertEquals(cheio.motivo, f.motivo)
            assertEquals(cheio.culturas, f.culturas)
            assertEquals(cheio.cidade, f.cidade)
            assertEquals(cheio.contato, f.contato)
            assertEquals(cheio.tipoVenda, f.tipoVenda)
        }
    }

    @Test
    fun trocaDeMotivoNaoMexeNosOutrosCampos() {
        val antes = FormularioAbertura(
            culturas = setOf(1),
            cidade = "X",
            nomeFazenda = "Y",
            contato = "1",
            idDistribuidor = 3,
        )
        val depois = antes.comMotivo(MotivoAtendimento.TREINAMENTO)
        assertEquals(setOf(1), depois.culturas)
        assertEquals("X", depois.cidade)
        assertEquals("Y", depois.nomeFazenda)
        assertEquals("1", depois.contato)
        assertEquals(3, depois.idDistribuidor)
    }

    @Test
    fun culturaAlternadaColocaETira() {
        val vazio = FormularioAbertura()
        val com = vazio.comCulturaAlternada(5)
        assertEquals(setOf(5), com.culturas)
        assertEquals(setOf(5, 7), com.comCulturaAlternada(7).culturas)
        assertEquals(emptySet<Int>(), com.comCulturaAlternada(5).culturas)
    }
}
