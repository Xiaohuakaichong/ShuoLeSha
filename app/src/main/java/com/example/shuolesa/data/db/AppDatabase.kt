package com.example.shuolesa.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AudioRecordEntity::class, AudioRecordFtsEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun audioRecordDao(): AudioRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_records ADD COLUMN recordingMode TEXT DEFAULT 'lifelog'")
                db.execSQL("ALTER TABLE audio_records ADD COLUMN audioFormat TEXT")
            }
        }

        /** v3.1：为 title/summary/transcription 建立 FTS4 索引。 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `audio_records_fts`
                    USING FTS4(`title`, `summary`, `transcription`, content=`audio_records`)
                    """.trimIndent(),
                )
                // 用现有数据重建 FTS 索引
                db.execSQL("INSERT INTO audio_records_fts(audio_records_fts) VALUES('rebuild')")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shuolesa_db",
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
