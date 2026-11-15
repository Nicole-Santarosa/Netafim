"""
Teste de fumaça da API de acesso, SÓ DE LEITURA: não cria nem altera dados.

Serve para conferir, contra a API ligada ao banco de verdade (Supabase), que a conexão como
app_django, o RLS, o ator por transação e as funções de token funcionam juntos. Use uma conta
Netafim Admin ativa.

    API_URL=http://localhost:8000/api/v1 SMOKE_EMAIL=admin@empresa.com SMOKE_SENHA=... \
        uv run python scripts/smoke_api.py

A senha vem só de variável de ambiente (nunca de argumento de linha de comando) e não é impressa.
Sai com código 0 quando tudo passa e 1 quando algum passo falha.
"""

import json
import os
import sys
import urllib.error
import urllib.request
import uuid

URL = os.environ.get("API_URL", "http://localhost:8000/api/v1").rstrip("/")
EMAIL = os.environ.get("SMOKE_EMAIL", "")
SENHA = os.environ.get("SMOKE_SENHA", "")
resultados: list[bool] = []


def chamar(metodo: str, caminho: str, corpo=None, token: str | None = None):
    cab = {"Content-Type": "application/json"}
    if token:
        cab["Authorization"] = f"Bearer {token}"
    dados = json.dumps(corpo).encode() if corpo is not None else None
    req = urllib.request.Request(URL + caminho, data=dados, headers=cab, method=metodo)  # noqa: S310
    try:
        with urllib.request.urlopen(req, timeout=30) as resposta:  # noqa: S310
            texto = resposta.read().decode()
            return resposta.status, (json.loads(texto) if texto else None)
    except urllib.error.HTTPError as erro:
        texto = erro.read().decode()
        try:
            return erro.code, json.loads(texto)
        except ValueError:
            return erro.code, texto[:200]


def passo(nome: str, obtido, esperado, detalhe: str = "") -> None:
    ok = obtido == esperado
    resultados.append(ok)
    print(f"{'OK   ' if ok else 'FALHA'} {nome}: {obtido} (esperado {esperado}) {detalhe}".rstrip())


def main() -> int:
    if not EMAIL or not SENHA:
        print("Defina SMOKE_EMAIL e SMOKE_SENHA (e, se preciso, API_URL).")
        return 2
    print(f"API: {URL}")

    status, dados = chamar("GET", "/auth/termo")
    passo("API no ar (GET /auth/termo)", status, 200)

    status, dados = chamar(
        "POST",
        "/auth/login",
        {
            "email": "naoexiste@exemplo.com",
            "senha": "x" * 12,
            "dispositivo": {"id_dispositivo": str(uuid.uuid4())},
        },
    )
    passo("login inválido recusado", status, 401)

    status, dados = chamar(
        "POST",
        "/auth/login",
        {
            "email": EMAIL,
            "senha": SENHA,
            "dispositivo": {"id_dispositivo": str(uuid.uuid4()), "modelo": "smoke"},
        },
    )
    passo("login da conta de teste", status, 200, "" if status == 200 else str(dados)[:120])
    if status != 200:
        return 1
    access, refresh = dados["access"], dados["refresh"]
    passo("permissões vêm do banco", bool(dados["usuario"]["permissoes"]), True)

    status, me = chamar("GET", "/me", token=access)
    passo(
        "GET /me (ator por transação + RLS)",
        status,
        200,
        me.get("perfil", {}).get("nome_perfil", ""),
    )
    status, perfis = chamar("GET", "/perfis", token=access)
    passo("GET /perfis", (status, len(perfis)), (200, 6))
    status, _ = chamar("GET", "/usuarios", token=access)
    passo("GET /usuarios", status, 200)
    status, _ = chamar("GET", "/me")
    passo("sem token é recusado", status, 401)

    status, novo = chamar("POST", "/auth/refresh", {"refresh": refresh})
    passo("renovação de sessão (fn_rotacionar_token)", status, 200)
    if status == 200:
        status, _ = chamar(
            "POST", "/auth/logout", {"refresh": novo["refresh"]}, token=novo["access"]
        )
        passo("logout (fn_revogar_token)", status, 204)

    print()
    print(
        "RESULTADO:",
        "tudo certo" if all(resultados) else "HOUVE FALHAS",
        f"({sum(resultados)}/{len(resultados)})",
    )
    return 0 if all(resultados) else 1


if __name__ == "__main__":
    sys.exit(main())
