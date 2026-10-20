package com.sitelink.manager.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SiteEntity::class,
        TrustedHostKeyEntity::class,
        ConnectionLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun trustedHostKeyDao(): TrustedHostKeyDao
    abstract fun connectionLogDao(): ConnectionLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sitelink_manager.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Populate initial editable sample site
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).siteDao().insertSite(
                                    SiteEntity(
                                        name = "ITC Grand Chola",
                                        ipAddress = "14.142.185.130",
                                        description = "Luxury Hotel - Chennai Site Infrastructure",
                                        notes = "Default hotel site configuration. Ports: 8080/8082/8083 (ZMP), 2222 (VM), 2223 (BM)."
                                    )
                                )
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
