package com.netafim.posvenda.acesso.dados

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.dataStoreSessao: DataStore<Preferences> by preferencesDataStore("sessao_acesso")

/**
 * Guarda a sessão em DataStore, com os tokens cifrados (AES-256-GCM) por uma chave
 * que nunca sai do Android Keystore. Mesmo com acesso aos arquivos do app, os tokens
 * não podem ser lidos fora deste aparelho.
 */
class ArmazenamentoSessaoSeguro(contexto: Context) : ArmazenamentoSessao {

    private val dataStore = contexto.applicationContext.dataStoreSessao

    override suspend fun ler(): SessaoGuardada? {
        val prefs = dataStore.data.first()
        val access = prefs[ACCESS]?.let(::decifrar) ?: return null
        val refresh = prefs[REFRESH]?.let(::decifrar) ?: return null
        val usuario = prefs[USUARIO]?.let(::decifrar) ?: return null
        return SessaoGuardada(access, refresh, usuario)
    }

    override suspend fun salvar(sessao: SessaoGuardada) {
        dataStore.edit {
            it[ACCESS] = cifrar(sessao.accessToken)
            it[REFRESH] = cifrar(sessao.refreshToken)
            it[USUARIO] = cifrar(sessao.usuarioJson)
        }
    }

    override suspend fun limpar() {
        dataStore.edit {
            it.remove(ACCESS)
            it.remove(REFRESH)
            it.remove(USUARIO)
        }
    }

    override suspend fun idDispositivo(): String =
        dataStore.data.first()[ID_DISPOSITIVO] ?: novoIdDispositivo()

    override suspend fun novoIdDispositivo(): String {
        val novo = UUID.randomUUID().toString()
        dataStore.edit { it[ID_DISPOSITIVO] = novo }
        return novo
    }

    // ------------------------------------------------------------- criptografia

    private fun chave(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gerador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gerador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gerador.generateKey()
    }

    private fun cifrar(texto: String): String {
        val cifra = Cipher.getInstance(TRANSFORMACAO).apply { init(Cipher.ENCRYPT_MODE, chave()) }
        val dados = cifra.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cifra.iv + dados, Base64.NO_WRAP)
    }

    /** Devolve null se o dado não puder ser lido (ex.: chave apagada ao restaurar backup). */
    private fun decifrar(valor: String): String? = runCatching {
        val bytes = Base64.decode(valor, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val cifra = Cipher.getInstance(TRANSFORMACAO).apply {
            init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(TAG_BITS, iv))
        }
        String(cifra.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "netafim_sessao_v1"
        const val TRANSFORMACAO = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        val ACCESS = stringPreferencesKey("access")
        val REFRESH = stringPreferencesKey("refresh")
        val USUARIO = stringPreferencesKey("usuario")
        val ID_DISPOSITIVO = stringPreferencesKey("id_dispositivo")
    }
}
