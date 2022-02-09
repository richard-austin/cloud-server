package com.proxy;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class CloudInstanceMap {
    private final Logger logger = (Logger) LoggerFactory.getLogger("CLOUD");

    Map<String, Cloud> map;
    // List of keys by Cloud instance value, used for remove by value
    Map<Cloud, List<String>> keyList;

    CloudInstanceMap()
    {
        map = new ConcurrentHashMap<>();
        keyList = new ConcurrentHashMap<>();
    }

    /**
     * put: Put a Cloud instance into the product key map
     * @param key: The key (Session is or product key)
     * @param cloud: The Cloud instance
     * @return: The Cloud instance
     */
    Cloud put(String key, Cloud cloud)
    {
        if(!keyList.containsKey(cloud)) {
            List<String> list = new ArrayList<>();
            list.add(key);
            keyList.put(cloud, list);
        }
        else
            keyList.get(cloud).add(key);

        return map.put(key, cloud);
    }

    /**
     * get: Get Cloud instance by key (Session id or product id)
     * @param key: The key
     * @return: The Cloud instance
     */
    Cloud get(String key)
    {
        return map.get(key);
    }

    /**
     * remove: Remove the cloud instance for this key
     * @param key: The key
     * @return: The Cloud instance
     */
    Cloud remove(String key)
    {
        Cloud inst = null;
        try {
            inst = map.get(key);
            map.remove(key);
            keyList.get(inst).remove(key);
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
    Cloud removeByValue(Cloud cloud)
    {
        List<String> kl = this.keyList.get(cloud);
        kl.forEach(key -> map.remove(key));

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
}
