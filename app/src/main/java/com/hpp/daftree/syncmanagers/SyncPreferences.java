package com.hpp.daftree.syncmanagers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.hpp.daftree.R;

public class SyncPreferences {
    private static final String PREFS_NAME = "DaftreeSyncPrefs";
    private static final String KEY_FIRST_SYNC_COMPLETE = "firstSyncComplete";
    public static final String KEY_CAN_Create_Transaction = "canCreateTransaction";
    public static final String DEFAULT_CURRENCY = "defaultCurrency";
    private static final String KEY_USER_TYPE = "user_type";

    // New Key
    private static final String KEY_LAST_DEBT_SYNC = "lastDebtSyncTime";

    private final SharedPreferences sharedPreferences;
    private final Context context;

    public SyncPreferences(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.context = context;
    }

    public void setFirstSyncComplete(boolean isComplete) {
        sharedPreferences.edit().putBoolean(KEY_FIRST_SYNC_COMPLETE, isComplete).apply();
    }

    public String getUserType() {
        return sharedPreferences.getString(KEY_USER_TYPE, "user");
    }

    public void setKeyUserType(String userType) {
        sharedPreferences.edit().putString(KEY_USER_TYPE, userType).apply();
    }

    public boolean canCreateTransaction() {
        return sharedPreferences.getBoolean(KEY_CAN_Create_Transaction, true);
    }

    public void setCanCreateTransaction(boolean canCreate) {
        sharedPreferences.edit().putBoolean(KEY_CAN_Create_Transaction, canCreate).apply();
    }

    public void setLocalCurrency(String localCurrency) {
        sharedPreferences.edit().putString(DEFAULT_CURRENCY, localCurrency).apply();
    }

    public String getLocalCurrency(String localCurrency) {
        return sharedPreferences.getString(localCurrency, context.getString(R.string.local_currency));
    }

    // --- New Methods ---
    public long getLastDebtSyncTime() {
        return sharedPreferences.getLong(KEY_LAST_DEBT_SYNC, 0L);
    }

    public void setLastDebtSyncTime(long timestamp) {
        sharedPreferences.edit().putLong(KEY_LAST_DEBT_SYNC, timestamp).apply();
    }
}