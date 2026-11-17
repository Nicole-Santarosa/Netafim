package com.example.netafim.core.modelo

// Como a lista volta ordenada. A regra de cada ordem fica no repositório; a tela só escolhe uma.
enum class OrdemDaLista {
    // Primeiro o que está em atendimento, depois os mais antigos, desempate pelo protocolo.
    FILA_DO_TECNICO,

    // Do mais recente para o mais antigo, desempate pelo id.
    MAIS_RECENTE_PRIMEIRO,
}

// O que a tela pede ao observar a lista. Conjunto vazio e nulo significam "sem filtro" naquele critério,
// então FiltroDeAtendimentos() traz tudo o que o usuário logado pode ver.
data class FiltroDeAtendimentos(
    // Status de negócio aceitos.
    val status: Set<StatusAtendimento> = emptySet(),
    // Canais do atendimento aceitos (quem abriu).
    val origem: Set<OrigemAtendimento> = emptySet(),
    // Só os criados neste aparelho ou só os vindos do servidor.
    val origemDoRegistro: OrigemDoRegistro? = null,
    // Só os que têm o usuário logado como técnico responsável. O id do usuário nunca passa pela tela.
    val somenteMeusComoTecnico: Boolean = false,
    // Texto digitado na busca, aplicado pelo repositório só sobre os dados locais.
    val busca: String = "",
    val ordem: OrdemDaLista = OrdemDaLista.MAIS_RECENTE_PRIMEIRO,
)
