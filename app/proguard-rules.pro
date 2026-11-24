# ==============================================================================
# قواعد الحماية والتمويه (R8 High-Obfuscation) - Wallet (com.hpp.daftree)
# متوافقة مع Gradle 9.5.0 / AGP 9.3.3 / R8 Strict Full Mode
# ==============================================================================

-dontusemixedcaseclassnames
-allowaccessmodification
-repackageclasses 'c.h.d'
-overloadaggressively

-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses,JavascriptInterface,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ==============================================================================
# 1. قواعد Room Database (الحد الأدنى الاحتياطي — بقية القواعد مدمجة في room-runtime)
# ==============================================================================
-keep interface com.hpp.daftree.database.RecurringOperationDao { *; }
-keep interface com.hpp.daftree.database.RecurringOperationTrackingDao { *; }

# ==============================================================================
# 2. قواعد Firestore & Serialization (مقصورة بدقة على كلاسات البيانات فقط)
# ==============================================================================
-keepclassmembers class com.hpp.daftree.database.** {
    public <init>();
    @com.google.firebase.firestore.PropertyName *;
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

-keepclassmembers class com.hpp.daftree.models.** {
    public <init>();
    @com.google.firebase.firestore.PropertyName *;
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

-keepclassmembers class com.hpp.daftree.database.AccountLatestTransaction {
    public <init>();
    public <fields>;
}

-keepclassmembers class com.hpp.daftree.** implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==============================================================================
# 3. حماية كلاسات المزامنة والكيانات الخاصة بـ Firestore
# ==============================================================================
-keepclassmembers class com.hpp.daftree.database.User {
    public <init>();
    @com.google.firebase.firestore.PropertyName *;
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    java.util.Map devices;
    java.util.Map getDevices();
    void setDevices(java.util.Map);
}

-keepclassmembers class com.hpp.daftree.database.DeviceInfo {
    public <init>();
    @com.google.firebase.firestore.PropertyName *;
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

-keepclassmembers class com.hpp.daftree.database.Currency {
    public <init>(...);
    public void setCode(java.lang.String);
    public void setSymbol(java.lang.String);
    public java.lang.String getCode();
    public java.lang.String getSymbol();
}

-keepclassmembers class com.hpp.daftree.syncmanagers.FirestoreRestoreHelper {
    private void restoreCurrencies(...);
    private ** generateCurrencyCodeAndSymbol(...);
}

-keepclassmembers class com.hpp.daftree.database.ExternalAccountEntity {
    public <init>();
    @com.google.firebase.firestore.PropertyName *;
}

-keepclassmembers class com.hpp.daftree.database.ExternalTransactionEntity {
    public <init>();
    @com.google.firebase.firestore.PropertyName *;
}

-keepclassmembers class com.hpp.daftree.database.RecurringOperation {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

-keepclassmembers class com.hpp.daftree.database.RecurringOperationTracking {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# ==============================================================================
# 4. واجهات JavaScript و ViewBinding و WorkManager و Tink
# ==============================================================================
-keepclassmembers class com.hpp.daftree.dialogs.UpdateDialog$WebAppInterface {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static ** inflate(...);
    public static ** bind(...);
}

-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}

# ==============================================================================
# 5. Gson (@SerializedName مع السماح بالتمويه الكامل لأسماء الكلاسات والحقول)
# ==============================================================================
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ==============================================================================
# 6. تحذيرات المكتبات (موجهة بدقة بدلاً من -ignorewarnings الشامل)
# ==============================================================================
-dontwarn com.facebook.**
-dontwarn com.itextpdf.**
-dontwarn org.bouncycastle.**
-dontwarn org.spongycastle.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn org.apache.http.**
-dontwarn org.apache.commons.**
-dontwarn com.google.re2j.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.checkerframework.**

# ==============================================================================
# 7. تنظيف سجلات Logcat من نسخة الإنتاج
# ==============================================================================
-assumenosideeffects class android.util.Log {
   public static boolean isLoggable(java.lang.String, int);
   public static int v(...);
   public static int i(...);
   public static int w(...);
   public static int d(...);
   public static int e(...);
}

# ==============================================================================
# 8. بديل ضيق وآمن لقواعد firebase-auth (بدلاً من قفل كل com.google.android.gms.internal.**)
# ==============================================================================
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.android.gms.internal.firebase-auth-api.** { *; }

# ==============================================================================
# 9. حماية مسجّلات Firebase (ComponentRegistrar) و Crashlytics في R8 Strict Full Mode
# ==============================================================================
-keep class com.google.firebase.components.ComponentDiscoveryService {
    <init>();
}
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
-keep class com.google.firebase.crashlytics.CrashlyticsRegistrar { *; }
-keep class com.google.firebase.crashlytics.FirebaseCrashlytics { *; }

# ==============================================================================
# 10. قواعد وحدة الميزانية والنفقات (Room + Firestore + MPAndroidChart)
# ==============================================================================
-keep interface com.hpp.daftree.budget.data.BudgetCategoryDao { *; }
-keep interface com.hpp.daftree.budget.data.BudgetEntryDao { *; }
-keep interface com.hpp.daftree.budget.data.MonthlyBudgetLimitDao { *; }

-keepclassmembers class com.hpp.daftree.budget.data.** {
    public <init>();
    @com.google.firebase.firestore.PropertyName *;
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

-keep class com.github.mikephil.charting.animation.ChartAnimator { *; }
-keepclassmembers class com.github.mikephil.charting.** {
    public void set*(...);
    public *** get*();
}
-dontwarn com.github.mikephil.charting.**
