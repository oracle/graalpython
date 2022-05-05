/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.pegparser;

import java.util.HashMap;

/**
 * Cache that is used in the generated parser. Really just a convenient interface around nested
 * HashMaps mapping <code>
 * (int tokenPos) -> (int ruleId) -> (T cachedItem)
 * </code>
 */
class RuleResultCache<T> {

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
    private final HashMap<Integer, HashMap<Integer, CachedItem<T>>> mainCache;

    public RuleResultCache(AbstractParser parser) {
        this.parser = parser;
        this.mainCache = new HashMap<>();
    }

    public boolean hasResult(int pos, int ruleId) {
        return mainCache.containsKey(pos) && mainCache.get(pos).containsKey(ruleId);
    }

    public T getResult(int pos, int ruleId) {
        CachedItem<T> item = mainCache.get(pos).get(ruleId);
        parser.reset(item.endPos);
        return item.node;
    }

    public T putResult(int pos, int ruleId, T node) {
        HashMap<Integer, CachedItem<T>> posCache = mainCache.get(pos);
        if (posCache == null) {
            posCache = new HashMap<>();
            mainCache.put(pos, posCache);
        }
        posCache.put(ruleId, new CachedItem<>(node, parser.mark()));
        return node;
    }

    public void clear() {
        mainCache.clear();
    }
}
