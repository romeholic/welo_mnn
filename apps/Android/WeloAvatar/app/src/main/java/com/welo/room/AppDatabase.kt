package com.welo.room

import android.content.Context
import androidx.room.Database
import androidx.room.TypeConverters
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.liulishuo.okdownload.BuildConfig
import com.welo.room.dao.ChatDao
import com.welo.room.table.ChatMessage
import com.welo.room.table.ChatSession
import com.welo.room.table.User
import com.welo.util.LogUtil

@Database(
    entities = [User::class, ChatSession::class, ChatMessage::class],
    version = 1, // 版本号增加
    exportSchema = true // 启用导出模式以便更好地跟踪模式更改
)
@TypeConverters(DateConverter::class, ListStringConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_chat_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(DatabaseCallback())

                // 开发环境使用破坏性迁移，生产环境使用安全降级
                if (BuildConfig.DEBUG) {
                    builder.fallbackToDestructiveMigration()
                } else {
                    builder.fallbackToDestructiveMigrationOnDowngrade()
                }

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                LogUtil.d("dataBase", "数据库创建成功，版本: ${db.version}")
            }
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys = ON;")
                LogUtil.d("dataBase", "数据库打开，当前版本: ${db.version}")
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                // 可以在这里重新初始化一些必要的基础数据
            }
        }

        // 数据库迁移策略 1→2
        private val MIGRATION_1_2 = androidx.room.migration.Migration(1, 2) {
            it.execSQL("ALTER TABLE chat_messages ADD COLUMN aiResponseTime INTEGER")
        }
    }
}