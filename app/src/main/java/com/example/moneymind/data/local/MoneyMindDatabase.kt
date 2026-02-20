package com.example.moneymind.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LedgerEntryEntity::class,
        OwnedAccountEntity::class,
        OwnerAliasEntity::class,
        InstallmentPlanEntity::class,
        QuickTemplateEntity::class,
        BudgetTargetEntity::class,
        MonthlyClosingEntity::class,
        ClassificationRuleEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MoneyMindDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun ownedAccountDao(): OwnedAccountDao
    abstract fun ownerAliasDao(): OwnerAliasDao
    abstract fun installmentPlanDao(): InstallmentPlanDao
    abstract fun quickTemplateDao(): QuickTemplateDao
    abstract fun budgetTargetDao(): BudgetTargetDao
    abstract fun monthlyClosingDao(): MonthlyClosingDao
    abstract fun classificationRuleDao(): ClassificationRuleDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ledger_entries_new (
                        id TEXT NOT NULL,
                        fingerprint TEXT NOT NULL,
                        occurredAtMillis INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        category TEXT NOT NULL,
                        description TEXT NOT NULL,
                        merchant TEXT,
                        source TEXT NOT NULL,
                        spendingKind TEXT NOT NULL,
                        countedInExpense INTEGER NOT NULL,
                        accountMask TEXT,
                        counterpartyName TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO ledger_entries_new (
                        id, fingerprint, occurredAtMillis, amount, type, category, description,
                        merchant, source, spendingKind, countedInExpense, accountMask, counterpartyName
                    )
                    SELECT
                        id,
                        CAST((occurredAtMillis / 60000) AS TEXT) || '|' ||
                        type || '|' ||
                        amount || '|' ||
                        lower(trim(description)) || '|' ||
                        id AS fingerprint,
                        occurredAtMillis,
                        amount,
                        type,
                        category,
                        description,
                        merchant,
                        source,
                        spendingKind,
                        countedInExpense,
                        accountMask,
                        counterpartyName
                    FROM ledger_entries
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE ledger_entries")
                db.execSQL("ALTER TABLE ledger_entries_new RENAME TO ledger_entries")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_ledger_entries_fingerprint ON ledger_entries(fingerprint)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ledger_entries_occurredAtMillis ON ledger_entries(occurredAtMillis)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quick_templates (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        merchant TEXT,
                        category TEXT NOT NULL,
                        spendingKind TEXT NOT NULL,
                        repeatMonthlyDay INTEGER,
                        enabled INTEGER NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS budget_targets (
                        key TEXT NOT NULL,
                        category TEXT,
                        amount INTEGER NOT NULL,
                        PRIMARY KEY(key)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS monthly_closings (
                        month TEXT NOT NULL,
                        carryIn INTEGER NOT NULL,
                        expectedClosing INTEGER NOT NULL,
                        actualClosing INTEGER,
                        delta INTEGER,
                        closedAtMillis INTEGER,
                        PRIMARY KEY(month)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS classification_rules (
                        id TEXT NOT NULL,
                        keyword TEXT NOT NULL,
                        spendingKind TEXT NOT NULL,
                        category TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: MoneyMindDatabase? = null

        fun getInstance(context: Context): MoneyMindDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoneyMindDatabase::class.java,
                    "money_mind.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
