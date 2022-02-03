package com.proxy;

import com.google.common.collect.HashBiMap;
import java.util.function.BiConsumer;

public class CloudInstanceMap {
    HashBiMap<String, Cloud> _mapByProdId;
    HashBiMap<String, Cloud> _mapBySessionId;

    CloudInstanceMap()
    {
        _mapByProdId = HashBiMap.create();
        _mapBySessionId = HashBiMap.create();
    }

    /**
     * putByProductId: Put a Cloud instance into the product key map
     * @param key
     * @param cloud
     * @return: The Cloud instance
     */
    Cloud putByProductId(String key, Cloud cloud)
    {
        return _mapByProdId.put(key, cloud);
    }

    Cloud putBySessionId(String key, Cloud cloud)
    {
        return _mapBySessionId.put(key, cloud);
    }

    Cloud getByProductId(String key)
    {
        return _mapByProdId.get(key);
    }

    Cloud getBySessionId(String key)
    {
        return _mapBySessionId.get(key);
    }

    Cloud removeByProductId(String productId)
    {
        Cloud inst = _mapByProdId.get(productId);
        if(inst != null) {
            _mapByProdId.remove(productId);
            _mapBySessionId.inverse().remove(inst);
        }

        return inst;
    }

    Cloud removeBySessionId(String productId)
    {
        Cloud inst = _mapBySessionId.get(productId);
        if(inst != null) {
            _mapBySessionId.remove(productId);
            _mapByProdId.inverse().remove(inst);
        }
        return inst;
    }

    public boolean containsProductKey(String prodId) {
        return _mapByProdId.containsKey(prodId);
    }

    public void forEach(BiConsumer<? super String, ? super Cloud> action)
    {
        _mapByProdId.forEach(action);
    }
}
