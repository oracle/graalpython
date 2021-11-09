/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser;

import java.util.HashMap;

/**
 * Cache that is used in the generated parser.
 * @param <T>
 */
public class RuleResultCache <T> {

        private final Parser parser;

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

        public RuleResultCache(Parser parser) {
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

        public <T2> T2 putResult(int pos, int ruleId, T2 node) {
            HashMap posCache = mainCache.get(pos);
            if (posCache == null) {
                posCache = new HashMap();
                mainCache.put(pos, posCache);
            }
            posCache.put(ruleId, new CachedItem(node, parser.mark()));
            return node;
        }

    }
