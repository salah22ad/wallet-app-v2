package com.hpp.daftree.models;

import androidx.room.ColumnInfo;
public class AccountTotalsByCurrency {

    @ColumnInfo(name = "currency")
    public String currency;
    @ColumnInfo(name = "totalDebit")
    public double totalDebit;
    @ColumnInfo(name = "totalCredit")
    public double totalCredit;
}
