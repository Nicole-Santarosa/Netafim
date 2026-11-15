"""Cache do limite de tentativas: compartilhado entre processos e sem impedir a API de subir."""

import os

from django.conf import settings

from config import settings as cfg


def test_cache_padrao_e_em_arquivo_para_valer_entre_os_workers():
    assert settings.CACHES["default"]["BACKEND"].endswith("FileBasedCache")


def test_pasta_vem_de_cache_dir(monkeypatch, tmp_path):
    monkeypatch.setenv("CACHE_DIR", str(tmp_path / "ainda" / "nao" / "existe"))
    assert cfg._pasta_do_cache() == str(tmp_path / "ainda" / "nao" / "existe")


def test_nao_cria_a_pasta_na_carga_das_configuracoes(monkeypatch, tmp_path):
    # no build da imagem isto roda como root: criar a pasta aqui deixaria a app sem acesso
    destino = tmp_path / "nova"
    monkeypatch.setenv("CACHE_DIR", str(destino))
    cfg._pasta_do_cache()
    assert not destino.exists()


def test_sem_permissao_de_escrita_usa_o_fallback_em_vez_de_derrubar(monkeypatch, tmp_path):
    monkeypatch.setenv("CACHE_DIR", str(tmp_path))
    monkeypatch.setattr(os, "access", lambda *a, **k: False)
    assert cfg._pasta_do_cache() is None


def test_sem_pasta_temporaria_utilizavel_devolve_none(monkeypatch):
    monkeypatch.delenv("CACHE_DIR", raising=False)

    def sem_tmp():
        raise FileNotFoundError("No usable temporary directory")

    monkeypatch.setattr(cfg.tempfile, "gettempdir", sem_tmp)
    assert cfg._pasta_do_cache() is None
