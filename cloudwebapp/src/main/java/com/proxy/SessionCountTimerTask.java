package com.proxy;

import java.util.TimerTask;

public class SessionCountTimerTask extends TimerTask {
    String nvrSessionId;
    CloudMQ cloud;

    SessionCountTimerTask(String nvrSessionId, CloudMQ cloud)
    {
        this.nvrSessionId = nvrSessionId;
        this.cloud = cloud;
    }

    @Override
    public void run() {
        cloud.decSessionCount(nvrSessionId);
    }
}
