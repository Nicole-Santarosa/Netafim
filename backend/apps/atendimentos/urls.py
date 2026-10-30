from django.urls import path

from apps.atendimentos.views import AtendimentosView

urlpatterns = [
    path("atendimentos", AtendimentosView.as_view()),
]
