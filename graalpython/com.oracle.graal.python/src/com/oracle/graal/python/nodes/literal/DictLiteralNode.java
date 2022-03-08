/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.literal.DictLiteralNodeFactory.DynamicDictLiteralNodeGen;
import com.oracle.graal.python.nodes.literal.DictLiteralNodeFactory.FixedDictLiteralNodeGen;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class DictLiteralNode {

    abstract static class FixedDictLiteralNode extends LiteralNode {

        @Child private PythonObjectFactory factory = PythonObjectFactory.create();
        @Children private final ExpressionNode[] values;

        @CompilationFinal(dimensions = 1) private final TruffleString[] keys;

        protected FixedDictLiteralNode(ExpressionNode[] keys, ExpressionNode[] values) {
            this.keys = new TruffleString[keys.length];
            for (int i = 0; i < keys.length; i++) {
                this.keys[i] = ((StringLiteralNode) keys[i]).getValue();
            }
            this.values = values;
        }

        @Specialization
        @ExplodeLoop
        public PDict create(VirtualFrame frame) {
            HashMapStorage dictStorage = new HashMapStorage(values.length);
            for (int i = 0; i < values.length; i++) {
                Object value = values[i].execute(frame);
                dictStorage.put(keys[i], value);
            }
            return factory.createDict(dictStorage);
        }
    }

    abstract static class DynamicDictLiteralNode extends LiteralNode {

        @Child private PythonObjectFactory factory = PythonObjectFactory.create();
        @Children private final ExpressionNode[] keys;
        @Children private final ExpressionNode[] values;
        @Children private final HashingStorageLibrary[] libs;

        protected DynamicDictLiteralNode(ExpressionNode[] keys, ExpressionNode[] values) {
            this.keys = keys;
            this.values = values;
            this.libs = new HashingStorageLibrary[keys.length];
            for (int i = 0; i < this.libs.length; i++) {
                this.libs[i] = HashingStorageLibrary.getFactory().createDispatched(2);
            }
        }

        @ExplodeLoop
        private HashingStorage eval(VirtualFrame frame, ConditionProfile hasFrame) {
            boolean allStrings = true;
            Object[] evalKeys = new Object[this.keys.length];
            Object[] evalValues = new Object[this.values.length];
            for (int i = 0; i < values.length; i++) {
                evalKeys[i] = keys[i].execute(frame);
                evalValues[i] = values[i].execute(frame);
                if (allStrings && !(evalKeys[i] instanceof String)) {
                    allStrings = false;
                }
            }
            HashingStorage storage = PDict.createNewStorage(allStrings, evalKeys.length);
            for (int i = 0; i < values.length; i++) {
                storage = libs[i].setItemWithFrame(storage, evalKeys[i], evalValues[i], hasFrame, frame);
            }
            return storage;
        }

        @Specialization
        public PDict create(VirtualFrame frame,
                        @Cached ConditionProfile hasFrame) {
            HashingStorage dictStorage = eval(frame, hasFrame);
            return factory.createDict(dictStorage);
        }
    }

    static final class EmptyDictLiteralNode extends LiteralNode {

        @Child private PythonObjectFactory factory = PythonObjectFactory.create();

        @Override
        public Object execute(VirtualFrame frame) {
            return factory.createDict(EmptyStorage.INSTANCE);
        }
    }

    public static ExpressionNode createEmptyDictLiteral() {
        return new EmptyDictLiteralNode();
    }

    public static ExpressionNode create(ExpressionNode[] keys, ExpressionNode[] values) {
        assert keys.length == values.length;
        if (keys.length == 0) {
            return new EmptyDictLiteralNode();
        }
        if (keys.length > HashMapStorage.SIZE_THRESHOLD) {
            return DynamicDictLiteralNodeGen.create(keys, values);
        }
        for (ExpressionNode key : keys) {
            if (!(key instanceof StringLiteralNode)) {
                return DynamicDictLiteralNodeGen.create(keys, values);
            }
        }
        return FixedDictLiteralNodeGen.create(keys, values);
    }
}
