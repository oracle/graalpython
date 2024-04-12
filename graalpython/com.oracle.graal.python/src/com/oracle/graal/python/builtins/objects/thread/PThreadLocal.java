/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.thread;

import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(InteropLibrary.class)
@ImportStatic(SpecialMethodSlot.class)
public final class PThreadLocal extends PythonBuiltinObject {
    private final ThreadLocal<PDict> threadLocalDict;
    private final Object[] args;
    private final PKeyword[] keywords;

    public PThreadLocal(Object cls, Shape instanceShape, Object[] args, PKeyword[] keywords) {
        super(cls, instanceShape);
        threadLocalDict = new ThreadLocal<>();
        this.args = args;
        this.keywords = keywords;
    }

    @TruffleBoundary
    public PDict getThreadLocalDict() {
        return threadLocalDict.get();
    }

    @TruffleBoundary
    public void setThreadLocalDict(PDict dict) {
        threadLocalDict.set(dict);
    }

    public Object[] getArgs() {
        return args;
    }

    public PKeyword[] getKeywords() {
        return keywords;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        List<String> keys = getLocalAttributes();
        return new Keys(keys.toArray(PythonUtils.EMPTY_STRING_ARRAY));
    }

    @TruffleBoundary
    private List<String> getLocalAttributes() {
        PDict localDict = getThreadLocalDict();
        List<String> keys = new ArrayList<>();
        if (localDict != null) {
            final HashingStorage storage = localDict.getDictStorage();
            HashingStorageIterator it = HashingStorageGetIterator.executeUncached(storage);
            while (HashingStorageIteratorNext.executeUncached(storage, it)) {
                Object key = assertNoJavaString(HashingStorageIteratorKey.executeUncached(storage, it));
                if (key instanceof TruffleString) {
                    TruffleString strKey = (TruffleString) key;
                    keys.add(strKey.toJavaStringUncached());
                }
            }
        }
        return keys;
    }

    @Ignore
    private Object readMember(Node inliningTarget, String member, HashingStorageGetItem getItem, TruffleString.FromJavaStringNode fromJavaStringNode) {
        PDict localDict = getThreadLocalDict();
        return localDict == null ? null : getItem.execute(inliningTarget, localDict.getDictStorage(), fromJavaStringNode.execute(member, TS_ENCODING));
    }

    @ExportMessage
    public boolean isMemberReadable(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("getItem") @Cached HashingStorageGetItem getItem,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        return readMember(inliningTarget, member, getItem, fromJavaStringNode) != null;
    }

    @ExportMessage
    public boolean isMemberModifiable(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("getItem") @Cached HashingStorageGetItem getItem,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        return readMember(inliningTarget, member, getItem, fromJavaStringNode) != null;
    }

    @ExportMessage
    public boolean isMemberInsertable(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("getItem") @Cached HashingStorageGetItem getItem,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        return !isMemberReadable(member, inliningTarget, getItem, fromJavaStringNode);
    }

    @ExportMessage
    public boolean isMemberInvocable(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("getItem") @Cached HashingStorageGetItem getItem,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        PDict localDict = getThreadLocalDict();
        return localDict != null && PGuards.isCallable(getItem.execute(inliningTarget, localDict.getDictStorage(), fromJavaStringNode.execute(member, TS_ENCODING)));
    }

    @ExportMessage
    public boolean isMemberRemovable(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("getItem") @Cached HashingStorageGetItem getItem,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        return isMemberReadable(member, inliningTarget, getItem, fromJavaStringNode);
    }

    @ExportMessage
    public boolean hasMemberReadSideEffects(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("getItem") @Cached HashingStorageGetItem getItem,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared @Cached GetObjectSlotsNode getSlotsNode) {
        Object attr = readMember(inliningTarget, member, getItem, fromJavaStringNode);
        return attr != null && getSlotsNode.execute(inliningTarget, attr).tp_descr_get() != null;
    }

    @ExportMessage
    public boolean hasMemberWriteSideEffects(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("getItem") @Cached HashingStorageGetItem getItem,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared @Cached GetObjectSlotsNode getSlotsNode) {
        Object attr = readMember(inliningTarget, member, getItem, fromJavaStringNode);
        return attr != null && getSlotsNode.execute(inliningTarget, attr).tp_descr_set() != null;
    }
}
