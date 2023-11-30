package com.proxy.cloudListener;

import com.proxy.CloudMQ;

import java.util.TimerTask;

public class NVRSessionTimerTask extends TimerTask {
    String productId;
    CloudMQInstanceMap map;

    NVRSessionTimerTask(String productId, CloudMQInstanceMap map)
    {
        this.productId = productId;
        this.map = map;
    }

    @Override
    public void run() {
         CloudMQ cloud = map.remove(productId);
         if(cloud != null)
            cloud.stop();
    }
}
