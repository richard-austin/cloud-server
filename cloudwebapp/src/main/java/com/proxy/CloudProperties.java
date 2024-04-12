package com.proxy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import grails.config.Config;
import grails.core.GrailsApplication;

import java.io.FileReader;
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

    public void setGrailsApplication(GrailsApplication grailsApplication) throws Exception {
        this.grailsApplication = grailsApplication;
        CloudProperties.theInstance = this;
        setupConfigParams();
    }

    static final int REQUEST_TIMEOUT_SECS = 300;
    private String USERNAME;
    private String PASSWORD;
    private String AMQ_URL;
    private String AMQ_TRUSTSTORE_PATH;
    private String AMQ_TRUSTSTORE_PASSWORD;
    private String PRIVATE_KEY_PATH;
    private int BROWSER_FACING_PORT;
    private String LOG_LEVEL;
    private JsonObject cloudCreds;


    private void setupConfigParams() throws Exception {
        Config config = grailsApplication.getConfig();
        USERNAME = config.getProperty("cloud.username");
        PASSWORD = config.getProperty("cloud.password");
        AMQ_URL = config.getProperty("cloud.mqURL");
        AMQ_TRUSTSTORE_PATH = config.getProperty("cloud.mqTrustStorePath");
        AMQ_TRUSTSTORE_PASSWORD = config.getProperty("cloud.mqTrustStorePassword");
        PRIVATE_KEY_PATH = config.getProperty("cloud.privateKeyPath");
        BROWSER_FACING_PORT = Integer.parseInt(Objects.requireNonNull(config.getProperty("cloud.browserFacingPort")));
        LOG_LEVEL = config.getProperty("cloud.logLevel");
        cloudCreds = getCloudCreds();
    }

    public JsonObject getCloudCreds() throws Exception {
        JsonObject json;
        Config config = grailsApplication.getConfig();
        try {
            Gson gson = new Gson();
            json = gson.fromJson(new FileReader(config.toProperties().getProperty("appHomeDirectory")+"/cloud-creds.json"), JsonObject.class);
        }
        catch(Exception ex) {
            throw new Exception("Error when getting Cloud credentials");
        }
        return json;
    }
    public String getAMQ_KEYSTORE_PATH() {return cloudCreds.get("mqClientKSPath").getAsString();}

    public String getAMQ_KEYSTORE_PASSWORD() {
        return cloudCreds.get("mqClientKSPW").getAsString();
    }
    public String getAMQ_USER() {return cloudCreds.get("mqUser").getAsString();}
    public String getAMQ_PASSWORD() {return cloudCreds.get("mqPw").getAsString();}

    public String getAMQ_URL(){return AMQ_URL;}
    public String getAMQ_TRUSTSTORE_PATH() {
        return AMQ_TRUSTSTORE_PATH;
    }
    public String getAMQ_TRUSTSTORE_PASSWORD() {
        return AMQ_TRUSTSTORE_PASSWORD;
    }
    public String getUSERNAME() { return USERNAME;}
    public String getPASSWORD() {return PASSWORD;}
    public String getPRIVATE_KEY_PATH() { return PRIVATE_KEY_PATH; }  // Private key to decrypt the product ID
    public int getBROWSER_FACING_PORT() { return BROWSER_FACING_PORT; }
    public String getLOG_LEVEL() { return LOG_LEVEL; }
}

