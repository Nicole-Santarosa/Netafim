# Classes de rede serializadas com kotlinx.serialization.
-keepclassmembers class com.example.netafim.acesso.dados.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.netafim.acesso.dados.**$$serializer { *; }
