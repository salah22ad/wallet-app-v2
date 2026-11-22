package com.hpp.daftree.database;

import androidx.room.ColumnInfo; // إضافة
import androidx.room.Embedded;

import java.util.Objects;

public class AccountWithBalance {

    @Embedded
    public Account account; // Account محمي بالفعل داخلياً

    @ColumnInfo(name = "balance") // يطابق: AS balance
    public double balance;

    @ColumnInfo(name = "transactionCount") // يطابق: AS transactionCount
    public long transactionCount;

    @ColumnInfo(name = "lastTransactionDate") // يطابق: AS lastTransactionDate
    public long lastTransactionDate;
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountWithBalance that = (AccountWithBalance) o;
        // مقارنة الكائن الداخلي Account ومقارنة الرصيد
        return Double.compare(that.balance, balance) == 0 &&
                Objects.equals(account, that.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(account, balance);
    }
}