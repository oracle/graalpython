/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.interop;

import java.util.HashMap;
import java.util.Map;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * A container class used to store per-node attributes used by the instrumentation framework.
 *
 */
@ExportLibrary(InteropLibrary.class)
public final class InteropMap implements TruffleObject {
    private final Map<String, Object> data;

    public InteropMap(Map<String, Object> data) {
        this.data = data;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage(name = "readMember")
    @TruffleBoundary
    Object getKey(String name,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            assert hasKey(name, gil);
            return data.get(name);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage(name = "isMemberReadable")
    @TruffleBoundary
    boolean hasKey(String name,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return data.containsKey(name);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage(name = "getMembers")
    @TruffleBoundary
    Object getKeys(@SuppressWarnings("unused") boolean includeInternal,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return new InteropArray(data.keySet().toArray());
        } finally {
            gil.release(mustRelease);
        }
    }

    @TruffleBoundary
    public static InteropMap fromPDict(PDict dict) {
        Map<String, Object> map = new HashMap<>();
        final HashingStorage storage = dict.getDictStorage();
        HashingStorageIterator it = HashingStorageGetIterator.executeUncached(storage);
        while (HashingStorageIteratorNext.executeUncached(storage, it)) {
            Object key = HashingStorageIteratorKey.executeUncached(storage, it);
            Object value = HashingStorageIteratorValue.executeUncached(storage, it);
            map.put(key.toString(), value);
        }
        return new InteropMap(map);
    }

    @TruffleBoundary
    public static InteropMap fromPythonObject(PythonObject globals) {
        Map<String, Object> map = new HashMap<>();
        for (TruffleString name : globals.getAttributeNames()) {
            map.put(name.toJavaStringUncached(), globals.getAttribute(name));
        }
        return new InteropMap(map);
    }
}
