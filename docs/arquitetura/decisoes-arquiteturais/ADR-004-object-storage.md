# ADR-004 — Object Storage privado e separado para arquivos

- **Status:** Aceito como decisão de arquitetura no Documento de Requisitos e MER/DER.
- **Referência temporal:** Documento de Requisitos (setembro/2026) e MER/DER (29/09/2026).

## Contexto
O sistema precisa armazenar fotos, vídeos, assinaturas e PDFs. Esses arquivos precisam de controle de acesso, integridade e verificação de segurança, sem misturar binários ao banco relacional.

## Decisão
Utilizar Object Storage separado e privado. O PostgreSQL armazena `storage_key`, hash e metadados, não o binário nem URL pública permanente.

## Consequências
- Banco relacional mantém metadados e referências.
- Arquivos são armazenados fora do PostgreSQL.
- Acesso deve ser autorizado e gerado sob demanda.
- Servidor confere tamanho e hash.
- Download de evidências só após `varredura_status = limpo`.
- Upload respeita tipos e etapas permitidos no modelo.

## Fluxo proposto
Conforme Contrato v0.1:
1. Android envia metadados.
2. Binário é enviado separadamente.
3. Servidor confere tamanho e SHA-256.
4. Arquivo é gravado no Object Storage.
5. Varredura de segurança é executada.
6. Download é liberado após varredura limpa.

O endpoint de upload aparece como novo no contrato. Proxy ou URL pré-assinada ainda está pendente.

## Alternativas consideradas
Os documentos consultados não registram comparação formal entre provedores. Não presumir um fornecedor específico.

## Pendências
Escolher provedor/região; definir proxy ou URL pré-assinada; definir autorização, antivírus, limites, retenção e backup; implementar e testar upload e integridade.

## Referências
Documento de Requisitos (5.2 e apêndice 10.2); MER e DER (página 3 e EVIDENCIA); Contrato de Sincronização v0.1 (seção 7 e ponto F da seção 9).
