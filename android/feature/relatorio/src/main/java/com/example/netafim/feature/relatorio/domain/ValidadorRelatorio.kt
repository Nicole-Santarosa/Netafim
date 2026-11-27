package com.example.netafim.feature.relatorio.domain

import com.example.netafim.feature.relatorio.model.CatalogoMotivoCampoRelatorio
import com.example.netafim.feature.relatorio.model.EtapaEvidencia
import com.example.netafim.feature.relatorio.model.EtapaRelatorio
import com.example.netafim.feature.relatorio.model.RelatorioRascunho

object ValidadorRelatorio {

    fun validarEtapa(
        etapa: EtapaRelatorio,
        rascunho: RelatorioRascunho
    ): String? {
        val config = CatalogoMotivoCampoRelatorio.configuracaoPara(rascunho.motivo)
        return when (etapa) {
            EtapaRelatorio.CONSTATACOES -> {
                if (config.tipoAtendimentoObrigatorio && rascunho.tipoAtendimento.isBlank()) {
                    return "Informe o tipo de atendimento para ${rascunho.motivo.titulo}."
                }
                if (config.descricaoConstatacoesObrigatoria && rascunho.descricaoConstatacoes.isBlank()) {
                    return "Descreva as principais constatações para ${rascunho.motivo.titulo}."
                }
                if (config.evidenciaConstatacaoObrigatoria) {
                    val temEvidenciaValida = rascunho.evidencias.any {
                        it.etapa == EtapaEvidencia.CONSTATACAO && it.disponivel && it.caminhoLocal.isNotBlank()
                    }
                    if (!temEvidenciaValida) {
                        return "Adicione ao menos uma foto ou vídeo de constatação."
                    }
                }
                if (config.tipoTreinamentoObrigatorio && rascunho.tipoTreinamento.isBlank()) {
                    return "Selecione o tipo de treinamento."
                }
                if (config.localTreinamentoObrigatorio && rascunho.localTreinamento.isBlank()) {
                    return "Informe o local do treinamento."
                }
                if (config.qtdPessoasTreinadasObrigatoria) {
                    val qtd = rascunho.qtdPessoasTreinadas.trim().toIntOrNull()
                    if (qtd == null || qtd <= 0) {
                        return "Informe um número de pessoas treinadas maior que zero."
                    }
                }
                null
            }

            EtapaRelatorio.ATIVIDADES -> {
                if (config.atividadesExecutadasObrigatoria && rascunho.atividadesExecutadas.isBlank()) {
                    return "Descreva as atividades executadas para ${rascunho.motivo.titulo}."
                }
                if (config.evidenciaAtividadeObrigatoria) {
                    val temEvidenciaAtividade = rascunho.evidencias.any {
                        it.etapa == EtapaEvidencia.ATIVIDADE && it.disponivel && it.caminhoLocal.isNotBlank()
                    }
                    if (!temEvidenciaAtividade) {
                        return "Adicione ao menos uma foto ou vídeo das atividades."
                    }
                }
                null
            }

            EtapaRelatorio.PLANO_ACAO -> {
                if (config.planoAcaoObrigatorio && rascunho.planoAcao.isBlank()) {
                    return "Preencha o plano de ação para ${rascunho.motivo.titulo}."
                }
                if (config.conclusaoVisitaObrigatoria && rascunho.conclusaoVisita.isBlank()) {
                    return "Preencha a conclusão sobre a visita."
                }
                if (config.evidenciaGeralObrigatoria) {
                    val temEvidenciaGeral = rascunho.evidencias.any {
                        (it.etapa == EtapaEvidencia.GERAL || it.etapa == EtapaEvidencia.ANEXO_EXTRA) &&
                                it.disponivel && it.caminhoLocal.isNotBlank()
                    }
                    if (!temEvidenciaGeral) {
                        return "Adicione ao menos uma evidência para ${rascunho.motivo.titulo}."
                    }
                }
                null
            }

            EtapaRelatorio.ASSINATURA -> validarFinalizacao(rascunho)
        }
    }

    fun validarFinalizacao(rascunho: RelatorioRascunho): String? {
        for (etapa in listOf(
            EtapaRelatorio.CONSTATACOES,
            EtapaRelatorio.ATIVIDADES,
            EtapaRelatorio.PLANO_ACAO
        )) {
            val erroEtapa = validarEtapa(etapa, rascunho)
            if (erroEtapa != null) return erroEtapa
        }

        val config = CatalogoMotivoCampoRelatorio.configuracaoPara(rascunho.motivo)
        if (config.assinaturaEspecialistaObrigatoria) {
            val especialistaOk = rascunho.assinaturaEspecialista.confirmada &&
                    !rascunho.assinaturaEspecialista.caminhoImagemLocal.isNullOrBlank()
            if (!especialistaOk) {
                return "A assinatura do especialista é obrigatória para finalizar o relatório."
            }
        }

        if (config.assinaturaClienteObrigatoria) {
            val clienteOk = rascunho.assinaturaCliente.confirmada &&
                    !rascunho.assinaturaCliente.caminhoImagemLocal.isNullOrBlank()
            if (!clienteOk) {
                return "A assinatura do cliente é obrigatória para este motivo."
            }
        }

        return null
    }
}