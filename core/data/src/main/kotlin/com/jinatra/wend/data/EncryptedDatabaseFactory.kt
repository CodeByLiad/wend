package com.jinatra.wend.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.io.File

internal object EncryptedDatabaseFactory {
    const val NAME = "wend.db"

    fun open(context: Context, name: String = NAME): WendDatabase {
        require(name.matches(Regex("[a-zA-Z0-9_-]+\\.db")))
        val target = File(context.noBackupFilesDir, name)
        return build(context, target) { DatabaseKeyVault(context, name).loadOrCreate(target.exists()) }
    }

    internal fun openWithKey(context: Context, target: File, password: ByteArray): WendDatabase {
        require(password.size == 32)
        return build(context, target) { password }
    }

    private fun build(context: Context, target: File, password: () -> ByteArray): WendDatabase = Room.databaseBuilder(context, WendDatabase::class.java, target.absolutePath)
        .openHelperFactory { configuration -> DeferredHelper(configuration, password) }.build()

    /** Room may configure helpers during injection. Key IO waits for the first DAO connection. */
    private class DeferredHelper(
        private val configuration: SupportSQLiteOpenHelper.Configuration,
        private val password: () -> ByteArray,
    ) : SupportSQLiteOpenHelper {
        private var delegate: SQLiteOpenHelper? = null
        private var wal = false
        private var closed = false
        override val databaseName: String? get() = configuration.name
        override val writableDatabase: SupportSQLiteDatabase get() = getHelper().writableDatabase
        override val readableDatabase: SupportSQLiteDatabase get() = getHelper().readableDatabase

        @Synchronized private fun getHelper(): SQLiteOpenHelper {
            check(!closed) { "Database is closed" }
            return delegate ?: run {
                System.loadLibrary("sqlcipher")
                Logger.setTarget(NoopTarget())
                helper(configuration, password()).also {
                    it.setWriteAheadLoggingEnabled(wal)
                    delegate = it
                }
            }
        }

        @Synchronized override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            wal = enabled
            delegate?.setWriteAheadLoggingEnabled(enabled)
        }

        @Synchronized override fun close() {
            if (!closed) {
                closed = true
                delegate?.close()
            }
        }
    }

    /**
     * The stock vendor helper installs a corruption handler that can delete data.
     * Retain wrongly keyed/corrupt files and forward Room's migration callbacks.
     */
    private fun helper(configuration: SupportSQLiteOpenHelper.Configuration, password: ByteArray): SQLiteOpenHelper = object : SQLiteOpenHelper(
        configuration.context, configuration.name, password, null, configuration.callback.version, 0,
        DatabaseErrorHandler { _, _ -> throw DataUnavailable("Encrypted database could not be opened; data was preserved") }, null, false,
    ) {
        override fun onConfigure(db: SQLiteDatabase) = configuration.callback.onConfigure(db)
        override fun onCreate(db: SQLiteDatabase) = configuration.callback.onCreate(db)
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = configuration.callback.onUpgrade(db, oldVersion, newVersion)
        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = configuration.callback.onDowngrade(db, oldVersion, newVersion)
        override fun onOpen(db: SQLiteDatabase) = configuration.callback.onOpen(db)
        override fun close() {
            super.close()
            password.fill(0)
        }
    }
}
