package com.proxy;

import grails.config.Config;
import grails.core.GrailsApplication;

public final class CloudProperties {
    GrailsApplication grailsApplication;
    static private CloudProperties theInstance;

    private CloudProperties()
    {
    }

    static CloudProperties getInstance()
    {
        return theInstance;
    }

    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
        CloudProperties.theInstance = this;
        setupConfigParams();
    }

    private void setupConfigParams()
    {
        Config config = grailsApplication.getConfig();
        USERNAME = config.getProperty("cloud.username");
        PASSWORD = config.getProperty("cloud.password");
        TRUSTSTORE_PATH = config.getProperty("cloud.trustStorePath");
        CLOUD_KEYSTORE_PATH = config.getProperty("cloud.keyStorePath");
        TRUSTSTORE_PASSWORD = config.getProperty("cloud.trustStorePassword");
        CLOUD_KEYSTORE_PASSWORD = config.getProperty("cloud.keyStorePassword");
    }

    static final int REQUEST_TIMEOUT_SECS = 300;
    private String USERNAME;
    private String PASSWORD;
    private String TRUSTSTORE_PATH;
    private String CLOUD_KEYSTORE_PATH;
    private String CLOUD_KEYSTORE_PASSWORD;
    private String TRUSTSTORE_PASSWORD;

    public String getTRUSTSTORE_PATH() {
        return TRUSTSTORE_PATH;
    }
    public String getCLOUD_KEYSTORE_PATH() {
        return CLOUD_KEYSTORE_PATH;
    }
    public String getCLOUD_KEYSTORE_PASSWORD() {
        return CLOUD_KEYSTORE_PASSWORD;
    }
    public String getTRUSTSTORE_PASSWORD() {
        return TRUSTSTORE_PASSWORD;
    }
    public String getUSERNAME() { return USERNAME;}
    public String getPASSWORD() {return PASSWORD;}
}
