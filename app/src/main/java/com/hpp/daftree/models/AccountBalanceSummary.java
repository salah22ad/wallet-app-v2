package com.hpp.daftree.models;

import androidx.room.ColumnInfo; // إضافة
import java.util.Date;

public class AccountBalanceSummary {

    @ColumnInfo(name = "balance")
    public double balance;

    @ColumnInfo(name = "lastTransactionDate")
    public Date lastTransactionDate;

    @ColumnInfo(name = "accountId")
    public int accountId;

    @ColumnInfo(name = "accountName")
    public String accountName;
}