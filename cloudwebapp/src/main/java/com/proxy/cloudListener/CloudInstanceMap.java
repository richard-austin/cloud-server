package com.proxy.cloudListener;

import ch.qos.logback.classic.Logger;
import com.proxy.Cloud;
import org.checkerframework.checker.regex.qual.Regex;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class CloudInstanceMap {
    private final Logger logger = (Logger) LoggerFactory.getLogger("CLOUD");
    private final String productIdRegex = "^(?:[A-Z0-9]{4}-){3}[A-Z0-9]{4}$";
    private final long nvrSessionIdTimeout = 30 * 60 * 1000; // Remove nvrSessionId references after 1/2 hour of non use.

    Map<String, Cloud> map;
    // List of keys by Cloud instance value, used for remove by value
    Map<Cloud, List<String>> keyList;
    Map<String, Timer> timers;

    CloudInstanceMap()
    {
        map = new ConcurrentHashMap<>();
        keyList = new ConcurrentHashMap<>();
        timers = new ConcurrentHashMap<>();
    }

    /**
     * put: Put a Cloud instance into the product key map
     * @param key: The key (Session id or product key)
     * @param cloud: The Cloud instance
     * @return: The Cloud instance
     */
    Cloud put(String key, Cloud cloud)
    {
        final boolean isSessionId = !key.matches(productIdRegex);

        if(!keyList.containsKey(cloud)) {
            List<String> list = new ArrayList<>();
            list.add(key);
            keyList.put(cloud, list);
        }
        else
            keyList.get(cloud).add(key);

        if(isSessionId)
            createTimer(key);

        return map.put(key, cloud);
    }

    /**
     * get: Get Cloud instance by key (Session id or product id)
     * @param key: The key
     * @return: The Cloud instance
     */
    public Cloud get(String key)
    {
        if(timers.containsKey(key))
        {
            timers.get(key).cancel();
            createTimer(key);
        }
        return map.get(key);
    }

    /**
     * remove: Remove this key reference to the Cloud instance. If the key is a product ID, remove the Cloud instance and all references
     * @param key: The key
     * @return: The Cloud instance
     */
    public Cloud remove(String key)
    {
        final boolean isProductId = key.matches(productIdRegex);

        Cloud inst = null;
        try {
            inst = map.get(key);
            if(isProductId)
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
        }
        catch(Exception ex)
        {
            logger.error(ex.getClass().getName()+" exception in CloudInstanceMap.remove: "+ex.getMessage());
        }
        return inst;
    }

    /**
     * removeByValue: Remove by Cloud instance value (for all keys)
     * @param cloud: The Cloud instance to remove from the map
     * @return: The Cloud instance
     */
    public Cloud removeByValue(Cloud cloud)
    {
        List<String> kl = this.keyList.get(cloud);
        kl.forEach(key -> {
            map.remove(key);
            if(timers.containsKey(key))
            {
                timers.get(key).cancel();
                timers.remove(key);
            }
        });

        keyList.remove(cloud);
        return cloud;
    }

    /**
     * containsKey: Returns true if the key is present in the map (product id or session id)
     * @param key: The key
     * @return: true if the key is present, else false
     */
    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    /**
     * forEach: Iterate over Cloud instances and their key lists
     * @param action: Object to hols a key/value pair
     */
    public void forEach(BiConsumer<? super Cloud, ? super List<String>> action)
    {
        keyList.forEach(action);
    }

   private void createTimer(String key)
   {
       BrowserSessionTimerTask task = new BrowserSessionTimerTask(key, this);
       Timer timer = new Timer(key);
       timer.schedule(task, nvrSessionIdTimeout);
       timers.put(key, timer);
   }
}
