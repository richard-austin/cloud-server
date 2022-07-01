package com.proxy;

import com.proxy.cloudListener.CloudInstanceMap;

import java.util.TimerTask;

public class SessionCountTimerTask extends TimerTask {
    String nvrSessionId;
    Cloud cloud;

    SessionCountTimerTask(String nvrSessionId, Cloud cloud)
    {
        this.nvrSessionId = nvrSessionId;
        this.cloud = cloud;
    }

    @Override
    public void run() {
        cloud.decSessionCount(nvrSessionId);
    }
}
