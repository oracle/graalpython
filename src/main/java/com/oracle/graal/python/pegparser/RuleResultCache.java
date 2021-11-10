/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser;

import java.util.HashMap;

/**
 * Cache that is used in the generated parser. Really just a convenient
 * interface around nested HashMaps mapping
 * <code>
 * (int tokenPos) -> (int ruleId) -> (T cachedItem)
 * </code>
 */
class RuleResultCache <T> {

    private final AbstractParser parser;

    private static class CachedItem<T> {

        final T node;
        final int endPos;

        CachedItem(T node, int endPos) {
            this.node = node;
            this.endPos = endPos;
        }
    }

    // HashMap<start pos, HashMap<rule id, (result, end pos)>>
    private final HashMap<Integer, HashMap<Integer, CachedItem>> mainCache;

    public RuleResultCache(AbstractParser parser) {
        this.parser = parser;
        this.mainCache = new HashMap<>();
    }

    public boolean hasResult(int pos, int ruleId) {
        return mainCache.containsKey(pos) && mainCache.get(pos).containsKey(ruleId);
    }

    public T getResult(int pos, int ruleId) {
        CachedItem item = mainCache.get(pos).get(ruleId);
        parser.reset(item.endPos);
        return (T)item.node;
    }

    public T putResult(int pos, int ruleId, T node) {
        HashMap posCache = mainCache.get(pos);
        if (posCache == null) {
            posCache = new HashMap();
            mainCache.put(pos, posCache);
        }
        posCache.put(ruleId, new CachedItem(node, parser.mark()));
        return node;
    }
}
