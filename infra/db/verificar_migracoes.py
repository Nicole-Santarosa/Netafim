#!/usr/bin/env python3
"""
Confere a numeração dos scripts de banco em infra/db/base e infra/db/migracoes e calcula checksums.

Regras (DT-011): o nome é NNN_descricao_em_minusculas.sql; o número é único dentro de cada pasta
(a sequência de migracoes/ é compartilhada por todos os integrantes). Não exige que a sequência não
tenha buracos: PRs empilhados e em paralelo chegam à main em ordem diferente da numeração.

Uso:
    python infra/db/verificar_migracoes.py              # confere (código de saída 1 se houver erro)
    python infra/db/verificar_migracoes.py --checksums  # imprime "arquivo  sha256" para o registro

O checksum é o SHA-256 do conteúdo com quebras de linha normalizadas para LF, para que o mesmo arquivo
dê o mesmo valor no Windows e no Linux. Só usa a biblioteca padrão.
"""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent
PASTAS = ("base", "migracoes")
PADRAO = re.compile(r"^(\d{3})_[a-z0-9]+(?:_[a-z0-9]+)*\.sql$")


def arquivos(pasta: str) -> list[Path]:
    caminho = RAIZ / pasta
    return sorted(caminho.glob("*.sql")) if caminho.is_dir() else []


def checksum(caminho: Path) -> str:
    conteudo = caminho.read_bytes().replace(b"\r\n", b"\n")
    return hashlib.sha256(conteudo).hexdigest()


def verificar() -> list[str]:
    erros: list[str] = []
    for pasta in PASTAS:
        vistos: dict[str, str] = {}
        for arquivo in arquivos(pasta):
            casou = PADRAO.match(arquivo.name)
            if not casou:
                erros.append(f"{pasta}/{arquivo.name}: nome fora do padrão NNN_descricao.sql")
                continue
            numero = casou.group(1)
            if numero in vistos:
                erros.append(
                    f"{pasta}/: número {numero} repetido em {vistos[numero]} e {arquivo.name}"
                )
            else:
                vistos[numero] = arquivo.name
    return erros


def main(argv: list[str]) -> int:
    if "--checksums" in argv:
        for pasta in PASTAS:
            for arquivo in arquivos(pasta):
                print(f"{pasta}/{arquivo.name}  {checksum(arquivo)}")
        return 0
    erros = verificar()
    for erro in erros:
        print(f"ERRO: {erro}", file=sys.stderr)
    if not erros:
        total = sum(len(arquivos(p)) for p in PASTAS)
        print(f"OK: {total} script(s) de banco com numeração válida e única por pasta.")
    return 1 if erros else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
