package com.example.netafim.core.modelo

import kotlinx.coroutines.flow.Flow

/**
 * Porta de entrada das telas para os atendimentos. É só a interface: a implementação concreta vive no módulo
 * de dados, que usa o Room e a sincronização. As telas dependem apenas deste contrato e nunca enxergam
 * entidade do Room nem o dadosJson.
 *
 * Contrato offline-first:
 * - Toda consulta devolve só o que o usuário logado pode ver, e nenhuma assinatura recebe o dono: quem sabe
 *   quem está logado é o repositório.
 * - As leituras olham só para os dados locais e emitem de novo a cada mudança.
 * - As escritas gravam primeiro no aparelho. ResultadoDaAcao.Sucesso quer dizer "salvo no aparelho", e a
 *   confirmação definitiva só existe quando a sincronização do registro for SINCRONIZADO. Se o servidor
 *   recusar no envio, o repositório atualiza o registro local (CONFLITO ou ERRO com a mensagem) e a tela
 *   percebe pelo observarAtendimento.
 */
interface AtendimentoRepository {
    /**
     * Acompanha um atendimento pelo id. Emite nulo quando ele não existe ou o usuário não o enxerga, e emite
     * de novo a cada mudança local, inclusive quando a sincronização muda de situação.
     */
    fun observarAtendimento(id: String): Flow<Atendimento?>

    /**
     * Acompanha a lista de atendimentos do usuário logado. A lista já volta filtrada pelo [filtro], já pela
     * busca e já na ordem pedida, então a tela não aplica regra nenhuma.
     */
    fun observarAtendimentos(filtro: FiltroDeAtendimentos): Flow<List<ItemDeAtendimento>>

    /**
     * Abre um atendimento. Grava primeiro no aparelho, com sincronização PENDENTE_DE_ENVIO, e o envio ao
     * servidor acontece na sincronização. Reenviar o mesmo id da [solicitacao] não duplica o atendimento.
     */
    suspend fun abrirAtendimento(solicitacao: SolicitacaoDeAbertura): ResultadoDaAcao

    /**
     * Inicia o atendimento (agendado para em atendimento). É um atalho para o botão Iniciar: o mesmo que
     * alterar o status para EM_ATENDIMENTO. Grava primeiro no aparelho.
     */
    suspend fun iniciarAtendimento(id: String): ResultadoDaAcao

    /**
     * Altera campos do atendimento. Não recebe a versão esperada: o repositório usa o versaoBase que guarda
     * no Room. Se o servidor responder com conflito, o registro local fica em CONFLITO sem perder a edição.
     */
    suspend fun alterarAtendimento(id: String, alteracao: AlteracaoDeAtendimento): ResultadoDaAcao

    /** Acompanha o catálogo de culturas guardado no aparelho. */
    fun observarCulturas(): Flow<List<Cultura>>

    /** Acompanha o catálogo de distribuidores guardado no aparelho, só os ativos. */
    fun observarDistribuidores(): Flow<List<Distribuidor>>
}
