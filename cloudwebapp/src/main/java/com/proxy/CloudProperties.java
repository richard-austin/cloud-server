package com.proxy;

import com.google.gson.*;
import grails.config.Config;
import grails.core.GrailsApplication;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URI;
import java.net.URISyntaxException;
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

    private String USERNAME;
    private String PASSWORD;
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
            json = gson.fromJson(new FileReader(config.toProperties().getProperty("varHomeDirectory")+"/cloud-creds.json"), JsonObject.class);
        }
        catch(Exception ex) {
            throw new Exception("Error when getting Cloud credentials");
        }
        return json;
    }

    public void setCloudCreds(String username, String password, String mqHost) throws Exception {
        JsonObject creds = getCloudCreds();
        // Update the ActiveMQW username and password if the new values are not blank
        if(!Objects.equals(username, "") && !Objects.equals(password, "")) {
            creds.remove("mqUser");
            JsonElement userName = new JsonPrimitive(username);
            creds.add("mqUser", userName);
            creds.remove("mqPw");
            creds.add("mqPw", new JsonPrimitive(password));
        }
        creds.remove("mqHost");
        creds.add("mqHost", new JsonPrimitive(mqHost));

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(creds);

        JsonElement je = JsonParser.parseString(json);
        String prettyJsonString = gson.toJson(je);

        String fileName = grailsApplication.getConfig().getProperty("varHomeDirectory") + "/cloud-creds.json";
        File file = new File(fileName);
        boolean b1 = file.setWritable(true);
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
        writer.write(prettyJsonString);
        boolean b2 = file.setWritable(false);
        writer.close();
        // Load in the new parameters
        setupConfigParams();
    }


    public String getAMQ_KEYSTORE_PATH() {return cloudCreds.get("mqClientKSPath").getAsString();}

    public String getAMQ_KEYSTORE_PASSWORD() {
        return cloudCreds.get("mqClientKSPW").getAsString();
    }
    public String getMQ_USER() {
        return cloudCreds.get("mqUser").getAsString();
    }

    public String getMQ_PASSWORD() {
        return cloudCreds.get("mqPw").getAsString();
    }

    public String getMQ_HOST() {
        var mqHost = cloudCreds.get("mqHost");
        return mqHost != null ? mqHost.getAsString() : "<none>";
    }

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

    public String getACTIVE_MQ_URL() {
        Config config = grailsApplication.getConfig();
        // Take the cloudActiveMQUrl in application.yml and replace the host with that which was set in
        //  Update ActiveMQ Credentials, leave it if it was never set
        URI uri;
        boolean hasFailover = false;
        final String failover = "failover://";
        try {
            String mqUrl = config.getProperty("cloud.mqURL");
            if(mqUrl != null && mqUrl.startsWith(failover)) {
                mqUrl = mqUrl.substring(failover.length());
                hasFailover = true;
            }

            uri = new URI(Objects.requireNonNull(mqUrl));
            if (!Objects.equals(getMQ_HOST(), "<none>"))
                uri = new URI(uri.getScheme().toLowerCase(), getMQ_HOST() + ":" + uri.getPort(),
                        uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException ignore) {
            return "";
        }
        return (hasFailover ? failover : "") + uri;
    }
}

