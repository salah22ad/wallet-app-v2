package com.hpp.daftree.budget.data;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.hpp.daftree.models.UUIDGenerator;

public final class BudgetDatabaseMigration {

    private static final String TAG = "BudgetMigration";

    private BudgetDatabaseMigration() {}

    public static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            Log.i(TAG, "Starting MIGRATION_19_20: Budget tables + local account/type icon columns");
            addLocalVisualColumns(db);
            createBudgetTables(db);
            seedDefaultCategoriesIfNeeded(db, "PENDING");
            // تسجيل علم المزامنة لرفع بيانات الترقية لمرة واحدة في runMaintenanceTasks
            db.execSQL("INSERT OR REPLACE INTO migration_flags (flag_key, value) VALUES ('migration_19_20_pending', 1)");
            Log.i(TAG, "Finished MIGRATION_19_20 successfully");
        }
    };

    private static void addLocalVisualColumns(@NonNull SupportSQLiteDatabase db) {
        if (!hasColumn(db, "account_types", "iconKey")) {
            db.execSQL("ALTER TABLE account_types ADD COLUMN iconKey TEXT");
        }
        if (!hasColumn(db, "account_types", "colorHex")) {
            db.execSQL("ALTER TABLE account_types ADD COLUMN colorHex TEXT");
        }
        if (!hasColumn(db, "accounts", "iconKey")) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN iconKey TEXT");
        }
        if (!hasColumn(db, "accounts", "colorHex")) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN colorHex TEXT");
        }
        if (!hasColumn(db, "accounts", "notifyWhatsApp")) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN notifyWhatsApp INTEGER NOT NULL DEFAULT 0");
        }
        if (!hasColumn(db, "accounts", "notifySms")) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN notifySms INTEGER NOT NULL DEFAULT 0");
        }
        // وسم كافة الحسابات بـ EDITED لرفع الحقول الجديدة إلى Firestore تلقائياً عبر SyncWorker
        long now = System.currentTimeMillis();
        db.execSQL("UPDATE accounts SET syncStatus = 'PENDING', lastModified = " + now + " WHERE isDeleted = 0");
        // علم إظهار فقاعة الترقية التوضيحية عند أول عملية بعد التحديث للإصدار الجديد
        db.execSQL("INSERT OR REPLACE INTO migration_flags (flag_key, value) VALUES ('notify_upgrade_banner_pending', 1)");
    }

    private static boolean hasColumn(@NonNull SupportSQLiteDatabase db, String table, String column) {
        Cursor c = null;
        try {
            c = db.query("PRAGMA table_info(" + table + ")");
            while (c.moveToNext()) {
                int idx = c.getColumnIndex("name");
                if (idx != -1 && column.equals(c.getString(idx))) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking column " + table + "." + column, e);
        } finally {
            if (c != null) c.close();
        }
        return false;
    }

    public static void createBudgetTables(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `budget_categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`firestoreId` TEXT, " +
                "`ownerUID` TEXT, " +
                "`code` TEXT, " +
                "`name` TEXT, " +
                "`type` TEXT, " +
                "`iconKey` TEXT, " +
                "`colorHex` TEXT, " +
                "`isDefault` INTEGER NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`isDeleted` INTEGER NOT NULL, " +
                "`deleteDate` INTEGER NOT NULL, " +
                "`deletedBy` TEXT, " +
                "`lastModified` INTEGER NOT NULL, " +
                "`lastSyncDate` INTEGER NOT NULL, " +
                "`syncStatus` TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `budget_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`firestoreId` TEXT, " +
                "`ownerUID` TEXT, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`categoryFirestoreId` TEXT, " +
                "`currencyId` INTEGER NOT NULL, " +
                "`currencyFirestoreId` TEXT, " +
                "`type` TEXT, " +
                "`amount` REAL NOT NULL, " +
                "`note` TEXT, " +
                "`entryDate` INTEGER NOT NULL, " +
                "`yearMonth` TEXT, " +
                "`syncStatus` TEXT, " +
                "`lastModified` INTEGER NOT NULL, " +
                "`lastSyncDate` INTEGER NOT NULL, " +
                "`isDeleted` INTEGER NOT NULL, " +
                "`deleteDate` INTEGER NOT NULL, " +
                "`deletedBy` TEXT)");

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_entries_yearMonth` ON `budget_entries` (`yearMonth`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_entries_currencyId` ON `budget_entries` (`currencyId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_entries_categoryId` ON `budget_entries` (`categoryId`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `monthly_budget_limits` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`firestoreId` TEXT, " +
                "`ownerUID` TEXT, " +
                "`currencyId` INTEGER NOT NULL, " +
                "`currencyFirestoreId` TEXT, " +
                "`yearMonth` TEXT, " +
                "`limitAmount` REAL NOT NULL, " +
                "`syncStatus` TEXT, " +
                "`lastModified` INTEGER NOT NULL, " +
                "`lastSyncDate` INTEGER NOT NULL, " +
                "`isDeleted` INTEGER NOT NULL, " +
                "`deleteDate` INTEGER NOT NULL, " +
                "`deletedBy` TEXT)");

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_monthly_budget_limits_ym_cur` " +
                "ON `monthly_budget_limits` (`yearMonth`, `currencyId`)");
    }

    public static void seedDefaultCategoriesIfNeeded(@NonNull SupportSQLiteDatabase db) {
        seedDefaultCategoriesIfNeeded(db, "NEW");
    }

    public static void seedDefaultCategoriesIfNeeded(@NonNull SupportSQLiteDatabase db, @NonNull String initialSyncStatus) {
        Cursor cursor = null;
        try {
            cursor = db.query("SELECT COUNT(*) FROM budget_categories");
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking budget_categories count", e);
            return;
        } finally {
            if (cursor != null) cursor.close();
        }

        long now = System.currentTimeMillis();
        // فئات المصروفات الافتراضية (10 تصنيفات)
        insertDefaultCategory(db, "food", "EXPENSE", "food", "#F97316", 1, now, initialSyncStatus);
        insertDefaultCategory(db, "transport", "EXPENSE", "transport", "#3B82F6", 2, now, initialSyncStatus);
        insertDefaultCategory(db, "bills", "EXPENSE", "bills", "#F59E0B", 3, now, initialSyncStatus);
        insertDefaultCategory(db, "education", "EXPENSE", "education", "#8B5CF6", 4, now, initialSyncStatus);
        insertDefaultCategory(db, "health", "EXPENSE", "health", "#A855F7", 5, now, initialSyncStatus);
        insertDefaultCategory(db, "shopping", "EXPENSE", "shopping", "#14B8A6", 6, now, initialSyncStatus);
        insertDefaultCategory(db, "family", "EXPENSE", "family", "#EF4444", 7, now, initialSyncStatus);
        insertDefaultCategory(db, "gifts", "EXPENSE", "gifts", "#EAB308", 8, now, initialSyncStatus);
        insertDefaultCategory(db, "housing", "EXPENSE", "housing", "#22C55E", 9, now, initialSyncStatus);
        insertDefaultCategory(db, "other", "EXPENSE", "other", "#06B6D4", 10, now, initialSyncStatus);

        // فئات الدخل الافتراضية (8 تصنيفات)
        insertDefaultCategory(db, "salary", "INCOME", "salary", "#10B981", 1, now, initialSyncStatus);
        insertDefaultCategory(db, "freelance", "INCOME", "freelance", "#0EA5E9", 2, now, initialSyncStatus);
        insertDefaultCategory(db, "transfers", "INCOME", "transfers", "#14B8A6", 3, now, initialSyncStatus);
        insertDefaultCategory(db, "investments", "INCOME", "investments", "#6366F1", 4, now, initialSyncStatus);
        insertDefaultCategory(db, "sales", "INCOME", "sales", "#059669", 5, now, initialSyncStatus);
        insertDefaultCategory(db, "income_gifts", "INCOME", "gifts", "#F59E0B", 6, now, initialSyncStatus);
        insertDefaultCategory(db, "extra_income", "INCOME", "extra_income", "#8B5CF6", 7, now, initialSyncStatus);
        insertDefaultCategory(db, "income_other", "INCOME", "income_other", "#EAB308", 8, now, initialSyncStatus);
    }

    private static void insertDefaultCategory(SupportSQLiteDatabase db,
                                              String code, String type, String iconKey,
                                              String colorHex, int sortOrder, long now, String syncStatus) {
        String firestoreId = UUIDGenerator.generateSequentialUUID();
        String sql = "INSERT INTO budget_categories " +
                "(firestoreId, ownerUID, code, name, type, iconKey, colorHex, isDefault, sortOrder, isDeleted, deleteDate, deletedBy, lastModified, lastSyncDate, syncStatus) VALUES (" +
                DatabaseUtils.sqlEscapeString(firestoreId) + ", '', " +
                DatabaseUtils.sqlEscapeString(code) + ", '', " +
                DatabaseUtils.sqlEscapeString(type) + ", " +
                DatabaseUtils.sqlEscapeString(iconKey) + ", " +
                DatabaseUtils.sqlEscapeString(colorHex) + ", " +
                "1, " + sortOrder + ", 0, 0, '', " + now + ", 0, " + DatabaseUtils.sqlEscapeString(syncStatus) + ")";
        db.execSQL(sql);
    }
}
