from django.db.utils import Error as ErroDeBanco
from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import exception_handler, set_rollback

# SQLSTATE exato do PostgreSQL para (status HTTP, código do corpo, mensagem curta).
# A mensagem não cita tabela nem coluna do banco. O 23514 fica de fora porque
# usa a mensagem escrita pelo próprio gatilho, e o 23502 porque leva o nome do campo.
_TRADUCAO = {
    "23505": (status.HTTP_409_CONFLICT, "duplicado", "Já existe um registro com esses dados."),
    "23503": (
        status.HTTP_422_UNPROCESSABLE_ENTITY,
        "referencia_invalida",
        "Um dos valores informados não existe ou não pode ser usado.",
    ),
    "42501": (
        status.HTTP_403_FORBIDDEN,
        "sem_permissao",
        "Você não tem permissão para esta operação.",
    ),
}


def _traduzir(causa):
    """Devolve (status, codigo, mensagem) para o erro do PostgreSQL, ou None se
    ele não for um dos que a API traduz."""
    sqlstate = getattr(causa, "sqlstate", None)
    if not sqlstate:
        return None
    diag = getattr(causa, "diag", None)

    if sqlstate == "23514":
        # O mesmo sqlstate vem de dois lugares. Violação de CHECK de tabela traz o
        # nome da constraint (constraint_name) e a mensagem do banco é técnica
        # ("violates check constraint ck_..."), então não vai para o usuário. Já
        # o erro lançado por um gatilho (raise exception) não tem constraint_name
        # e a mensagem foi escrita para ser lida, então ela segue como está.
        if getattr(diag, "constraint_name", None):
            mensagem = "Os dados informados não atendem às regras do sistema."
        else:
            mensagem = getattr(diag, "message_primary", None) or str(causa)
        return status.HTTP_422_UNPROCESSABLE_ENTITY, "regra_de_negocio", mensagem

    if sqlstate == "23502":
        mensagem = "Campo obrigatório não informado."
        coluna = getattr(diag, "column_name", None)
        if coluna:
            mensagem = f"Campo obrigatório não informado: {coluna}."
        return status.HTTP_422_UNPROCESSABLE_ENTITY, "campo_obrigatorio", mensagem

    if sqlstate in _TRADUCAO:
        return _TRADUCAO[sqlstate]

    # Classe 22 do PostgreSQL: valor com formato ou tamanho inválido.
    if sqlstate.startswith("22"):
        return status.HTTP_400_BAD_REQUEST, "valor_invalido", "Um dos valores informados é inválido."

    return None


def tratar_excecao(exc, contexto):
    """EXCEPTION_HANDLER do DRF. Traduz erros do PostgreSQL em respostas HTTP
    com o corpo {"codigo": ..., "detail": ...}. O resto segue o caminho normal."""
    # Erros que o próprio DRF já sabe tratar (validação, 404, 401 etc.).
    resposta = exception_handler(exc, contexto)
    if resposta is not None:
        return resposta

    # Só erros de banco que vieram do PostgreSQL com um sqlstate conhecido.
    if not isinstance(exc, ErroDeBanco):
        return None
    traduzido = _traduzir(exc.__cause__)
    if traduzido is None:
        return None

    # Com ATOMIC_REQUESTS a transação da requisição ficou abortada pelo erro.
    # Marcar o rollback evita que o Django tente confirmá-la.
    set_rollback()

    codigo_http, codigo, mensagem = traduzido
    return Response({"codigo": codigo, "detail": mensagem}, status=codigo_http)
