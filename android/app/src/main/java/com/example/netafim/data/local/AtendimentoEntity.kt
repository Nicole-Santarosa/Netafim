package com.example.netafim.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

//informa ao Room que essa classe representa a tabela local.
@Entity(tableName = "ATENDIMENTO_LOCAL")
data class AtendimentoEntity(
    @PrimaryKey
    val idAtendimento: String,

    val numeroProtocolo: String? = null, //É opcional porque um atendimento criado offline pode ainda não ter protocolo atribuído pelo servidor.

    val dadosJson: String, //Guarda os dados do atendimento em formato JSON, conforme o modelo do projeto.

    val origemRegistro: String, //Identifica a origem do registro.

    val statusLocal: String, //Registra o estado local do atendimento.

    val versaoBase: Int = 0, //Ajuda a identificar a versão a partir da qual o atendimento foi editado.

    val syncSeqServidor: Long? = null, //Pode ser nulo quando ainda não há sequência conhecida no servidor.

    val dataCriacaoLocal: Long //Armazena a data e hora de criação como timestamp em milissegundos.
)