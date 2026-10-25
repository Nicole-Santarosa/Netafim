"""
Modelos do módulo de acesso, conforme o MER/DER (Anexo A.1 e parte do A.5).

Tabelas: PERFIL, PERMISSAO, PERFIL_PERMISSAO, DISTRIBUIDOR, CLIENTE, USUARIO,
DISPOSITIVO, TOKEN_ACESSO e DOMINIO_EMAIL_CORPORATIVO.

As regras que o banco precisa garantir sozinho (seção 5.1 do MER/DER) estão em
CHECK constraints aqui e em gatilhos PostgreSQL na migration 0002.
"""

import uuid
from datetime import timedelta

from django.contrib.auth.base_user import AbstractBaseUser, BaseUserManager
from django.db import models
from django.db.models import F, Q
from django.db.models.functions import Lower
from django.utils import timezone

from .constantes import (
    NivelPerfil,
    SituacaoUsuario,
    StatusOrganizacao,
    TipoOrganizacao,
    TipoToken,
)


class SemExcluidosManager(models.Manager):
    """Esconde registros com exclusão lógica (deletado_em preenchido)."""

    def get_queryset(self):
        return super().get_queryset().filter(deletado_em__isnull=True)


class ComTimestamps(models.Model):
    data_criacao = models.DateTimeField(auto_now_add=True)
    data_atualizacao = models.DateTimeField(auto_now=True)
    deletado_em = models.DateTimeField(null=True, blank=True)

    class Meta:
        abstract = True


class Sincronizavel(models.Model):
    """sync_seq é atribuído por gatilho no banco; o valor enviado pela aplicação é ignorado."""

    sync_seq = models.BigIntegerField(default=0, editable=False)

    class Meta:
        abstract = True


# --------------------------------------------------------------------------- perfis


class Permissao(models.Model):
    id_permissao = models.AutoField(primary_key=True)
    codigo = models.CharField(max_length=50, unique=True)
    descricao = models.CharField(max_length=150)

    class Meta:
        db_table = "permissao"
        ordering = ["codigo"]
        verbose_name = "permissão"
        verbose_name_plural = "permissões"

    def __str__(self) -> str:
        return self.codigo


class Perfil(models.Model):
    id_perfil = models.AutoField(primary_key=True)
    nome_perfil = models.CharField(max_length=50, unique=True)
    tipo_organizacao = models.CharField(max_length=15, choices=TipoOrganizacao.choices)
    nivel = models.CharField(max_length=10, choices=NivelPerfil.choices)
    descricao = models.TextField(null=True, blank=True)
    permissoes = models.ManyToManyField(
        Permissao, through="PerfilPermissao", related_name="perfis", blank=True
    )

    class Meta:
        db_table = "perfil"
        ordering = ["id_perfil"]
        verbose_name = "perfil"
        verbose_name_plural = "perfis"
        constraints = [
            models.UniqueConstraint(
                fields=["tipo_organizacao", "nivel"], name="perfil_tipo_nivel_unico"
            ),
            # Alvo da chave estrangeira composta usuario(id_perfil, tipo_organizacao),
            # que impede dar a um usuário um perfil de outro tipo de organização.
            models.UniqueConstraint(
                fields=["id_perfil", "tipo_organizacao"], name="perfil_id_tipo_unico"
            ),
            models.CheckConstraint(
                condition=Q(tipo_organizacao__in=TipoOrganizacao.values),
                name="perfil_tipo_organizacao_valido",
            ),
            models.CheckConstraint(
                condition=Q(nivel__in=NivelPerfil.values), name="perfil_nivel_valido"
            ),
        ]

    def __str__(self) -> str:
        return self.nome_perfil

    @property
    def eh_admin(self) -> bool:
        return self.nivel == NivelPerfil.ADMIN


class PerfilPermissao(models.Model):
    pk = models.CompositePrimaryKey("perfil_id", "permissao_id")
    perfil = models.ForeignKey(Perfil, on_delete=models.CASCADE, db_column="id_perfil")
    permissao = models.ForeignKey(Permissao, on_delete=models.CASCADE, db_column="id_permissao")

    class Meta:
        db_table = "perfil_permissao"
        verbose_name = "permissão do perfil"
        verbose_name_plural = "permissões dos perfis"

    def __str__(self) -> str:
        return f"{self.perfil_id}:{self.permissao_id}"


# --------------------------------------------------------------------------- organizações


class Distribuidor(ComTimestamps, Sincronizavel):
    id_distribuidor = models.AutoField(primary_key=True)
    nome = models.CharField(max_length=150)
    regiao = models.CharField(max_length=100)
    email = models.EmailField(max_length=150, null=True, blank=True)
    telefone = models.CharField(max_length=20, null=True, blank=True)
    status = models.CharField(
        max_length=20, choices=StatusOrganizacao.choices, default=StatusOrganizacao.ATIVO
    )

    objects = SemExcluidosManager()
    todos = models.Manager()

    class Meta:
        db_table = "distribuidor"
        ordering = ["nome"]
        verbose_name = "distribuidor"
        verbose_name_plural = "distribuidores"
        base_manager_name = "todos"
        constraints = [
            models.CheckConstraint(
                condition=Q(status__in=StatusOrganizacao.values),
                name="distribuidor_status_valido",
            ),
        ]

    def __str__(self) -> str:
        return self.nome


class Cliente(ComTimestamps, Sincronizavel):
    """Cliente final como organização (empresa ou produtor rural)."""

    id_cliente = models.AutoField(primary_key=True)
    nome = models.CharField(max_length=150)
    email = models.EmailField(max_length=150, null=True, blank=True)
    telefone = models.CharField(max_length=20, null=True, blank=True)
    distribuidor = models.ForeignKey(
        Distribuidor,
        on_delete=models.PROTECT,
        null=True,
        blank=True,
        db_column="id_distribuidor",
        related_name="clientes",
        help_text="Distribuidor da carteira, quando houver.",
    )
    status = models.CharField(
        max_length=20, choices=StatusOrganizacao.choices, default=StatusOrganizacao.ATIVO
    )

    objects = SemExcluidosManager()
    todos = models.Manager()

    class Meta:
        db_table = "cliente"
        ordering = ["nome"]
        verbose_name = "cliente"
        verbose_name_plural = "clientes"
        base_manager_name = "todos"
        constraints = [
            models.CheckConstraint(
                condition=Q(status__in=StatusOrganizacao.values), name="cliente_status_valido"
            ),
        ]

    def __str__(self) -> str:
        return self.nome


# --------------------------------------------------------------------------- usuário


class UsuarioManager(BaseUserManager):
    use_in_migrations = True

    def get_queryset(self):
        return super().get_queryset().filter(deletado_em__isnull=True)

    def get_by_natural_key(self, email):
        return self.get(email__iexact=email)

    def create_user(self, email, nome, password=None, **extra):
        if not email:
            raise ValueError("O e-mail é obrigatório.")
        usuario = self.model(email=self.normalize_email(email).lower(), nome=nome, **extra)
        if password:
            usuario.set_password(password)
        else:
            usuario.set_unusable_password()
        usuario.full_clean(exclude=["password"])
        usuario.save(using=self._db)
        return usuario

    def create_superuser(self, email, nome, password=None, **extra):
        """
        Cria um Netafim Admin ativo (comando ``createsuperuser``). No primeiro uso
        também registra o domínio do e-mail como domínio corporativo da Netafim,
        porque só um Netafim Admin pode cadastrar domínios pela API.
        """
        perfil = Perfil.objects.get(
            tipo_organizacao=TipoOrganizacao.NETAFIM, nivel=NivelPerfil.ADMIN
        )
        DominioEmailCorporativo.objects.get_or_create(
            dominio=email.rsplit("@", 1)[-1].strip().lower(),
            defaults={"tipo_organizacao": TipoOrganizacao.NETAFIM},
        )
        agora = timezone.now()
        extra.update(
            situacao=SituacaoUsuario.ATIVO,
            tipo_organizacao=TipoOrganizacao.NETAFIM,
            perfil=perfil,
            data_aprovacao=agora,
        )
        return self.create_user(email, nome, password, **extra)


class Usuario(AbstractBaseUser, ComTimestamps):
    """
    Qualquer pessoa com login. O autocadastro nasce pendente, sem perfil nem
    organização; o Netafim Admin aprova e define os dois (MER/DER, seção 7.1).
    """

    id_usuario = models.AutoField(primary_key=True)
    nome = models.CharField(max_length=150)
    email = models.EmailField(max_length=150)
    telefone = models.CharField(max_length=20, null=True, blank=True)

    situacao = models.CharField(
        max_length=15, choices=SituacaoUsuario.choices, default=SituacaoUsuario.PENDENTE
    )
    falhas_login = models.PositiveSmallIntegerField(default=0)
    bloqueado_ate = models.DateTimeField(null=True, blank=True)
    mfa_ativo = models.BooleanField(default=False)
    senha_alterada_em = models.DateTimeField(null=True, blank=True)
    exige_troca_senha = models.BooleanField(default=False)

    tipo_organizacao = models.CharField(
        max_length=15, choices=TipoOrganizacao.choices, null=True, blank=True
    )
    perfil = models.ForeignKey(
        Perfil,
        on_delete=models.PROTECT,
        null=True,
        blank=True,
        db_column="id_perfil",
        related_name="usuarios",
    )
    distribuidor = models.ForeignKey(
        Distribuidor,
        on_delete=models.PROTECT,
        null=True,
        blank=True,
        db_column="id_distribuidor",
        related_name="usuarios",
    )
    cliente = models.ForeignKey(
        Cliente,
        on_delete=models.PROTECT,
        null=True,
        blank=True,
        db_column="id_cliente",
        related_name="usuarios",
    )
    aprovado_por = models.ForeignKey(
        "self",
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        db_column="id_aprovado_por",
        related_name="+",
    )
    data_aprovacao = models.DateTimeField(null=True, blank=True)

    consentimento_lgpd = models.BooleanField(default=False)
    data_consentimento = models.DateTimeField(null=True, blank=True)
    versao_termo_consentimento = models.CharField(max_length=20, null=True, blank=True)
    data_revogacao_consentimento = models.DateTimeField(null=True, blank=True)
    data_exclusao_solicitada = models.DateTimeField(null=True, blank=True)

    objects = UsuarioManager()
    todos = models.Manager()

    USERNAME_FIELD = "email"
    EMAIL_FIELD = "email"
    REQUIRED_FIELDS = ["nome"]

    class Meta:
        db_table = "usuario"
        ordering = ["nome"]
        verbose_name = "usuário"
        verbose_name_plural = "usuários"
        base_manager_name = "todos"
        constraints = [
            models.UniqueConstraint(
                Lower("email"),
                condition=Q(deletado_em__isnull=True),
                name="usuario_email_unico_ci",
            ),
            models.CheckConstraint(
                condition=Q(situacao__in=SituacaoUsuario.values),
                name="usuario_situacao_valida",
            ),
            # Uma organização por usuário e tipo coerente com a chave preenchida.
            models.CheckConstraint(
                condition=(
                    Q(
                        tipo_organizacao__isnull=True,
                        distribuidor__isnull=True,
                        cliente__isnull=True,
                    )
                    | Q(
                        tipo_organizacao=TipoOrganizacao.NETAFIM,
                        distribuidor__isnull=True,
                        cliente__isnull=True,
                    )
                    | Q(
                        tipo_organizacao=TipoOrganizacao.DISTRIBUIDOR,
                        distribuidor__isnull=False,
                        cliente__isnull=True,
                    )
                    | Q(
                        tipo_organizacao=TipoOrganizacao.CLIENTE,
                        cliente__isnull=False,
                        distribuidor__isnull=True,
                    )
                ),
                name="usuario_organizacao_coerente",
            ),
            # Conta ativa exige perfil e organização definidos na aprovação.
            models.CheckConstraint(
                condition=~Q(situacao=SituacaoUsuario.ATIVO)
                | Q(perfil__isnull=False, tipo_organizacao__isnull=False),
                name="usuario_ativo_exige_perfil",
            ),
            # O banco só aceita hash de senha (algoritmo$...) ou senha inutilizada (!...).
            models.CheckConstraint(
                condition=Q(password__regex=r"^(![A-Za-z0-9]*|[a-z0-9_]+\$.+)$"),
                name="usuario_password_formato_hash",
            ),
            models.CheckConstraint(
                condition=Q(consentimento_lgpd=False)
                | Q(data_consentimento__isnull=False, versao_termo_consentimento__isnull=False),
                name="usuario_consentimento_comprovado",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.nome} <{self.email}>"

    # ----- integração com o Django -------------------------------------------------

    @property
    def is_active(self) -> bool:  # usado pelo Django e pelo simplejwt
        return self.situacao == SituacaoUsuario.ATIVO and self.deletado_em is None

    @property
    def is_staff(self) -> bool:  # acesso ao /admin apenas para Netafim Admin
        return self.eh_netafim_admin

    def has_perm(self, perm, obj=None) -> bool:
        return self.is_active and self.eh_netafim_admin

    def has_module_perms(self, app_label) -> bool:
        return self.is_active and self.eh_netafim_admin

    def set_password(self, raw_password):
        super().set_password(raw_password)
        self.senha_alterada_em = timezone.now()

    def clean(self):
        super().clean()
        self.email = (self.email or "").strip().lower()

    # ----- regras de negócio --------------------------------------------------------

    @property
    def eh_admin(self) -> bool:
        return self.perfil_id is not None and self.perfil.nivel == NivelPerfil.ADMIN

    @property
    def eh_netafim_admin(self) -> bool:
        return self.tipo_organizacao == TipoOrganizacao.NETAFIM and self.eh_admin

    @property
    def esta_bloqueado_temporariamente(self) -> bool:
        return self.bloqueado_ate is not None and self.bloqueado_ate > timezone.now()

    @property
    def id_organizacao(self) -> int | None:
        if self.tipo_organizacao == TipoOrganizacao.DISTRIBUIDOR:
            return self.distribuidor_id
        if self.tipo_organizacao == TipoOrganizacao.CLIENTE:
            return self.cliente_id
        return None

    def mesma_organizacao(self, outro: "Usuario") -> bool:
        return (
            self.tipo_organizacao is not None
            and self.tipo_organizacao == outro.tipo_organizacao
            and self.id_organizacao == outro.id_organizacao
        )

    def codigos_permissao(self) -> frozenset[str]:
        """Permissões do perfil do usuário, com cache por instância."""
        if not self.is_active or self.perfil_id is None:
            return frozenset()
        cache = getattr(self, "_cache_permissoes", None)
        if cache is None:
            cache = frozenset(self.perfil.permissoes.values_list("codigo", flat=True))
            self._cache_permissoes = cache
        return cache

    def tem_permissao(self, codigo: str) -> bool:
        return codigo in self.codigos_permissao()


# --------------------------------------------------------------------------- aparelhos e tokens


class Dispositivo(models.Model):
    """Aparelho Android autorizado de um usuário; o UUID é gerado no próprio aparelho."""

    id_dispositivo = models.UUIDField(primary_key=True)
    usuario = models.ForeignKey(
        Usuario, on_delete=models.PROTECT, db_column="id_usuario", related_name="dispositivos"
    )
    modelo = models.CharField(max_length=80, null=True, blank=True)
    versao_app = models.CharField(max_length=20, null=True, blank=True)
    ultima_sincronizacao = models.DateTimeField(null=True, blank=True)
    revogado_em = models.DateTimeField(null=True, blank=True)
    limpeza_remota_solicitada_em = models.DateTimeField(null=True, blank=True)
    limpeza_remota_confirmada_em = models.DateTimeField(null=True, blank=True)
    data_criacao = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "dispositivo"
        ordering = ["-data_criacao"]
        verbose_name = "dispositivo"
        verbose_name_plural = "dispositivos"

    def __str__(self) -> str:
        return f"{self.modelo or 'Aparelho'} ({self.id_dispositivo})"


class TokenAcesso(models.Model):
    """
    Refresh tokens (com rotação), convites e redefinições de senha. Só o hash
    SHA-256 é gravado. Criação e revogação passam por apps.contas.servicos.tokens.
    """

    LIMITE_VALIDADE = timedelta(days=90)

    id_token = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    usuario = models.ForeignKey(
        Usuario, on_delete=models.PROTECT, db_column="id_usuario", related_name="tokens"
    )
    dispositivo = models.ForeignKey(
        Dispositivo,
        on_delete=models.PROTECT,
        null=True,
        blank=True,
        db_column="id_dispositivo",
        related_name="tokens",
    )
    tipo = models.CharField(max_length=15, choices=TipoToken.choices)
    token_hash = models.CharField(max_length=64, unique=True)
    criado_em = models.DateTimeField(default=timezone.now)
    expira_em = models.DateTimeField()
    usado_em = models.DateTimeField(null=True, blank=True)
    revogado_em = models.DateTimeField(null=True, blank=True)
    substituido_por = models.ForeignKey(
        "self",
        on_delete=models.PROTECT,
        null=True,
        blank=True,
        db_column="substituido_por",
        related_name="+",
    )
    ip = models.GenericIPAddressField(null=True, blank=True)

    class Meta:
        db_table = "token_acesso"
        verbose_name = "token de acesso"
        verbose_name_plural = "tokens de acesso"
        indexes = [models.Index(fields=["usuario", "tipo"], name="token_usuario_tipo_idx")]
        constraints = [
            models.CheckConstraint(
                condition=Q(tipo__in=TipoToken.values), name="token_tipo_valido"
            ),
            models.CheckConstraint(
                condition=Q(token_hash__regex=r"^[0-9a-f]{64}$"), name="token_hash_sha256_hex"
            ),
            models.CheckConstraint(
                condition=Q(expira_em__gt=F("criado_em"))
                & Q(expira_em__lte=F("criado_em") + timedelta(days=90)),
                name="token_validade_maxima_90_dias",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.tipo}:{self.id_token}"

    @property
    def valido(self) -> bool:
        return (
            self.revogado_em is None and self.usado_em is None and self.expira_em > timezone.now()
        )


class DominioEmailCorporativo(models.Model):
    """Domínios aceitos para usuários da Netafim e de cada distribuidor (RNF18)."""

    id_dominio = models.AutoField(primary_key=True)
    dominio = models.CharField(max_length=120, unique=True)
    tipo_organizacao = models.CharField(
        max_length=15,
        choices=[
            (TipoOrganizacao.NETAFIM.value, TipoOrganizacao.NETAFIM.label),
            (TipoOrganizacao.DISTRIBUIDOR.value, TipoOrganizacao.DISTRIBUIDOR.label),
        ],
    )
    distribuidor = models.ForeignKey(
        Distribuidor,
        on_delete=models.PROTECT,
        null=True,
        blank=True,
        db_column="id_distribuidor",
        related_name="dominios",
    )
    ativo = models.BooleanField(default=True)
    data_criacao = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "dominio_email_corporativo"
        ordering = ["dominio"]
        verbose_name = "domínio de e-mail corporativo"
        verbose_name_plural = "domínios de e-mail corporativo"
        constraints = [
            models.CheckConstraint(
                condition=Q(
                    dominio__regex=r"^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$"
                ),
                name="dominio_minusculo_sem_arroba",
            ),
            models.CheckConstraint(
                condition=Q(tipo_organizacao=TipoOrganizacao.NETAFIM, distribuidor__isnull=True)
                | Q(tipo_organizacao=TipoOrganizacao.DISTRIBUIDOR, distribuidor__isnull=False),
                name="dominio_organizacao_coerente",
            ),
        ]

    def __str__(self) -> str:
        return self.dominio

    def save(self, *args, **kwargs):
        self.dominio = (self.dominio or "").strip().lower().lstrip("@")
        super().save(*args, **kwargs)
