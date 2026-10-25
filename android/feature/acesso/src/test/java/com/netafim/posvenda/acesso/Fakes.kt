package com.netafim.posvenda.acesso

import com.netafim.posvenda.acesso.dados.ArmazenamentoSessao
import com.netafim.posvenda.acesso.dados.SessaoGuardada
import com.netafim.posvenda.acesso.dominio.DadosCadastro
import com.netafim.posvenda.acesso.dominio.ErroAcesso
import com.netafim.posvenda.acesso.dominio.Organizacao
import com.netafim.posvenda.acesso.dominio.Perfil
import com.netafim.posvenda.acesso.dominio.RepositorioAcesso
import com.netafim.posvenda.acesso.dominio.Resultado
import com.netafim.posvenda.acesso.dominio.Sessao
import com.netafim.posvenda.acesso.dominio.Usuario
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow

val USUARIO_EXEMPLO = Usuario(
    id = 7,
    nome = "Ana Souza",
    email = "ana@agroirriga.com.br",
    telefone = "16999998888",
    situacao = "ativo",
    perfil = Perfil(3, "Distribuidor Admin", "distribuidor", "admin"),
    organizacao = Organizacao("distribuidor", 1, "Agro Irriga Ltda"),
    permissoes = setOf("abrir_atendimento", "vincular_tecnico"),
    exigeTrocaSenha = false,
)

class ArmazenamentoEmMemoria : ArmazenamentoSessao {
    var guardada: SessaoGuardada? = null
    private var id = UUID.randomUUID().toString()
    var idsGerados = 0

    override suspend fun ler() = guardada
    override suspend fun salvar(sessao: SessaoGuardada) { guardada = sessao }
    override suspend fun limpar() { guardada = null }
    override suspend fun idDispositivo() = id
    override suspend fun novoIdDispositivo(): String {
        idsGerados++
        id = UUID.randomUUID().toString()
        return id
    }
}

class RepositorioFalso : RepositorioAcesso {
    override val sessao = MutableStateFlow<Sessao?>(null)

    var resultadoEntrar: Resultado<Usuario> = Resultado.Sucesso(USUARIO_EXEMPLO)
    var resultadoCadastro: Resultado<String> = Resultado.Sucesso("Solicitação recebida.")
    var resultadoTermo: Resultado<String> = Resultado.Sucesso("1.0")
    var resultadoPerfil: Resultado<Usuario> = Resultado.Sucesso(USUARIO_EXEMPLO)
    var ultimoCadastro: DadosCadastro? = null
    var chamadasEntrar = 0
    var saiu = false

    override suspend fun entrar(email: String, senha: String): Resultado<Usuario> {
        chamadasEntrar++
        val r = resultadoEntrar
        if (r is Resultado.Sucesso) sessao.value = Sessao("a", "r", r.valor)
        return r
    }

    override suspend fun sair() {
        saiu = true
        sessao.value = null
    }

    override suspend fun atualizarPerfil() = resultadoPerfil
    override suspend fun versaoTermoVigente() = resultadoTermo

    override suspend fun solicitarCadastro(dados: DadosCadastro): Resultado<String> {
        ultimoCadastro = dados
        return resultadoCadastro
    }

    override suspend fun esqueciSenha(email: String): Resultado<String> = Resultado.Sucesso("ok")
}

fun falhaApi(codigo: String, mensagem: String = "erro", status: Int = 400) =
    Resultado.Falha(ErroAcesso.Api(codigo, mensagem, status))
