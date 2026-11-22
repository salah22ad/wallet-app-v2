package com.hpp.daftree.models;

public class LanguageItem {
    private String code;
    private String name;        // الاسم بلغة التطبيق الحالية
    private String nativeName;  // الاسم بلغته الأصلية
    private String flag;
    private boolean isRtl;

    public LanguageItem(String code, String name, String nativeName, String flag, boolean isRtl) {
        this.code = code;
        this.name = name;
        this.nativeName = nativeName;
        this.flag = flag;
        this.isRtl = isRtl;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getNativeName() { return nativeName; }
    public String getFlag() { return flag; }
    public boolean isRtl() { return isRtl; }
}