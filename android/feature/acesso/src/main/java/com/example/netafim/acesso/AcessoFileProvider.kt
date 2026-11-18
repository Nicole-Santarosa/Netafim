package com.example.netafim.acesso

import androidx.core.content.FileProvider

/**
 * FileProvider só do módulo de acesso, para entregar a cópia dos dados do titular como arquivo. É uma
 * subclasse de propósito: o app pode ter outro FileProvider (câmera, relatórios), e duas declarações com o
 * mesmo nome de classe no manifesto não se mesclam.
 */
class AcessoFileProvider : FileProvider()
