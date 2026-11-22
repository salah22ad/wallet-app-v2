package com.hpp.daftree.database;

import android.util.Log;
import androidx.room.ColumnInfo; // إضافة
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName; // إضافة
import com.google.firebase.firestore.ServerTimestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

@IgnoreExtraProperties
@Entity(tableName = "user_profile")
public class User {

    @PrimaryKey
    @ColumnInfo(name = "id")
    private int id = 1;

    @ColumnInfo(name = "name")
    @PropertyName("name")
    private String name;

    @ColumnInfo(name = "email")
    @PropertyName("email")
    private String email;

    @ColumnInfo(name = "address")
    @PropertyName("address") // تم تصحيح الاسم بناءً على Repository (addres vs address) تأكد من الاسم في فايرستور
    private String address;

    @ColumnInfo(name = "phone")
    @PropertyName("phone")
    private String phone;

    @ColumnInfo(name = "ownerUID")
    @PropertyName("ownerUID")
    private String ownerUID;

    @ColumnInfo(name = "password")
    @Exclude // عادة لا نخزن الباسورد في فايرستور
    private String password;

    @ColumnInfo(name = "company")
    @PropertyName("company")
    private String company;

    @ColumnInfo(name = "profileImageUri")
    @PropertyName("profileImageUri")
    private String profileImageUri;

    @ColumnInfo(name = "syncStatus")
    @PropertyName("syncStatus")
    private String syncStatus;

    @ColumnInfo(name = "lastModified")
    @PropertyName("lastModified")
    private long lastModified;

    @ServerTimestamp
    @ColumnInfo(name = "createdAt")
    @PropertyName("createdAt")
    private Date createdAt;

    // --- حقول @Ignore (لا تخزن في Room لكن قد تأتي من Firestore) ---
    // نضع لها PropertyName فقط لكي يتعرف عليها ProGuard والفايرستور

    @Ignore
    @PropertyName("userType")
    private String userType;

    @Ignore
    @PropertyName("is_active")
    private boolean is_active;

    @Ignore
    @PropertyName("is_premium")
    private boolean is_premium;

    @Ignore
    @PropertyName("plan_type")
    private String plan_type = "free";

    @Ignore
    @PropertyName("premium_activated_by")
    private String premium_activated_by = "";

    @Ignore
    @PropertyName("premium_activated_at")
    private String premium_activated_at = "";

    @Ignore
    @PropertyName("subscription_start")
    private Timestamp subscription_start = new Timestamp(new Date());

    @Ignore
    @PropertyName("subscriptionStatus")
    private String subscriptionStatus = "";
    @Ignore
    @PropertyName("subscription_expiry")
    private Timestamp subscription_expiry;

    @Ignore
    @PropertyName("max_devices")
    private int max_devices;

    @Ignore
    @PropertyName("app_Version")
    private String app_Version;

    @Ignore
    @PropertyName("successfulReferrals")
    private int successfulReferrals;

    @Ignore
    @PropertyName("transactions_count")
    private int transactions_count = 0;

    @Ignore
    @PropertyName("ad_rewards")
    private int ad_rewards = 0;

    @Ignore
    @PropertyName("db_upgrade")
    private int db_upgrade;

    @Ignore
    @PropertyName("referral_rewards")
    private int referral_rewards = 0;

    @Ignore
    @PropertyName("max_transactions")
    private int max_transactions;

    @Ignore
    @PropertyName("login_count")
    private long login_count;

    @Ignore
    @PropertyName("last_login")
    private Object last_login;

    @Ignore
    @PropertyName("last_logout")
    private Object last_logout;

    @Ignore
    @PropertyName("created_at")
    private Object created_at;

    @Ignore
    @PropertyName("updated_at")
    private Object updated_at;

    @Ignore
    @PropertyName("devices")
    private Map<String, DeviceInfo> devices = new HashMap<>();

    @Ignore
    @PropertyName("fcmToken")
    private String fcmToken;
    @Ignore
    @PropertyName("app_VersionLast")
    private String app_VersionLast;
    @Ignore
    @PropertyName("guestUID")
    private String guestUID;

    @Ignore
    @PropertyName("deviceId")
    private String deviceId;


//    @Ignore
//    @Exclude
//    private boolean isDebtFeatureEnabled = false;
//    @Ignore
//    @Exclude
//    private Object debtFeatureActivatedAt;
//    @Ignore
//    @Exclude
//    private String fullName;
//    @Ignore
//    @Exclude
//    private String phoneNumber;
//    @Ignore
//    @Exclude
//    private String countryCode;

    public User() {
    }

    @Ignore
    public User(String name, String company, String address, String phone, String email, String ownerUID) {
        this.name = name;
        this.company = company;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.ownerUID = ownerUID;
    }

    // --- Getters & Setters ---
    // يجب وضع @PropertyName على الـ Getters/Setters الخاصة بـ Firestore

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @PropertyName("name")
    public String getName() { return name; }
    @PropertyName("name")
    public void setName(String name) { this.name = name; }

    @PropertyName("email")
    public String getEmail() { return email; }
    @PropertyName("email")
    public void setEmail(String email) { this.email = email; }

    @PropertyName("address")
    public String getAddress() { return address; }
    @PropertyName("address")
    public void setAddress(String address) { this.address = address; }

    @PropertyName("phone")
    public String getPhone() { return phone; }
    @PropertyName("phone")
    public void setPhone(String phone) { this.phone = phone; }

    @Exclude
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    @PropertyName("syncStatus")
    public String getSyncStatus() { return syncStatus; }
    @PropertyName("syncStatus")
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    @PropertyName("userType") // تم التغيير من Exclude إلى PropertyName لأن الـ Repo يقرؤه
    public String getUserType() { return userType; }
    @PropertyName("userType")
    public void setUserType(String userType) { this.userType = userType; }

    @PropertyName("lastModified")
    public long getLastModified() { return lastModified; }
    @PropertyName("lastModified")
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    @Exclude
    public Date getCreatedAt() { return createdAt; }
    @Exclude
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    @PropertyName("company")
    public String getCompany() { return company; }
    @PropertyName("company")
    public void setCompany(String company) { this.company = company; }

    @PropertyName("profileImageUri") // عادة الصورة URI تخزن محلياً فقط أو ترفع Storage وليس Firestore مباشرة، لكن حسب منطقك
    public String getProfileImageUri() { return profileImageUri; }
    @PropertyName("profileImageUri")
    public void setProfileImageUri(String profileImageUri) { this.profileImageUri = profileImageUri; }

    @PropertyName("ownerUID")
    public String getOwnerUID() { return ownerUID; }
    @PropertyName("ownerUID")
    public void setOwnerUID(String ownerUID) { this.ownerUID = ownerUID; }

    // ... يمكنك تطبيق نفس النمط على باقي الـ Getters/Setters للحقول التي تأتي من Firestore

    // Helper Methods


    private String convertTimestampToString(Object timestamp) {
        try {
            if (timestamp instanceof Timestamp) {
                return formatDate(((Timestamp) timestamp).toDate());
            } else if (timestamp instanceof String) {
                return (String) timestamp;
            } else if (timestamp instanceof Date) {
                return formatDate((Date) timestamp);
            }
        } catch (Exception e) {
            Log.e("convertTimestampToString", e.toString());
        }
        return "";
    }

    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(date);
    }

    @PropertyName("guestUID")
    public String getGuestUID() { return guestUID; }
    @PropertyName("guestUID")
    public void setGuestUID(String guestUID) { this.guestUID = guestUID; }
    public boolean isIs_active() { return is_active; }
    @PropertyName("is_active")
    public void setIs_active(boolean is_active) { this.is_active = is_active; }
    @PropertyName("is_premium")
    public boolean isIs_premium() { return is_premium; }
    @PropertyName("is_premium")
    public void setIs_premium(boolean is_premium) { this.is_premium = is_premium; }
    public int getMax_devices() { return max_devices; }
    @PropertyName("max_devices")
    public void setMax_devices(int max_devices) { this.max_devices = max_devices; }
    public String getApp_Version() { return app_Version; }
    @PropertyName("app_Version")
    public void setApp_Version(String app_Version) { this.app_Version = app_Version; }
    public int getSuccessfulReferrals() { return successfulReferrals; }
    @PropertyName("successfulReferrals")
    public void setSuccessfulReferrals(int successfulReferrals) { this.successfulReferrals = successfulReferrals; }
    @PropertyName("transactions_count")
    public int getTransactions_count() { return transactions_count; }
    @PropertyName("transactions_count")
    public void setTransactions_count(int transactions_count) { this.transactions_count = transactions_count; }
    @PropertyName("ad_rewards")
    public int getAd_rewards() { return ad_rewards; }
    @PropertyName("ad_rewards")
    public void setAd_rewards(int ad_rewards) { this.ad_rewards = ad_rewards; }
    @PropertyName("referral_rewards")
    public int getReferral_rewards() { return referral_rewards; }
    @PropertyName("referral_rewards")
    public void setReferral_rewards(int referral_rewards) { this.referral_rewards = referral_rewards; }
    public int getDb_upgrade() { return db_upgrade; }
    @PropertyName("db_upgrade")
    public void setDb_upgrade(int db_upgrade) { this.db_upgrade = db_upgrade; }
    @PropertyName("max_transactions")
    public int getMax_transactions() { return max_transactions; }
    @PropertyName("max_transactions")
    public void setMax_transactions(int max_transactions) { this.max_transactions = max_transactions; }
    @PropertyName("login_count")
    public long getLogin_count() { return login_count; }
    @PropertyName("login_count")
    public void setLogin_count(long login_count) { this.login_count = login_count; }
    @PropertyName("devices")
    public Map<String, DeviceInfo> getDevices() { return devices; }
    @PropertyName("devices")
    public void setDevices(Map<String, DeviceInfo> devices) { this.devices = devices; }
    public String getLast_login() { return convertTimestampToString(last_login); }
    @PropertyName("last_login")
    public void setLast_login(Object last_login) { this.last_login = last_login; }
    public String getUpdated_at() { return convertTimestampToString(updated_at); }
    public void setUpdated_at(Object updated_at) { this.updated_at = updated_at; }
    public String getLast_logout() { return convertTimestampToString(last_logout); }
    public void setLast_logout(Object last_logout) { this.last_logout = last_logout; }
    public String getFcmToken() { return fcmToken; }

    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
    @PropertyName("app_VersionLast")
    public String getLastVersion() { return app_VersionLast; }
    @PropertyName("app_VersionLast")
    public void setLastVersion(String app_VersionLast) { this.app_VersionLast = app_VersionLast; }
//@PropertyName("isDebtFeatureEnabled")
//    public boolean isIsDebtFeatureEnabled() { return isDebtFeatureEnabled; }
//    @PropertyName("isDebtFeatureEnabled")
//    public void setIsDebtFeatureEnabled(boolean isDebtFeatureEnabled) { this.isDebtFeatureEnabled = isDebtFeatureEnabled; }

    @PropertyName("created_at")
    public String getCreated_at() {
        return convertTimestampToString(created_at);
    }
    @PropertyName("created_at")
    public void setCreated_at(Object created_at) {
        this.created_at = created_at;
    }

    @PropertyName("plan_type")
    public String getPlan_type() {
        return plan_type;
    }

    @PropertyName("premium_activated_by")
    public String getPremium_activated_by() {
        return premium_activated_by;
    }

    @PropertyName("premium_activated_at")
    public String getPremium_activated_at() {
        return premium_activated_at;
    }



    @PropertyName("subscriptionStatus")
    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    @PropertyName("subscription_expiry")
    public Timestamp getSubscription_expiry() {
        return subscription_expiry;
    }

    // Setters
    @PropertyName("plan_type")
    public void setPlan_type(String plan_type) {
        this.plan_type = plan_type;
    }

    @PropertyName("premium_activated_by")
    public void setPremium_activated_by(String premium_activated_by) {
        this.premium_activated_by = premium_activated_by;
    }


    @PropertyName("subscription_start")
    public Timestamp getSubscription_start() {
        return subscription_start;
    }

    @PropertyName("subscription_start")
    public void setSubscription_start(Timestamp subscription_start) {
        this.subscription_start = subscription_start;
    }

    @PropertyName("premium_activated_at")
    public void setPremium_activated_at(String premium_activated_at) {
        this.premium_activated_at = premium_activated_at;
    }


    @PropertyName("subscriptionStatus")
    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    @PropertyName("subscription_expiry")
    public void setSubscription_expiry(Timestamp subscription_expiry) {
        this.subscription_expiry = subscription_expiry;
    }


    public static String getCurrentLocalDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }
    @PropertyName("deviceId")
    public String getDeviceId() { return deviceId; }
    @PropertyName("deviceId")
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
}