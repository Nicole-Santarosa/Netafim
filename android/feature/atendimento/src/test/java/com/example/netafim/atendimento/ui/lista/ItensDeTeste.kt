package com.example.netafim.atendimento.ui.lista

import com.example.netafim.core.modelo.Atendimento
import com.example.netafim.core.modelo.ItemDeAtendimento
import com.example.netafim.core.modelo.MotivoAtendimento
import com.example.netafim.core.modelo.OrigemAtendimento
import com.example.netafim.core.modelo.SituacaoDeSincronizacao
import com.example.netafim.core.modelo.SituacaoDoProjeto
import com.example.netafim.core.modelo.StatusAtendimento
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

// Item de lista de teste, com valor padrão em tudo o que o teste não quer destacar.
fun itemDeTeste(
    id: String,
    status: StatusAtendimento = StatusAtendimento.AGENDADO,
    origem: OrigemAtendimento = OrigemAtendimento.CLIENTE_FINAL,
    aberto: String = "2026-11-01T12:00:00Z",
    protocolo: String? = "OS-$id",
    nomeCliente: String? = "Cliente $id",
    local: String? = null,
    idTecnico: Int? = null,
) = ItemDeAtendimento(
    id = id,
    protocolo = protocolo,
    motivo = MotivoAtendimento.POS_VENDA,
    status = status,
    origem = origem,
    abertoEm = Instant.parse(aberto),
    nomeCliente = nomeCliente,
    local = local,
    idTecnicoResponsavel = idTecnico,
    sincronizacao = SituacaoDeSincronizacao.SINCRONIZADO,
)

fun ids(lista: List<ItemDeAtendimento>) = lista.map { it.id }

// Itens de uma lista que já respondeu. Se ainda estiver carregando, o teste falha com a mensagem dizendo isso,
// em vez de comparar com uma lista vazia que esconderia o problema.
fun prontos(estado: EstadoDaLista): List<ItemDeAtendimento> =
    (estado as? EstadoDaLista.Pronta)?.itens ?: throw AssertionError("A lista ainda está em $estado")

// Atendimento completo de teste, em cima do itemDeTeste. Só o que os testes de detalhe e edição variam
// (id, status e situação do projeto) é parâmetro; o resto fica com valor fixo.
fun atendimentoDeTeste(
    id: String = "1",
    status: StatusAtendimento = StatusAtendimento.AGENDADO,
    situacaoProjeto: SituacaoDoProjeto? = null,
) = Atendimento(
    item = itemDeTeste(id, status = status),
    produtor = "Produtor $id",
    nomeFazenda = null,
    cidade = null,
    culturas = emptyList(),
    situacaoProjeto = situacaoProjeto,
    tipoVenda = null,
    historico = emptyList(),
    conflito = null,
    erroDeEnvio = null,
)

// O StateFlow dos ViewModels usa WhileSubscribed: só consulta o repositório enquanto alguém coleta. No teste
// esse alguém é esta coleta em segundo plano, que o runTest cancela sozinho no fim e que roda sem esperar
// (UnconfinedTestDispatcher), então o valor do StateFlow já está atualizado na linha seguinte.
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.manterColetando(fluxo: Flow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fluxo.collect {} }
}
