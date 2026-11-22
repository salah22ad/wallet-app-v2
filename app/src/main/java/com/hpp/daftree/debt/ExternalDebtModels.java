package com.hpp.daftree.debt;

import com.google.firebase.firestore.PropertyName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ExternalDebtModels {

    // 1. الأب: المستخدم (صاحب الديون)
    public static class UserDebtSummary {
        @PropertyName("ownerUid")
        public String ownerUid;
        @PropertyName("ownerName")
        public String ownerName;
        @PropertyName("ownerPhone")
        public String ownerPhone;
        // قائمة الحسابات التي يمتلكها هذا المستخدم وتخصني
        public List<AccountDebtSummary> accounts = new ArrayList<>();
    }

    // 2. الابن الأوسط: الحساب (مثلاً: "محطة صلاح"، "صلاح الحداد")
    public static class AccountDebtSummary {
        @PropertyName("accountId")
        public String accountId;
        @PropertyName("accountName")
        public String accountName;
        // قائمة العملات داخل هذا الحساب المحدد
        public List<CurrencySummary> currencies = new ArrayList<>();
    }

    // 3. الابن الأصغر: ملخص العملة
    public static class CurrencySummary implements Serializable {
        @PropertyName("currencyName")
        public String currencyName;

        @PropertyName("accountFirestoreId")
        public String accountFirestoreId;

        @PropertyName("ownerUid")
        public String ownerUid;

        @PropertyName("currencyFirestoreId")
        public String currencyFirestoreId;

        // الحقول الخاصة بالعرض (لا تحتاج PropertyName للحفظ إذا كانت محسوبة، لكن يفضل إضافتها لو أردنا حفظ الكاش)
        @PropertyName("totalDebit")
        public double totalDebit;
        @PropertyName("totalCredit")
        public double totalCredit;
        @PropertyName("netBalance")
        public double netBalance;
    }

    // 4. نموذج العملية
    public static class ExternalTransaction {
        @PropertyName("firestoreId")
        public String firestoreId;

        @PropertyName("amount")
        public double amount;

        @PropertyName("details")
        public String details;

        @PropertyName("type")
        public int type; // 1: مدين، -1: دائن

        @PropertyName("timestamp")
        public Object timestamp;

        @PropertyName("accountFirestoreId")
        public String accountFirestoreId;

        @PropertyName("ownerUID")
        public String ownerUID;

        @PropertyName("currencyFirestoreId")
        public String currencyFirestoreId;

        public ExternalTransaction() {}
    }
}