package com.example.netafim.core.modelo

// Para quem o atendimento foi destinado (campo destinado_a). Os códigos são os do CHECK do banco.
// Não tem valor DESCONHECIDO de propósito: o app só envia este campo, nunca guarda um destino que não entende.
// No banco o destino pode ser nulo (atendimento ainda aberto), por isso deCodigo devolve nulo.
enum class DestinadoA(val codigo: String) {
    NETAFIM("netafim"),
    DISTRIBUIDOR("distribuidor");

    companion object {
        // Nulo para código ausente ou não reconhecido.
        fun deCodigo(codigo: String?): DestinadoA? = entries.firstOrNull { it.codigo == codigo }
    }
}
