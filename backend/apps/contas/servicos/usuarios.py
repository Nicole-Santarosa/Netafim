"""
Ciclo de vida do usuário: solicitação de cadastro, convite, aprovação, recusa,
perfil, situação, edição e exclusão lógica.

As regras de quem pode o quê estão em DUAS camadas: aqui (mensagens claras e códigos estáveis para o
app) e no banco (RLS e gatilhos), que valem mesmo se esta camada falhar. Toda gravação usa
``update_fields`` para nunca reenviar colunas que os gatilhos do banco protegem (perfil,
organização, contadores de login, senha de terceiros).
"""

from dataclasses import dataclass

from django.conf import settings
from django.db import transaction
from django.utils import timezone

from .. import contexto
from ..constantes import (
    NivelPerfil,
    Permissao,
    SituacaoUsuario,
    StatusOrganizacao,
    TipoOrganizacao,
    TipoToken,
)
from ..escopo import pode_gerenciar
from ..excecoes import ErroNegocio, SemPermissao
from ..models import Cliente, Distribuidor, DominioEmailCorporativo, Perfil, Usuario
from . import auditoria, banco, notificacoes, tokens
from .acesso import definir_senha_por_token, validar_senha
from .auditoria import Acao, Resultado

# --------------------------------------------------------------------------- utilitários


@dataclass(frozen=True)
class Organizacao:
    tipo: str
    distribuidor: Distribuidor | None = None
    cliente: Cliente | None = None


def resolver_organizacao(
    tipo: str | None, id_distribuidor: int | None, id_cliente: int | None
) -> Organizacao:
    if tipo == TipoOrganizacao.NETAFIM:
        if id_distribuidor or id_cliente:
            raise ErroNegocio(
                "Usuário da Netafim não pertence a distribuidor nem cliente.",
                "organizacao_incoerente",
            )
        return Organizacao(tipo)
    if tipo == TipoOrganizacao.DISTRIBUIDOR:
        if not id_distribuidor or id_cliente:
            raise ErroNegocio(
                "Informe somente o distribuidor do usuário.", "organizacao_incoerente"
            )
        distribuidor = Distribuidor.objects.filter(pk=id_distribuidor).first()
        if distribuidor is None or distribuidor.status != StatusOrganizacao.ATIVO:
            raise ErroNegocio("Distribuidor não encontrado ou inativo.", "organizacao_invalida")
        return Organizacao(tipo, distribuidor=distribuidor)
    if tipo == TipoOrganizacao.CLIENTE:
        if not id_cliente or id_distribuidor:
            raise ErroNegocio("Informe somente o cliente do usuário.", "organizacao_incoerente")
        cliente = Cliente.objects.filter(pk=id_cliente).first()
        if cliente is None or cliente.status != StatusOrganizacao.ATIVO:
            raise ErroNegocio("Cliente não encontrado ou inativo.", "organizacao_invalida")
        return Organizacao(tipo, cliente=cliente)
    raise ErroNegocio("Defina o tipo de organização do usuário.", "organizacao_obrigatoria")


def resolver_perfil(id_perfil: int | None, tipo_organizacao: str) -> Perfil:
    perfil = Perfil.objects.filter(pk=id_perfil).first() if id_perfil else None
    if perfil is None:
        raise ErroNegocio("Perfil não encontrado.", "perfil_invalido")
    if perfil.tipo_organizacao != tipo_organizacao:
        raise ErroNegocio(
            "O perfil precisa ser do mesmo tipo de organização do usuário.", "perfil_incompativel"
        )
    return perfil


def dominio_autorizado(email: str, organizacao: Organizacao) -> bool:
    """Netafim e distribuidores só com e-mail corporativo; cliente final não usa a lista."""
    if organizacao.tipo == TipoOrganizacao.CLIENTE:
        return True
    filtros = {
        "dominio": email.rsplit("@", 1)[-1].lower(),
        "ativo": True,
        "tipo_organizacao": organizacao.tipo,
    }
    if organizacao.tipo == TipoOrganizacao.DISTRIBUIDOR:
        filtros["distribuidor"] = organizacao.distribuidor
    return DominioEmailCorporativo.objects.filter(**filtros).exists()


def _exigir_dominio(email: str, organizacao: Organizacao) -> None:
    if not dominio_autorizado(email, organizacao):
        raise ErroNegocio(
            "O domínio do e-mail não está autorizado para esta organização. Cadastre o "
            "domínio corporativo antes de aprovar.",
            "dominio_nao_autorizado",
        )


def _exigir(ator: Usuario, codigo: str) -> None:
    if not ator.tem_permissao(codigo):
        raise SemPermissao()


def _exigir_gestao(ator: Usuario, alvo: Usuario) -> None:
    if not pode_gerenciar(ator, alvo):
        raise SemPermissao("Você não pode gerenciar este usuário.")


def _ultimo_netafim_admin(alvo: Usuario) -> bool:
    if not alvo.eh_netafim_admin or alvo.situacao != SituacaoUsuario.ATIVO:
        return False
    return (
        not Usuario.objects.filter(
            situacao=SituacaoUsuario.ATIVO,
            tipo_organizacao=TipoOrganizacao.NETAFIM,
            perfil__nivel=NivelPerfil.ADMIN,
        )
        .exclude(pk=alvo.pk)
        .exists()
    )


def _registrar_consentimento(usuario: Usuario, versao_termo: str) -> None:
    if versao_termo != settings.CONTAS["VERSAO_TERMO_VIGENTE"]:
        raise ErroNegocio(
            "Aceite a versão vigente do aviso de privacidade.",
            "termo_desatualizado",
            versao_vigente=settings.CONTAS["VERSAO_TERMO_VIGENTE"],
        )
    usuario.consentimento_lgpd = True
    usuario.data_consentimento = timezone.now()
    usuario.versao_termo_consentimento = versao_termo
    usuario.data_revogacao_consentimento = None


CAMPOS_CONSENTIMENTO = [
    "consentimento_lgpd",
    "data_consentimento",
    "versao_termo_consentimento",
    "data_revogacao_consentimento",
    "data_atualizacao",
]

# --------------------------------------------------------------------------- cadastro


def solicitar_cadastro(
    *, nome: str, email: str, senha: str, telefone: str | None, versao_termo: str, request=None
) -> Usuario | None:
    """
    Autocadastro pelo aplicativo: nasce pendente, sem perfil nem organização.
    Se o e-mail já existe, não faz nada e a API responde igual, para não revelar
    quais e-mails estão cadastrados.
    """
    email = email.strip().lower()
    with transaction.atomic():
        contexto.limpar()
        # Sem ator ainda: o RLS só deixa ver (e devolver no INSERT) a linha deste e-mail.
        contexto.definir_email_login(email)
        if Usuario.objects.filter(email__iexact=email).exists():
            auditoria.registrar(
                Acao.CADASTRO_SOLICITADO,
                Resultado.NEGADO,
                request=request,
                motivo="email_existente",
            )
            return None

        usuario = Usuario(
            nome=nome.strip(),
            email=email,
            telefone=telefone or None,
            situacao=SituacaoUsuario.PENDENTE,
        )
        validar_senha(senha, usuario, campo="senha")
        usuario.set_password(senha)
        _registrar_consentimento(usuario, versao_termo)
        try:
            banco.salvar(usuario)
        except ErroNegocio as exc:
            if exc.codigo == "email_em_uso":  # corrida entre duas solicitações iguais
                return None
            raise
    auditoria.registrar(
        Acao.CADASTRO_SOLICITADO, usuario=usuario, request=request, entidade=usuario
    )
    return usuario


@transaction.atomic
def criar_por_admin(
    ator: Usuario,
    *,
    nome: str,
    email: str,
    telefone: str | None = None,
    tipo_organizacao: str | None = None,
    id_distribuidor: int | None = None,
    id_cliente: int | None = None,
    id_perfil: int | None = None,
    request=None,
) -> Usuario:
    """
    Netafim Admin cria qualquer usuário, que já nasce aprovado. Admin de distribuidor ou de cliente
    cria usuários de nível Usuário da própria organização, que ficam pendentes até a validação do
    Netafim Admin. Em ambos os casos o usuário recebe um convite por e-mail para definir a senha.

    O banco só emite convite para usuário pendente (``fn_emitir_token``); por isso o usuário é
    criado pendente, o convite é emitido e, quando quem cria é o Netafim Admin, a aprovação vem logo
    em seguida.
    """
    _exigir(ator, Permissao.CRIAR_USUARIO)
    email = email.strip().lower()
    if Usuario.objects.filter(email__iexact=email).exists():
        raise ErroNegocio("Já existe um usuário com este e-mail.", "email_em_uso")

    if ator.eh_netafim_admin:
        organizacao = resolver_organizacao(tipo_organizacao, id_distribuidor, id_cliente)
        perfil = resolver_perfil(id_perfil, organizacao.tipo)
        _exigir_dominio(email, organizacao)
    else:
        organizacao = Organizacao(
            ator.tipo_organizacao, distribuidor=ator.distribuidor, cliente=ator.cliente
        )
        perfil = Perfil.objects.get(
            tipo_organizacao=ator.tipo_organizacao, nivel=NivelPerfil.USUARIO
        )

    usuario = Usuario(
        nome=nome.strip(),
        email=email,
        telefone=telefone or None,
        situacao=SituacaoUsuario.PENDENTE,
        tipo_organizacao=organizacao.tipo,
        distribuidor=organizacao.distribuidor,
        cliente=organizacao.cliente,
        perfil=perfil,
    )
    usuario.set_unusable_password()
    banco.salvar(usuario)

    token = tokens.emitir(usuario.pk, TipoToken.CONVITE, ip=auditoria.ip_da_requisicao(request))
    transaction.on_commit(lambda: notificacoes.convite(usuario, token))
    auditoria.registrar(
        Acao.CADASTRO_SOLICITADO, usuario=ator, request=request, entidade=usuario, via="convite"
    )

    if ator.eh_netafim_admin:
        usuario.situacao = SituacaoUsuario.ATIVO
        usuario.aprovado_por = ator
        usuario.data_aprovacao = timezone.now()
        banco.salvar(
            usuario,
            update_fields=["situacao", "aprovado_por", "data_aprovacao", "data_atualizacao"],
        )
        auditoria.registrar(Acao.CADASTRO_APROVADO, usuario=ator, request=request, entidade=usuario)
    return usuario


def aceitar_convite(token: str, senha: str, versao_termo: str, *, request=None) -> Usuario:
    with transaction.atomic():
        usuario = definir_senha_por_token(token, senha, TipoToken.CONVITE, request=request)
        # O dono do convite segue como ator da transação (definido em definir_senha_por_token).
        _registrar_consentimento(usuario, versao_termo)
        banco.salvar(usuario, update_fields=CAMPOS_CONSENTIMENTO)
    return usuario


def reenviar_convite(ator: Usuario, alvo: Usuario, *, request=None) -> None:
    """
    Reenvia o link para definir a senha. Pendente recebe um novo convite (14 dias); quem já foi
    aprovado recebe o link de redefinição (2 horas), porque o banco só emite convite para usuário
    pendente.
    """
    _exigir(ator, Permissao.CRIAR_USUARIO)
    _exigir_gestao(ator, alvo)
    if alvo.has_usable_password():
        raise ErroNegocio("Este usuário já definiu a senha.", "convite_ja_aceito")
    ip = auditoria.ip_da_requisicao(request)
    if alvo.situacao == SituacaoUsuario.PENDENTE:
        token = tokens.emitir(alvo.pk, TipoToken.CONVITE, ip=ip)
        transaction.on_commit(lambda: notificacoes.convite(alvo, token))
    elif alvo.situacao == SituacaoUsuario.ATIVO:
        token = tokens.emitir(alvo.pk, TipoToken.RESET_SENHA, ip=ip)
        transaction.on_commit(lambda: notificacoes.redefinicao_senha(alvo, token))
    else:
        raise ErroNegocio("Este usuário não pode receber convite.", "convite_indisponivel")


# --------------------------------------------------------------------------- aprovação


@transaction.atomic
def aprovar(
    ator: Usuario,
    alvo: Usuario,
    *,
    tipo_organizacao: str | None = None,
    id_distribuidor: int | None = None,
    id_cliente: int | None = None,
    id_perfil: int | None = None,
    request=None,
) -> Usuario:
    """
    Fluxo de cadastro (Regras de Negócio): o Netafim Admin valida os dados, define a
    organização e o perfil, e libera o usuário. Campos omitidos mantêm o que o
    convite já definiu.
    """
    _exigir(ator, Permissao.APROVAR_CADASTRO)
    alvo = Usuario.objects.select_for_update(of=("self",)).get(pk=alvo.pk)
    if alvo.situacao != SituacaoUsuario.PENDENTE:
        raise ErroNegocio("Somente cadastros pendentes podem ser aprovados.", "nao_pendente")

    tipo = tipo_organizacao or alvo.tipo_organizacao
    if tipo_organizacao is None:
        id_distribuidor, id_cliente = alvo.distribuidor_id, alvo.cliente_id
    organizacao = resolver_organizacao(tipo, id_distribuidor, id_cliente)
    perfil = resolver_perfil(id_perfil or alvo.perfil_id, organizacao.tipo)
    _exigir_dominio(alvo.email, organizacao)

    alvo.tipo_organizacao = organizacao.tipo
    alvo.distribuidor = organizacao.distribuidor
    alvo.cliente = organizacao.cliente
    alvo.perfil = perfil
    alvo.situacao = SituacaoUsuario.ATIVO
    alvo.aprovado_por = ator
    alvo.data_aprovacao = timezone.now()
    banco.salvar(
        alvo,
        update_fields=[
            "tipo_organizacao",
            "distribuidor",
            "cliente",
            "perfil",
            "situacao",
            "aprovado_por",
            "data_aprovacao",
            "data_atualizacao",
        ],
    )

    transaction.on_commit(lambda: notificacoes.cadastro_aprovado(alvo))
    auditoria.registrar(
        Acao.CADASTRO_APROVADO,
        usuario=ator,
        request=request,
        entidade=alvo,
        perfil=perfil.nome_perfil,
    )
    return alvo


@transaction.atomic
def recusar(ator: Usuario, alvo: Usuario, *, request=None) -> Usuario:
    _exigir(ator, Permissao.APROVAR_CADASTRO)
    alvo = Usuario.objects.select_for_update(of=("self",)).get(pk=alvo.pk)
    if alvo.situacao != SituacaoUsuario.PENDENTE:
        raise ErroNegocio("Somente cadastros pendentes podem ser recusados.", "nao_pendente")
    alvo.situacao = SituacaoUsuario.RECUSADO
    alvo.aprovado_por = ator
    alvo.data_aprovacao = timezone.now()
    banco.salvar(
        alvo, update_fields=["situacao", "aprovado_por", "data_aprovacao", "data_atualizacao"]
    )
    transaction.on_commit(lambda: notificacoes.cadastro_recusado(alvo))
    auditoria.registrar(Acao.CADASTRO_RECUSADO, usuario=ator, request=request, entidade=alvo)
    return alvo


# --------------------------------------------------------------------------- gestão

TRANSICOES_SITUACAO = {
    SituacaoUsuario.ATIVO: {SituacaoUsuario.INATIVO, SituacaoUsuario.BLOQUEADO},
    SituacaoUsuario.INATIVO: {SituacaoUsuario.ATIVO},
    SituacaoUsuario.BLOQUEADO: {SituacaoUsuario.ATIVO},
}


@transaction.atomic
def alterar_situacao(ator: Usuario, alvo: Usuario, nova: str, *, request=None) -> Usuario:
    """Ativar e desativar (RF16). Bloquear e desbloquear é exclusivo do Netafim Admin."""
    _exigir(ator, Permissao.EDITAR_USUARIO)
    _exigir_gestao(ator, alvo)
    alvo = Usuario.objects.select_for_update(of=("self",)).select_related("perfil").get(pk=alvo.pk)
    atual = alvo.situacao
    if nova not in TRANSICOES_SITUACAO.get(atual, set()):
        raise ErroNegocio(
            f"Não é possível passar de '{atual}' para '{nova}'.", "transicao_invalida"
        )
    if SituacaoUsuario.BLOQUEADO in (atual, nova) and not ator.eh_netafim_admin:
        raise SemPermissao("Somente o Administrador Netafim bloqueia ou desbloqueia contas.")
    if nova != SituacaoUsuario.ATIVO and _ultimo_netafim_admin(alvo):
        raise ErroNegocio(
            "Não é possível desativar o último Administrador Netafim ativo.", "ultimo_admin"
        )

    alvo.situacao = nova
    campos = ["situacao", "data_atualizacao"]
    # Zerar o contador de login é do Netafim Admin; os demais não podem mexer nele (gatilho do
    # banco).
    if nova == SituacaoUsuario.ATIVO and ator.eh_netafim_admin:
        alvo.falhas_login = 0
        alvo.bloqueado_ate = None
        campos += ["falhas_login", "bloqueado_ate"]
    banco.salvar(alvo, update_fields=campos)  # o banco revoga os tokens ao inativar ou bloquear
    auditoria.registrar(
        Acao.SITUACAO_ALTERADA, usuario=ator, request=request, entidade=alvo, de=atual, para=nova
    )
    return alvo


@transaction.atomic
def definir_perfil(ator: Usuario, alvo: Usuario, id_perfil: int, *, request=None) -> Usuario:
    _exigir(ator, Permissao.DEFINIR_PERFIL)
    alvo = Usuario.objects.select_for_update(of=("self",)).select_related("perfil").get(pk=alvo.pk)
    if alvo.tipo_organizacao is None:
        raise ErroNegocio(
            "Aprove o cadastro definindo a organização antes do perfil.", "organizacao_obrigatoria"
        )
    perfil = resolver_perfil(id_perfil, alvo.tipo_organizacao)
    if perfil.nivel != NivelPerfil.ADMIN and _ultimo_netafim_admin(alvo):
        raise ErroNegocio(
            "Não é possível rebaixar o último Administrador Netafim ativo.", "ultimo_admin"
        )
    anterior = alvo.perfil.nome_perfil if alvo.perfil_id else None
    alvo.perfil = perfil
    banco.salvar(alvo, update_fields=["perfil", "data_atualizacao"])
    # As permissões são lidas do banco a cada requisição: valem já na próxima, sem nova sessão.
    auditoria.registrar(
        Acao.PERFIL_ALTERADO,
        usuario=ator,
        request=request,
        entidade=alvo,
        de=anterior,
        para=perfil.nome_perfil,
    )
    return alvo


def editar(ator: Usuario, alvo: Usuario, *, request=None, **campos) -> Usuario:
    _exigir(ator, Permissao.EDITAR_USUARIO)
    _exigir_gestao(ator, alvo)
    atualizar = ["data_atualizacao"]
    if "nome" in campos:
        alvo.nome = campos["nome"].strip()
        atualizar.append("nome")
    if "telefone" in campos:
        alvo.telefone = campos["telefone"] or None
        atualizar.append("telefone")
    banco.salvar(alvo, update_fields=atualizar)
    return alvo


@transaction.atomic
def excluir(ator: Usuario, alvo: Usuario, *, request=None) -> None:
    """Exclusão lógica: some das listagens e libera o e-mail; os registros ligados ficam."""
    _exigir(ator, Permissao.EXCLUIR_USUARIO)
    _exigir_gestao(ator, alvo)
    alvo = Usuario.objects.select_for_update(of=("self",)).select_related("perfil").get(pk=alvo.pk)
    if _ultimo_netafim_admin(alvo):
        raise ErroNegocio(
            "Não é possível excluir o último Administrador Netafim ativo.", "ultimo_admin"
        )
    situacao_anterior = alvo.situacao
    alvo.deletado_em = timezone.now()
    campos = ["deletado_em", "data_atualizacao"]
    if alvo.situacao == SituacaoUsuario.ATIVO:
        alvo.situacao = SituacaoUsuario.INATIVO
        campos.append("situacao")
    banco.salvar(alvo, update_fields=campos)  # o banco revoga os tokens da conta excluída
    auditoria.registrar(
        Acao.SITUACAO_ALTERADA,
        usuario=ator,
        request=request,
        entidade=alvo,
        de=situacao_anterior,
        para="excluido",
    )
