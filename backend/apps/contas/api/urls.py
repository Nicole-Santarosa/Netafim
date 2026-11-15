from django.urls import path
from rest_framework.routers import SimpleRouter

from . import views, views_auth

roteador = SimpleRouter(trailing_slash=False)
roteador.register("usuarios", views.UsuarioViewSet, basename="usuario")
roteador.register("distribuidores", views.DistribuidorViewSet, basename="distribuidor")
roteador.register("clientes", views.ClienteViewSet, basename="cliente")
roteador.register("perfis", views.PerfilViewSet, basename="perfil")
roteador.register("permissoes", views.PermissaoViewSet, basename="permissao")
roteador.register("dominios", views.DominioViewSet, basename="dominio")
roteador.register("dispositivos", views.DispositivoViewSet, basename="dispositivo")

urlpatterns = [
    path("auth/login", views_auth.LoginView.as_view(), name="auth-login"),
    path("auth/refresh", views_auth.RenovarSessaoView.as_view(), name="auth-refresh"),
    path("auth/logout", views_auth.LogoutView.as_view(), name="auth-logout"),
    path("auth/cadastro", views_auth.SolicitarCadastroView.as_view(), name="auth-cadastro"),
    path("auth/termo", views_auth.TermoVigenteView.as_view(), name="auth-termo"),
    path(
        "auth/convite/aceitar", views_auth.AceitarConviteView.as_view(), name="auth-convite-aceitar"
    ),
    path("auth/senha/esqueci", views_auth.EsqueciSenhaView.as_view(), name="auth-senha-esqueci"),
    path(
        "auth/senha/redefinir", views_auth.RedefinirSenhaView.as_view(), name="auth-senha-redefinir"
    ),
    path("me", views_auth.MeView.as_view(), name="me"),
    path("me/senha", views_auth.AlterarSenhaView.as_view(), name="me-senha"),
    *roteador.urls,
]
