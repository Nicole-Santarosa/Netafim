"""
Modelos do módulo de acesso: apenas o MAPEAMENTO das tabelas do banco do grupo.

O esquema (tabelas, constraints, gatilhos, funções e RLS) pertence ao banco, não ao Django:
todos os modelos são ``managed = False`` e o Django nunca cria nem altera essas tabelas.
O retrato do banco usado nos testes está em ``backend/db/snapshot_banco_atual.sql``.

Contrato com o banco (ver ``contexto.py``): a API conecta como ``app_django`` e informa o ator em
cada transação. ``token_acesso`` não tem modelo de propósito: o papel da API não tem privilégio
nela, e os tokens só passam pelas funções ``fn_emitir_token``, ``fn_rotacionar_token`` e
``fn_revogar_token``.
"""

from django.contrib.auth.base_user import AbstractBaseUser, BaseUserManager
from django.db import models
from django.utils import timezone

from .constantes import NivelPerfil, SituacaoUsuario, StatusOrganizacao, TipoOrganizacao


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
    """sync_seq é atribuído pelo gatilho do banco; o valor enviado pela aplicação é ignorado."""

    sync_seq = models.BigIntegerField(default=0, editable=False)

    class Meta:
        abstract = True


# --------------------------------------------------------------------------- perfis


class Permissao(models.Model):
    id_permissao = models.AutoField(primary_key=True)
    codigo = models.CharField(max_length=50, unique=True)
    descricao = models.CharField(max_length=150)

    class Meta:
        managed = False
        db_table = "permissao"
        ordering = ["codigo"]

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
        managed = False
        db_table = "perfil"
        ordering = ["id_perfil"]

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
        managed = False
        db_table = "perfil_permissao"

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
        managed = False
        db_table = "distribuidor"
        ordering = ["nome"]
        base_manager_name = "todos"

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
    )
    status = models.CharField(
        max_length=20, choices=StatusOrganizacao.choices, default=StatusOrganizacao.ATIVO
    )

    objects = SemExcluidosManager()
    todos = models.Manager()

    class Meta:
        managed = False
        db_table = "cliente"
        ordering = ["nome"]
        base_manager_name = "todos"

    def __str__(self) -> str:
        return self.nome


# --------------------------------------------------------------------------- usuário


class UsuarioManager(BaseUserManager):
    """Só usuários não excluídos. A criação passa pela API (RLS) e não por create_user."""

    def get_queryset(self):
        return super().get_queryset().filter(deletado_em__isnull=True)

    def get_by_natural_key(self, email):
        return self.get(email__iexact=email)


class Usuario(AbstractBaseUser):
    """Qualquer pessoa com login. O autocadastro nasce pendente, sem perfil nem organização."""

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

    data_criacao = models.DateTimeField(auto_now_add=True)
    data_atualizacao = models.DateTimeField(auto_now=True)
    deletado_em = models.DateTimeField(null=True, blank=True)

    objects = UsuarioManager()
    todos = models.Manager()

    USERNAME_FIELD = "email"
    EMAIL_FIELD = "email"
    REQUIRED_FIELDS = ["nome"]

    class Meta:
        managed = False
        db_table = "usuario"
        ordering = ["nome"]
        base_manager_name = "todos"

    def __str__(self) -> str:
        return f"{self.nome} <{self.email}>"

    # ----- integração com o Django -------------------------------------------------

    @property
    def is_active(self) -> bool:  # usado pelo Django e pelo simplejwt
        return self.situacao == SituacaoUsuario.ATIVO and self.deletado_em is None

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
        """Permissões do perfil do usuário (lidas do banco), com cache por instância."""
        if not self.is_active or self.perfil_id is None:
            return frozenset()
        cache = getattr(self, "_cache_permissoes", None)
        if cache is None:
            cache = frozenset(self.perfil.permissoes.values_list("codigo", flat=True))
            self._cache_permissoes = cache
        return cache

    def tem_permissao(self, codigo: str) -> bool:
        return codigo in self.codigos_permissao()


# --------------------------------------------------------------------------- aparelhos e domínios


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
        managed = False
        db_table = "dispositivo"
        ordering = ["-data_criacao"]

    def __str__(self) -> str:
        return f"{self.modelo or 'Aparelho'} ({self.id_dispositivo})"


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
        managed = False
        db_table = "dominio_email_corporativo"
        ordering = ["dominio"]

    def __str__(self) -> str:
        return self.dominio

    def save(self, *args, **kwargs):
        self.dominio = (self.dominio or "").strip().lower().lstrip("@")
        super().save(*args, **kwargs)
