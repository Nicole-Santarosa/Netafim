import os
import subprocess
import sys
from pathlib import Path

# Pasta backend/, onde o pacote config pode ser importado.
PASTA_BACKEND = Path(__file__).resolve().parent.parent


def test_trava_recusa_host_remoto():
    # A trava roda na hora em que o settings_teste é importado. Por isso o teste
    # importa o módulo em um processo separado, com um host remoto no ambiente,
    # e espera que esse processo morra com o erro de configuração.
    ambiente = {**os.environ, "TESTE_DB_HOST": "db.exemplo.com"}
    resultado = subprocess.run(
        [sys.executable, "-c", "import config.settings_teste"],
        cwd=PASTA_BACKEND,
        env=ambiente,
        capture_output=True,
        text=True,
    )

    assert resultado.returncode != 0
    assert "ImproperlyConfigured" in resultado.stderr
    assert "db.exemplo.com" in resultado.stderr
