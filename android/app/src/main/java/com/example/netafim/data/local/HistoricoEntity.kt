package com.example.netafim.data.local

import androidx.room.Entity
import androidx.room.Index

// Entidade para armazenamento local do histórico dos atendimentos.
@Entity(
    tableName = "HISTORICO_LOCAL",
    primaryKeys = ["usuarioDonoId", "idHistorico"],
    indices = [
        Index(value = ["usuarioDonoId", "idAtendimento"]),
        Index(value = ["usuarioDonoId", "dataEvento"])
    ]
)
data class HistoricoEntity(
    // Usuário proprietário do registro local.
    val usuarioDonoId: Int,

    // Identificador estável do evento fornecido pelo servidor.
    val idHistorico: Long,

    // Atendimento ao qual o evento pertence.
    val idAtendimento: String,

    // Tipo do evento.
    val tipoEvento: String,

    // Origem do evento.
    val origemEvento: String,

    // Valor anterior, quando existir.
    val valorAnterior: String?,

    // Novo valor do evento.
    val valorNovo: String,

    // Data e hora do evento em ISO 8601.
    val dataEvento: String
)
