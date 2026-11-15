"""
Migration só de ESTADO (DT-002): ``usuario.password`` passa a ser um ``HashSenhaField``, que o Django
grava mas nunca lê. Como o modelo é ``managed = False``, nada muda no banco.
"""

import apps.contas.campos
from django.db import migrations


class Migration(migrations.Migration):

    dependencies = [
        ("contas", "0001_inicial"),
    ]

    operations = [
        migrations.AlterField(
            model_name="usuario",
            name="password",
            field=apps.contas.campos.HashSenhaField(max_length=128, verbose_name="password"),
        ),
    ]
