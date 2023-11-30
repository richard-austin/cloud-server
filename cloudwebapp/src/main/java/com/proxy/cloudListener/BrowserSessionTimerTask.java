package com.proxy.cloudListener;

import java.util.TimerTask;

/**
 * BrowserSessionTimerTask: Timer task to remove session id entries from the CloudInstanceMap
 *                          when they haven't been used for more than a given period of time
 */
public class BrowserSessionTimerTask extends TimerTask {
    String nvrSessionId;
    CloudMQInstanceMap map;

    BrowserSessionTimerTask(String nvrSessionId, CloudMQInstanceMap map)
    {
        this.nvrSessionId = nvrSessionId;
        this.map = map;
    }

    @Override
    public void run() {
        map.remove(nvrSessionId);
    }
}
