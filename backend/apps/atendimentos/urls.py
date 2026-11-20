from django.urls import path

from apps.atendimentos.views import AtendimentosView, CulturasView, DetalheAtendimentoView
from apps.atendimentos.views_sync import PullView, PushView

urlpatterns = [
    # Sincronização offline-first (contrato em docs/api/sync.md). As duas rotas
    # são independentes: quem decide a ordem push e pull é o app.
    path("sync/pull", PullView.as_view()),
    path("sync/push", PushView.as_view()),
    path("atendimentos", AtendimentosView.as_view()),
    path("atendimentos/<uuid:id_atendimento>", DetalheAtendimentoView.as_view()),
    path("culturas", CulturasView.as_view()),
]
