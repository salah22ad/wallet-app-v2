package com.hpp.daftree.database;

import android.util.Log;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName; // إضافة

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class DeviceInfo implements Serializable {

    @PropertyName("deviceId")
    private String deviceId;

    @PropertyName("deviceName")
    private String deviceName;

    @PropertyName("deviceModel")
    private String deviceModel;

    @PropertyName("androidVersion")
    private String androidVersion;

    @PropertyName("active")
    private boolean active;

    @PropertyName("registeredAt")
    private Object registeredAt;

    @PropertyName("lastActiveAt")
    private Object lastActiveAt;

    public DeviceInfo() {
        // Required empty constructor for Firestore
    }

    // Getters and Setters with PropertyName
    @PropertyName("deviceId")
    public String getDeviceId() { return deviceId; }
    @PropertyName("deviceId")
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    @PropertyName("deviceName")
    public String getDeviceName() { return deviceName; }
    @PropertyName("deviceName")
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    @PropertyName("deviceModel")
    public String getDeviceModel() { return deviceModel; }
    @PropertyName("deviceModel")
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

    @PropertyName("androidVersion")
    public String getAndroidVersion() { return androidVersion; }
    @PropertyName("androidVersion")
    public void setAndroidVersion(String androidVersion) { this.androidVersion = androidVersion; }

    @PropertyName("active")
    public boolean isActive() { return active; }
    @PropertyName("active")
    public void setActive(boolean active) { this.active = active; }

    // Custom Getters for UI/Logic can keep their names or be annotated if stored
    // Note: registeredAt is stored as Object, accessed as String via helper
    @PropertyName("registeredAt")
    public void setRegisteredAt(Object registeredAt) {
        this.registeredAt = registeredAt;
    }

    // This getter returns String (computed), Firestore ignores it unless mapped,
    // but since field 'registeredAt' exists, we are safe.
    // We exclude this helper from Firestore mapping to avoid confusion
    // or map it to a different name if needed.
    // For now, keeping logic as is, just annotating the field is enough for serialization.
    public String getRegisteredAt() {
        return convertTimestampToString(registeredAt);
    }

    @PropertyName("lastActiveAt")
    public void setLastActiveAt(Object lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
    @PropertyName("lastActiveAt")
    public String getLastActiveAt() {
        return convertTimestampToString(lastActiveAt);
    }

    private String convertTimestampToString(Object timestamp) {
        try {
            if (timestamp instanceof Timestamp) {
                Date date = ((Timestamp) timestamp).toDate();
                return formatDate(date);
            } else if (timestamp instanceof String) {
                return (String) timestamp;
            } else if (timestamp instanceof Date) {
                return formatDate((Date) timestamp);
            }
        } catch (Exception e) {
            Log.e("convertTimestampToString",e.toString());
        }
        return "";
    }

    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(date);
    }

    public String getDisplayName() {
        if (deviceName != null && !deviceName.trim().isEmpty()) {
            return deviceName;
        }
        return deviceModel != null ? deviceModel : "";
    }

    public static String getCurrentLocalDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("deviceId", deviceId);
        map.put("deviceName", deviceName);
        map.put("deviceModel", deviceModel);
        map.put("androidVersion", androidVersion);
        map.put("active", active);
        map.put("registeredAt", registeredAt);
        map.put("lastActiveAt", lastActiveAt);
        return map;
    }
}