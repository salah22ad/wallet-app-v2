package com.hpp.daftree.models;

import androidx.room.ColumnInfo; // إضافة

public class MonthlySummary {

    @ColumnInfo(name = "totalDebit")
    public double totalDebit;

    @ColumnInfo(name = "totalCredit")
    public double totalCredit;

    @ColumnInfo(name = "yearMonth")
    public String yearMonth; // بصيغة "YYYY-MM"
}