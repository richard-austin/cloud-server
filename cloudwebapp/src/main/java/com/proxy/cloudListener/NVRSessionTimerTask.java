package com.proxy.cloudListener;

import com.proxy.Cloud;

import java.util.TimerTask;

public class NVRSessionTimerTask extends TimerTask {
    String productId;
    CloudInstanceMap map;

    NVRSessionTimerTask(String productId, CloudInstanceMap map)
    {
        this.productId = productId;
        this.map = map;
    }

    @Override
    public void run() {
         Cloud cloud = map.get(productId);
         map.removeByValue(cloud);
    }
}