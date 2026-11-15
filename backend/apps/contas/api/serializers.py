"""Serializers do módulo de acesso. Entradas e saídas separadas para o contrato ficar explícito."""

from django.conf import settings
from drf_spectacular.utils import extend_schema_field
from rest_framework import serializers

from ..constantes import SituacaoUsuario, TipoOrganizacao
from ..models import (
    Cliente,
    Dispositivo,
    Distribuidor,
    DominioEmailCorporativo,
    Perfil,
    Permissao,
    Usuario,
)
from ..servicos import titular

# --------------------------------------------------------------------------- saídas


class PermissaoSerializer(serializers.ModelSerializer):
    class Meta:
        model = Permissao
        fields = ["id_permissao", "codigo", "descricao"]


class PerfilResumoSerializer(serializers.ModelSerializer):
    class Meta:
        model = Perfil
        fields = ["id_perfil", "nome_perfil", "tipo_organizacao", "nivel"]


class PerfilSerializer(serializers.ModelSerializer):
    permissoes = serializers.SlugRelatedField(many=True, read_only=True, slug_field="codigo")

    class Meta:
        model = Perfil
        fields = [
            "id_perfil",
            "nome_perfil",
            "tipo_organizacao",
            "nivel",
            "descricao",
            "permissoes",
        ]


class OrganizacaoSerializer(serializers.Serializer):
    tipo = serializers.ChoiceField(choices=TipoOrganizacao.choices)
    id = serializers.IntegerField(allow_null=True)
    nome = serializers.CharField()


def organizacao_de(usuario: Usuario) -> dict | None:
    if usuario.tipo_organizacao == TipoOrganizacao.NETAFIM:
        return {"tipo": TipoOrganizacao.NETAFIM, "id": None, "nome": "Netafim"}
    if usuario.tipo_organizacao == TipoOrganizacao.DISTRIBUIDOR and usuario.distribuidor:
        return {
            "tipo": TipoOrganizacao.DISTRIBUIDOR,
            "id": usuario.distribuidor_id,
            "nome": usuario.distribuidor.nome,
        }
    if usuario.tipo_organizacao == TipoOrganizacao.CLIENTE and usuario.cliente:
        return {
            "tipo": TipoOrganizacao.CLIENTE,
            "id": usuario.cliente_id,
            "nome": usuario.cliente.nome,
        }
    return None


class UsuarioSerializer(serializers.ModelSerializer):
    perfil = PerfilResumoSerializer(read_only=True)
    organizacao = serializers.SerializerMethodField()
    convite_pendente = serializers.SerializerMethodField()
    ultimo_acesso = serializers.DateTimeField(source="last_login", read_only=True)

    class Meta:
        model = Usuario
        fields = [
            "id_usuario",
            "nome",
            "email",
            "telefone",
            "situacao",
            "tipo_organizacao",
            "organizacao",
            "perfil",
            "convite_pendente",
            "data_aprovacao",
            "ultimo_acesso",
            "data_criacao",
        ]
        read_only_fields = fields

    @extend_schema_field(OrganizacaoSerializer(allow_null=True))
    def get_organizacao(self, obj):
        return organizacao_de(obj)

    def get_convite_pendente(self, obj) -> bool:
        return obj.aguarda_definir_senha


class MeSerializer(UsuarioSerializer):
    permissoes = serializers.SerializerMethodField()

    class Meta(UsuarioSerializer.Meta):
        fields = [
            *UsuarioSerializer.Meta.fields,
            "permissoes",
            "exige_troca_senha",
            "mfa_ativo",
            "versao_termo_consentimento",
        ]
        read_only_fields = fields

    def get_permissoes(self, obj) -> list[str]:
        return sorted(obj.codigos_permissao())


class DistribuidorSerializer(serializers.ModelSerializer):
    class Meta:
        model = Distribuidor
        fields = [
            "id_distribuidor",
            "nome",
            "regiao",
            "email",
            "telefone",
            "status",
            "data_criacao",
            "data_atualizacao",
        ]
        read_only_fields = ["id_distribuidor", "data_criacao", "data_atualizacao"]


class ClienteSerializer(serializers.ModelSerializer):
    id_distribuidor = serializers.PrimaryKeyRelatedField(
        source="distribuidor",
        queryset=Distribuidor.objects.all(),
        allow_null=True,
        required=False,
    )

    class Meta:
        model = Cliente
        fields = [
            "id_cliente",
            "nome",
            "email",
            "telefone",
            "id_distribuidor",
            "status",
            "data_criacao",
            "data_atualizacao",
        ]
        read_only_fields = ["id_cliente", "data_criacao", "data_atualizacao"]


class DominioSerializer(serializers.ModelSerializer):
    id_distribuidor = serializers.PrimaryKeyRelatedField(
        source="distribuidor",
        queryset=Distribuidor.objects.all(),
        allow_null=True,
        required=False,
    )

    class Meta:
        model = DominioEmailCorporativo
        fields = [
            "id_dominio",
            "dominio",
            "tipo_organizacao",
            "id_distribuidor",
            "ativo",
            "data_criacao",
        ]
        read_only_fields = ["id_dominio", "data_criacao"]

    def validate_dominio(self, valor: str) -> str:
        return valor.strip().lower().lstrip("@")

    def validate(self, dados):
        tipo = dados.get("tipo_organizacao", getattr(self.instance, "tipo_organizacao", None))
        distribuidor = dados.get("distribuidor", getattr(self.instance, "distribuidor", None))
        if tipo == TipoOrganizacao.DISTRIBUIDOR and distribuidor is None:
            raise serializers.ValidationError(
                {"id_distribuidor": "Obrigatório para domínio de distribuidor."}
            )
        if tipo == TipoOrganizacao.NETAFIM and distribuidor is not None:
            raise serializers.ValidationError(
                {"id_distribuidor": "Domínio da Netafim não pertence a distribuidor."}
            )
        return dados


class DispositivoSerializer(serializers.ModelSerializer):
    class Meta:
        model = Dispositivo
        fields = [
            "id_dispositivo",
            "modelo",
            "versao_app",
            "ultima_sincronizacao",
            "revogado_em",
            "data_criacao",
        ]
        read_only_fields = fields


class TokensSerializer(serializers.Serializer):
    access = serializers.CharField()
    refresh = serializers.CharField()
    access_expira_em = serializers.DateTimeField()
    refresh_expira_em = serializers.DateTimeField()
    usuario = MeSerializer()


class MensagemSerializer(serializers.Serializer):
    detail = serializers.CharField()


class TermoSerializer(serializers.Serializer):
    versao_vigente = serializers.CharField()


# --------------------------------------------------------------------------- entradas


class DispositivoEntradaSerializer(serializers.Serializer):
    id_dispositivo = serializers.UUIDField()
    modelo = serializers.CharField(max_length=80, required=False, allow_blank=True)
    versao_app = serializers.CharField(max_length=20, required=False, allow_blank=True)


class LoginSerializer(serializers.Serializer):
    email = serializers.EmailField(max_length=150)
    senha = serializers.CharField(max_length=128, trim_whitespace=False)
    # Obrigatório: o banco só emite refresh token para um aparelho do usuário.
    dispositivo = DispositivoEntradaSerializer()


class RefreshSerializer(serializers.Serializer):
    refresh = serializers.CharField(max_length=128)


class AceiteTermoMixin(serializers.Serializer):
    aceite_termo = serializers.BooleanField()
    versao_termo = serializers.CharField(max_length=20)

    def validate_aceite_termo(self, valor: bool) -> bool:
        if not valor:
            raise serializers.ValidationError("É preciso aceitar o aviso de privacidade.")
        return valor


class CadastroSerializer(AceiteTermoMixin):
    nome = serializers.CharField(max_length=150)
    email = serializers.EmailField(max_length=150)
    telefone = serializers.CharField(max_length=20, required=False, allow_blank=True)
    senha = serializers.CharField(max_length=128, trim_whitespace=False)


class AceitarConviteSerializer(AceiteTermoMixin):
    token = serializers.CharField(max_length=128)
    senha = serializers.CharField(max_length=128, trim_whitespace=False)


class EsqueciSenhaSerializer(serializers.Serializer):
    email = serializers.EmailField(max_length=150)


class RedefinirSenhaSerializer(serializers.Serializer):
    token = serializers.CharField(max_length=128)
    nova_senha = serializers.CharField(max_length=128, trim_whitespace=False)


class AlterarSenhaSerializer(serializers.Serializer):
    senha_atual = serializers.CharField(max_length=128, trim_whitespace=False)
    nova_senha = serializers.CharField(max_length=128, trim_whitespace=False)


class AtualizarDadosSerializer(serializers.Serializer):
    nome = serializers.CharField(max_length=150, required=False)
    telefone = serializers.CharField(
        max_length=20, required=False, allow_blank=True, allow_null=True
    )


class CriarUsuarioSerializer(serializers.Serializer):
    nome = serializers.CharField(max_length=150)
    email = serializers.EmailField(max_length=150)
    telefone = serializers.CharField(max_length=20, required=False, allow_blank=True)
    tipo_organizacao = serializers.ChoiceField(choices=TipoOrganizacao.choices, required=False)
    id_distribuidor = serializers.IntegerField(required=False, allow_null=True)
    id_cliente = serializers.IntegerField(required=False, allow_null=True)
    id_perfil = serializers.IntegerField(required=False, allow_null=True)


class AprovarSerializer(serializers.Serializer):
    tipo_organizacao = serializers.ChoiceField(choices=TipoOrganizacao.choices, required=False)
    id_distribuidor = serializers.IntegerField(required=False, allow_null=True)
    id_cliente = serializers.IntegerField(required=False, allow_null=True)
    id_perfil = serializers.IntegerField(required=False, allow_null=True)


class SituacaoSerializer(serializers.Serializer):
    situacao = serializers.ChoiceField(
        choices=[
            SituacaoUsuario.ATIVO,
            SituacaoUsuario.INATIVO,
            SituacaoUsuario.BLOQUEADO,
        ]
    )


class DefinirPerfilSerializer(serializers.Serializer):
    id_perfil = serializers.IntegerField()


class PermissoesDoPerfilSerializer(serializers.Serializer):
    permissoes = serializers.ListField(child=serializers.CharField(max_length=50), allow_empty=True)

    def validate_permissoes(self, codigos: list[str]) -> list[str]:
        codigos = sorted(set(codigos))
        existentes = set(
            Permissao.objects.filter(codigo__in=codigos).values_list("codigo", flat=True)
        )
        desconhecidos = set(codigos) - existentes
        if desconhecidos:
            raise serializers.ValidationError(
                f"Permissões inexistentes: {', '.join(sorted(desconhecidos))}."
            )
        return codigos


def versao_termo_vigente() -> str:
    return settings.CONTAS["VERSAO_TERMO_VIGENTE"]


# --------------------------------------------------------------------------- MFA (TOTP)


class LoginMfaSerializer(serializers.Serializer):
    """Resposta do login quando a conta tem MFA: ainda NÃO há sessão; falta o segundo fator."""

    mfa_necessario = serializers.BooleanField()
    desafio = serializers.CharField()
    desafio_expira_em = serializers.DateTimeField()


class MfaVerificarSerializer(serializers.Serializer):
    desafio = serializers.CharField(max_length=2048)
    # 6 dígitos do aplicativo (aceita "123 456") ou um código de recuperação (XXXXX-XXXXX)
    codigo = serializers.CharField(max_length=32, trim_whitespace=False)


class MfaCodigoSerializer(serializers.Serializer):
    codigo = serializers.CharField(max_length=32, trim_whitespace=False)


class MfaDesativarSerializer(serializers.Serializer):
    senha = serializers.CharField(max_length=128, trim_whitespace=False)
    codigo = serializers.CharField(max_length=32, trim_whitespace=False)


class MfaEstadoSerializer(serializers.Serializer):
    ativo = serializers.BooleanField()
    codigos_recuperacao_restantes = serializers.IntegerField()


class MfaIniciadoSerializer(serializers.Serializer):
    segredo = serializers.CharField()
    otpauth_uri = serializers.CharField()


class MfaConfirmadoSerializer(serializers.Serializer):
    """Os códigos de recuperação só aparecem nesta resposta; o banco guarda apenas o hash."""

    codigos_recuperacao = serializers.ListField(child=serializers.CharField())


# --------------------------------------------------------------------------- direitos do titular


class SolicitacaoCriarSerializer(serializers.Serializer):
    tipo = serializers.ChoiceField(choices=titular.TIPOS)
    observacao = serializers.CharField(max_length=1000, required=False, allow_blank=True)


class SolicitacaoTitularSerializer(serializers.Serializer):
    id_solicitacao = serializers.IntegerField()
    tipo = serializers.CharField()
    status = serializers.CharField()
    observacao = serializers.CharField(allow_null=True)
    data_solicitacao = serializers.DateTimeField()
    data_prazo = serializers.DateTimeField()
    data_conclusao = serializers.DateTimeField(allow_null=True)
    justificativa_negativa = serializers.CharField(allow_null=True)


class SolicitacaoAdminSerializer(serializers.Serializer):
    id_solicitacao = serializers.IntegerField()
    id_usuario = serializers.IntegerField(allow_null=True)
    nome_titular = serializers.CharField(allow_null=True)
    email_contato = serializers.CharField()
    tipo = serializers.CharField()
    status = serializers.CharField()
    observacao = serializers.CharField(allow_null=True)
    data_solicitacao = serializers.DateTimeField()
    data_prazo = serializers.DateTimeField()
    data_conclusao = serializers.DateTimeField(allow_null=True)
    id_responsavel = serializers.IntegerField(allow_null=True)
    justificativa_negativa = serializers.CharField(allow_null=True)
    atrasada = serializers.BooleanField()


class NegarSolicitacaoSerializer(serializers.Serializer):
    justificativa = serializers.CharField(max_length=2000)


class DadosPessoaisSerializer(serializers.Serializer):
    """Cópia dos dados pessoais que o módulo de acesso guarda do titular."""

    formato = serializers.IntegerField()
    exportado_em = serializers.DateTimeField()
    conta = MeSerializer()
    consentimento = serializers.DictField()
    exclusao_solicitada_em = serializers.DateTimeField(allow_null=True)
    dispositivos = DispositivoSerializer(many=True)
    solicitacoes_lgpd = SolicitacaoTitularSerializer(many=True)
    # Seção do módulo de atendimentos: só vem quando esse módulo está instalado.
    atendimentos = serializers.DictField(required=False)
