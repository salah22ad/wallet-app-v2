package com.hpp.daftree.utils;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.BuildConfig;

public class AppErrorHandler {

    // دالة مركزية ترجع OnFailureListener وتستخدم Analytics مجاناً
    public static OnFailureListener handle2(Context context, String className, String methodName) {
        return e -> {
            if (e instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException fex = (FirebaseFirestoreException) e;

                // نصطاد فقط أخطاء الصلاحيات المرفوضة
                if (fex.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    logErrorToAnalytics2(context, className, methodName);
                }
            } else {
                // طباعة الأخطاء الأخرى في سجل أندرويد ستوديو فقط أثناء التطوير
                Log.e("AppErrorHandler", "خطأ آخر: " + e.getMessage());
            }
        };
    }
    // لم تعد بحاجة لتمرير اسم الكلاس واسم الدالة! نكتفي بـ Context فقط
    public static OnFailureListener handle(Context context) {

        // 1. التقاط مسار الاستدعاء الحالي لمعرفة من قام بمناداة هذه الدالة
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();

        String callerClassName = "UnknownClass";
        String callerMethodName = "UnknownMethod";
        int callerLineNumber = 0;

        // 2. حلقة للبحث عن أول كلاس استدعى هذا الكود (نتجاهل كلاسات النظام وكلاسنا الحالي)
        for (StackTraceElement element : trace) {
            String cName = element.getClassName();
            if (!cName.equals(Thread.class.getName()) && !cName.equals(AppErrorHandler.class.getName())) {
                callerClassName = element.getClassName();
                // استخراج اسم الكلاس فقط (بدون مسار الحزمة الطويل)
                callerClassName = callerClassName.substring(callerClassName.lastIndexOf('.') + 1);
                callerMethodName = element.getMethodName();
                callerLineNumber = element.getLineNumber(); // السطر السحري
                break;
            }
        }

        final String finalClass = callerClassName;
        final String finalMethod = callerMethodName;
        final int finalLine = callerLineNumber;

        return e -> {
            if (e instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException fex = (FirebaseFirestoreException) e;

                if (fex.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    // نمرر رقم السطر ورسالة الخطأ الأصلية أيضاً
                    logErrorToAnalytics(context, finalClass, finalMethod, finalLine, fex.getMessage());
                }
            } else {
                Log.e("AppErrorHandler", "خطأ آخر: " + e.getMessage());
            }
        };
    }

    private static void logErrorToAnalytics(Context context, String className, String methodName, int lineNumber, String errorMessage) {
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("first_run", true);
        if(isFirstRun) return;
        FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
        String userId = AppAuth.getUserID(context);
        String uid;
        boolean isGuest = SecureLicenseManager.getInstance(context).isGuest();
        if (isGuest) {
            uid =(("Guest_" + SecureLicenseManager.getInstance(context).guestUID())!= null)? "Guest_" + SecureLicenseManager.getInstance(context):"Guest_Unlogged" ;
        } else {
            uid =(("user_" + userId)!= null)?("user_" + userId):("user_Unlogged");
        }
        // تجميع كل التفاصيل (بما فيها رقم السطر ورسالة الخطأ) في نص واحد
        String combinedLog = "Class: " + className
                +"\n"+ " | Method: " + methodName
                +"\n"+ " | Line: " + lineNumber
                +"\n"+ " | Msg: " + errorMessage
                +"\n"+ " | UID: " + uid;

        Bundle bundle = new Bundle();
        bundle.putString("full_error_details", combinedLog);

        mFirebaseAnalytics.logEvent("app_logic_error", bundle);
        Log.d("AppErrorHandler", "تم تسجيل الخطأ: " + combinedLog);
    }
    private static void logErrorToAnalytics2(Context context, String className, String methodName) {
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("first_run", true);
        if(isFirstRun) return;
        FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
        String userId = AppAuth.getUserID(context);
        String uid ="";
        boolean isGuest = SecureLicenseManager.getInstance(context).isGuest();
            if (isGuest) {
                uid =("Guest_" + SecureLicenseManager.getInstance(context).guestUID());
            } else {
                uid =("user_" + userId);
            }


        // فكرتك العبقرية: تجميع كل البيانات في نص واحد مفصول بعلامة واضحة
        String combinedLog = "Type: Permission_Denied | Class: " + className + " | Method: " + methodName + " | UID: " + uid;

        // إرسال المتغير المدمج فقط
        Bundle bundle = new Bundle();
        bundle.putString("full_error_details", combinedLog);

        mFirebaseAnalytics.logEvent("app_logic_error", bundle);
    }
    private static void logErrorToAnalytics1(Context context, String className, String methodName) {
        FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
        FirebaseAuth auth = FirebaseAuth.getInstance();

        // جلب معرف المستخدم أو تسجيله كمجهول إذا كان مسجل خروج
        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : "Unlogged_User";

        // تجهيز بيانات الخطأ كـ Bundle
        Bundle bundle = new Bundle();
        bundle.putString("error_type", "Permission_Denied");
        bundle.putString("class_name", className);
        bundle.putString("method_name", methodName);
        bundle.putString("user_uid", uid);

        // السطر السحري: إرسال الحدث إلى تحليلات فايربيز (بدون استهلاك أي كتابة في فايرستور)
        mFirebaseAnalytics.logEvent("app_logic_error", bundle);

        Log.d("AppErrorHandler", "تم إرسال الخطأ للإحصائيات: " + className);
    }
}
