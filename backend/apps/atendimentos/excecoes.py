from rest_framework import status
from rest_framework.exceptions import APIException


class ConflitoDeVersao(APIException):
    """A versão enviada pelo aplicativo não é a que está no banco: alguém
    alterou o atendimento antes. Devolve a versão atual para o aplicativo
    buscar os dados novos e tentar de novo."""

    status_code = status.HTTP_409_CONFLICT

    def __init__(self, versao_atual):
        super().__init__()
        # O corpo segue o formato do tradutor de erros (codigo e detail). É
        # atribuído direto, e não pelo construtor, porque o construtor do DRF
        # converte todo valor em texto e a versão precisa continuar número.
        self.detail = {
            "codigo": "conflito_de_versao",
            "detail": "O atendimento foi alterado por outra pessoa. Atualize e tente de novo.",
            "versao_atual": versao_atual,
        }


class SemPermissaoParaAlterar(APIException):
    """O atendimento é visível e a versão confere, mas o banco (RLS de UPDATE)
    não deixou alterar."""

    status_code = status.HTTP_403_FORBIDDEN

    def __init__(self):
        super().__init__()
        self.detail = {
            "codigo": "sem_permissao",
            "detail": "Você não tem permissão para alterar este atendimento.",
        }
