from django.urls import path

from apps.atendimentos.views import AberturaAtendimentoView

urlpatterns = [
    path("atendimentos", AberturaAtendimentoView.as_view()),
]
