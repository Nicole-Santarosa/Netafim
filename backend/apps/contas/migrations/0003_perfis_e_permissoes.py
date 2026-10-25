"""
Carga dos seis perfis, das permissões e da matriz perfil x permissão
(MER/DER, Anexo B). Os dados ficam congelados aqui de propósito: mudanças futuras
na matriz entram em uma nova migration, para cada ambiente ter o mesmo histórico.
"""

from django.db import migrations

NA, NU, DA, DU, CA, CU = (
    "Netafim Admin",
    "Netafim Usuário",
    "Distribuidor Admin",
    "Distribuidor Usuário",
    "Cliente Admin",
    "Cliente Usuário",
)
TODOS = (NA, NU, DA, DU, CA, CU)

PERFIS = [
    (NA, "netafim", "admin", "Acesso total à plataforma, aprovação de cadastros e gestão completa."),
    (NU, "netafim", "usuario", "Perfil operacional da Netafim (técnico)."),
    (DA, "distribuidor", "admin", "Administrador do distribuidor e da sua carteira de clientes."),
    (DU, "distribuidor", "usuario", "Perfil operacional do distribuidor (técnico)."),
    (CA, "cliente", "admin", "Administrador da empresa cliente."),
    (CU, "cliente", "usuario", "Perfil básico do cliente final."),
]

# (codigo, descricao, perfis que recebem a permissão)
MATRIZ = [
    ("aprovar_cadastro", "Aprovar cadastros", (NA,)),
    ("criar_usuario", "Criar usuários (dentro da própria organização)", (NA, DA, CA)),
    ("editar_usuario", "Editar usuários (dentro da própria organização)", (NA, DA, CA)),
    ("excluir_usuario", "Excluir usuários (dentro da própria organização)", (NA, DA, CA)),
    ("definir_perfil", "Definir perfil / hierarquia", (NA,)),
    ("abrir_atendimento", "Abrir atendimento", TODOS),
    ("vincular_tecnico", "Vincular técnico ao atendimento", (NA, DA)),
    ("responder_atendimento", "Responder atendimento", TODOS),
    ("encerrar_atendimento", "Encerrar atendimento", (NA,)),
    ("consultar_proprios_atendimentos", "Consultar próprios atendimentos", TODOS),
    ("consultar_atendimentos_empresa", "Consultar atendimentos da empresa", (NA, DA, CA)),
    (
        "consultar_atendimentos_clientes_vinculados",
        "Consultar atendimentos de clientes vinculados",
        (NA, DA),
    ),
    ("consultar_todos_atendimentos", "Consultar todos os atendimentos da plataforma", (NA,)),
    ("dashboard_gerencial", "Dashboard gerencial (dentro do escopo do perfil)", (NA, DA, CA)),
    ("destinar_atendimento", "Destinar atendimento (Netafim ou Distribuidor)", (NA,)),
    ("reclassificar_atendimento", "Reclassificar atendimento", (NA,)),
    ("editar_atendimento_finalizado", "Editar atendimento finalizado", (NA,)),
]


def carregar(apps, schema_editor):
    Perfil = apps.get_model("contas", "Perfil")
    Permissao = apps.get_model("contas", "Permissao")
    PerfilPermissao = apps.get_model("contas", "PerfilPermissao")

    perfis = {}
    for nome, tipo, nivel, descricao in PERFIS:
        perfis[nome], _ = Perfil.objects.update_or_create(
            tipo_organizacao=tipo,
            nivel=nivel,
            defaults={"nome_perfil": nome, "descricao": descricao},
        )

    for codigo, descricao, donos in MATRIZ:
        permissao, _ = Permissao.objects.update_or_create(
            codigo=codigo, defaults={"descricao": descricao}
        )
        for nome in donos:
            PerfilPermissao.objects.get_or_create(perfil=perfis[nome], permissao=permissao)


def descarregar(apps, schema_editor):
    apps.get_model("contas", "PerfilPermissao").objects.all().delete()
    apps.get_model("contas", "Permissao").objects.filter(
        codigo__in=[codigo for codigo, _, _ in MATRIZ]
    ).delete()
    apps.get_model("contas", "Perfil").objects.filter(
        nome_perfil__in=[nome for nome, *_ in PERFIS]
    ).delete()


class Migration(migrations.Migration):
    dependencies = [("contas", "0002_regras_banco")]

    operations = [migrations.RunPython(carregar, descarregar)]
