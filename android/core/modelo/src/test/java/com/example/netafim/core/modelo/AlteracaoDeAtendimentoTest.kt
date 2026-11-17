package com.example.netafim.core.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AlteracaoDeAtendimentoTest {

    private fun recusa(montar: () -> AlteracaoDeAtendimento): String? =
        assertThrows(IllegalArgumentException::class.java) { montar() }.message

    // ---- Mudanca

    @Test
    fun manterEDiferenteDeDefinirNulo() {
        // Manter quer dizer "não mexer"; Definir(null) quer dizer "limpar o campo".
        assertNotEquals(Mudanca.Manter, Mudanca.Definir<Int>(null))
        assertEquals(Mudanca.Definir(5), Mudanca.Definir(5))
    }

    // ---- campos alterados

    @Test
    fun semNenhumCampoAlteradoERecusada() {
        assertEquals("A alteração precisa mudar pelo menos um campo.", recusa { AlteracaoDeAtendimento() })
    }

    @Test
    fun cadaCampoSozinhoContaComoAlterado() {
        AlteracaoDeAtendimento(status = StatusAtendimento.EM_ATENDIMENTO)
        AlteracaoDeAtendimento(destinadoA = DestinadoA.NETAFIM)
        AlteracaoDeAtendimento(idDistribuidor = 4)
        AlteracaoDeAtendimento(idTecnicoResponsavel = Mudanca.Definir(9))
        AlteracaoDeAtendimento(situacaoProjeto = Mudanca.Definir(SituacaoDoProjeto.GARANTIA))
    }

    @Test
    fun limparOTecnicoOuASituacaoTambemContaComoAlteracao() {
        AlteracaoDeAtendimento(idTecnicoResponsavel = Mudanca.Definir(null))
        AlteracaoDeAtendimento(situacaoProjeto = Mudanca.Definir(null))
    }

    // ---- DESCONHECIDO

    @Test
    fun recusaStatusDesconhecido() {
        assertEquals(
            "O status da alteração não pode ser DESCONHECIDO.",
            recusa { AlteracaoDeAtendimento(status = StatusAtendimento.DESCONHECIDO) },
        )
    }

    @Test
    fun recusaSituacaoDoProjetoDesconhecida() {
        assertEquals(
            "A situação do projeto da alteração não pode ser DESCONHECIDO.",
            recusa {
                AlteracaoDeAtendimento(situacaoProjeto = Mudanca.Definir(SituacaoDoProjeto.DESCONHECIDO))
            },
        )
    }

    // ---- destino (espelha ck_atend_destino do banco)

    @Test
    fun destinarAoDistribuidorExigeOIdDoDistribuidor() {
        assertEquals(
            "Destinar a um distribuidor exige informar o idDistribuidor.",
            recusa { AlteracaoDeAtendimento(destinadoA = DestinadoA.DISTRIBUIDOR) },
        )
        AlteracaoDeAtendimento(destinadoA = DestinadoA.DISTRIBUIDOR, idDistribuidor = 4)
    }

    @Test
    fun destinarAoNetafimNaoPedeDistribuidorEIdDistribuidorSozinhoEAceito() {
        AlteracaoDeAtendimento(destinadoA = DestinadoA.NETAFIM)
        AlteracaoDeAtendimento(idDistribuidor = 4)
    }
}
