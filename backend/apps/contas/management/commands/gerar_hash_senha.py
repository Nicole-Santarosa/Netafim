"""
Gera o hash (Argon2) de uma senha para gravar direto no banco.

Serve para o PRIMEIRO Netafim Admin: o RLS do banco exige um administrador como ator, então a API
não consegue criar o primeiro. Rode o comando, copie o hash e aplique no SQL Editor do Supabase:

    update usuario set password = '<hash>' where lower(email) = lower('<e-mail do admin>');

A senha é lida sem eco (getpass), nunca vai para argumento de linha de comando nem para arquivo, e o
comando não acessa o banco.
"""

from getpass import getpass

from django.contrib.auth.hashers import make_password
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError
from django.core.management.base import BaseCommand, CommandError


class Command(BaseCommand):
    help = "Gera o hash Argon2 de uma senha (lida sem eco) para gravar no banco."

    def handle(self, *args, **options):
        senha = getpass("Senha: ")
        if senha != getpass("Repita a senha: "):
            raise CommandError("As senhas não conferem.")
        try:
            validate_password(senha)
        except ValidationError as exc:
            raise CommandError(" ".join(exc.messages)) from exc
        self.stdout.write(make_password(senha))
