package com.example.netafim.data.sync

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "OPERACAO_SYNC",
    indices = [
        Index(value = ["usuarioDonoId", "estado"]),
        Index(value = ["usuarioDonoId", "id"])
    ]
)
data class SyncOperationEntity(

    @PrimaryKey
    // Permite identificar cada operação individualmente.
    val idOperacao: String,

    // Proprietário da operação: ID do usuário autenticado.
    val usuarioDonoId: Int,

    // Entidade relacionada: atendimento ou evidencia.
    val entidade: String,

    // UUID do registro relacionado.
    val id: String,

    // Tipo da operação: criar ou atualizar.
    val tipo: String,

    // Nula quando se trata de uma criação.
    val versaoBase: Int?,

    // Dados da operação em JSON.
    val campos: String,

    // Data da operação no formato ISO 8601.
    val feitoEm: String,

    // Número de tentativas realizadas.
    val tentativas: Int = 0,

    // Estado atual da operação.
    val estado: String
)
