"""Endpoints de autenticação e da conta do próprio usuário (/auth e /me)."""

from django.db.transaction import non_atomic_requests
from django.utils.decorators import method_decorator
from drf_spectacular.utils import PolymorphicProxySerializer, extend_schema
from rest_framework import status
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from ..constantes import TipoToken
from ..excecoes import DesafioMfa
from ..servicos import acesso, mfa, titular, usuarios
from . import serializers as s

MSG_CADASTRO = (
    "Solicitação recebida. Se os dados estiverem corretos, a Netafim vai analisar o "
    "cadastro e você receberá um e-mail quando o acesso for liberado."
)
MSG_ESQUECI = (
    "Se o e-mail estiver cadastrado e ativo, você receberá um link para criar uma nova senha."
)


def resposta_tokens(usuario, par, codigo=status.HTTP_200_OK) -> Response:
    dados = {
        "access": par.access,
        "refresh": par.refresh,
        "access_expira_em": par.access_expira_em,
        "refresh_expira_em": par.refresh_expira_em,
        "usuario": s.MeSerializer(usuario).data,
    }
    return Response(dados, status=codigo)


class PublicaView(APIView):
    authentication_classes: list = []
    permission_classes = [AllowAny]


# Login e renovação rodam FORA do ATOMIC_REQUESTS: a contagem de falhas e a revogação por reuso de
# refresh (feitas pelo banco) precisam ficar gravadas mesmo quando a resposta é um erro. Os serviços
# abrem a própria transação e só levantam o erro depois que ela termina.
@method_decorator(non_atomic_requests, name="dispatch")
@extend_schema(
    tags=["auth"],
    request=s.LoginSerializer,
    responses=PolymorphicProxySerializer(
        component_name="LoginResposta",
        serializers=[s.TokensSerializer, s.LoginMfaSerializer],
        resource_type_field_name=None,
    ),
)
class LoginView(PublicaView):
    throttle_scope = "login"

    def post(self, request):
        entrada = s.LoginSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        dados = entrada.validated_data
        d = dados["dispositivo"]
        dispositivo = acesso.DadosDispositivo(
            id_dispositivo=d["id_dispositivo"],
            modelo=d.get("modelo") or None,
            versao_app=d.get("versao_app") or None,
        )
        try:
            usuario, par = acesso.autenticar(
                dados["email"], dados["senha"], request=request, dispositivo=dispositivo
            )
        except DesafioMfa as desafio:
            # senha correta, conta com MFA: ainda não há sessão, só o desafio para o segundo passo
            return Response(
                {
                    "mfa_necessario": True,
                    "desafio": desafio.desafio,
                    "desafio_expira_em": desafio.expira_em,
                }
            )
        return resposta_tokens(usuario, par)


@method_decorator(non_atomic_requests, name="dispatch")
@extend_schema(tags=["auth"], request=s.MfaVerificarSerializer, responses=s.TokensSerializer)
class MfaVerificarView(PublicaView):
    """Segundo passo do login: desafio + código do aplicativo (ou de recuperação) abre a sessão."""

    throttle_scope = "mfa"

    def post(self, request):
        entrada = s.MfaVerificarSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        usuario, par = acesso.concluir_login_mfa(
            entrada.validated_data["desafio"], entrada.validated_data["codigo"], request=request
        )
        return resposta_tokens(usuario, par)


@method_decorator(non_atomic_requests, name="dispatch")
@extend_schema(tags=["auth"], request=s.RefreshSerializer, responses=s.TokensSerializer)
class RenovarSessaoView(PublicaView):
    throttle_scope = "login"

    def post(self, request):
        entrada = s.RefreshSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        usuario, par = acesso.renovar_sessao(entrada.validated_data["refresh"], request=request)
        return resposta_tokens(usuario, par)


@extend_schema(tags=["auth"], request=s.RefreshSerializer, responses={204: None})
class LogoutView(APIView):
    def post(self, request):
        entrada = s.RefreshSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        acesso.encerrar_sessao(request.user, entrada.validated_data["refresh"], request=request)
        return Response(status=status.HTTP_204_NO_CONTENT)


@extend_schema(tags=["auth"], request=s.CadastroSerializer, responses={202: s.MensagemSerializer})
class SolicitarCadastroView(PublicaView):
    throttle_scope = "cadastro"

    def post(self, request):
        entrada = s.CadastroSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        dados = entrada.validated_data
        usuarios.solicitar_cadastro(
            nome=dados["nome"],
            email=dados["email"],
            senha=dados["senha"],
            telefone=dados.get("telefone"),
            versao_termo=dados["versao_termo"],
            request=request,
        )
        return Response({"detail": MSG_CADASTRO}, status=status.HTTP_202_ACCEPTED)


@extend_schema(tags=["auth"], responses=s.TermoSerializer)
class TermoVigenteView(PublicaView):
    def get(self, request):
        return Response({"versao_vigente": s.versao_termo_vigente()})


@extend_schema(
    tags=["auth"], request=s.AceitarConviteSerializer, responses={200: s.MensagemSerializer}
)
class AceitarConviteView(PublicaView):
    throttle_scope = "login"

    def post(self, request):
        entrada = s.AceitarConviteSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        dados = entrada.validated_data
        usuarios.aceitar_convite(
            dados["token"], dados["senha"], dados["versao_termo"], request=request
        )
        return Response({"detail": "Senha definida. Você já pode entrar no aplicativo."})


@extend_schema(
    tags=["auth"], request=s.EsqueciSenhaSerializer, responses={202: s.MensagemSerializer}
)
class EsqueciSenhaView(PublicaView):
    throttle_scope = "cadastro"

    def post(self, request):
        entrada = s.EsqueciSenhaSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        acesso.solicitar_redefinicao_senha(entrada.validated_data["email"], request=request)
        return Response({"detail": MSG_ESQUECI}, status=status.HTTP_202_ACCEPTED)


@extend_schema(
    tags=["auth"], request=s.RedefinirSenhaSerializer, responses={200: s.MensagemSerializer}
)
class RedefinirSenhaView(PublicaView):
    throttle_scope = "login"

    def post(self, request):
        entrada = s.RedefinirSenhaSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        dados = entrada.validated_data
        acesso.definir_senha_por_token(
            dados["token"], dados["nova_senha"], TipoToken.RESET_SENHA, request=request
        )
        return Response({"detail": "Senha redefinida. Entre com a nova senha."})


@extend_schema(tags=["me"])
class MeView(APIView):
    permission_classes = [IsAuthenticated]

    @extend_schema(responses=s.MeSerializer)
    def get(self, request):
        return Response(s.MeSerializer(request.user).data)

    @extend_schema(request=s.AtualizarDadosSerializer, responses=s.MeSerializer)
    def patch(self, request):
        entrada = s.AtualizarDadosSerializer(data=request.data, partial=True)
        entrada.is_valid(raise_exception=True)
        usuario = request.user
        dados = entrada.validated_data
        if "nome" in dados:
            usuario.nome = dados["nome"].strip()
        if "telefone" in dados:
            usuario.telefone = dados["telefone"] or None
        usuario.save(update_fields=["nome", "telefone", "data_atualizacao"])
        return Response(s.MeSerializer(usuario).data)


@extend_schema(tags=["me"], request=s.AlterarSenhaSerializer, responses={204: None})
class AlterarSenhaView(APIView):
    def post(self, request):
        entrada = s.AlterarSenhaSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        dados = entrada.validated_data
        acesso.alterar_senha(
            request.user, dados["senha_atual"], dados["nova_senha"], request=request
        )
        return Response(status=status.HTTP_204_NO_CONTENT)


@extend_schema(tags=["me"])
class MfaEstadoView(APIView):
    @extend_schema(responses=s.MfaEstadoSerializer)
    def get(self, request):
        return Response(mfa.estado(request.user))


@extend_schema(tags=["me"], request=None, responses=s.MfaIniciadoSerializer)
class MfaIniciarView(APIView):
    """Gera o segredo (ainda não ativo). Cadastre no aplicativo e confirme com um código."""

    throttle_scope = "mfa"

    def post(self, request):
        return Response(mfa.iniciar(request.user))


@extend_schema(tags=["me"], request=s.MfaCodigoSerializer, responses=s.MfaConfirmadoSerializer)
class MfaConfirmarView(APIView):
    throttle_scope = "mfa"

    def post(self, request):
        entrada = s.MfaCodigoSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        codigos = mfa.confirmar(request.user, entrada.validated_data["codigo"], request=request)
        return Response({"codigos_recuperacao": codigos})


@extend_schema(tags=["me"], request=s.MfaDesativarSerializer, responses={204: None})
class MfaDesativarView(APIView):
    throttle_scope = "mfa"

    def post(self, request):
        entrada = s.MfaDesativarSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        dados = entrada.validated_data
        mfa.desativar(request.user, dados["senha"], dados["codigo"], request=request)
        return Response(status=status.HTTP_204_NO_CONTENT)


# --------------------------------------------------------------------------- direitos do titular


@extend_schema(tags=["lgpd"], responses=s.DadosPessoaisSerializer)
class DadosPessoaisView(APIView):
    """Acesso e portabilidade: cópia dos dados pessoais do próprio usuário (LGPD, art. 18)."""

    throttle_scope = "titular"

    def get(self, request):
        return Response(titular.exportar_dados(request.user, request=request))


@extend_schema(tags=["lgpd"])
class MinhasSolicitacoesView(APIView):
    """O titular lista e abre pedidos sobre os próprios dados."""

    throttle_scope = "titular"

    @extend_schema(responses=s.SolicitacaoTitularSerializer(many=True))
    def get(self, request):
        return Response(titular.minhas_solicitacoes())

    @extend_schema(
        request=s.SolicitacaoCriarSerializer, responses={201: s.SolicitacaoTitularSerializer}
    )
    def post(self, request):
        entrada = s.SolicitacaoCriarSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        pedido = titular.registrar(
            request.user,
            entrada.validated_data["tipo"],
            entrada.validated_data.get("observacao"),
            request=request,
        )
        return Response(pedido, status=status.HTTP_201_CREATED)
