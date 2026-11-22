package com.hpp.daftree.models;

import com.hpp.daftree.database.Transaction;
import java.util.*;

public class TransactionUtils {
    /**
     * إزالة التكرارات بناءً على firestoreId مع الحفاظ على الترتيب
     * @param transactions القائمة الأصلية (قد تحتوي على تكرارات)
     * @return قائمة جديدة بدون تكرارات (أول ظهور لكل firestoreId)
     */
    public static List<Transaction> distinctByFirestoreId(List<Transaction> transactions) {
        if (transactions == null || transactions.size() <= 1) return transactions;
        Map<String, Transaction> uniqueMap = new LinkedHashMap<>();
        for (Transaction tx : transactions) {
            String key = tx.getFirestoreId(); // non-null guaranteed
            if (!uniqueMap.containsKey(key)) {
                uniqueMap.put(key, tx); // نحتفظ بأول ظهور فقط
            }
        }
        return new ArrayList<>(uniqueMap.values());
    }
}