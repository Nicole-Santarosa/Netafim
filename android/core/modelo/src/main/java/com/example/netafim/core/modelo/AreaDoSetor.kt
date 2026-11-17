package com.example.netafim.core.modelo

// Área ou setor da Netafim que abre o atendimento (só existe na origem Netafim). Os códigos são os do
// contrato da API (AreaSetor) e os mesmos do CHECK ck_atend_area_setor do banco.
// Diferente dos outros enums do core, este não tem DESCONHECIDO: o app só envia a área na abertura e nunca
// a recebe do servidor, então não existe código que ele precise guardar sem entender.
enum class AreaDoSetor(val codigo: String) {
    COMERCIAL("comercial"),
    ENGENHARIA("engenharia"),
    INSTALACAO("instalacao"),
    MARKETING("marketing"),
    POS_VENDA("pos_venda"),
}
