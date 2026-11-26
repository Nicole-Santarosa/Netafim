package com.example.netafim.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// Testes para o armazenamento do cursor global
class CursorGlobalDataStoreTest {

    @get:Rule
    val pastaTemporaria = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var cursorDataStore: CursorGlobalDataStore

    private lateinit var escopo: CoroutineScope // Escopo de corotina para gerenciar as operações assíncronas

    @Before
    // Configura os componentes necessários para os testes
    fun configurar() {
        escopo = CoroutineScope(
            SupervisorJob() + Dispatchers.IO // Usa um SupervisorJob para permitir que falhas em uma corotina não cancelem as outras
        )

        // Cria um DataStore de preferências temporário para os testes
        dataStore = PreferenceDataStoreFactory.create(
            scope = escopo,
            produceFile = {
                File(
                    pastaTemporaria.root,
                    "cursor_teste.preferences_pb"
                )
            }
        )

        cursorDataStore = CursorGlobalDataStore(dataStore) // Inicializa o CursorGlobalDataStore com o DataStore temporário
    }

    @After
    // Finaliza o escopo de corotina após os testes
    fun finalizar() {
        escopo.cancel()
    }

    @Test
    // Verifica se o cursor inexistente retorna zero
    fun cursorInexistente_deveRetornarZero() = runBlocking {
        val cursor = cursorDataStore.obterCursorGlobal(1) // Obtém o cursor global para o usuário com ID 1

        assertEquals(0L, cursor) // Verifica se o valor retornado é zero, indicando que o cursor não existe
    }

    @Test
    // Verifica se a atualização do cursor persiste o novo valor
    fun atualizarCursor_devePersistirNovoValor() = runBlocking {
        cursorDataStore.atualizarCursorGlobal(1, 150L) // Atualiza o cursor global para o usuário com ID 1 para o valor 150

        val cursor = cursorDataStore.obterCursorGlobal(1) // Obtém o cursor global atualizado para o usuário com ID 1

        assertEquals(150L, cursor) // Verifica se o valor retornado é 150, confirmando que a atualização foi bem-sucedida
    }

    @Test
    // Verifica se a atualização do cursor não permite retrocesso, ou seja, não permite que o valor do cursor seja reduzido
    fun atualizarCursor_naoDevePermitirRetrocesso() = runBlocking {
        cursorDataStore.atualizarCursorGlobal(1, 200L) // Atualiza o cursor global para o usuário com ID 1 para o valor 200
        cursorDataStore.atualizarCursorGlobal(1, 100L)

        val cursor = cursorDataStore.obterCursorGlobal(1) // Obtém o cursor global atualizado para o usuário com ID 1

        assertEquals(200L, cursor) // Verifica se o valor retornado é 200, indicando que o cursor não permite retroceder
    }

    @Test
    // Verifica se usuários diferentes possuem cursores independentes, ou seja, a atualização do cursor de um usuário não afeta o cursor de outro usuário
    fun usuariosDiferentes_devemPossuirCursoresIndependentes() = runBlocking {
        cursorDataStore.atualizarCursorGlobal(1, 100L) // Atualiza o cursor global para o usuário com ID 1 para o valor 100
        cursorDataStore.atualizarCursorGlobal(2, 300L)

        // Verifica se os cursores dos usuários são independentes, ou seja, a atualização do cursor de um usuário não afeta o cursor de outro usuário
        val cursorUsuario1 =
            cursorDataStore.obterCursorGlobal(1)

        val cursorUsuario2 =
            cursorDataStore.obterCursorGlobal(2)

        assertEquals(100L, cursorUsuario1) // Verifica se o valor do cursor do usuário 1 é 100
        assertEquals(300L, cursorUsuario2)
    }

    @Test
    // Verifica se a reinicialização do cursor retorna o valor zero, ou seja, após reiniciar o cursor de um usuário, o valor do cursor deve ser 0
    fun reiniciarCursor_deveRetornarZero() = runBlocking {
        cursorDataStore.atualizarCursorGlobal(1, 500L) // Atualiza o cursor global para o usuário com ID 1 para o valor 500

        cursorDataStore.reiniciarCursorGlobal(1) // Reinicia o cursor global para o usuário com ID 1, definindo o valor do cursor como 0

        val cursor = cursorDataStore.obterCursorGlobal(1) //    Obtém o cursor global atualizado para o usuário com ID 1 após a reinicialização

        assertEquals(0L, cursor) // Verifica se o valor retornado é 0, confirmando que a reinicialização do cursor foi bem-sucedida
    }

    @Test
    // Verifica se a tentativa de obter o cursor global com um ID de usuário inválido lança uma exceção IllegalArgumentException, garantindo que o método trate corretamente entradas inválidas
    fun usuarioInvalido_deveLancarExcecao() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                cursorDataStore.obterCursorGlobal(0)
            }
        }
    }

    @Test
    // Verifica se o cursor negativo não é permitido e lança uma exceção IllegalArgumentException, garantindo que o método trata corretamente entradas inválidas
    fun cursorNegativo_deveLancarExcecao() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                cursorDataStore.atualizarCursorGlobal(1, -10L)
            }
        }
    }
}
