/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.literal;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateNodeFactory
public abstract class DictLiteralNode extends LiteralNode {
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();
    @Children private final ExpressionNode[] keys;
    @Children private final ExpressionNode[] values;

    protected DictLiteralNode(ExpressionNode[] keys, ExpressionNode[] values) {
        this.keys = keys;
        this.values = values;
        assert keys.length == values.length;
    }

    static final class Keys {
        public final Object[] keys;
        public final boolean allStrings;

        Keys(Object[] keys, boolean allStrings) {
            this.keys = keys;
            this.allStrings = allStrings;
        }
    }

    @ExplodeLoop
    private Keys evalKeys(VirtualFrame frame) {
        boolean allStrings = true;
        Object[] evalKeys = new Object[this.keys.length];
        for (int i = 0; i < values.length; i++) {
            evalKeys[i] = keys[i].execute(frame);
            if (!(evalKeys[i] instanceof String)) {
                allStrings = false;
            }
        }
        return new Keys(evalKeys, allStrings);
    }

    @ExplodeLoop
    private HashingStorage evalAndSetValues(VirtualFrame frame, HashingStorage dictStorage, Keys evalKeys, ConditionProfile hasFrame, HashingStorageLibrary lib) {
        HashingStorage storage = dictStorage;
        for (int i = 0; i < values.length; i++) {
            final Object val = values[i].execute(frame);
            storage = lib.setItemWithFrame(storage, evalKeys.keys[i], val, hasFrame, frame);
        }
        return storage;
    }

    @Specialization
    public PDict create(VirtualFrame frame,
                    @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                    @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
        Keys evalKeys = evalKeys(frame);
        HashingStorage dictStorage = PDict.createNewStorage(evalKeys.allStrings, evalKeys.keys.length);
        dictStorage = evalAndSetValues(frame, dictStorage, evalKeys, hasFrame, lib);
        return factory.createDict(dictStorage);
    }
}
