package com.example.netafim.core.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnumsDoDominioTest {

    @Test
    fun codigoNaoReconhecidoViraDesconhecidoNosCincoEnums() {
        // Vazio, caixa diferente e um código inventado: nenhum deles pode derrubar o registro.
        listOf("", "ABERTO", "pausado", " ").forEach { codigo ->
            assertEquals(codigo, StatusAtendimento.DESCONHECIDO, StatusAtendimento.deCodigo(codigo))
            assertEquals(codigo, MotivoAtendimento.DESCONHECIDO, MotivoAtendimento.deCodigo(codigo))
            assertEquals(codigo, TipoVenda.DESCONHECIDO, TipoVenda.deCodigo(codigo))
            assertEquals(codigo, OrigemAtendimento.DESCONHECIDO, OrigemAtendimento.deCodigo(codigo))
            assertEquals(codigo, SituacaoDoProjeto.DESCONHECIDO, SituacaoDoProjeto.deCodigo(codigo))
        }
    }

    @Test
    fun codigosReaisContinuamSendoReconhecidos() {
        StatusAtendimento.entries.filter { it != StatusAtendimento.DESCONHECIDO }
            .forEach { assertEquals(it, StatusAtendimento.deCodigo(it.codigo)) }
        MotivoAtendimento.entries.filter { it != MotivoAtendimento.DESCONHECIDO }
            .forEach { assertEquals(it, MotivoAtendimento.deCodigo(it.codigo)) }
        TipoVenda.entries.filter { it != TipoVenda.DESCONHECIDO }
            .forEach { assertEquals(it, TipoVenda.deCodigo(it.codigo)) }
        OrigemAtendimento.entries.filter { it != OrigemAtendimento.DESCONHECIDO }
            .forEach { assertEquals(it, OrigemAtendimento.deCodigo(it.codigo)) }
        SituacaoDoProjeto.entries.filter { it != SituacaoDoProjeto.DESCONHECIDO }
            .forEach { assertEquals(it, SituacaoDoProjeto.deCodigo(it.codigo)) }
    }

    @Test
    fun cadaEnumTemExatamenteUmDesconhecido() {
        // Os cinco enums de negócio ganham o valor; contar evita que um deles fique sem.
        assertEquals(1, StatusAtendimento.entries.count { it.name == "DESCONHECIDO" })
        assertEquals(1, MotivoAtendimento.entries.count { it.name == "DESCONHECIDO" })
        assertEquals(1, TipoVenda.entries.count { it.name == "DESCONHECIDO" })
        assertEquals(1, OrigemAtendimento.entries.count { it.name == "DESCONHECIDO" })
        assertEquals(1, SituacaoDoProjeto.entries.count { it.name == "DESCONHECIDO" })
    }

    @Test
    fun statusDesconhecidoNaoEFinal() {
        assertFalse(StatusAtendimento.DESCONHECIDO.ehFinal)
    }

    @Test
    fun motivosDoClienteNaoOferecemDesconhecido() {
        assertFalse(MotivoAtendimento.DESCONHECIDO in MotivoAtendimento.motivosDoCliente)
    }

    @Test
    fun destinadoAReconheceOsDoisDestinosDoBanco() {
        assertEquals(DestinadoA.NETAFIM, DestinadoA.deCodigo("netafim"))
        assertEquals(DestinadoA.DISTRIBUIDOR, DestinadoA.deCodigo("distribuidor"))
        assertEquals("netafim", DestinadoA.NETAFIM.codigo)
        assertEquals("distribuidor", DestinadoA.DISTRIBUIDOR.codigo)
    }

    @Test
    fun destinadoADevolveNuloParaAusenteOuNaoReconhecido() {
        // No banco o destino pode ser nulo, então aqui não existe DESCONHECIDO.
        assertNull(DestinadoA.deCodigo(null))
        assertNull(DestinadoA.deCodigo(""))
        assertNull(DestinadoA.deCodigo("regiao"))
        assertTrue(DestinadoA.entries.none { it.name == "DESCONHECIDO" })
    }
}
