"""Testes unitários: não usam banco de dados, então não precisam do PostgreSQL do conftest pai."""

import pytest


@pytest.fixture(autouse=True)
def banco():
    """Sobrescreve a fixture do conftest pai, que cria um banco por teste."""
    yield
