package com.example.netafim.feature.relatorio.model

enum class MotivoAtendimento(val codigoBanco: String, val titulo: String) {
    DIGITAL_FARMING("digital_farming", "Digital Farming"),
    DUVIDAS("duvidas", "Dúvidas"),
    ENTREGA_TECNICA("entrega_tecnica", "Entrega Técnica"),
    POS_VENDA("pos_venda", "Pós-venda"),
    PROGRAMACAO_FEIRAS("programacao_feiras", "Programação de Feiras"),
    TREINAMENTO("treinamento", "Treinamento");

    companion object {
        fun fromCodigo(codigo: String): MotivoAtendimento =
            entries.firstOrNull { it.codigoBanco.equals(codigo, ignoreCase = true) }
                ?: POS_VENDA
    }
}

enum class EtapaRelatorio(val ordem: Int, val titulo: String) {
    CONSTATACOES(1, "Constatações"),
    ATIVIDADES(2, "Atividades"),
    PLANO_ACAO(3, "Plano de Ação"),
    ASSINATURA(4, "Assinatura");

    fun proxima(): EtapaRelatorio? = entries.firstOrNull { it.ordem == this.ordem + 1 }
    fun anterior(): EtapaRelatorio? = entries.firstOrNull { it.ordem == this.ordem - 1 }
}

enum class EtapaEvidencia(val codigoBanco: String) {
    CONSTATACAO("constatacao"),
    ATIVIDADE("atividade"),
    GERAL("geral"),
    ANEXO_EXTRA("anexo_extra")
}

enum class TipoEvidencia(val codigoBanco: String) {
    FOTO("foto"),
    VIDEO("video"),
    DOCUMENTO("documento")
}

enum class OrigemCapturaEvidencia(val codigoBanco: String) {
    CAMERA_APP("camera_app"),
    ANEXO("anexo")
}

data class EvidenciaItem(
    val id: String,
    val etapa: EtapaEvidencia,
    val tipo: TipoEvidencia,
    val origemCaptura: OrigemCapturaEvidencia,
    val caminhoLocal: String,
    val nomeArquivo: String,
    val mimeType: String,
    val tamanhoBytes: Long,
    val disponivel: Boolean = true
)

data class AssinaturaRegistro(
    val tipoAssinante: String, // "especialista" | "cliente"
    val caminhoImagemLocal: String?,
    val nomeAssinante: String = "",
    val confirmada: Boolean = false
)

data class ConfiguracaoMotivoRelatorio(
    val motivo: MotivoAtendimento,
    val tipoAtendimentoObrigatorio: Boolean = false,
    val tipoTreinamentoObrigatorio: Boolean = false,
    val descricaoConstatacoesObrigatoria: Boolean = false,
    val atividadesExecutadasObrigatoria: Boolean = false,
    val planoAcaoObrigatorio: Boolean = false,
    val conclusaoVisitaObrigatoria: Boolean = true,
    val localTreinamentoObrigatorio: Boolean = false,
    val qtdPessoasTreinadasObrigatoria: Boolean = false,
    val evidenciaConstatacaoObrigatoria: Boolean = false,
    val evidenciaAtividadeObrigatoria: Boolean = false,
    val evidenciaGeralObrigatoria: Boolean = false,
    val assinaturaEspecialistaObrigatoria: Boolean = true,
    val assinaturaClienteObrigatoria: Boolean = false
)

object CatalogoMotivoCampoRelatorio {
    // Espelha as regras documentadas em "Fluxo formulário do relatório.docx" e na tabela MOTIVO_CAMPO_RELATORIO do MER/DER.
    fun configuracaoPara(motivo: MotivoAtendimento): ConfiguracaoMotivoRelatorio =
        when (motivo) {
            MotivoAtendimento.DIGITAL_FARMING -> ConfiguracaoMotivoRelatorio(
                motivo = motivo,
                tipoAtendimentoObrigatorio = true,
                descricaoConstatacoesObrigatoria = true,
                atividadesExecutadasObrigatoria = true,
                planoAcaoObrigatorio = true,
                conclusaoVisitaObrigatoria = true,
                evidenciaConstatacaoObrigatoria = true,
                evidenciaAtividadeObrigatoria = false,
                evidenciaGeralObrigatoria = false,
                assinaturaEspecialistaObrigatoria = true,
                assinaturaClienteObrigatoria = false
            )
            MotivoAtendimento.DUVIDAS -> ConfiguracaoMotivoRelatorio(
                motivo = motivo,
                tipoAtendimentoObrigatorio = true,
                descricaoConstatacoesObrigatoria = true,
                atividadesExecutadasObrigatoria = false,
                planoAcaoObrigatorio = false,
                conclusaoVisitaObrigatoria = true,
                evidenciaConstatacaoObrigatoria = true,
                evidenciaAtividadeObrigatoria = false,
                evidenciaGeralObrigatoria = false,
                assinaturaEspecialistaObrigatoria = true,
                assinaturaClienteObrigatoria = false
            )
            MotivoAtendimento.ENTREGA_TECNICA -> ConfiguracaoMotivoRelatorio(
                motivo = motivo,
                tipoAtendimentoObrigatorio = false,
                descricaoConstatacoesObrigatoria = false,
                atividadesExecutadasObrigatoria = true,
                planoAcaoObrigatorio = true,
                conclusaoVisitaObrigatoria = true,
                evidenciaConstatacaoObrigatoria = false,
                evidenciaAtividadeObrigatoria = false,
                evidenciaGeralObrigatoria = false,
                assinaturaEspecialistaObrigatoria = true,
                assinaturaClienteObrigatoria = false
            )
            MotivoAtendimento.POS_VENDA -> ConfiguracaoMotivoRelatorio(
                motivo = motivo,
                tipoAtendimentoObrigatorio = false,
                descricaoConstatacoesObrigatoria = true,
                atividadesExecutadasObrigatoria = true,
                planoAcaoObrigatorio = true,
                conclusaoVisitaObrigatoria = true,
                evidenciaConstatacaoObrigatoria = true,
                evidenciaAtividadeObrigatoria = false,
                evidenciaGeralObrigatoria = false,
                assinaturaEspecialistaObrigatoria = true,
                assinaturaClienteObrigatoria = false
            )
            MotivoAtendimento.PROGRAMACAO_FEIRAS -> ConfiguracaoMotivoRelatorio(
                motivo = motivo,
                tipoAtendimentoObrigatorio = false,
                descricaoConstatacoesObrigatoria = false,
                atividadesExecutadasObrigatoria = false,
                planoAcaoObrigatorio = false,
                conclusaoVisitaObrigatoria = true,
                evidenciaConstatacaoObrigatoria = false,
                evidenciaAtividadeObrigatoria = false,
                evidenciaGeralObrigatoria = true,
                assinaturaEspecialistaObrigatoria = true,
                assinaturaClienteObrigatoria = false
            )
            MotivoAtendimento.TREINAMENTO -> ConfiguracaoMotivoRelatorio(
                motivo = motivo,
                tipoAtendimentoObrigatorio = false,
                tipoTreinamentoObrigatorio = true,
                descricaoConstatacoesObrigatoria = false,
                atividadesExecutadasObrigatoria = false,
                planoAcaoObrigatorio = false,
                conclusaoVisitaObrigatoria = true,
                localTreinamentoObrigatorio = true,
                qtdPessoasTreinadasObrigatoria = true,
                evidenciaConstatacaoObrigatoria = false,
                evidenciaAtividadeObrigatoria = false,
                evidenciaGeralObrigatoria = true,
                assinaturaEspecialistaObrigatoria = true,
                assinaturaClienteObrigatoria = false
            )
        }
}

data class RelatorioRascunho(
    val idRelatorio: String = "",
    val idAtendimento: String = "",
    val numeroOs: String = "",
    val dataConclusao: String = "",
    val motivo: MotivoAtendimento = MotivoAtendimento.POS_VENDA,
    val tipoAtendimento: String = "",
    val tipoTreinamento: String = "",
    val localTreinamento: String = "",
    val qtdPessoasTreinadas: String = "",
    val descricaoConstatacoes: String = "",
    val atividadesExecutadas: String = "",
    val planoAcao: String = "",
    val conclusaoVisita: String = "",
    val evidencias: List<EvidenciaItem> = emptyList(),
    val assinaturaEspecialista: AssinaturaRegistro = AssinaturaRegistro(
        tipoAssinante = "especialista",
        caminhoImagemLocal = null
    ),
    val assinaturaCliente: AssinaturaRegistro = AssinaturaRegistro(
        tipoAssinante = "cliente",
        caminhoImagemLocal = null
    )
)

data class RelatorioUiState(
    val etapaAtual: EtapaRelatorio = EtapaRelatorio.CONSTATACOES,
    val rascunho: RelatorioRascunho = RelatorioRascunho(),
    val salvando: Boolean = false,
    val salvoComSucesso: Boolean = false,
    val mensagemErro: String? = null,
    val mensagemAviso: String? = null
)