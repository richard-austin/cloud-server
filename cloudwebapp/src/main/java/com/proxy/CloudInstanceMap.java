package com.proxy;

import com.google.common.collect.HashBiMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CloudInstanceMap {
    HashBiMap<String, Cloud> _mapByProdId;
    HashBiMap<String, Cloud> _mapBySessionId;

    CloudInstanceMap()
    {
        _mapByProdId = HashBiMap.create();
        _mapBySessionId = HashBiMap.create();
    }

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

    Cloud removeBySessionIdId(String productId)
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
}
