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
            seedDefaultCategoriesIfNeeded(db);
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
        // فئات المصروفات الافتراضية (حالتها NEW لرفعها إلى Firestore عند الترقية/المزامنة)
        insertDefaultCategory(db, "bills", "EXPENSE", "bills", "#F59E0B", 1, now);
        insertDefaultCategory(db, "housing", "EXPENSE", "housing", "#22C55E", 2, now);
        insertDefaultCategory(db, "food", "EXPENSE", "food", "#F97316", 3, now);
        insertDefaultCategory(db, "transport", "EXPENSE", "transport", "#3B82F6", 4, now);
        insertDefaultCategory(db, "health", "EXPENSE", "health", "#A855F7", 5, now);
        insertDefaultCategory(db, "other", "EXPENSE", "other", "#06B6D4", 6, now);

        // فئات الدخل الافتراضية
        insertDefaultCategory(db, "salary", "INCOME", "salary", "#10B981", 1, now);
        insertDefaultCategory(db, "freelance", "INCOME", "freelance", "#0EA5E9", 2, now);
        insertDefaultCategory(db, "investments", "INCOME", "investments", "#6366F1", 3, now);
        insertDefaultCategory(db, "income_other", "INCOME", "income_other", "#EAB308", 4, now);
    }

    private static void insertDefaultCategory(SupportSQLiteDatabase db,
                                              String code, String type, String iconKey,
                                              String colorHex, int sortOrder, long now) {
        String firestoreId = UUIDGenerator.generateSequentialUUID();
        String sql = "INSERT INTO budget_categories " +
                "(firestoreId, ownerUID, code, name, type, iconKey, colorHex, isDefault, sortOrder, isDeleted, deleteDate, deletedBy, lastModified, lastSyncDate, syncStatus) VALUES (" +
                DatabaseUtils.sqlEscapeString(firestoreId) + ", '', " +
                DatabaseUtils.sqlEscapeString(code) + ", '', " +
                DatabaseUtils.sqlEscapeString(type) + ", " +
                DatabaseUtils.sqlEscapeString(iconKey) + ", " +
                DatabaseUtils.sqlEscapeString(colorHex) + ", " +
                "1, " + sortOrder + ", 0, 0, '', " + now + ", 0, 'NEW')";
        db.execSQL(sql);
    }
}
