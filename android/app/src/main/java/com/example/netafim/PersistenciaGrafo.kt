package com.example.netafim

import android.content.Context
import com.example.netafim.data.local.NetafimDatabase
import com.example.netafim.data.repository.AtendimentoRepository
import com.example.netafim.data.repository.SyncOperationRepository
import com.example.netafim.data.repository.SyncConflictRepository

/**
 * Centraliza a inicialização dos repositórios de persistência local.
 *
 * Esta classe pertence ao módulo :app. Assim, o módulo
 * :feature:acesso permanece independente do Room.
 */
object PersistenciaGrafo {

    private lateinit var atendimentoLocal: AtendimentoRepository
    private lateinit var operacoesSyncLocal: SyncOperationRepository
    private lateinit var conflitosSyncLocal: SyncConflictRepository

    private var inicializado = false

    /**
     * Inicializa o banco local e os respectivos repositórios.
     */
    fun inicializar(contexto: Context) {
        if (inicializado) return

        val bancoLocal = NetafimDatabase.getDatabase(
            contexto.applicationContext
        )

        // Repositório de atendimentos.
        atendimentoLocal = AtendimentoRepository(
            atendimentoDao = bancoLocal.atendimentoDao()
        )

        // Repositório da fila de operações de sincronização.
        operacoesSyncLocal = SyncOperationRepository(
            dao = bancoLocal.syncOperationDao()
        )

        // Repositório de conflitos de sincronização.
        conflitosSyncLocal = SyncConflictRepository(
            dao = bancoLocal.syncConflictDao()
        )

        inicializado = true
    }

    /**
     * Disponibiliza o repositório de atendimentos.
     */
    val repositorioAtendimentoLocal: AtendimentoRepository
        get() {
            check(inicializado) {
                "PersistenciaGrafo ainda não foi inicializado."
            }
            return atendimentoLocal
        }

    /**
     * Disponibiliza o repositório da fila de sincronização.
     */
    val repositorioOperacoesSync: SyncOperationRepository
        get() {
            check(inicializado) {
                "PersistenciaGrafo ainda não foi inicializado."
            }
            return operacoesSyncLocal
        }

    /**
     * Disponibiliza o repositório de conflitos.
     */
    val repositorioConflitosSync: SyncConflictRepository
        get() {
            check(inicializado) {
                "PersistenciaGrafo ainda não foi inicializado."
            }
            return conflitosSyncLocal
        }
}
