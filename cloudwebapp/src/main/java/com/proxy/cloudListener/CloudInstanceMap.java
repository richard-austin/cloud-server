package com.proxy.cloudListener;

import ch.qos.logback.classic.Logger;
import com.proxy.Cloud;
import grails.util.Holders;
import groovy.json.JsonBuilder;
import org.grails.web.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

//interface CloudService {
//    ObjectCommandResponse getAccounts();
//}
//
//interface ObjectCommandResponse {
//    Object responseObject = null;
//}

public class CloudInstanceMap {
    private final Logger logger = (Logger) LoggerFactory.getLogger("CLOUD");
    private final String productIdRegex = "^(?:[A-Z0-9]{4}-){3}[A-Z0-9]{4}$";
    private final long browserSessionTimeout = 2 * 60 * 1000; // Remove browser (nvrSessionId) references after 1/2 hour of non use.
    private final long nvrSessionTimeout = 30 * 1000;  // Remove NVR session references after 30 seconds without a heartbeat.
    Map<String, Cloud> map;
    // List of keys by Cloud instance value, used for remove by value
    Map<Cloud, List<String>> keyList;
    Map<String, Timer> timers;
    SimpMessagingTemplate brokerMessagingTemplate;
    final String update = new JSONObject()
            .put("message", "update")
            .toString();

    CloudInstanceMap() {
        ApplicationContext ctx = Holders.getGrailsApplication().getMainContext();
        brokerMessagingTemplate = (SimpMessagingTemplate) ctx.getBean("brokerMessagingTemplate");
        map = new ConcurrentHashMap<>();
        keyList = new ConcurrentHashMap<>();
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
        final boolean isSessionId = !key.matches(productIdRegex);
        brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update);

        if (!keyList.containsKey(cloud)) {
            List<String> list = new ArrayList<>();
            list.add(key);
            keyList.put(cloud, list);
        } else
            keyList.get(cloud).add(key);

        if (isSessionId)
            createBrowserSessionTimer(key);
        else
            createNVRSessionTimer(cloud.getProductId());

        return map.put(key, cloud);
    }

    /**
     * get: Get Cloud instance by key (Session id or product id)
     *
     * @param key: The key
     * @return: The Cloud instance
     */
    public Cloud get(String key) {
        if (timers.containsKey(key)) {
            timers.get(key).cancel();
            createBrowserSessionTimer(key);
        }
        return map.get(key);
    }

    /**
     * getSessions: Gets the number of currently active sessions against product ID
     *
     * @return: Map of number of sessions by product ID
     */
    public Map<String, Integer> getSessions() {
        Map<String, Integer> retVal = new ConcurrentHashMap<>();

        keyList.forEach((cloud, keyList) -> {
            final String productId = cloud.getProductId();

            List<String> sessions = keyList.stream()
                    .filter((key) -> !key.matches(productIdRegex))
                    .collect(Collectors.toList());

            retVal.put(productId, sessions.size());
        });
        return retVal;
    }

    /**
     * remove: Remove this key reference to the Cloud instance. If the key is a product ID, remove the Cloud instance and all references
     *
     * @param key: The key
     * @return: The Cloud instance
     */
    public Cloud remove(String key) {
        brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update);
        final boolean isProductId = key.matches(productIdRegex);

        Cloud inst = null;
        try {
            inst = map.get(key);
            if (isProductId)
                // For product ID, remove all references before removing the Cloud instance itself
                removeByValue(inst);
            else {
                keyList.get(inst).remove(key);
                map.remove(key);
                timers.get(key).cancel();
                timers.get(key).purge();
                timers.remove(key);
            }
            return inst;
        } catch (Exception ex) {
            logger.error(ex.getClass().getName() + " exception in CloudInstanceMap.remove: " + ex.getMessage());
        }
        return inst;
    }

    /**
     * removeByValue: Remove by Cloud instance value (for all keys)
     *
     * @param cloud: The Cloud instance to remove from the map
     * @return: The Cloud instance
     */
    public Cloud removeByValue(Cloud cloud) {
        brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update);
        List<String> kl = this.keyList.get(cloud);
        kl.forEach(key -> {
            map.remove(key);
            if (timers.containsKey(key)) {
                timers.get(key).cancel();
                timers.remove(key);
            }
        });

        keyList.remove(cloud);
        return cloud;
    }

    /**
     * resetNVRTimeout: Called on receiving heartbeats from the CloudProxy. Resets the timeout to start again to prevent the
     * removal of the Cloud reference (and associated browser session ID's) from the map.
     *
     * @param productId: The Cloud instance
     */
    public void resetNVRTimeout(String productId) {

        timers.get(productId).cancel();
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
    public void forEach(BiConsumer<? super Cloud, ? super List<String>> action) {
        keyList.forEach(action);
    }

    private void createBrowserSessionTimer(String nvrSessionId) {
        if (!productIdRegex.matches(nvrSessionId)) {
            BrowserSessionTimerTask task = new BrowserSessionTimerTask(nvrSessionId, this);
            Timer timer = new Timer(nvrSessionId);
            timer.schedule(task, browserSessionTimeout);
            timers.put(nvrSessionId, timer);
        }
    }

    private void createNVRSessionTimer(String productId) {
        NVRSessionTimerTask task = new NVRSessionTimerTask(productId, this);
        Timer timer = new Timer(productId);
        timer.schedule(task, nvrSessionTimeout);
        timers.put(productId, timer);
    }
}
