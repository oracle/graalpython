/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.common;

import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.GetDictStorageNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.LenNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.SetDictStorageNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class HashingCollectionNodes {

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class LenNode extends PNodeWithContext {
        public abstract int execute(PHashingCollection c);

        @Specialization(limit = "4")
        static int getLen(PHashingCollection c,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(c)") HashingStorageLibrary lib) {
            return lib.length(getStorage.execute(c));
        }

        public static LenNode create() {
            return LenNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class SetItemNode extends PNodeWithContext {
        public abstract void execute(VirtualFrame frame, PHashingCollection c, Object key, Object value);

        @Specialization(limit = "4")
        void doSetItem(VirtualFrame frame, PHashingCollection c, Object key, Object value,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @Cached SetDictStorageNode setStorage,
                        @CachedLibrary("c.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = getStorage.execute(c);
            storage = lib.setItemWithFrame(storage, key, value, hasFrame, frame);
            setStorage.execute(c, storage);
        }

        public static SetItemNode create() {
            return SetItemNodeGen.create();
        }
    }

    @ImportStatic({PGuards.class})
    @GenerateUncached
    public abstract static class GetDictStorageNode extends PNodeWithContext {

        public abstract HashingStorage execute(PHashingCollection c);

        @Specialization
        static HashingStorage get(PBaseSet c) {
            return c.getDictStorage();
        }

        @Specialization
        static HashingStorage get(PDict c) {
            return c.getDictStorage();
        }

        @Specialization
        static HashingStorage get(PMappingproxy c) {
            return c.getDictStorage();
        }

        public static GetDictStorageNode create() {
            return GetDictStorageNodeGen.create();
        }

        public static GetDictStorageNode getUncached() {
            return GetDictStorageNodeGen.getUncached();
        }
    }

    @ImportStatic({PGuards.class})
    @GenerateUncached
    public abstract static class SetDictStorageNode extends PNodeWithContext {

        public abstract void execute(PHashingCollection c, HashingStorage storage);

        @Specialization
        static void set(PBaseSet c, HashingStorage storage) {
            c.setDictStorage(storage);
        }

        @Specialization
        static void set(PDict c, HashingStorage storage) {
            c.setDictStorage(storage);
        }

        @Specialization
        static void set(PMappingproxy c, HashingStorage storage) {
            c.setDictStorage(storage);
        }

        public static SetDictStorageNode create() {
            return SetDictStorageNodeGen.create();
        }

        public static SetDictStorageNode getUncached() {
            return SetDictStorageNodeGen.getUncached();
        }
    }
}
