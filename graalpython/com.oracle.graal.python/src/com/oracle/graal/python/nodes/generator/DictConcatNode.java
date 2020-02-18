/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateNodeFactory
public abstract class DictConcatNode extends ExpressionNode {

    @Children final ExpressionNode[] mappables;

    protected DictConcatNode(ExpressionNode... mappablesNodes) {
        this.mappables = mappablesNodes;
    }

    @ExplodeLoop
    @Specialization
    public Object concat(VirtualFrame frame,
                    @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                    @CachedLibrary(limit = "1") HashingStorageLibrary firstlib,
                    @CachedLibrary(limit = "1") HashingStorageLibrary otherlib) {
        PDict first = null;
        PDict other;
        for (ExpressionNode n : mappables) {
            if (first == null) {
                first = expectDict(n.execute(frame));
            } else {
                other = expectDict(n.execute(frame));
                addAllToDict(frame, first, other, hasFrame, firstlib, otherlib);
            }
        }
        return first;
    }

    private static void addAllToDict(VirtualFrame frame, PDict dict, PDict other, ConditionProfile hasFrame,
                    HashingStorageLibrary firstlib, HashingStorageLibrary otherlib) {
        ThreadState state = PArguments.getThreadState(frame);
        HashingStorage dictStorage = dict.getDictStorage();
        for (Object key : other.keys()) {
            Object value;
            if (hasFrame.profile(frame != null)) {
                value = otherlib.getItemWithState(other.getDictStorage(), key, state);
                dictStorage = firstlib.setItemWithState(dictStorage, key, value, state);
            } else {
                value = otherlib.getItem(other.getDictStorage(), key);
                dictStorage = firstlib.setItem(dictStorage, key, value);
            }
        }
        dict.setDictStorage(dictStorage);
    }

    private static PDict expectDict(Object first) {
        if (!(first instanceof PDict)) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException("non-dictionary in dictionary appending");
        }
        return (PDict) first;
    }

}
