"""
Erros do DRF com ``codigo``, no mesmo formato dos erros de negócio do módulo de acesso:
``{"detail": "mensagem", "codigo": "codigo_especifico"}``.

Decisão do grupo (Documentação Técnica, seção 2): o 400 de validação continua no formato
padrão do DRF, sem ``codigo``, de propósito; os erros 401, 403, 404, 405 e 415 passam a incluir
``codigo``. O Android lê ``codigo`` como opcional e cai na mensagem de ``detail`` quando ele
não vem.

O 401 do JWT vinha do simplejwt como ``{"detail", "code", "messages"}``: o ``code`` tinha outro
nome e o ``messages`` expunha a classe do token. Aqui o ``code`` vira um ``codigo`` já conhecido
pelo app e o ``messages`` sai:

- ``token_not_valid`` e ``user_not_found``  ->  ``token_invalido``
- ``user_inactive``                         ->  ``conta_inativa``
- ``device_revoked``                        ->  ``dispositivo_revogado``
- 401 sem token                             ->  ``nao_autenticado``

Este é o ``EXCEPTION_HANDLER`` do acesso. Quando o tratador compartilhado
``apps/core/erros.py`` (que traduz os erros do PostgreSQL) entrar na mesma base, ele deve
chamar ``padronizar_erro`` no lugar de ``rest_framework.views.exception_handler``, para os dois
tratamentos valerem juntos.
"""

from rest_framework.views import exception_handler as tratador_padrao_do_drf

CODIGO_POR_STATUS = {
    401: "nao_autenticado",
    403: "sem_permissao",
    404: "nao_encontrado",
    405: "metodo_nao_permitido",
    415: "tipo_de_midia_nao_suportado",
}

# ``code`` que o simplejwt e o AutenticacaoJWT usam no 401  ->  ``codigo`` da API.
CODIGO_DO_JWT = {
    "token_not_valid": "token_invalido",
    "user_not_found": "token_invalido",
    "user_inactive": "conta_inativa",
    "device_revoked": "dispositivo_revogado",
}


def padronizar_erro(exc, contexto):
    """Trata a exceção como o DRF e acrescenta ``codigo`` nos 401, 403, 404, 405 e 415."""
    resposta = tratador_padrao_do_drf(exc, contexto)
    if resposta is None or not isinstance(resposta.data, dict) or "codigo" in resposta.data:
        return resposta  # sem resposta, 400 em lista ou erro de negócio que já traz o codigo
    if resposta.status_code not in CODIGO_POR_STATUS:
        return resposta  # 400, 429 e demais ficam como o DRF faz

    dados = dict(resposta.data)
    codigo_do_jwt = dados.pop("code", None)
    dados.pop("messages", None)  # detalhe interno do simplejwt (classe e tipo do token)
    if resposta.status_code == 401 and codigo_do_jwt is not None:
        codigo = CODIGO_DO_JWT.get(codigo_do_jwt, "token_invalido")
    else:
        codigo = CODIGO_POR_STATUS[resposta.status_code]
    detalhe = dados.pop("detail", "")
    resposta.data = {"detail": detalhe, "codigo": codigo, **dados}
    return resposta
