from django.urls import path

from apps.atendimentos.views import AtendimentosView, DetalheAtendimentoView

urlpatterns = [
    path("atendimentos", AtendimentosView.as_view()),
    path("atendimentos/<uuid:id_atendimento>", DetalheAtendimentoView.as_view()),
]
