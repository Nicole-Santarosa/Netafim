"""Campos de modelo do módulo de acesso."""

from django.db import models


class HashSenhaField(models.CharField):
    """
    Hash da senha, SOMENTE DE ESCRITA: o Django grava (INSERT e UPDATE), mas nunca lê.

    Em todo SELECT a coluna é trocada por ``NULL`` (inclusive em ``select_related`` feito por outro
    módulo, por exemplo ``atendimento.solicitante``). Assim a conexão comum da API (``app_django``)
    pode perder o privilégio de leitura de ``usuario.password`` (DT-002) sem que nenhuma consulta
    quebre, e sem depender de cada desenvolvedor lembrar de ``defer("password")``.

    O hash só chega ao processo pela conexão ``auth``, nas funções ``fn_credencial_por_email`` e
    ``fn_credencial_por_id`` (ver ``servicos/credenciais.py``).

    Consequência: depois de carregar um usuário, ``usuario.password`` vale ``None``. Nunca confira
    senha por ``usuario.check_password``; use ``credenciais.conferir``.
    """

    def select_format(self, compiler, sql, params):
        return "NULL", []
