from django.urls import path

from apps.atendimentos.views import AtendimentosView, CulturasView, DetalheAtendimentoView

urlpatterns = [
    path("atendimentos", AtendimentosView.as_view()),
    path("atendimentos/<uuid:id_atendimento>", DetalheAtendimentoView.as_view()),
    path("culturas", CulturasView.as_view()),
]
