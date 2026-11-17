package com.example.netafim.core.modelo

// Protocolo no formato do banco: "OS-" e pelo menos quatro dígitos (OS-0477, OS-10000). Quatro dígitos é o
// mínimo, não o máximo: depois de OS-9999 vem OS-10000, e comparar como texto poria o 10000 antes do 9999.
private val formatoDoProtocolo = Regex("^OS-(\\d+)$")

// Dígitos do protocolo sem os zeros à esquerda, ou null quando o formato não é o esperado.
// Ficar com o texto dos dígitos (em vez de converter para Long) evita estouro em número muito grande.
private fun digitosDoProtocolo(protocolo: String): String? =
    formatoDoProtocolo.matchEntire(protocolo)?.groupValues?.get(1)?.trimStart('0')

// Compara dois protocolos pelo número depois de "OS-", como inteiro: o de menos dígitos é o menor e,
// com a mesma quantidade, vale a ordem dos dígitos. O mesmo número com zeros diferentes (OS-0050 e
// OS-50) dá empate. Protocolo fora do formato volta a ser comparado como texto entre si; diante de um
// protocolo válido, o válido vem primeiro. Assim a ordem continua total e o sort nunca vê um ciclo.
// Protocolo nulo (atendimento criado offline que ainda não subiu) ou vazio vem depois de qualquer protocolo,
// e dois ausentes empatam. Por isso só serve como último critério de desempate: quem ordena deve comparar
// antes os critérios principais (status, data) e deixar o protocolo por último.
fun compararProtocolos(a: String?, b: String?): Int {
    if (a.isNullOrBlank()) return if (b.isNullOrBlank()) 0 else 1
    if (b.isNullOrBlank()) return -1
    val numeroA = digitosDoProtocolo(a)
    val numeroB = digitosDoProtocolo(b)
    return when {
        numeroA != null && numeroB != null ->
            compareValuesBy(numeroA, numeroB, { it.length }, { it })
        numeroA != null -> -1
        numeroB != null -> 1
        else -> a.compareTo(b)
    }
}

// O mesmo critério como Comparator, para usar no thenBy das ordenações das listas.
val ComparadorDeProtocolo: Comparator<String?> = Comparator(::compararProtocolos)
