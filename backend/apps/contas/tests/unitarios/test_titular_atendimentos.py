"""Seção de atendimentos na exportação do titular: o import protegido, sem banco de dados."""

import sys
import types

from apps.contas.servicos import titular


def test_sem_o_app_de_atendimentos_a_secao_nao_existe():
    # nesta branch o app ainda não está instalado: a exportação segue só com os dados da conta
    assert titular._secao_atendimentos(object()) is None


def test_com_o_app_instalado_chama_a_funcao_do_modulo_com_o_titular(monkeypatch):
    chamadas = []
    saida = {"id_usuario": 7, "atendimentos": [], "historico": []}

    def exportar_atendimentos(usuario):
        chamadas.append(usuario)
        return saida

    pacote = types.ModuleType("apps.atendimentos")
    modulo = types.ModuleType("apps.atendimentos.exportacao")
    modulo.exportar_atendimentos = exportar_atendimentos
    monkeypatch.setitem(sys.modules, "apps.atendimentos", pacote)
    monkeypatch.setitem(sys.modules, "apps.atendimentos.exportacao", modulo)
    monkeypatch.setattr(titular.apps_instalados, "is_installed", lambda nome: True)

    titular_ = object()
    assert titular._secao_atendimentos(titular_) is saida
    assert chamadas == [titular_]
