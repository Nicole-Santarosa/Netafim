package com.example.netafim.core.modelo

// Contrato de sincronização entre o transporte (as rotas POST /sync/push e GET /sync/pull) e as regras do
// app. Este arquivo só define tipos e interfaces, sem implementação, sem Retrofit, sem Room e sem lógica de
// rede: serve de combinado entre a camada de transporte e a camada local antes de qualquer código de verdade.
//
// O que NÃO é definido aqui, porque é da camada local: a fila de operações guardada no Room, o DataStore onde o
// cursor do PULL fica, os DAOs e a implementação do repository. Por isso não existe neste arquivo nenhum
// SincronizacaoRepository nem OperacaoPendente. O que está aqui é só a forma dos dados que atravessam a
// fronteira: o que o servidor devolve, as falhas possíveis e a interface do coordenador do ciclo.
//
// Diferença para o ResultadoDaAcao, que já existe no módulo:
// - ResultadoDaAcao é o retorno das ações do usuário no AtendimentoRepository (abrir, iniciar, alterar). Quer
//   dizer "já foi salvo no aparelho" e não fala nada do servidor.
// - ResultadoDaOperacao é o que o servidor responde para CADA operação do lote do PUSH. Só existe depois que
//   o envio aconteceu, e é ele que faz a camada de dados atualizar o registro local (SINCRONIZADO, CONFLITO
//   ou ERRO).
//
// Contrato do servidor descrito em docs/api/sync.md. Os pontos marcados como "decidido, ainda não
// implementado" ou "proposta" abaixo seguem o estado daquele documento.

/**
 * Identificador de uma operação da fila de envio. É um UUID gerado uma única vez, no momento em que o
 * usuário faz a ação, e guardado junto com a operação na fila.
 *
 * Ele precisa ser preservado em TODO reenvio da mesma operação: nunca se gera outro id por causa de falha de
 * comunicação (timeout, queda de rede, 5xx). O servidor usa esse id para correlacionar a resposta e, quando
 * existir a tabela de operações processadas, para reconhecer um reenvio. Gerar um id novo a cada tentativa
 * quebraria essa correspondência e poderia aplicar a mesma ação duas vezes.
 */
data class IdDaOperacao(val valor: String)

/**
 * O registro de um atendimento como o servidor devolve, já pronto para ser guardado no aparelho.
 *
 * O [atendimento] já vem mapeado para o modelo do app, com a sincronização em SINCRONIZADO, sem conflito e
 * sem erro de envio. Esse mapeamento é feito pela camada de dados, e quem usa este tipo pode confiar que o
 * que está nele é a cópia confirmada pelo servidor.
 *
 * [versao] e [syncSeq] existem separados porque o Atendimento não tem campo de versão: o controle de versão é
 * interno do repository, que guarda a versaoBase no Room. A [versao] vira a versaoBase local e é a que vai
 * como versao_esperada no próximo envio de alteração. O [syncSeq] é a posição do registro na ordem do
 * servidor e serve para o app saber se o que recebeu é mais novo do que tem.
 *
 * O [historico] traz os eventos do atendimento inteiro, e não só os novos. A camada local deduplica por
 * EventoDoHistorico.id, que é o id_historico do servidor, para o mesmo evento nunca aparecer duas vezes. Fica
 * vazio quando o servidor não mandou histórico ou o atendimento não tem eventos.
 */
data class RegistroDoServidor(
    val atendimento: Atendimento,
    val versao: Int,
    val syncSeq: Long,
    val historico: List<EventoDoHistorico> = emptyList(),
)

/**
 * Resultado de UMA operação do lote do PUSH, como o servidor devolve (campo resultado de cada item da
 * resposta). O servidor responde 200 para o lote mesmo quando algumas operações falham, então cada operação
 * tem o seu próprio resultado, na mesma ordem em que foi enviada.
 *
 * Conflito de versão, duplicidade com conteúdo diferente, rejeição, duplicado igual e operação reutilizada
 * são situações DISTINTAS e nunca devem ser misturadas: cada uma pede um tratamento diferente da camada
 * local (sucesso, conflito para o usuário resolver, erro para corrigir ou descartar). Por isso cada uma é
 * um tipo próprio, e não um código dentro de um tipo só.
 */
sealed interface ResultadoDaOperacao {
    /**
     * O atendimento novo foi gravado no servidor (criar). A camada local troca a cópia local pelo
     * [registro], guarda protocolo, versao e syncSeq e marca o registro como SINCRONIZADO.
     */
    data class Criado(val registro: RegistroDoServidor) : ResultadoDaOperacao

    /**
     * A alteração foi aplicada no servidor (atualizar). A camada local troca a cópia pelo [registro], que
     * já traz a versao e o syncSeq novos, e marca o registro como SINCRONIZADO.
     */
    data class Atualizado(val registro: RegistroDoServidor) : ResultadoDaOperacao

    /**
     * O servidor já tinha processado esse mesmo atendimento com o mesmo conteúdo (reenvio de uma criação).
     * É sucesso idempotente: nada foi duplicado. O [registro] é a cópia atual do servidor, que o contrato
     * sempre entrega neste caso. A camada local trata como Criado: aplica o registro e marca como
     * SINCRONIZADO.
     */
    data class DuplicadoIgual(val registro: RegistroDoServidor) : ResultadoDaOperacao

    /**
     * Já existe um atendimento com esse mesmo id, mas com dados diferentes dos enviados (409 duplicado, com
     * comparacao diferente). Não é sucesso: é um conflito de conteúdo. O [registroDoServidor] é a cópia atual
     * do servidor, que o contrato sempre entrega neste caso. A camada local o guarda, marca o registro como
     * CONFLITO e mantém o que o usuário digitou, sem descartar nada. A [mensagem] é a que o servidor
     * devolveu.
     *
     * Este NÃO é o caso em que o atendimento existe mas o usuário não o enxerga: esse chega como Rejeitada,
     * com status 409 e codigo "duplicado", sem registro, para o servidor não revelar que o atendimento
     * existe.
     */
    data class DuplicidadeComConteudoDiferente(
        val registroDoServidor: RegistroDoServidor,
        val mensagem: String,
    ) : ResultadoDaOperacao

    /**
     * A versão que o aparelho tinha não é a do servidor (409 conflito_de_versao). [versaoAtualDoServidor] é a
     * versao_atual que o servidor devolveu, e [registroDoServidor] é a cópia atual dele, que o conflito de
     * versão do PUSH sempre traz. A camada local marca o registro como CONFLITO, guarda essas duas
     * informações no ConflitoDoAtendimento e mantém a alteração do usuário para ele refazer.
     */
    data class ConflitoDeVersao(
        val versaoAtualDoServidor: Int,
        val registroDoServidor: RegistroDoServidor,
    ) : ResultadoDaOperacao

    /**
     * O servidor recusou a operação. [status] é o código HTTP equivalente e pode ser:
     * - 400: validação por campo, ou valor que o banco recusa por formato ou tamanho;
     * - 403: falta de permissão;
     * - 404: atualizar um atendimento inexistente, excluído ou invisível ao usuário;
     * - 409: criação com um id que já existe mas que o usuário não enxerga (codigo "duplicado"). Quer dizer
     *   que a criação usou um id de outra pessoa; o servidor não manda registro, para não revelar que o
     *   atendimento existe. A camada local marca ERRO com a mensagem, sem GET e sem reenvio automático
     *   (regra proposta, pendente de confirmação da camada local);
     * - 422: regra de negócio ou CHECK do banco.
     *
     * [codigo] é o código de erro do servidor (por exemplo sem_permissao, nao_encontrado, duplicado,
     * valor_invalido, regra_de_negocio, referencia_invalida, campo_obrigatorio). É nulo na validação por
     * campo (400), que o servidor devolve sem código. [mensagem] é o texto geral. [erros] é um mapa do nome
     * do campo para uma mensagem só: a camada de dados junta as mensagens quando o servidor mandar mais de
     * uma por campo. Fica vazio quando o erro não é de campo.
     *
     * Reenviar igual não adianta. A camada local marca o registro como ERRO com a mensagem, para o usuário
     * corrigir ou descartar.
     */
    data class Rejeitada(
        val status: Int,
        val codigo: String?,
        val mensagem: String,
        val erros: Map<String, String>,
    ) : ResultadoDaOperacao

    /**
     * 409 operacao_reutilizada: o mesmo id de operação chegou com outro usuário ou com conteúdo diferente do
     * que o servidor já tinha guardado para ele. Em condições normais não acontece, porque o app nunca gera
     * outro id nem altera uma operação já enfileirada, então indica defeito. A camada local marca o registro
     * como ERRO e não reenvia sozinha. Depende de uma tabela de operações processadas que ainda não existe
     * no servidor (proposta).
     *
     * No contrato proposto o servidor responde isso como rejeitado, com status 409 e codigo
     * "operacao_reutilizada". A camada de dados converte essa resposta para este tipo, para o tratamento
     * ficar separado de uma rejeição comum.
     *
     * Mantido como tipo próprio por decisão alinhada com a camada local em 22/11/2026: ele indica reutilização
     * indevida do identificador da operação e merece tratamento e diagnóstico próprios, mesmo chegando no
     * transporte como Rejeitada 409.
     */
    data object OperacaoReutilizada : ResultadoDaOperacao

    /**
     * Um resultado que este app não entende, por exemplo um valor novo que um servidor mais recente passou a
     * mandar. [codigo] é o valor recebido. Alinhado com a camada local em 22/11/2026:
     * - o [codigo] recebido deve ser preservado para diagnóstico;
     * - a mensagem mostrada ao usuário deve ser genérica e indicar incompatibilidade ou a necessidade de
     *   atualizar o aplicativo;
     * - o registro vai para ERRO;
     * - não há reenvio automático, porque reenviar não resolve, e atualizar o app resolve;
     * - nunca se trata como sucesso.
     */
    data class Desconhecido(val codigo: String) : ResultadoDaOperacao
}

/**
 * Falha de transporte: a chamada a uma das duas rotas (PUSH ou PULL) não produziu uma resposta que o app
 * consiga aplicar. No PUSH, nenhuma operação do lote vale como concluída e a fila fica como está. No PULL, a
 * página não é aplicada e o cursor não avança. Não confundir com a rejeição de uma operação (Rejeitada), que
 * é um resultado normal dentro de um lote que chegou inteiro.
 */
sealed interface FalhaDeTransporte {
    /**
     * Sem resposta útil: timeout, aparelho sem rede ou erro 5xx do servidor. A camada local mantém a fila e
     * reenvia depois com os mesmos [IdDaOperacao], com espera crescente entre as tentativas.
     */
    data object SemComunicacao : FalhaDeTransporte

    /**
     * 401 que continua depois de o app tentar renovar o token e falhar. A camada local interrompe o ciclo,
     * mantém a fila e o cursor como estão e pede um novo login. Um 403 dentro de uma operação NÃO é isso: é
     * uma rejeição da operação e não derruba a sessão.
     */
    data object SessaoInvalida : FalhaDeTransporte

    /**
     * O servidor recusou o pedido do PULL por formato (400), por exemplo desde ou limite inválidos.
     * [mensagem] é a que o servidor devolveu. Indica defeito na montagem do pedido, então o app não deve
     * repetir o mesmo pedido sem corrigi-lo: repetir igual só devolve o mesmo 400.
     */
    data class PedidoRecusado(val mensagem: String) : FalhaDeTransporte
}

/**
 * Falha do PUSH que atinge o lote inteiro: o servidor recusou o lote antes de executar qualquer operação.
 * Os motivos são lote vazio, acima de 50 operações e id_operacao ausente, repetido ou fora do formato.
 * Representa especificamente essa recusa do lote no PUSH, e por isso nunca vem do PULL.
 *
 * Por ser um subtipo de [FalhaDeTransporte], uma FalhaDoLote cabe em qualquer lugar que aceite uma
 * FalhaDeTransporte, como o ResultadoDoCiclo.Interrompido. Quem trata só as falhas de transporte em geral
 * não precisa conhecer este tipo, e quem precisa distinguir a recusa do lote olha para ele.
 */
sealed interface FalhaDoLote : FalhaDeTransporte {
    /**
     * O servidor recusou o lote por formato (400), por exemplo lote vazio, acima de 50 operações ou
     * id_operacao ausente, repetido dentro do lote ou fora do formato. Nenhuma operação foi executada.
     * [mensagem] é a que o servidor devolveu. Indica defeito na montagem do lote, então a camada local não
     * deve reenviar o mesmo lote sem corrigi-lo.
     */
    data class LoteInvalido(val mensagem: String) : FalhaDoLote
}

/**
 * Falha ao gravar no aparelho (Room) algo que o servidor já entregou. Não é resposta da API e não tem
 * relação com a rede.
 *
 * Regra para os dois casos: em falha local NÃO se marca a operação como concluída e NÃO se avança nem se
 * persiste o cursor do PULL. Assim, no ciclo seguinte, a mesma operação ou a mesma página é tratada de novo.
 * Uma criação reenviada volta como DuplicadoIgual; numa atualização reenviada vale a limitação conhecida do
 * servidor, em que uma resposta perdida volta como conflito de versão.
 */
sealed interface FalhaDePersistenciaLocal {
    /**
     * O erro aconteceu ao aplicar no aparelho o resultado de uma operação do PUSH. [idOperacao] diz qual
     * operação ficou sem ser concluída, e [causa] é a descrição do erro para registro e diagnóstico.
     */
    data class AoAplicarResultado(
        val idOperacao: IdDaOperacao,
        val causa: String,
    ) : FalhaDePersistenciaLocal

    /**
     * O erro aconteceu ao aplicar no aparelho uma página do PULL. A página inteira conta como não aplicada
     * e o cursor não é persistido. [causa] é a descrição do erro.
     */
    data class AoAplicarPagina(val causa: String) : FalhaDePersistenciaLocal
}

/**
 * Posição do PULL, guardada no DataStore: o maior sync_seq que o app já aplicou. É uma "posição de
 * sincronização com sobreposição e recuperação por resincronização completa", e NÃO uma garantia absoluta
 * de que nada ficou para trás: o servidor tira o sync_seq na escrita e não no commit, então uma transação
 * que confirma tarde pode ficar atrás do cursor.
 *
 * Por isso o app pede o PULL com sobreposição, com desde = max(0, cursor - 1000), e aplica o resultado de
 * forma idempotente. O que a sobreposição não alcançar só é recuperado pela resincronização completa
 * (MotivoDaResincronizacaoCompleta).
 *
 * Atenção à unidade da sobreposição: as 1.000 posições são posições globais da seq_sync, que é uma só
 * sequência compartilhada por 22 tabelas (e também pela coluna nova do histórico, decidida e ainda não
 * implementada). Elas NÃO equivalem a 1.000 atendimentos: numa base com muita escrita em outras tabelas, a
 * janela cobre bem menos atendimentos do que o número sugere.
 *
 * O [cursor] só é persistido depois de a página inteira ser aplicada com sucesso no aparelho. Se a
 * gravação falhar no meio, o cursor anterior continua valendo.
 */
data class PosicaoDeSincronizacao(val cursor: Long)

/**
 * Uma página da resposta do PULL.
 *
 * Formato decidido com a camada local em 22/11/2026, ainda não implementado no servidor: cada item é um
 * atendimento com o histórico completo, o limite da página conta atendimentos, e o [cursor] da página é o
 * maior sync_seq efetivo da página, que é o maior entre o sync_seq do atendimento e o dos seus eventos de
 * histórico. Enquanto isso não existir, o servidor só olha o sync_seq do atendimento.
 *
 * [itens] vêm em ordem crescente de sync_seq. [temMais] é verdadeiro quando existe outra página, e então o
 * app repete o pedido com desde igual ao [cursor] recebido. Quando a página termina sem mais nada, é falso.
 * O app só persiste o cursor depois de aplicar a página inteira.
 */
data class PaginaDoPull(
    val itens: List<ItemDoPull>,
    val cursor: Long,
    val temMais: Boolean,
)

/**
 * Um item de uma página do PULL: ou um atendimento para guardar, ou a notícia de que ele foi removido.
 */
sealed interface ItemDoPull {
    /**
     * Um atendimento novo ou alterado. A camada local o aplica de forma idempotente: só substitui a cópia
     * local quando a versao ou o syncSeq do [registro] forem maiores que os locais, e nunca sobrescreve um
     * registro em PENDENTE_DE_ENVIO ou CONFLITO, porque ali há trabalho do usuário que não subiu.
     *
     * O histórico do registro é aplicado mesmo quando a versão do atendimento não mudou, deduplicando por
     * EventoDoHistorico.id. Isso é necessário porque, com o sync_seq no próprio histórico (decidido, ainda
     * não implementado), um evento novo pode chegar sem o atendimento ter mudado de versão.
     */
    data class AtendimentoAtualizado(val registro: RegistroDoServidor) : ItemDoPull

    /**
     * O atendimento foi excluído no servidor (tombstone). Traz só o [idAtendimento], a [versao] e o
     * [syncSeq], sem histórico nem outros campos.
     *
     * Regra alinhada com a camada local em 22/11/2026, ainda não implementada: o tombstone só apaga a cópia
     * local se ela estiver em SINCRONIZADO. Em PENDENTE_DE_ENVIO ou CONFLITO a cópia é mantida, com o que o
     * usuário digitou. Se o PUSH seguinte devolver 404 ou 403 (rejeição), o registro vai para ERRO com a
     * mensagem, mas os dados do usuário não são apagados.
     */
    data class Removido(
        val idAtendimento: String,
        val versao: Int,
        val syncSeq: Long,
    ) : ItemDoPull
}

/**
 * Por que o app faz uma resincronização completa. É a forma de recuperar um registro que ficou para trás do
 * cursor e de receber eventos antigos de histórico, que ficam com sync_seq 0 e não chegam pelo PULL
 * incremental. Alinhada com a camada local em 22/11/2026, ainda não implementada.
 *
 * Procedimento, igual para os três motivos:
 * 1. Executar o PUSH primeiro.
 * 2. Voltar o cursor a 0.
 * 3. Executar o PULL de todas as páginas, repetindo enquanto temMais for verdadeiro.
 * 4. Nunca sobrescrever um registro em PENDENTE_DE_ENVIO nem em CONFLITO.
 * 5. Aplicar de forma idempotente.
 * 6. Persistir o cursor só depois de cada página inteira ter sido aplicada com sucesso.
 * 7. Reconciliar no fim (regra aprovada, implementação pendente na camada local). Condições:
 *    - roda só depois de todas as páginas do PULL completo terem sido aplicadas com sucesso e de a última
 *      página ter vindo com temMais = false;
 *    - durante todo o PULL completo, o app guarda os ids de atendimento recebidos e, no fim, compara com os
 *      atendimentos locais do usuário da sessão (usuarioDonoId);
 *    - remove somente as cópias em SINCRONIZADO cujo id não apareceu, porque o servidor não as devolveu
 *      mais;
 *    - preserva as cópias em PENDENTE_DE_ENVIO, CONFLITO e ERRO, como no tombstone;
 *    - qualquer falha de página, de comunicação ou de persistência cancela a reconciliação, e nada é
 *      removido;
 *    - nunca roda no PULL incremental, onde a ausência de um id não diz nada. Só o PULL completo parte do
 *      cursor 0.
 */
enum class MotivoDaResincronizacaoCompleta {
    // O usuário pediu, por uma ação própria na tela.
    MANUAL,

    // Automática: o app ficou mais de sete dias sem sincronizar e voltou a ter conexão. Passado tanto
    // tempo, a sobreposição do cursor não basta para garantir que nada foi perdido.
    MAIS_DE_SETE_DIAS_SEM_SINCRONIZAR,

    // Automática: a versão do contrato com o servidor mudou, então o que está guardado pode ter outro
    // formato ou faltar dados novos.
    MUDANCA_DE_VERSAO_DO_CONTRATO,
}

/**
 * Orquestra um ciclo de sincronização: junta o PUSH e o PULL na ordem certa. É só a interface, a
 * implementação é da camada de dados. Cada rota continua independente no servidor.
 *
 * A ordem e a orquestração estão alinhadas com a camada local em 22/11/2026, pendentes de consolidação por
 * escrito do grupo; implementação não iniciada.
 */
interface CoordenadorDeSincronizacao {
    /**
     * Faz um ciclo normal: PUSH antes de PULL. Regras:
     * - Falha de transporte (FalhaDeTransporte) no PUSH não dispara o PULL: o ciclo termina em
     *   ResultadoDoCiclo.Interrompido. A recusa do lote inteiro (FalhaDoLote) é uma dessas falhas.
     * - Falha de transporte no PULL também interrompe o ciclo, e o cursor anterior continua valendo.
     * - 401 (sessão inválida) interrompe o ciclo e mantém fila e cursor.
     * - Rejeição individual de uma operação NÃO impede o PULL: ela só marca aquele registro como ERRO.
     */
    suspend fun sincronizar(): ResultadoDoCiclo

    /**
     * Faz a resincronização completa pelo [motivo], com o procedimento descrito em
     * MotivoDaResincronizacaoCompleta.
     */
    suspend fun resincronizarTudo(motivo: MotivoDaResincronizacaoCompleta): ResultadoDoCiclo
}

/**
 * Como um ciclo de sincronização terminou. Os três casos são distintos: terminou, foi interrompido por falha
 * do servidor ou da rede, ou falhou ao gravar no aparelho.
 */
sealed interface ResultadoDoCiclo {
    /**
     * O ciclo terminou. [operacoesProcessadas] conta as operações do PUSH que receberam um resultado
     * individual do servidor, inclusive as rejeitadas. Isso NÃO quer dizer que foram aceitas nem que ficaram
     * sincronizadas: a rejeição continua refletida no estado ERRO do registro. [itensAplicados] é quantos
     * itens do PULL foram aplicados no aparelho. Os dois podem ser zero.
     */
    data class Concluido(val operacoesProcessadas: Int, val itensAplicados: Int) : ResultadoDoCiclo

    /**
     * O ciclo parou por uma [falha] de transporte, que vale para as duas rotas, PUSH e PULL: sem
     * comunicação, sessão inválida, pedido recusado (PULL) ou lote recusado (PUSH, que é um subtipo). A fila
     * e o cursor ficam como estavam, e a próxima tentativa recomeça de onde parou.
     */
    data class Interrompido(val falha: FalhaDeTransporte) : ResultadoDoCiclo

    /**
     * O ciclo parou porque o aparelho não conseguiu gravar o que o servidor entregou. A [falha] diz se foi
     * ao aplicar o resultado de uma operação ou uma página do PULL. Nesses casos a operação não é marcada
     * como concluída e o cursor não avança.
     */
    data class FalhouNoAparelho(val falha: FalhaDePersistenciaLocal) : ResultadoDoCiclo
}

// Assinatura confirmada pela camada local em 22/11/2026, FORA deste contrato e SEM alterar o AtendimentoRepository:
//
//     suspend fun completarTipoDeVenda(id: String, tipoVenda: TipoVenda): ResultadoDaAcao
//
// Motivo: o tipo de venda passou a ser obrigatório em todo atendimento (decisão da Netafim de 18/11/2026).
// Um atendimento aberto offline antes dessa regra pode estar na fila sem tipo de venda e receberia 400 no
// envio. Esta função serve para o usuário informar o tipo de venda do atendimento que ficou parado.
//
// O que a operação faz: atualiza o atendimento local e reativa a operação que já existe na fila, sem criar
// duplicata. O sucesso só é retornado depois da confirmação da transação no Room, para a tela nunca mostrar
// como salvo algo que ainda pode ser desfeito.
//
// A implementação é da camada local, porque mexe no Room e na fila. A tela onde o usuário escolhe o tipo de
// venda é da camada de telas. O retorno é um ResultadoDaAcao, pois é uma ação do usuário que grava primeiro no
// aparelho, como as demais do AtendimentoRepository.
