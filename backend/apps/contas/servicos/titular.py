"""
Direitos do titular de dados (LGPD, art. 18): pedir, acompanhar, exportar e anonimizar.

Quem é o titular registra e acompanha só os próprios pedidos, por funções do banco da migração
010 (``fn_titular_*``), porque as policies de ``solicitacao_titular`` só deixam o Netafim Admin
ler e gravar. Quem atende é o Netafim Admin, pelas policies que já existem. Pedidos de
anonimização e de eliminação executam ``fn_anonimizar_usuario``, que preserva o id (então o
histórico legal dos atendimentos continua íntegro) e revoga aparelhos e tokens.

O prazo de resposta (15 dias, art. 19, II) vem do banco; ``atrasada`` avisa o administrador.
"""

from django.apps import apps as apps_instalados
from django.db import DatabaseError, IntegrityError, ProgrammingError, connection, transaction
from django.utils import timezone

from ..excecoes import ErroNegocio, SemPermissao
from ..models import Usuario
from . import auditoria, banco, usuarios
from .auditoria import Acao

TIPOS = (
    "confirmacao",
    "acesso",
    "correcao",
    "anonimizacao",
    "eliminacao",
    "portabilidade",
    "compartilhamento",
    "revogacao_consentimento",
)
TIPOS_QUE_ANONIMIZAM = frozenset({"anonimizacao", "eliminacao"})
ABERTOS = ("recebida", "em_analise")
STATUS = (*ABERTOS, "atendida", "negada")

_COLUNAS_ADMIN = (
    "s.id_solicitacao, s.id_usuario, s.nome_titular, s.email_contato, s.tipo, s.status, "
    "s.observacao, s.data_solicitacao, s.data_prazo, s.data_conclusao, s.id_responsavel, "
    "s.justificativa_negativa, "
    "(s.status in ('recebida', 'em_analise') and s.data_prazo < now()) as atrasada"
)


def _dicts(cursor) -> list[dict]:
    nomes = [coluna[0] for coluna in cursor.description]
    return [dict(zip(nomes, linha, strict=True)) for linha in cursor.fetchall()]


_SQLSTATE_FUNCAO_INEXISTENTE = "42883"


def _indisponivel() -> ErroNegocio:
    return ErroNegocio(
        "Os pedidos de titulares não estão disponíveis neste servidor.",
        "lgpd_indisponivel",
        status_code=503,
    )


def _nao_encontrada() -> ErroNegocio:
    return ErroNegocio("Pedido não encontrado.", "solicitacao_nao_encontrada", status_code=404)


# --------------------------------------------------------------------------- o próprio titular


def minhas_solicitacoes() -> list[dict]:
    try:
        with transaction.atomic(), connection.cursor() as cursor:
            cursor.execute("select * from fn_titular_minhas_solicitacoes()")
            return _dicts(cursor)
    except ProgrammingError as exc:  # a migração 010 ainda não foi aplicada no banco
        if banco.sqlstate(exc) == _SQLSTATE_FUNCAO_INEXISTENTE:
            raise _indisponivel() from exc
        raise


def registrar(usuario: Usuario, tipo: str, observacao: str | None, *, request=None) -> dict:
    if tipo not in TIPOS:
        raise ErroNegocio("Tipo de pedido inválido.", "tipo_invalido")
    try:
        with transaction.atomic(), connection.cursor() as cursor:
            cursor.execute("select fn_titular_registrar_solicitacao(%s, %s)", [tipo, observacao])
            id_solicitacao = cursor.fetchone()[0]
    except ProgrammingError as exc:  # a migração 010 ainda não foi aplicada no banco
        if banco.sqlstate(exc) == _SQLSTATE_FUNCAO_INEXISTENTE:
            raise _indisponivel() from exc
        raise
    except IntegrityError as exc:
        if banco.constraint(exc) == "ux_solic_aberta":
            raise ErroNegocio(
                "Você já tem um pedido deste tipo em andamento. Aguarde a resposta.",
                "solicitacao_em_aberto",
                status_code=409,
            ) from exc
        raise
    except DatabaseError as exc:
        traduzido = banco.traduzir(exc)
        if traduzido is exc:
            raise
        raise traduzido from exc
    auditoria.registrar(Acao.SOLICITACAO_TITULAR, usuario=usuario, request=request, tipo=tipo)
    return next(p for p in minhas_solicitacoes() if p["id_solicitacao"] == id_solicitacao)


def _secao_atendimentos(usuario: Usuario) -> dict | None:
    """Seção "atendimentos" da exportação, montada pelo módulo de atendimentos.

    Devolve ``None`` enquanto o app ``apps.atendimentos`` não está instalado nesta branch, para a
    exportação seguir funcionando só com os dados da conta. Quando o app estiver instalado, a
    função dele roda na mesma transação e com o ator da requisição já definido, então o RLS vale
    para a sessão: quem exporta pelo próprio login recebe só o que o banco deixa ele ver.
    """
    if not apps_instalados.is_installed("apps.atendimentos"):
        return None
    from apps.atendimentos.exportacao import exportar_atendimentos  # evita ciclo e dependência

    return exportar_atendimentos(usuario)


def exportar_dados(usuario: Usuario, *, request=None) -> dict:
    """Cópia dos dados pessoais que ESTE módulo guarda do titular (acesso e portabilidade).

    Não inclui hash de senha, segredo de MFA nem tokens. A seção ``atendimentos`` vem do módulo de
    atendimentos (``_secao_atendimentos``) e só aparece quando esse módulo está instalado.
    """
    from ..api import serializers as s  # evita ciclo: o serializer importa serviços

    try:
        solicitacoes = minhas_solicitacoes()
    except ErroNegocio as exc:
        # Sem a migração 010 o titular ainda pode exportar o que o módulo guarda dele.
        if exc.codigo != "lgpd_indisponivel":
            raise
        solicitacoes = []
    dados = {
        "formato": 1,
        "exportado_em": timezone.now(),
        "conta": s.MeSerializer(usuario).data,
        "consentimento": {
            "aceito": usuario.consentimento_lgpd,
            "data": usuario.data_consentimento,
            "versao_termo": usuario.versao_termo_consentimento,
            "data_revogacao": usuario.data_revogacao_consentimento,
        },
        "exclusao_solicitada_em": usuario.data_exclusao_solicitada,
        "dispositivos": s.DispositivoSerializer(usuario.dispositivos.all(), many=True).data,
        "solicitacoes_lgpd": solicitacoes,
    }
    # Antes da auditoria: se a seção falhar, a exportação não foi feita e nada é registrado.
    atendimentos = _secao_atendimentos(usuario)
    if atendimentos is not None:
        dados["atendimentos"] = atendimentos
    with connection.cursor() as cursor:
        cursor.execute(
            "select fn_audit('exportacao_dados', 'sucesso', 'usuario', %s)", [str(usuario.pk)]
        )
    auditoria.registrar(Acao.EXPORTACAO_DADOS, usuario=usuario, request=request)
    return dados


# --------------------------------------------------------------------------- o administrador


def _exigir_admin(ator: Usuario) -> None:
    if not ator.eh_netafim_admin:
        raise SemPermissao("Somente o Administrador Netafim atende pedidos de titulares.")


def listar(ator: Usuario, status: str | None = None) -> list[dict]:
    _exigir_admin(ator)
    if status is not None and status not in STATUS:
        raise ErroNegocio("Situação inválida.", "status_invalido")
    with connection.cursor() as cursor:
        cursor.execute(
            f"select {_COLUNAS_ADMIN} from solicitacao_titular s "  # noqa: S608
            "where (%s::text is null or s.status = %s) order by s.data_prazo, s.id_solicitacao",
            [status, status],
        )
        return _dicts(cursor)


def _carregar(id_solicitacao: int, *, travar: bool = False) -> dict:
    with connection.cursor() as cursor:
        cursor.execute(
            f"select {_COLUNAS_ADMIN} from solicitacao_titular s "  # noqa: S608
            f"where s.id_solicitacao = %s{' for update' if travar else ''}",
            [id_solicitacao],
        )
        linhas = _dicts(cursor)
    if not linhas:
        raise _nao_encontrada()
    return linhas[0]


def _exigir_aberta(pedido: dict) -> None:
    if pedido["status"] not in ABERTOS:
        raise ErroNegocio("Este pedido já foi concluído.", "solicitacao_encerrada", status_code=409)


def analisar(ator: Usuario, id_solicitacao: int, *, request=None) -> dict:
    _exigir_admin(ator)
    with transaction.atomic():
        pedido = _carregar(id_solicitacao, travar=True)
        _exigir_aberta(pedido)
        with connection.cursor() as cursor:
            cursor.execute(
                "update solicitacao_titular set status = 'em_analise', id_responsavel = %s "
                "where id_solicitacao = %s",
                [ator.pk, id_solicitacao],
            )
    auditoria.registrar(
        Acao.SOLICITACAO_TITULAR, usuario=ator, request=request, andamento="em_analise"
    )
    return _carregar(id_solicitacao)


def atender(ator: Usuario, id_solicitacao: int, *, request=None) -> dict:
    """Conclui o pedido. Anonimização e eliminação executam a anonimização do titular."""
    _exigir_admin(ator)
    with transaction.atomic():
        pedido = _carregar(id_solicitacao, travar=True)
        _exigir_aberta(pedido)
        anonimizou = pedido["tipo"] in TIPOS_QUE_ANONIMIZAM
        if anonimizou:
            _anonimizar_titular(ator, pedido)
        with connection.cursor() as cursor:
            cursor.execute(
                "update solicitacao_titular set status = 'atendida', data_conclusao = now(), "
                "id_responsavel = %s where id_solicitacao = %s",
                [ator.pk, id_solicitacao],
            )
    auditoria.registrar(
        Acao.SOLICITACAO_TITULAR,
        usuario=ator,
        request=request,
        andamento="atendida",
        tipo=pedido["tipo"],
        anonimizou=anonimizou,
    )
    return _carregar(id_solicitacao)


def _anonimizar_titular(ator: Usuario, pedido: dict) -> None:
    id_titular = pedido["id_usuario"]
    if id_titular is None:
        raise ErroNegocio("O titular deste pedido não existe mais.", "titular_inexistente")
    if id_titular == ator.pk:
        # Separação de funções: quem apaga uma conta não é a própria pessoa.
        raise ErroNegocio(
            "Um administrador não atende o próprio pedido de anonimização. Peça a outro "
            "administrador.",
            "proprio_pedido",
            status_code=403,
        )
    titular = Usuario.objects.filter(pk=id_titular).first()
    if titular is not None and usuarios._ultimo_netafim_admin(titular):
        raise ErroNegocio(
            "Não é possível anonimizar o último Administrador Netafim ativo.", "ultimo_admin"
        )
    with connection.cursor() as cursor:
        cursor.execute("select fn_anonimizar_usuario(%s)", [id_titular])
        # O pedido é o registro legal de que o direito foi exercido e cumprido: fica, mas sem
        # nome nem e-mail de quem já foi anonimizado.
        cursor.execute(
            "update solicitacao_titular set nome_titular = null, email_contato = %s "
            "where id_usuario = %s",
            [f"anonimizado-{id_titular}@anonimizado.invalid", id_titular],
        )


def negar(ator: Usuario, id_solicitacao: int, justificativa: str, *, request=None) -> dict:
    _exigir_admin(ator)
    justificativa = (justificativa or "").strip()
    if not justificativa:
        raise ErroNegocio("Informe o motivo da negativa.", "justificativa_obrigatoria")
    with transaction.atomic():
        pedido = _carregar(id_solicitacao, travar=True)
        _exigir_aberta(pedido)
        with connection.cursor() as cursor:
            cursor.execute(
                "update solicitacao_titular set status = 'negada', data_conclusao = now(), "
                "id_responsavel = %s, justificativa_negativa = %s where id_solicitacao = %s",
                [ator.pk, justificativa, id_solicitacao],
            )
    auditoria.registrar(
        Acao.SOLICITACAO_TITULAR,
        usuario=ator,
        request=request,
        andamento="negada",
        tipo=pedido["tipo"],
    )
    return _carregar(id_solicitacao)
