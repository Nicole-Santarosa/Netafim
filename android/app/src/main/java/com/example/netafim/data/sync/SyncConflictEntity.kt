package com.example.netafim.data.sync

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Entidade que representa um conflito de sincronização no banco de dados local. Cada instância desta entidade corresponde a um registro que não pôde ser sincronizado corretamente devido a divergências entre os dados locais e os dados do servidor.
@Entity(
    tableName = "CONFLITO_SYNC",
    indices = [
        Index(value = ["usuarioDonoId", "idAtendimento"]),
        Index(value = ["usuarioDonoId", "dataIdentificacao"])
    ]
)
data class SyncConflictEntity(

    @PrimaryKey
    // Identificador único do conflito, gerado a partir do hash do registro local e do registro do servidor.
    val idConflito: String,

    // Proprietário do conflito.
    val usuarioDonoId: Int,

    // Atendimento relacionado.
    val idAtendimento: String,

    // Cópia local preservada antes da resolução.
    val registroLocalJson: String,

    // Cópia recebida do servidor.
    val registroServidorJson: String,

    // Versão local no momento da identificação.
    val versaoLocal: Int,

    // Versão informada pelo servidor.
    val versaoServidor: Int,

    // Momento em que o conflito foi identificado.
    val dataIdentificacao: Long
)
