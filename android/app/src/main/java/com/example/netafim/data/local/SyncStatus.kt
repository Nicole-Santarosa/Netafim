package com.example.netafim.data.local

// Centraliza os estados de sincronização permitidos para um atendimento.
// Esses valores não representam o status de negócio do atendimento.
object SyncStatus {

    // O atendimento já foi sincronizado com o servidor.
    const val SINCRONIZADO = "sincronizado"

    // O atendimento possui uma operação aguardando envio.
    const val PENDENTE_ENVIO = "pendente_envio"

    // O servidor identificou uma divergência de versão.
    const val CONFLITO = "conflito"

    // O servidor rejeitou a operação ou ocorreu um erro que precisa ser tratado.
    const val ERRO = "erro"
}

// Centraliza a origem de cada atendimento armazenado localmente.
object OrigemRegistro {

    // Atendimento criado diretamente no dispositivo.
    const val LOCAL = "local"

    // Atendimento recebido do servidor por meio da sincronização.
    const val SERVIDOR = "servidor"
}
