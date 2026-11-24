package com.hpp.daftree.database;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.R;
import com.hpp.daftree.models.UUIDGenerator;
import com.hpp.daftree.budget.data.BudgetCategory;
import com.hpp.daftree.budget.data.BudgetCategoryDao;
import com.hpp.daftree.budget.data.BudgetDatabaseMigration;
import com.hpp.daftree.budget.data.BudgetEntry;
import com.hpp.daftree.budget.data.BudgetEntryDao;
import com.hpp.daftree.budget.data.MonthlyBudgetLimit;
import com.hpp.daftree.budget.data.MonthlyBudgetLimitDao;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.syncmanagers.SyncCoordinator;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                Account.class,
                Transaction.class,
                DeletionLog.class,
                User.class,
                Currency.class,
                AccountType.class,
                MigrationFlag.class,
                ExternalAccountEntity.class,
                RecurringOperation.class,
                RecurringOperationTracking.class,
                ExternalTransactionEntity.class,
                BudgetCategory.class,
                BudgetEntry.class,
                MonthlyBudgetLimit.class}
        , version = 20, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {


    // DAOs
    public abstract DeletionLogDao deletionLogDao();

    public abstract AccountDao accountDao();

    public abstract TransactionDao transactionDao();

    public abstract UserDao userDao();

    public abstract CurrencyDao currencyDao();

    public abstract AccountTypeDao accountTypeDao();


    public abstract ExternalDebtDao externalDebtDao();

    public abstract MigrationFlagDao migrationFlagDao();

    public abstract RecurringOperationDao recurringOperationDao();

    public abstract RecurringOperationTrackingDao recurringOperationTrackingDao();

    public abstract BudgetCategoryDao budgetCategoryDao();

    public abstract BudgetEntryDao budgetEntryDao();

    public abstract MonthlyBudgetLimitDao monthlyBudgetLimitDao();

    // Singleton Instance
    private static volatile AppDatabase INSTANCE;

    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    private static final String DATABASE_NAME = "daftree_database";


    public static AppDatabase getDatabase(final Context context) {
        // 1. الفحص الذهبي: إعادة البناء إذا كانت النسخة فارغة أو الاتصال مغلقاً
        if (INSTANCE == null || !INSTANCE.isOpen()) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null || !INSTANCE.isOpen()) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, DATABASE_NAME)
                            .addMigrations(
                                    MIGRATION_1_2,
                                    createMigration2To3(context),
                                    MIGRATION_3_4, MIGRATION_4_5,
                                    MIGRATION_5_6, MIGRATION_6_7,
                                    MIGRATION_7_8, MIGRATION_8_9,
                                    MIGRATION_9_10, MIGRATION_10_11,
                                    MIGRATION_11_12, MIGRATION_12_13,
                                    MIGRATION_13_14, MIGRATION_14_16,
                                    MIGRATION_16_17,MIGRATION_17_18,
                                    createMigration18To19(context),
                                    MIGRATION_19_20
                            )
                            .addCallback(new Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    BudgetDatabaseMigration.seedDefaultCategoriesIfNeeded(db);
                                    // تفعيل المفاتيح الخارجية عند الإنشاء لأول مرة
                                    db.execSQL("PRAGMA foreign_keys = ON;");
                                }

                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    db.execSQL("PRAGMA foreign_keys = ON;");

                                    // ✅ الحل: استخدام خيط واحد منظم لجميع المهام بدلاً من إنشاء خيوط عشوائية
                                    databaseWriteExecutor.execute(() -> {
                                        try {
                                            // 1. تحديث حالة العملات مرة واحدة
                                            INSTANCE.currencyDao().updateAllSyncStatus("NEW", true);
                                            // 2. فحص الأعلام بتسلسل منظم (Sequential) وليس متوازي
                                            runMaintenanceTasks(context);
                                        } catch (Exception e) {
                                            Log.e("AppDatabase", "Error in onOpen maintenance", e);
                                        }
                                    });
                                }
                            })
                            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // ✅ مفعل وممتاز
                            .build();
                }
            }
        }
        return INSTANCE;
    }
    private static void runMaintenanceTasks(Context context) {
        String[] flags = {
                "migration_16_17_pending",
                "migration_12_13_pending",
                "migration_11_12_pending",
                "migration_9_10_pending",
                "migration_8_9_pending",
                "migration_6_7_pending",
                "migration_17_18_pending",
                "migration_18_19_pending",
                "migration_19_20_pending"
        };

        boolean needsSync = false;
        for (String flag : flags) {
            Integer value = INSTANCE.migrationFlagDao().getFlagValue(flag);
            if (value != null && value == 1) {
                needsSync = true;
                break;
            }
        }

        if (needsSync) {
            Log.i("AppDatabase", "Triggering one-time sync for pending migrations");
            // نشغل المزامنة مرة واحدة فقط لجميع الأعلام
            SyncCoordinator.triggerSyncWithCallback(context, () -> {
                // عند الانتهاء، نصفر جميع الأعلام في معاملة واحدة (Transaction)
                databaseWriteExecutor.execute(() -> {
                    INSTANCE.runInTransaction(() -> {
                        for (String flag : flags) {
                            INSTANCE.migrationFlagDao().setFlag(flag, 0);
                        }
                    });
                });
            });
        }
    }
    /**
     * دالة مساعدة لفحص حالة المهاجرة وتشغيل المزامنة لمرة واحدة فقط بعد التحديث
     */
    private static void checkAndTriggerSync(Context context, AppDatabase database, String flagKey) {
        try {
            Integer pending = database.migrationFlagDao().getFlagValue(flagKey);
            if (pending != null && pending == 1) {
                Log.i("AppDatabase", "بدء المزامنة التلقائية بعد المهاجرة: " + flagKey);
                SyncCoordinator.triggerSyncWithCallback(context, () -> {
                    // بعد انتهاء المزامنة بنجاح، نقوم بتصفير العلم (Flag) لمنع التكرار
                    Executors.newSingleThreadExecutor().execute(() -> {
                        database.migrationFlagDao().setFlag(flagKey, 0);
                        Log.i("AppDatabase", "تم إكمال المزامنة وتصفير العلم لـ: " + flagKey);
                    });
                });
            }
        } catch (Exception e) {
            Log.e("AppDatabase", "خطأ أثناء فحص علم المهاجرة: " + flagKey, e);
        }
    }

    public static void destroyInstance() {
        synchronized (AppDatabase.class) {
            if (INSTANCE != null) {
                if (INSTANCE.isOpen()) {
                    INSTANCE.close();
                }
                INSTANCE = null;
                Log.d("AppDatabase", "Database instance destroyed and set to null.");
            }
        }
    }

    // ================= MIGRATIONS =================

    static final Migration MIGRATION_19_20 = BudgetDatabaseMigration.MIGRATION_19_20;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (" +
                    "`id` INTEGER NOT NULL, `name` TEXT, `email` TEXT, `address` TEXT, `phone` TEXT, " +
                    "`password` TEXT, `syncStatus` TEXT, `lastModified` INTEGER NOT NULL, `createdAt` INTEGER, " +
                    "PRIMARY KEY(`id`))");
        }
    };

    static Migration createMigration2To3(final Context context) {
        return new Migration(2, 3) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase database) {
                database.execSQL("ALTER TABLE user_profile ADD COLUMN company TEXT");
                database.execSQL("ALTER TABLE user_profile ADD COLUMN profileImageUri TEXT");
                database.execSQL("CREATE TABLE IF NOT EXISTS `currencies` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `firestoreId` TEXT, " +
                        "`ownerUID` TEXT, `lastModified` INTEGER NOT NULL, `syncStatus` TEXT)");
                database.execSQL("CREATE TABLE IF NOT EXISTS `account_types` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `firestoreId` TEXT, " +
                        "`ownerUID` TEXT, `lastModified` INTEGER NOT NULL, `syncStatus` TEXT)");

                String uid = FirebaseAuth.getInstance().getUid() != null ? FirebaseAuth.getInstance().getUid() : "";

                addDefaultCurrencies(database, uid, context);
                addDefaultAccountTypes(database, uid, context);
            }

            private void addDefaultCurrencies(SupportSQLiteDatabase db, String uid, Context ctx) {
                Resources localizedResources = LanguageManager.getLocalizedResources(ctx);

                String[] currencies = {
                        localizedResources.getString(R.string.currency_local),
                        localizedResources.getString(R.string.currency_saudi),
                        localizedResources.getString(R.string.currency_dollar)
                };

                for (String currency : currencies) {
                    String firestoreId = UUIDGenerator.generateSequentialUUID();
                    long lastModified = System.currentTimeMillis();
                    String sql = "INSERT INTO currencies (name, firestoreId, ownerUID, lastModified, syncStatus) VALUES (" +
                            DatabaseUtils.sqlEscapeString(currency) + ", " +
                            DatabaseUtils.sqlEscapeString(firestoreId) + ", " +
                            DatabaseUtils.sqlEscapeString(uid) + ", " +
                            lastModified + ", " +
                            DatabaseUtils.sqlEscapeString("SYNCED") + ")";
                    db.execSQL(sql);
                }
            }

            private void addDefaultAccountTypes(SupportSQLiteDatabase db, String uid, Context ctx) {
                Resources localizedResources = LanguageManager.getLocalizedResources(ctx);

                String[] accountTypes = {
                        localizedResources.getString(R.string.account_type_customer),
                        localizedResources.getString(R.string.account_type_supplier),
                        localizedResources.getString(R.string.account_type_general)
                };

                for (String type : accountTypes) {
                    String firestoreId = UUIDGenerator.generateSequentialUUID();
                    long lastModified = System.currentTimeMillis();
                    String sql = "INSERT INTO account_types (name, firestoreId, ownerUID, lastModified, syncStatus) VALUES (" +
                            DatabaseUtils.sqlEscapeString(type) + ", " +
                            DatabaseUtils.sqlEscapeString(firestoreId) + ", " +
                            DatabaseUtils.sqlEscapeString(uid) + ", " +
                            lastModified + ", " +
                            DatabaseUtils.sqlEscapeString("SYNCED") + ")";
                    db.execSQL(sql);
                }
            }
        };
    }

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN importID INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE user_profile ADD COLUMN ownerUID TEXT ");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN billType TEXT");
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            Log.e("MIGRATION", "MIGRATION_6_7 Start");
            db.execSQL(
                    "INSERT INTO currencies (name, firestoreId, ownerUID, lastModified, syncStatus) " +
                            "SELECT 'محلي', '', '', strftime('%s','now')*1000, 'SYNCED' " +
                            "WHERE NOT EXISTS (SELECT 1 FROM currencies WHERE name='محلي')"

            );

            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transactions_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`accountId` INTEGER NOT NULL, " +
                            "`type` INTEGER NOT NULL, " +
                            "`amount` REAL NOT NULL, " +
                            "`details` TEXT, " +
                            "`currencyId` INTEGER NOT NULL, " +
                            "`timestamp` INTEGER, " +
                            "`firestoreId` TEXT, " +
                            "`syncStatus` TEXT, " +
                            "`lastModified` INTEGER NOT NULL, " +
                            "`importID` INTEGER NOT NULL, " +
                            "`billType` TEXT, " +
                            "`currencyFirestoreId` TEXT NOT NULL, " +
                            "`accountFirestoreId` TEXT NOT NULL, " +
                            "`ownerUID` TEXT NOT NULL, " +
                            "FOREIGN KEY(`currencyId`) REFERENCES `currencies`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT" +
                            ")"
            );

            db.execSQL(
                    "INSERT INTO `transactions_new` (" +
                            "`accountId`,`type`,`amount`,`details`,`currencyId`,`timestamp`," +
                            "`firestoreId`,`syncStatus`,`lastModified`,`importID`,`billType`,`currencyFirestoreId`,`accountFirestoreId`,`ownerUID`" +
                            ") " +
                            "SELECT " +
                            "T.`accountId`, IFNULL(T.`type`,0), IFNULL(T.`amount`,0.0), T.`details`, " +
                            "IFNULL(C.`id`, (SELECT id FROM currencies WHERE name='محلي' LIMIT 1)), " +
                            "T.`timestamp`, T.`firestoreId`, 'EDITED' as syncStatus, IFNULL(T.`lastModified`,0), " +
                            "IFNULL(T.`importID`,0), T.`billType`, " +
                            "IFNULL(C.`firestoreId`, (SELECT firestoreId FROM currencies WHERE name='محلي' LIMIT 1)), " +
                            "COALESCE(A.`firestoreId`, ''), COALESCE(A.`ownerUID`, '') " +
                            "FROM `transactions` AS T " +
                            "LEFT JOIN `currencies` AS C ON C.`name` = T.`currency` " +
                            "LEFT JOIN `accounts` AS A ON A.`id` = T.`accountId` ORDER BY T.`id`"
            );

            db.execSQL("DROP TABLE IF EXISTS `transactions`");
            db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`");

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_currencyId` ON `transactions` (`currencyId`)");

            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`firestoreId` TEXT NOT NULL, " +
                            "`syncStatus` TEXT, " +
                            "`lastModified` INTEGER NOT NULL, " +
                            "`ownerUID` TEXT NOT NULL, " +
                            "`accountName` TEXT, " +
                            "`phoneNumber` TEXT, " +
                            "`accountType` TEXT, " +
                            "`acTypeFirestoreId` TEXT NOT NULL" +
                            ")"
            );
            db.execSQL(
                    "INSERT INTO `accounts_new` (id, firestoreId, syncStatus, lastModified, ownerUID, accountName, phoneNumber, accountType, acTypeFirestoreId) " +
                            "SELECT acc.id, COALESCE(acc.firestoreId, ''), 'EDITED', IFNULL(acc.lastModified, 0), COALESCE(acc.ownerUID, ''), " +
                            "acc.accountName, acc.phoneNumber, acc.accountType, " +
                            "COALESCE((SELECT AcT.firestoreId FROM account_types AS AcT WHERE AcT.name = acc.accountType), '') " +
                            "FROM `accounts` AS acc"
            );
            db.execSQL("DROP TABLE `accounts`");
            db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`");

            db.execSQL("CREATE TABLE IF NOT EXISTS `migration_flags` (" +
                    "`flag_key` TEXT PRIMARY KEY NOT NULL, " +
                    "`value` INTEGER NOT NULL)");

            db.execSQL("INSERT OR REPLACE INTO migration_flags (flag_key, value) VALUES ('migration_6_7_pending', 1)");

            db.execSQL("ALTER TABLE currencies ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE account_types ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE account_types SET isDefault = 1 , syncStatus = 'NEW'");
            db.execSQL("UPDATE currencies SET isDefault = 1, syncStatus = 'NEW'");

        }
    };
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 1. Create External Accounts Table
            database.execSQL("CREATE TABLE IF NOT EXISTS `external_accounts` (" +
                    "`firestoreId` TEXT NOT NULL, " +
                    "`ownerUID` TEXT, " +
                    "`ownerName` TEXT, " +
                    "`accountName` TEXT, " +
                    "`phoneNumber` TEXT, " +
                    "`lastModified` INTEGER NOT NULL, " +
                    "`isDeleted` INTEGER NOT NULL DEFAULT 0, " +
                    "`deletedBy` TEXT, " +
                    "PRIMARY KEY(`firestoreId`))");

            // 2. Create External Transactions Table
            database.execSQL("CREATE TABLE IF NOT EXISTS `external_transactions` (" +
                    "`firestoreId` TEXT NOT NULL, " +
                    "`accountFirestoreId` TEXT, " +
                    "`currencyFirestoreId` TEXT, " +
                    "`amount` REAL NOT NULL, " +
                    "`type` INTEGER NOT NULL, " +
                    "`details` TEXT, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`lastModified` INTEGER NOT NULL, " +
                    "`ownerUID` TEXT, " +
                    "`currencyName` TEXT, " +
                    "`isDeleted` INTEGER NOT NULL DEFAULT 0, " +
                    "`deletedBy` TEXT, " +
                    "PRIMARY KEY(`firestoreId`), " +
                    "FOREIGN KEY(`accountFirestoreId`) REFERENCES `external_accounts`(`firestoreId`) ON UPDATE NO ACTION ON DELETE CASCADE)");

            // 3. Create Index
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_external_transactions_accountFirestoreId` ON `external_transactions` (`accountFirestoreId`)");
            database.execSQL("ALTER TABLE accounts ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE accounts ADD COLUMN deletedBy TEXT");

            // إضافة الحقول إلى جدول account_types
            database.execSQL("ALTER TABLE account_types ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE account_types ADD COLUMN deletedBy TEXT");

            // إضافة الحقول إلى جدول currencies
            database.execSQL("ALTER TABLE currencies ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE currencies ADD COLUMN deletedBy TEXT");
            database.execSQL("INSERT OR REPLACE INTO migration_flags (flag_key, value) VALUES ('migration_8_9_pending', 1)");
            long lastModified = System.currentTimeMillis();
            // إضافة الحقول إلى جدول transactions
            database.execSQL("ALTER TABLE transactions ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE transactions ADD COLUMN deletedBy TEXT");
            database.execSQL("UPDATE accounts SET syncStatus = 'EDITED', lastModified = " + lastModified);
            database.execSQL("UPDATE account_types SET  syncStatus = 'EDITED', lastModified = " + lastModified);
            database.execSQL("UPDATE currencies SET syncStatus = 'EDITED', lastModified = " + lastModified);
            database.execSQL("UPDATE transactions SET syncStatus = 'EDITED', lastModified = " + lastModified);
        }
    };
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.e("MIGRATION", "MIGRATION_7_8 Start - Adding currency codes and symbols");


            // التحقق من وجود العمود code قبل إضافته
            Cursor cursor = database.query("PRAGMA table_info(currencies)");
            boolean hasCode = false;
            boolean hasSymbol = false;

            try {
                while (cursor.moveToNext()) {
                    String columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    if ("code".equals(columnName)) {
                        hasCode = true;
                    }
                    if ("symbol".equals(columnName)) {
                        hasSymbol = true;
                    }
                }
            } finally {
                cursor.close();
            }

            // إضافة العمودين فقط إذا لم يكونا موجودين
            if (!hasCode) {
                database.execSQL("ALTER TABLE currencies ADD COLUMN code TEXT");
            }

            if (!hasSymbol) {
                database.execSQL("ALTER TABLE currencies ADD COLUMN symbol TEXT");
            }

            // تحديث البيانات للعملات الموجودة
            updateExistingCurrencies(database);
        }


        private void updateExistingCurrencies(SupportSQLiteDatabase db) {
            Map<String, CurrencyInfo> currencyMap = createCurrencyMapping();

            Cursor cursor = db.query("SELECT id, name FROM currencies");
            try {
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));

                    CurrencyInfo currencyInfo = findMatchingCurrency(name, currencyMap);
                    if (currencyInfo != null) {
                        updateCurrency(db, id, currencyInfo);
                    } else {
                        updateCurrencyWithDefault(db, id, name);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        private Map<String, CurrencyInfo> createCurrencyMapping() {
            Map<String, CurrencyInfo> map = new HashMap<>();
            String currencySymbol = "﷼";

            // العملات العربية
            addCurrencyVariants(map, currencySymbol, "ر.س",
                    "ريال سعودي", "ريال السعودي", "سعودي", "ريال سعودي", "الريال السعودي", "sar", "riyal", "saudi");

            addCurrencyVariants(map, currencySymbol, "ر.ي",
                    "ريال يمني", "ريال اليمن", "يمني", "ريال يمني", "الريال اليمني", "yer", "yemeni", "yemen", "yemen rial", "yemeni rial");

            addCurrencyVariants(map, "ج.م", "EGP",
                    "جنيه مصري", "جنيه مصرى", "مصري", "egp", "egyptian", "egypt");

            addCurrencyVariants(map, "د.إ", "AED",
                    "درهم إماراتي", "درهم اماراتي", "درهم", "aed", "uae", "emirati");

            addCurrencyVariants(map, "د.ك", "KWD",
                    "دينار كويتي", "دينار", "kwd", "kuwaiti", "kuwait");

            addCurrencyVariants(map, "د.ع", "IQD",
                    "دينار عراقي", "عراقي", "iqd", "iraqi", "iraq");

            addCurrencyVariants(map, "ل.س", "SYP",
                    "ليرة سورية", "ليرة سوريا", "ليرة", "syp", "syrian", "syria");

            addCurrencyVariants(map, "د.أ", "JOD",
                    "دينار أردني", "دينار اردني", "أردني", "jod", "jordanian", "jordan");

            addCurrencyVariants(map, currencySymbol, "ر.ق",
                    "ريال قطري", "قطري", "qar", "qatari", "qatar");

            addCurrencyVariants(map, "د.ب", "BHD",
                    "دينار بحريني", "بحريني", "bhd", "bahraini", "bahrain");

            addCurrencyVariants(map, currencySymbol, "ر.ع",
                    "ريال عماني", "عماني", "omr", "omani", "oman");

            addCurrencyVariants(map, "ل.ل", "LBP",
                    "ليرة لبنانية", "لبناني", "lbp", "lebanese", "lebanon");

            // العملات الدولية
            addCurrencyVariants(map, "$", "USD",
                    "دولار أمريكي", "دولار امريكي", "دولار", "دولار أمريكان", "دولار امريكان",
                    "usd", "dollar", "american", "united states");

            addCurrencyVariants(map, "€", "EUR",
                    "يورو", "eur", "euro");

            addCurrencyVariants(map, "£", "GBP",
                    "جنيه إسترليني", "جنيه استرليني", "جنيه", "gbp", "pound", "british");

            addCurrencyVariants(map, "¥", "JPY",
                    "ين ياباني", "ين", "jpy", "yen", "japanese");

            addCurrencyVariants(map, "₹", "INR",
                    "روبية هندية", "روبية", "inr", "rupee", "indian");

            addCurrencyVariants(map, "₺", "TRY",
                    "ليرة تركية", "تركي", "try", "turkish", "turkey");

            addCurrencyVariants(map, currencySymbol, "SAR",
                    "sar", "saudi riyal", "saudi rial", "riyal");

            addCurrencyVariants(map, currencySymbol, "YER",
                    "yer", "yemen riyal", "yemeni riyal", "yemeni rial");

            return map;
        }

        private void addCurrencyVariants(Map<String, CurrencyInfo> map, String symbol, String code, String... names) {
            CurrencyInfo info = new CurrencyInfo(symbol, code);
            for (String name : names) {
                map.put(normalizeName(name), info);
            }
        }

        private String normalizeName(String name) {
            if (name == null) return "";

            String normalized = name
                    .replaceAll("[إأآا]", "ا")
                    .replaceAll("ى", "ي")
                    .replaceAll("ة", "ه")
                    .replaceAll("ئ", "ي")
                    .replaceAll("ؤ", "و")
                    .replaceAll("\\p{M}", "")
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toLowerCase();

            return normalized;
        }

        private CurrencyInfo findMatchingCurrency(String name, Map<String, CurrencyInfo> currencyMap) {
            if (name == null) return null;

            String normalized = normalizeName(name);
            CurrencyInfo exactMatch = currencyMap.get(normalized);
            if (exactMatch != null) {
                return exactMatch;
            }

            for (Map.Entry<String, CurrencyInfo> entry : currencyMap.entrySet()) {
                if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                    return entry.getValue();
                }
            }

            return null;
        }

        private void updateCurrency(SupportSQLiteDatabase db, int id, CurrencyInfo currencyInfo) {
            String sql = "UPDATE currencies SET code = " + DatabaseUtils.sqlEscapeString(currencyInfo.code) +
                    ", symbol = " + DatabaseUtils.sqlEscapeString(currencyInfo.symbol) +
                    ", syncStatus = 'EDITED'" +
                    " WHERE id = " + id;
            db.execSQL(sql);
            Log.d("MIGRATION", "Updated currency ID " + id + " to " + currencyInfo.code + " (" + currencyInfo.symbol + ")");
        }

        private void updateCurrencyWithDefault(SupportSQLiteDatabase db, int id, String name) {
            String defaultSymbol = name.length() >= 2 ? name.substring(0, 2) + "." : name + ".";
            String defaultCode = name.length() >= 3 ? name.substring(0, 3).toUpperCase() : name.toUpperCase();

            String sql = "UPDATE currencies SET code = " + DatabaseUtils.sqlEscapeString(defaultCode) +
                    ", symbol = " + DatabaseUtils.sqlEscapeString(defaultSymbol) +
                    ", syncStatus = 'EDITED'" +
                    " WHERE id = " + id;
            db.execSQL(sql);
            Log.d("MIGRATION", "Updated currency ID " + id + " with default: " + defaultCode + " (" + defaultSymbol + ")");
        }

        @Keep
        class CurrencyInfo {
            final String symbol;
            final String code;

            CurrencyInfo(String symbol, String code) {
                this.symbol = symbol;
                this.code = code;
            }
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.e("MIGRATION", "MIGRATION_9_10 Start - Updating currency codes and symbols (Release Safe)");
            updateExistingCurrencies(database);
            database.execSQL("INSERT OR REPLACE INTO migration_flags (flag_key, value) VALUES ('migration_9_10_pending', 1)");

        }

        private void updateExistingCurrencies(SupportSQLiteDatabase db) {
            Map<String, CurrencyInfo> currencyMap = createCurrencyMapping();

            Cursor cursor = db.query("SELECT id, name FROM currencies");
            try {
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));

                    CurrencyInfo currencyInfo = findMatchingCurrency(name, currencyMap);
                    if (currencyInfo != null) {
                        updateCurrency(db, id, currencyInfo);
                    } else {
                        updateCurrencyWithDefault(db, id, name);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        private Map<String, CurrencyInfo> createCurrencyMapping() {
            Map<String, CurrencyInfo> map = new HashMap<>();
            String currencySymbol = "﷼";

            // نفس البيانات الموجودة في createCurrencyMapping الأصلية لضمان التطابق
            // العملات العربية
            addCurrencyVariants(map, currencySymbol, "ر.س",
                    "ريال سعودي", "ريال السعودي", "سعودي", "ريال سعودي", "الريال السعودي", "sar", "riyal", "saudi");

            addCurrencyVariants(map, currencySymbol, "ر.ي",
                    "ريال يمني", "ريال اليمن", "يمني", "ريال يمني", "الريال اليمني", "yer", "yemeni", "yemen", "yemen rial", "yemeni rial");

            addCurrencyVariants(map, "ج.م", "EGP",
                    "جنيه مصري", "جنيه مصرى", "مصري", "egp", "egyptian", "egypt");

            addCurrencyVariants(map, "د.إ", "AED",
                    "درهم إماراتي", "درهم اماراتي", "درهم", "aed", "uae", "emirati");

            addCurrencyVariants(map, "د.ك", "KWD",
                    "دينار كويتي", "دينار", "kwd", "kuwaiti", "kuwait");

            addCurrencyVariants(map, "د.ع", "IQD",
                    "دينار عراقي", "عراقي", "iqd", "iraqi", "iraq");

            addCurrencyVariants(map, "ل.س", "SYP",
                    "ليرة سورية", "ليرة سوريا", "ليرة", "syp", "syrian", "syria");

            addCurrencyVariants(map, "د.أ", "JOD",
                    "دينار أردني", "دينار اردني", "أردني", "jod", "jordanian", "jordan");

            addCurrencyVariants(map, currencySymbol, "ر.ق",
                    "ريال قطري", "قطري", "qar", "qatari", "qatar");

            addCurrencyVariants(map, "د.ب", "BHD",
                    "دينار بحريني", "بحريني", "bhd", "bahraini", "bahrain");

            addCurrencyVariants(map, currencySymbol, "ر.ع",
                    "ريال عماني", "عماني", "omr", "omani", "oman");

            addCurrencyVariants(map, "ل.ل", "LBP",
                    "ليرة لبنانية", "لبناني", "lbp", "lebanese", "lebanon");

            // العملات الدولية
            addCurrencyVariants(map, "$", "USD",
                    "دولار أمريكي", "دولار امريكي", "دولار", "دولار أمريكان", "دولار امريكان",
                    "usd", "dollar", "american", "united states");

            addCurrencyVariants(map, "€", "EUR",
                    "يورو", "eur", "euro");

            addCurrencyVariants(map, "£", "GBP",
                    "جنيه إسترليني", "جنيه استرليني", "جنيه", "gbp", "pound", "british");

            addCurrencyVariants(map, "¥", "JPY",
                    "ين ياباني", "ين", "jpy", "yen", "japanese");

            addCurrencyVariants(map, "₹", "INR",
                    "روبية هندية", "روبية", "inr", "rupee", "indian");

            addCurrencyVariants(map, "₺", "TRY",
                    "ليرة تركية", "تركي", "try", "turkish", "turkey");

            addCurrencyVariants(map, currencySymbol, "SAR",
                    "sar", "saudi riyal", "saudi rial", "riyal");

            addCurrencyVariants(map, currencySymbol, "YER",
                    "yer", "yemen riyal", "yemeni riyal", "yemeni rial");

            return map;
        }

        private void addCurrencyVariants(Map<String, CurrencyInfo> map, String symbol, String code, String... names) {
            CurrencyInfo info = new CurrencyInfo(symbol, code);
            for (String name : names) {
                map.put(normalizeName(name), info);
            }
        }

        private String normalizeName(String name) {
            if (name == null) return "";

            String normalized = name
                    .replaceAll("[إأآا]", "ا")
                    .replaceAll("ى", "ي")
                    .replaceAll("ة", "ه")
                    .replaceAll("ئ", "ي")
                    .replaceAll("ؤ", "و")
                    .replaceAll("\\p{M}", "")
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toLowerCase();

            return normalized;
        }

        private CurrencyInfo findMatchingCurrency(String name, Map<String, CurrencyInfo> currencyMap) {
            if (name == null) return null;

            String normalized = normalizeName(name);
            CurrencyInfo exactMatch = currencyMap.get(normalized);
            if (exactMatch != null) {
                return exactMatch;
            }

            for (Map.Entry<String, CurrencyInfo> entry : currencyMap.entrySet()) {
                if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                    return entry.getValue();
                }
            }

            return null;
        }

        private void updateCurrency(SupportSQLiteDatabase db, int id, CurrencyInfo currencyInfo) {
            String sql = "UPDATE currencies SET code = " + DatabaseUtils.sqlEscapeString(currencyInfo.code) +
                    ", symbol = " + DatabaseUtils.sqlEscapeString(currencyInfo.symbol) +
                    ", syncStatus = 'EDITED'" +
                    " WHERE id = " + id;
            db.execSQL(sql);
            Log.d("MIGRATION", "Updated currency ID " + id + " to " + currencyInfo.code + " (" + currencyInfo.symbol + ")");
        }

        private void updateCurrencyWithDefault(SupportSQLiteDatabase db, int id, String name) {
            String defaultSymbol = name.length() >= 2 ? name.substring(0, 2) + "." : name + ".";
            String defaultCode = name.length() >= 3 ? name.substring(0, 3).toUpperCase() : name.toUpperCase();

            String sql = "UPDATE currencies SET code = " + DatabaseUtils.sqlEscapeString(defaultCode) +
                    ", symbol = " + DatabaseUtils.sqlEscapeString(defaultSymbol) +
                    ", syncStatus = 'EDITED'" +
                    " WHERE id = " + id;
            db.execSQL(sql);
            Log.d("MIGRATION", "Updated currency ID " + id + " with default: " + defaultCode + " (" + defaultSymbol + ")");
        }

        @Keep
        class CurrencyInfo {
            final String symbol;
            final String code;

            CurrencyInfo(String symbol, String code) {
                this.symbol = symbol;
                this.code = code;
            }
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE external_accounts ADD COLUMN ownerPhone TEXT ");
        }
    };
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.e("MIGRATION", "MIGRATION_11_12 Start - Updating currencyFirestoreId based on currencyId");
            database.execSQL(
                    "UPDATE transactions " +
                            "SET " +
                            "currencyFirestoreId = COALESCE((SELECT firestoreId FROM currencies WHERE currencies.id = transactions.currencyId), ''), " +
                            "syncStatus = 'EDITED' " +
                            "WHERE EXISTS (SELECT 1 FROM currencies WHERE currencies.id = transactions.currencyId)"
            );
            database.execSQL("INSERT OR REPLACE INTO migration_flags (flag_key, value) VALUES ('migration_11_12_pending', 1)");

        }
    };

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN imageEncoded TEXT");

            database.execSQL(
                    "UPDATE transactions " +
                            "SET syncStatus = 'EDITED' "
            );
            database.execSQL("INSERT OR REPLACE INTO migration_flags (flag_key, value) VALUES ('migration_12_13_pending', 1)");

        }
    };
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // إنشاء الجداول إذا لم تكن موجودة (ضمان التوافق مع التحديثات)
            db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_operations` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`firestoreId` TEXT NOT NULL, " +
                    "`accountId` INTEGER NOT NULL, " +
                    "`accountName` TEXT, " +
                    "`amount` REAL NOT NULL, " +
                    "`type` INTEGER NOT NULL, " +
                    "`details` TEXT, " +
                    "`currencyId` INTEGER NOT NULL, " +
                    "`currencyFirestoreId` TEXT NOT NULL, " +
                    "`recurrenceType` TEXT NOT NULL, " +
                    "`recurrenceDay` INTEGER NOT NULL, " +
                    "`startDate` INTEGER NOT NULL, " +
                    "`endDate` INTEGER NOT NULL, " +
                    "`active` INTEGER NOT NULL DEFAULT 1, " +
                    "`syncStatus` TEXT, " +
                    "`lastModified` INTEGER NOT NULL, " +
                    "`ownerUID` TEXT NOT NULL)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_operation_tracking` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`recurringOperationId` INTEGER NOT NULL, " +
                    "`date` INTEGER NOT NULL)");
        }
    };
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 1. إنشاء جداول مؤقتة
            db.execSQL("CREATE TEMPORARY TABLE recurring_operations_temp AS SELECT * FROM recurring_operations");
            db.execSQL("CREATE TEMPORARY TABLE recurring_operation_tracking_temp AS SELECT * FROM recurring_operation_tracking");

            // 2. حذف الجداول الأصلية
            db.execSQL("DROP TABLE recurring_operations");
            db.execSQL("DROP TABLE recurring_operation_tracking");

            // 3. إعادة إنشاء الجداول بالتعريف الصحيح (بدون DEFAULT)
            db.execSQL("CREATE TABLE `recurring_operations` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`firestoreId` TEXT NOT NULL, " +
                    "`accountId` INTEGER NOT NULL, " +
                    "`accountName` TEXT, " +
                    "`amount` REAL NOT NULL, " +
                    "`type` INTEGER NOT NULL, " +
                    "`details` TEXT, " +
                    "`currencyId` INTEGER NOT NULL, " +
                    "`currencyFirestoreId` TEXT NOT NULL, " +
                    "`recurrenceType` TEXT NOT NULL, " +
                    "`recurrenceDay` INTEGER NOT NULL, " +
                    "`startDate` INTEGER NOT NULL, " +
                    "`endDate` INTEGER NOT NULL, " +
                    "`active` INTEGER NOT NULL, " +
                    "`syncStatus` TEXT, " +
                    "`lastModified` INTEGER NOT NULL, " +
                    "`ownerUID` TEXT NOT NULL)");

            db.execSQL("CREATE TABLE `recurring_operation_tracking` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`recurringOperationId` INTEGER NOT NULL, " +
                    "`date` INTEGER NOT NULL)");

            // 4. استعادة البيانات من الجداول المؤقتة
            db.execSQL("INSERT INTO recurring_operations SELECT * FROM recurring_operations_temp");
            db.execSQL("INSERT INTO recurring_operation_tracking SELECT * FROM recurring_operation_tracking_temp");

            // 5. حذف الجداول المؤقتة
            db.execSQL("DROP TABLE recurring_operations_temp");
            db.execSQL("DROP TABLE recurring_operation_tracking_temp");
        }
    };
    static final Migration MIGRATION_14_16 = new Migration(14, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 2. حذف الجداول الأصلية
            db.execSQL("DROP TABLE IF EXISTS recurring_operations");
            db.execSQL("DROP TABLE IF EXISTS recurring_operation_tracking");

            // 3. إعادة إنشاء الجداول بالتعريف الصحيح (مطابق للكيان)
            db.execSQL("CREATE TABLE `recurring_operations` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`firestoreId` TEXT NOT NULL, " +
                    "`accountId` INTEGER NOT NULL, " +
                    "`accountName` TEXT, " +
                    "`amount` REAL NOT NULL, " +
                    "`type` INTEGER NOT NULL, " +
                    "`details` TEXT, " +
                    "`currencyId` INTEGER NOT NULL, " +
                    "`currencyFirestoreId` TEXT NOT NULL, " +
                    "`recurrenceType` TEXT NOT NULL, " +
                    "`recurrenceDay` INTEGER NOT NULL, " +
                    "`startDate` INTEGER NOT NULL, " +
                    "`endDate` INTEGER NOT NULL, " +
                    "`active` INTEGER NOT NULL, " +
                    "`syncStatus` TEXT, " +
                    "`lastModified` INTEGER NOT NULL, " +
                    "`ownerUID` TEXT NOT NULL)");

            db.execSQL("CREATE TABLE `recurring_operation_tracking` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`recurringOperationId` INTEGER NOT NULL, " +
                    "`date` INTEGER NOT NULL)");

        }
    };
    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 1. إضافة عمود deleteDate إلى الجداول الأربعة
            db.execSQL("ALTER TABLE accounts ADD COLUMN deleteDate INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE transactions ADD COLUMN deleteDate INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE currencies ADD COLUMN deleteDate INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE account_types ADD COLUMN deleteDate INTEGER NOT NULL DEFAULT 0");
            db.execSQL("INSERT OR REPLACE INTO migration_flags (flag_key, value) VALUES ('migration_16_17_pending', 1)");

            // 2. تحديث deleteDate للسجلات المحذوفة مسبقاً (isDeleted = true)
            long now = System.currentTimeMillis();
            db.execSQL("UPDATE accounts SET  syncStatus = 'EDITED', deleteDate = " + now + " WHERE isDeleted = 1");
            db.execSQL("UPDATE transactions SET  syncStatus = 'EDITED' , deleteDate = " + now + " WHERE isDeleted = 1");
            db.execSQL("UPDATE currencies SET  syncStatus = 'EDITED' , deleteDate = " + now + " WHERE isDeleted = 1");
            db.execSQL("UPDATE account_types SET  syncStatus = 'EDITED' , deleteDate = " + now + " WHERE isDeleted = 1");
        }
    };
    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("UPDATE accounts SET  syncStatus = 'EDITED'");
            db.execSQL("UPDATE account_types SET  syncStatus = 'EDITED'");
            db.execSQL("INSERT OR REPLACE INTO migration_flags (flag_key, value) VALUES ('migration_17_18_pending', 1)");

        }
    };

    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            Log.e("MIGRATION", "MIGRATION_18_19 Start - Adding lastSyncDate to entities");
            long lastSyncDateFirst = System.currentTimeMillis();

            // 1. إضافة عمود lastSyncDate إلى الجداول الأربعة
            db.execSQL("ALTER TABLE accounts ADD COLUMN lastSyncDate INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE transactions ADD COLUMN lastSyncDate INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE currencies ADD COLUMN lastSyncDate INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE account_types ADD COLUMN lastSyncDate INTEGER NOT NULL DEFAULT 0");

            // 2. تحديث قيمة lastSyncDate لتكون الوقت الحالي، وتغيير syncStatus إلى EDITED لضمان الرفع
            // نستخدم strftime('%s','now')*1000 للحصول على الطابع الزمني بالمللي ثانية عبر SQLite
            db.execSQL("UPDATE accounts SET syncStatus = 'EDITED', lastSyncDate = " + lastSyncDateFirst); //strftime('%s','now')*1000");
            db.execSQL("UPDATE transactions SET syncStatus = 'EDITED', lastSyncDate = " + lastSyncDateFirst); //strftime('%s','now')*1000");
            db.execSQL("UPDATE currencies SET syncStatus = 'EDITED', lastSyncDate =  " + lastSyncDateFirst); //strftime('%s','now')*1000");
            db.execSQL("UPDATE account_types SET syncStatus = 'EDITED', lastSyncDate = " + lastSyncDateFirst); //strftime('%s','now')*1000");

            // 3. إضافة راية المهاجرة للمزامنة لمرة واحدة
            db.execSQL("INSERT OR REPLACE INTO migration_flags (flag_key, value) VALUES ('migration_18_19_pending', 1)");

            Log.e("MIGRATION", "MIGRATION_18_19 Finished");
        }
    };

    static Migration createMigration18To19(final Context context) {
        return new Migration(18, 19) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase db) {
                Log.e("MIGRATION", "MIGRATION_18_19 Start - Adding lastSyncDate to entities");

                // تحديد وقت الانطلاق الموحد للجميع
                long lastSyncDateFirst = System.currentTimeMillis();

                // 1. إضافة عمود lastSyncDate إلى الجداول الأربعة
                db.execSQL("ALTER TABLE accounts ADD COLUMN lastSyncDate INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE transactions ADD COLUMN lastSyncDate INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE currencies ADD COLUMN lastSyncDate INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE account_types ADD COLUMN lastSyncDate INTEGER NOT NULL DEFAULT 0");

                // 2. تحديث قيمة lastSyncDate لتكون الوقت الحالي، وتغيير syncStatus إلى EDITED لضمان الرفع
                db.execSQL("UPDATE accounts SET syncStatus = 'EDITED', lastSyncDate = " + lastSyncDateFirst);
                db.execSQL("UPDATE transactions SET syncStatus = 'EDITED', lastSyncDate = " + lastSyncDateFirst);
                db.execSQL("UPDATE currencies SET syncStatus = 'EDITED', lastSyncDate = " + lastSyncDateFirst);
                db.execSQL("UPDATE account_types SET syncStatus = 'EDITED', lastSyncDate = " + lastSyncDateFirst);

                // 3. تحديث وقت المزامنة في SecureLicenseManager لكل المفاتيح بنفس الوقت الموحد
//                SecureLicenseManager secureManager = SecureLicenseManager.getInstance(context);
//                secureManager.updateLastSyncTime(SecureLicenseManager.getAccountKey(), lastSyncDateFirst);
//                secureManager.updateLastSyncTime(SecureLicenseManager.getTransactionKey(), lastSyncDateFirst);
//                secureManager.updateLastSyncTime(SecureLicenseManager.getCurrencyKey(), lastSyncDateFirst);
//                secureManager.updateLastSyncTime(SecureLicenseManager.getAccountTypeKey(), lastSyncDateFirst);

                // 4. إضافة راية المهاجرة للمزامنة لمرة واحدة
                db.execSQL("INSERT OR REPLACE INTO migration_flags (flag_key, value) VALUES ('migration_18_19_pending', 1)");

                Log.e("MIGRATION", "MIGRATION_18_19 Finished");
            }
        };
    }
}