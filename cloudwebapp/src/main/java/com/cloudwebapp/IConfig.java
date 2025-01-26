package com.cloudwebapp;

public interface IConfig {
    String getAppHomeDirectory();
    String getVarHomeDirectory();

    String getMqTrustStorePath();
    String getUsername();
    String getPassword();
    String getMqURL();
    String getPrivateKeyPath();
    String getBrowserFacingPort();
    String getLogLevel();
}
