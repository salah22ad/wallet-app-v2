package com.hpp.daftree.models;

public class DeviceInfo {
    private String deviceName;
    private String lastActiveAt;
    private boolean active;

    // getters & setters
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(String lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}