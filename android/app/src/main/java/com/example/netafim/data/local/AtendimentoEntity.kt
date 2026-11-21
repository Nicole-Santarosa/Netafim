package com.example.netafim.data.local

import androidx.room.Entity

// Representa um atendimento armazenado no banco de dados local.
@Entity(
    tableName = "ATENDIMENTO_LOCAL",
    primaryKeys = ["usuarioDonoId", "idAtendimento"]
)
data class AtendimentoEntity(

    // ID do usuário autenticado que é proprietário desta cópia local.
    val usuarioDonoId: Int,

    // UUID v4 que identifica o atendimento.
    val idAtendimento: String,

    // Número do protocolo atribuído pelo servidor.
    // Será nulo enquanto o atendimento não for sincronizado.
    val numeroProtocolo: String? = null,

    // Dados do atendimento armazenados em formato JSON.
    val dadosJson: String,

    // Indica se o atendimento foi criado localmente ou recebido do servidor.
    val origemRegistro: String,

    // Situação da sincronização do atendimento.
    val statusLocal: String,

    // Última versão do registro conhecida pelo aplicativo.
    // Um atendimento novo, criado offline, começa com versão zero.
    val versaoBase: Int = 0,

    // Sequência de sincronização atribuída pelo servidor.
    val syncSeqServidor: Long? = null,

    // Data e hora de criação no dispositivo, em timestamp (milissegundos).
    val dataCriacaoLocal: Long
)
