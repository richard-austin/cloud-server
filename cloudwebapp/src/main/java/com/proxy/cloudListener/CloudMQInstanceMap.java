package com.proxy.cloudListener;

import com.cloudwebapp.beans.AppContextManager;
import com.cloudwebapp.messaging.UpdateMessage;
import com.proxy.CloudMQ;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

public class CloudMQInstanceMap {
    ConcurrentHashMap<String, CloudMQ> map;
    // List of keys by CloudMQ instance value, used for remove by value
    Map<String, Timer> timers;
    SimpMessagingTemplate brokerMessagingTemplate;
    final String update;

    CloudMQInstanceMap() {
        try {
            update = new JSONObject()
                    .put("message", "update")
                    .toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        ApplicationContext ctx = AppContextManager.getAppContext();
        brokerMessagingTemplate = (SimpMessagingTemplate) ctx.getBean("brokerMessagingTemplate");
        map = new ConcurrentHashMap<>();
        timers = new ConcurrentHashMap<>();
    }

    /**
     * put: Put a CloudMQ instance into the product key map
     *
     * @param key:   The key (Session id or product key)
     * @param cloud: The CloudMQ instance
     * @return: The CloudMQ instance
     */
    CloudMQ put(String key, CloudMQ cloud) {
        final var putCloudMQ = new UpdateMessage(key, "putCloudMQ", "");
        brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", putCloudMQ);
        createNVRSessionTimer(cloud.getProductId());
        return map.put(key, cloud);
    }

    /**
     * get: Get CloudMQ instance by key (product id)
     *
     * @param key: The key
     * @return: The CloudMQ instance
     */
    public CloudMQ get(String key) {
        return map.get(key);
    }

    /**
     * remove: Remove this key reference to the CloudMQ instance.
     *
     * @param key: The key
     * @return: The CloudMQ instance
     */
    public CloudMQ remove(String key) {
        final var removeCloudMQ = new UpdateMessage(key, "removeCloudMQ", "");
        brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", removeCloudMQ);
        Timer timer = timers.remove(key);
        if(timer != null)
            timer.cancel();
        return map.remove(key);
    }

    /**
     * resetNVRTimeout: Called on receiving heartbeats from the CloudProxy. Resets the timeout to start again to prevent the
     * removal of the CloudMQ reference (and associated browser session ID's) from the map.
     *
     * @param productId: The CloudMQ instance
     */
    public void resetNVRTimeout(String productId) {
        Timer timer = timers.get(productId);
        if(timer != null)
            timer.cancel();

        createNVRSessionTimer(productId);
    }

    /**
     * clear: Stop all timers and CloudMQ instances and clear the maps
     */
    void clear() {
        timers.forEach((key, val) -> {
            val.cancel();
            val.purge();  // Prevent any further heartbeats
        });
        map.forEach((key, val)-> val.stop());
        timers.clear();
        map.clear();
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

//    /**
//     * forEach: Iterate over CloudMQ instances and their key lists
//     *
//     * @param action: Object to hols a key/value pair
//     */
//    public void forEach(BiConsumer<? super String, ? super CloudMQ> action) {
//        map.forEach(action);
//    }

    private void createNVRSessionTimer(String productId) {
        NVRSessionTimerTask task = new NVRSessionTimerTask(productId, this);
        Timer timer = new Timer(productId);
        //   private final Logger logger = (Logger) LoggerFactory.getLogger("CLOUD");
        // Remove NVR session references after 20 seconds without a heartbeat.
        final long nvrSessionTimeout = 20 * 1000;
        timer.schedule(task, nvrSessionTimeout);
        timers.put(productId, timer);
    }
}
