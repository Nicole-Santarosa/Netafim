package com.example.netafim.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.netafim.data.sync.SyncOperationDao
import com.example.netafim.data.sync.SyncOperationEntity
import com.example.netafim.data.sync.SyncConflictDao
import com.example.netafim.data.sync.SyncConflictEntity

// Banco de dados local do aplicativo, responsável por armazenar os atendimentos e as operações de sincronização. Isso permite que os repositórios acessem suas respectivas tabelas por meio do Room.
@Database(
    entities = [
        AtendimentoEntity::class,
        SyncOperationEntity::class,
        SyncConflictEntity::class
    ],
    version = 2,
    exportSchema = true // Permite exportar o esquema do banco de dados para um arquivo JSON, útil para versionamento e documentação.
)
// Classe abstrata responsável pela criação do banco de dados local e pela definição das DAOs.
abstract class NetafimDatabase : RoomDatabase() {

    // Função abstrata responsável pela persistência de dados de atendimento.
    abstract fun atendimentoDao(): AtendimentoDao

    // DAO responsável pelas operações pendentes de sincronização.
    abstract fun syncOperationDao(): SyncOperationDao

    // DAO responsável por armazenar os conflitos de sincronização.
    abstract fun syncConflictDao(): SyncConflictDao

    companion object {

        /**
         * Migração da versão 1 para a versão 2.
         *
         * Preserva a tabela ATENDIMENTO_LOCAL e seus registros, criando somente as duas novas tabelas de sincronização.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {

            // Executa a migração do banco de dados, criando as tabelas e índices necessários para a sincronização.
            override fun migrate(db: SupportSQLiteDatabase) {

                // Cria a fila local de operações de sincronização.
                db.execSQL("""
                CREATE TABLE IF NOT EXISTS `OPERACAO_SYNC` (
                    `idOperacao` TEXT NOT NULL,
                    `usuarioDonoId` INTEGER NOT NULL,
                    `entidade` TEXT NOT NULL,
                    `id` TEXT NOT NULL,
                    `tipo` TEXT NOT NULL,
                    `versaoBase` INTEGER,
                    `campos` TEXT NOT NULL,
                    `feitoEm` TEXT NOT NULL,
                    `tentativas` INTEGER NOT NULL,
                    `estado` TEXT NOT NULL,
                    PRIMARY KEY(`idOperacao`)
                )
            """)

                // Índices para filtrar operações por usuário e estado.
                db.execSQL("""
                CREATE INDEX IF NOT EXISTS
                `index_OPERACAO_SYNC_usuarioDonoId_estado`
                ON `OPERACAO_SYNC` (`usuarioDonoId`, `estado`)
            """)

                db.execSQL("""
                CREATE INDEX IF NOT EXISTS
                `index_OPERACAO_SYNC_usuarioDonoId_id`
                ON `OPERACAO_SYNC` (`usuarioDonoId`, `id`)
            """)

                // Cria a tabela que preserva os dois lados de cada conflito.
                db.execSQL("""
                CREATE TABLE IF NOT EXISTS `CONFLITO_SYNC` (
                    `idConflito` TEXT NOT NULL,
                    `usuarioDonoId` INTEGER NOT NULL,
                    `idAtendimento` TEXT NOT NULL,
                    `registroLocalJson` TEXT NOT NULL,
                    `registroServidorJson` TEXT NOT NULL,
                    `versaoLocal` INTEGER NOT NULL,
                    `versaoServidor` INTEGER NOT NULL,
                    `dataIdentificacao` INTEGER NOT NULL,
                    PRIMARY KEY(`idConflito`)
                )
            """)

                // Índices para consultar conflitos por atendimento e data.
                db.execSQL("""
                CREATE INDEX IF NOT EXISTS
                `index_CONFLITO_SYNC_usuarioDonoId_idAtendimento`
                ON `CONFLITO_SYNC` (`usuarioDonoId`, `idAtendimento`)
            """)

                db.execSQL("""
                CREATE INDEX IF NOT EXISTS
                `index_CONFLITO_SYNC_usuarioDonoId_dataIdentificacao`
                ON `CONFLITO_SYNC` (`usuarioDonoId`, `dataIdentificacao`)
            """)
            }
        }

        // Instância do banco de dados.
        @Volatile // Garantia de visibilidade entre threads, evitando problemas de concorrência.
        private var INSTANCE: NetafimDatabase? = null

        // Função que retorna a instância do banco de dados, criando-a se necessário.
        fun getDatabase(context: Context): NetafimDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetafimDatabase::class.java,
                    "netafim_local.db"
                )
                    // Registra a migração da versão 1 para a versão 2.
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
