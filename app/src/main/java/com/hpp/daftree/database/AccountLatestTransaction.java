package com.hpp.daftree.database;

/**
 * POJO لنتيجة استعلام: أحدث عملية لكل حساب
 */
public class AccountLatestTransaction {
    public int accountId;
    public long latestTimestamp; // millis
}