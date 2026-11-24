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

    private lateinit var atendimentoLocal: AtendimentoRepository // Repositório de atendimentos.
    private lateinit var operacoesSyncLocal: SyncOperationRepository // Repositório da fila de operações de sincronização.
    private lateinit var conflitosSyncLocal: SyncConflictRepository // Repositório de conflitos de sincronização.

    private var inicializado = false // Indica se a persistência local já foi inicializada.

    /**
     * Inicializa o banco local e os respectivos repositórios.
     */
    fun inicializar(contexto: Context) {
        if (inicializado) return

        //  Inicializa o banco local.
        val bancoLocal = NetafimDatabase.getDatabase(
            contexto.applicationContext
        )

        // Repositório de atendimentos.
        atendimentoLocal = AtendimentoRepository(
            banco = bancoLocal
        )

        // Repositório da fila de operações de sincronização.
        operacoesSyncLocal = SyncOperationRepository(
            dao = bancoLocal.syncOperationDao()
        )

        // Repositório de conflitos de sincronização.
        conflitosSyncLocal = SyncConflictRepository(
            dao = bancoLocal.syncConflictDao()
        )

        inicializado = true // Marca a persistência local como inicializada.
    }

    /**
     * Disponibiliza o repositório de atendimentos.
     */
    val repositorioAtendimentoLocal: AtendimentoRepository
        get() {
            check(inicializado) {
                "PersistenciaGrafo ainda não foi inicializado."
            }
            return atendimentoLocal // Retorna o repositório de atendimentos.
        }

    /**
     * Disponibiliza o repositório da fila de sincronização.
     */
    val repositorioOperacoesSync: SyncOperationRepository
        get() {
            check(inicializado) {
                "PersistenciaGrafo ainda não foi inicializado."
            }
            return operacoesSyncLocal // Retorna o repositório da fila de operações de sincronização.
        }

    /**
     * Disponibiliza o repositório de conflitos.
     */
    val repositorioConflitosSync: SyncConflictRepository
        get() {
            check(inicializado) {
                "PersistenciaGrafo ainda não foi inicializado."
            }
            return conflitosSyncLocal // Retorna o repositório de conflitos de sincronização.
        }
}
