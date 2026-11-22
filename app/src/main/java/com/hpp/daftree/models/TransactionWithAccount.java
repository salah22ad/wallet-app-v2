package com.hpp.daftree.models;

import androidx.room.ColumnInfo; // إضافة
import androidx.room.Embedded;
import com.hpp.daftree.database.Transaction;

public class TransactionWithAccount {

    @Embedded
    public Transaction transaction; // Transaction محمي بالفعل

    @ColumnInfo(name = "accountName") // يطابق: acc.accountName as accountName
    public String accountName;
}