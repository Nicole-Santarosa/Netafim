package com.example.netafim.acesso

import com.example.netafim.acesso.ui.privacidade.CopiaDosDados
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CopiaDosDadosTest {
    @get:Rule
    val temporaria = TemporaryFolder()

    @Test
    fun `grava o texto em UTF-8 na pasta e cria a pasta se preciso`() {
        val pasta = File(temporaria.root, "compartilhar")
        val texto = "{\"nome\": \"José da Conceição\"}"
        val arquivo = CopiaDosDados.gravar(pasta, texto, "copia.json")
        assertTrue(pasta.isDirectory)
        assertEquals(texto, arquivo.readText(Charsets.UTF_8))
    }

    @Test
    fun `cada nova copia apaga as anteriores`() {
        val pasta = File(temporaria.root, "compartilhar")
        val antiga = CopiaDosDados.gravar(pasta, "antiga", "a.json")
        val nova = CopiaDosDados.gravar(pasta, "nova", "b.json")
        assertFalse(antiga.exists())
        assertTrue(nova.exists())
        assertEquals(listOf("b.json"), pasta.listFiles()!!.map { it.name })
    }

    @Test
    fun `limpar apaga tudo e aceita pasta que nao existe`() {
        val pasta = File(temporaria.root, "compartilhar")
        CopiaDosDados.gravar(pasta, "dados", "a.json")
        CopiaDosDados.limpar(pasta)
        assertEquals(0, pasta.listFiles()!!.size)
        CopiaDosDados.limpar(File(temporaria.root, "nao-existe"))
    }

    @Test
    fun `exportacao grande vai inteira para o arquivo`() {
        // Bem acima do limite do binder (cerca de 1 MB por transacao): no arquivo nao ha esse limite.
        val grande = "x".repeat(5_000_000)
        val arquivo = CopiaDosDados.gravar(File(temporaria.root, "compartilhar"), grande, "grande.json")
        assertEquals(5_000_000L, arquivo.length())
        assertEquals(grande, arquivo.readText(Charsets.UTF_8))
    }
}
