package com.example.netafim.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first

class CursorGlobalDataStore(
    private val dataStore: DataStore<Preferences>
) {

    /**
     * Recupera o último cursor global persistido para o usuário.
     * Caso ainda não exista um cursor, retorna 0.
     */
    suspend fun obterCursorGlobal(usuarioDonoId: Int): Long {
        validarUsuario(usuarioDonoId)

        val chave = criarChaveCursor(usuarioDonoId)

        return dataStore.data.first()[chave] ?: 0L
    }

    /**
     * Atualiza o cursor sem permitir que ele retroceda.
     * Deve ser chamado somente depois que a página inteira
     * tiver sido aplicada e persistida com sucesso no Room.
     */
    suspend fun atualizarCursorGlobal(
        usuarioDonoId: Int,
        novoCursor: Long
    ) {
        validarUsuario(usuarioDonoId)
        require(novoCursor >= 0) {
            "O cursor não pode ser negativo."
        }

        val chave = criarChaveCursor(usuarioDonoId)

        dataStore.edit { preferencias ->
            val cursorAtual = preferencias[chave] ?: 0L

            preferencias[chave] = maxOf(cursorAtual, novoCursor)
        }
    }

    /**
     * Reinicia o cursor do usuário para 0.
     * Utilizado em procedimentos de sincronização completa.
     */
    suspend fun reiniciarCursorGlobal(usuarioDonoId: Int) {
        validarUsuario(usuarioDonoId)

        val chave = criarChaveCursor(usuarioDonoId)

        dataStore.edit { preferencias ->
            preferencias[chave] = 0L
        }
    }

    private fun criarChaveCursor(usuarioDonoId: Int) =
        longPreferencesKey("cursor_global_usuario_$usuarioDonoId")

    private fun validarUsuario(usuarioDonoId: Int) {
        require(usuarioDonoId > 0) {
            "O ID do usuário deve ser maior que zero."
        }
    }
}
