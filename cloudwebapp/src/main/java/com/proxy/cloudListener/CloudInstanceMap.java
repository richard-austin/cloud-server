package com.proxy.cloudListener;

import ch.qos.logback.classic.Logger;
import com.proxy.Cloud;
import grails.util.Holders;
import org.grails.web.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class CloudInstanceMap {
 //   private final Logger logger = (Logger) LoggerFactory.getLogger("CLOUD");
    private final long nvrSessionTimeout = 20 * 1000;  // Remove NVR session references after 20 seconds without a heartbeat.
    Map<String, Cloud> map;
    // List of keys by Cloud instance value, used for remove by value
    Map<String, Timer> timers;
    SimpMessagingTemplate brokerMessagingTemplate;
    final String update = new JSONObject()
            .put("message", "update")
            .toString();

    CloudInstanceMap() {
        ApplicationContext ctx = Holders.getGrailsApplication().getMainContext();
        brokerMessagingTemplate = (SimpMessagingTemplate) ctx.getBean("brokerMessagingTemplate");
        map = new ConcurrentHashMap<>();
        timers = new ConcurrentHashMap<>();
    }

    /**
     * put: Put a Cloud instance into the product key map
     *
     * @param key:   The key (Session id or product key)
     * @param cloud: The Cloud instance
     * @return: The Cloud instance
     */
    Cloud put(String key, Cloud cloud) {
        brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update);
        createNVRSessionTimer(cloud.getProductId());
        return map.put(key, cloud);
    }

    /**
     * get: Get Cloud instance by key (product id)
     *
     * @param key: The key
     * @return: The Cloud instance
     */
    public Cloud get(String key) {
        return map.get(key);
    }

     /**
     * remove: Remove this key reference to the Cloud instance.
     *
     * @param key: The key
     * @return: The Cloud instance
     */
    public Cloud remove(String key) {
        brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update);
        Timer timer = timers.remove(key);
        if(timer != null)
            timer.cancel();
        return map.remove(key);
    }

    /**
     * resetNVRTimeout: Called on receiving heartbeats from the CloudProxy. Resets the timeout to start again to prevent the
     * removal of the Cloud reference (and associated browser session ID's) from the map.
     *
     * @param productId: The Cloud instance
     */
    public void resetNVRTimeout(String productId) {
        Timer timer = timers.get(productId);
        if(timer != null)
            timer.cancel();

        createNVRSessionTimer(productId);
    }

    /**
     * containsKey: Returns true if the key is present in the map (product id or session id)
     *
     * @param key: The key
     * @return: true if the key is present, else false
     */
    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    /**
     * forEach: Iterate over Cloud instances and their key lists
     *
     * @param action: Object to hols a key/value pair
     */
    public void forEach(BiConsumer<? super String, ? super Cloud> action) {
        map.forEach(action);
    }

     private void createNVRSessionTimer(String productId) {
        NVRSessionTimerTask task = new NVRSessionTimerTask(productId, this);
        Timer timer = new Timer(productId);
        timer.schedule(task, nvrSessionTimeout);
        timers.put(productId, timer);
    }
}
