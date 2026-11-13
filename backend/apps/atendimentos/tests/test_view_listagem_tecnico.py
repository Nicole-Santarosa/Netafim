import pytest

from apps.atendimentos.servicos import abrir_atendimento, atualizar_atendimento
from apps.atendimentos.tests.test_view_alteracao import (
    criar_admin_netafim,
    criar_usuario,
)
from apps.atendimentos.tests.test_view_listagem import (
    URL,
    cliente_autenticado,
    dados_pedido,
)

# Este arquivo é separado de test_view_listagem.py porque precisa dos ajudantes
# de test_view_alteracao, que por sua vez importam de test_view_listagem: juntar
# os dois criaria um import circular.


@pytest.mark.django_db
def test_lista_traz_o_tecnico_atribuido_e_nulo_para_quem_nao_tem(
    mundo, conexao_admin, entrar_como
):
    id_cliente_usuario = mundo["id_usuario_cliente"]
    entrar_como(id_cliente_usuario)
    com_tecnico = abrir_atendimento(
        id_cliente_usuario,
        dados_pedido(mundo["id_cliente"], data_abertura="2026-10-01T10:00:00Z"),
    )
    sem_tecnico = abrir_atendimento(
        id_cliente_usuario,
        dados_pedido(mundo["id_cliente"], data_abertura="2026-10-02T10:00:00Z"),
    )
    admin = criar_admin_netafim(conexao_admin)
    tecnico = criar_usuario(conexao_admin, "Netafim Usuário", "netafim")
    # Só o Netafim Admin vincula técnico.
    entrar_como(admin)
    atualizar_atendimento(
        com_tecnico.id_atendimento, 1, {"id_tecnico_responsavel": tecnico}
    )

    # A lista é lida por quem abriu os pedidos.
    entrar_como(id_cliente_usuario)
    resposta = cliente_autenticado(id_cliente_usuario).get(URL)

    assert resposta.status_code == 200
    por_id = {r["id_atendimento"]: r for r in resposta.data["results"]}
    assert por_id[str(com_tecnico.id_atendimento)]["id_tecnico_responsavel"] == tecnico
    assert por_id[str(sem_tecnico.id_atendimento)]["id_tecnico_responsavel"] is None
