package com.proxy;

import grails.config.Config;
import grails.core.GrailsApplication;

import java.util.Objects;

public final class CloudProperties {
    GrailsApplication grailsApplication;
    static private CloudProperties theInstance;

    private CloudProperties()
    {
    }

    public static CloudProperties getInstance()
    {
        return theInstance;
    }

    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
        CloudProperties.theInstance = this;
        setupConfigParams();
    }

    static final int REQUEST_TIMEOUT_SECS = 300;
    private String USERNAME;
    private String PASSWORD;
    private String AMQ_URL;
    private String AMQ_TRUSTSTORE_PATH;
    private String AMQ_KEYSTORE_PATH;
    private String AMQ_KEYSTORE_PASSWORD;
    private String AMQ_TRUSTSTORE_PASSWORD;
    private String AMQ_USER;
    private String AMQ_PASSWORD;
    private String PRIVATE_KEY_PATH;
    private int BROWSER_FACING_PORT;
    private String LOG_LEVEL;


    private void setupConfigParams()
    {
        Config config = grailsApplication.getConfig();
        USERNAME = config.getProperty("cloud.username");
        PASSWORD = config.getProperty("cloud.password");
        AMQ_URL = config.getProperty("cloud.mqURL");
        AMQ_TRUSTSTORE_PATH = config.getProperty("cloud.mqTrustStorePath");
        AMQ_KEYSTORE_PATH = config.getProperty("cloud.mqKeyStorePath");
        AMQ_TRUSTSTORE_PASSWORD = config.getProperty("cloud.mqTrustStorePassword");
        AMQ_KEYSTORE_PASSWORD = config.getProperty("cloud.mqKeyStorePassword");
        AMQ_USER = config.getProperty("cloud.mqUser");
        AMQ_PASSWORD = config.getProperty("cloud.mqPassword");
        PRIVATE_KEY_PATH = config.getProperty("cloud.privateKeyPath");
        BROWSER_FACING_PORT = Integer.parseInt(Objects.requireNonNull(config.getProperty("cloud.browserFacingPort")));
        LOG_LEVEL = config.getProperty("cloud.logLevel");
    }

    public String getAMQ_URL(){return AMQ_URL;}
    public String getAMQ_TRUSTSTORE_PATH() {
        return AMQ_TRUSTSTORE_PATH;
    }
    public String getAMQ_KEYSTORE_PATH() {
        return AMQ_KEYSTORE_PATH;
    }
    public String getAMQ_KEYSTORE_PASSWORD() {return AMQ_KEYSTORE_PASSWORD;}
    public String getAMQ_TRUSTSTORE_PASSWORD() {
        return AMQ_TRUSTSTORE_PASSWORD;
    }
    public String getAMQ_USER() {return AMQ_USER;}
    public String getAMQ_PASSWORD() {return AMQ_PASSWORD;}
    public String getUSERNAME() { return USERNAME;}
    public String getPASSWORD() {return PASSWORD;}
    public String getPRIVATE_KEY_PATH() { return PRIVATE_KEY_PATH; }  // Private key to decrypt the product ID
    public int getBROWSER_FACING_PORT() { return BROWSER_FACING_PORT; }
    public String getLOG_LEVEL() { return LOG_LEVEL; }
}

