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
package com.oracle.graal.python.lib;

import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDelItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent to use for PyDict_DelItem and PyDict_DelItemString functions available in CPython.
 * Note that these functions lead to places where there are hard casts to PyDictObject anyway, so we
 * just accept PDict.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class PyDictDelItem extends Node {
    // Note: for now this simply delegates to HashingStorageDelItem, but in the future, this should,
    // unlike HashingStorageDelItem, also handle native subclasses of dict

    public final void execute(Node inliningTarget, PDict dict, TruffleString key) {
        execute(null, inliningTarget, dict, key);
    }

    public abstract void execute(Frame frame, Node inliningTarget, PDict dict, TruffleString key);

    public abstract void execute(Frame frame, Node inliningTarget, PDict dict, Object key);

    // We never need a frame for reading string keys
    @Specialization
    static void delItemWithStringKey(Node inliningTarget, @SuppressWarnings("unused") PDict dict, TruffleString key,
                    @Shared("delStorageItem") @Cached HashingStorageDelItem delItem) {
        delItem.execute(inliningTarget, dict.getDictStorage(), key, dict);
    }

    @Specialization(replaces = "delItemWithStringKey")
    static void delItemCached(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") PDict dict, Object key,
                    @Shared("delStorageItem") @Cached HashingStorageDelItem delItem) {
        delItem.execute(frame, inliningTarget, dict.getDictStorage(), key, dict);
    }

    @Specialization(replaces = "delItemCached")
    static void delItem(Node inliningTarget, PDict dict, Object key,
                    @Shared("delStorageItem") @Cached HashingStorageDelItem delItem) {
        delItem.execute(null, inliningTarget, dict.getDictStorage(), key, dict);
    }
}
