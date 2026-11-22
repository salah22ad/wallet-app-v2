package com.hpp.daftree.models;
import android.content.Context;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.hpp.daftree.utils.SecureLicenseManager;

public class AppAuth {

    /**
     * هذه هي الدالة السحرية التي ستستخدمها في كل المشروع
     * تحاول جلب المستخدم من فايربيس، فإذا فشلت (بسبب الحظر) تجلبه من الذاكرة المحلية
     */
    public static String getUserID(Context context) {
        // 1. المحاولة الأولى: هل الفايربيس متصل ويعمل؟
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return user.getUid();
        }

        // 2. المحاولة الثانية: الفايربيس مفصول (أو محظور)، هات الـ UID المخزن
        String storedUID = SecureLicenseManager.getStoredUID(context);

        if (storedUID != null && !storedUID.isEmpty()) {
            return storedUID;
        }

        // 3. كارثة: لا يوجد فايربيس ولا يوجد تخزين محلي (مستخدم جديد كلياً)
        return null;
    }

    /**
     * دالة مساعدة لمعرفة هل نحن نعمل في وضع الأوفلاين (المحلي) أم لا
     */
    public static boolean isOfflineMode() {
        return FirebaseAuth.getInstance().getCurrentUser() == null;
    }
}
