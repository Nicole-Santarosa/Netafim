# Classes de rede serializadas com kotlinx.serialization.
-keepclassmembers class com.netafim.posvenda.acesso.dados.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.netafim.posvenda.acesso.dados.**$$serializer { *; }
