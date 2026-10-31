package com.example.netafim.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AtendimentoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NetafimDatabase : RoomDatabase() {

    abstract fun atendimentoDao(): AtendimentoDao

    companion object {
        @Volatile
        private var INSTANCE: NetafimDatabase? = null

        fun getDatabase(context: Context): NetafimDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetafimDatabase::class.java,
                    "netafim_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}