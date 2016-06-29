package org.telegram.api.engine;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 10.11.13
 * Time: 2:31
 */
public class AppInfo {
    protected int apiId;
    protected String deviceModel;
    protected String systemVersion;
    protected String appVersion;
    protected String langCode;

    public AppInfo(int apiId, String deviceModel, String systemVersion, String appVersion, String langCode) {
        this.apiId = apiId;
        this.deviceModel = deviceModel;
        this.systemVersion = systemVersion;
        this.appVersion = appVersion;
        this.langCode = langCode;
    }

    public int getApiId() {
        return apiId;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public String getSystemVersion() {
        return systemVersion;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getLangCode() {
        return langCode;
    }
}
