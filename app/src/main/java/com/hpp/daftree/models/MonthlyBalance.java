package com.hpp.daftree.models;

import androidx.room.ColumnInfo; // إضافة

public class MonthlyBalance {

    @ColumnInfo(name = "month")
    public String month; // بصيغة "YYYY-MM"

    @ColumnInfo(name = "totalDebit")
    public double totalDebit;

    @ColumnInfo(name = "totalCredit")
    public double totalCredit;
}