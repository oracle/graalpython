/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.function;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.dsl.NodeFactory;

/**
 * Context independent wrapper of a method that can be stored in special method slots of
 * {@link com.oracle.graal.python.builtins.PythonBuiltinClassType}. This wrapper is context and also
 * language instance independent. It provides only builtin node factory and a way to resolve itself
 * to its context specific version using {@link #getBuiltinMethod(PythonCore)}.
 *
 * @see com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot
 */
public abstract class BuiltinMethodInfo {
    private final NodeFactory<? extends PythonBuiltinBaseNode> factory;
    private final SpecialMethodSlot slot;
    private final PythonBuiltinClassType type;

    private BuiltinMethodInfo(NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type, SpecialMethodSlot slot) {
        this.factory = factory;
        this.type = type;
        this.slot = slot;
    }

    public final boolean isSameFactory(BuiltinMethodInfo info) {
        return info.factory == factory;
    }

    public final NodeFactory<? extends PythonBuiltinBaseNode> getFactory() {
        return factory;
    }

    public Object getBuiltinMethod(PythonCore core) {
        return slot.getValue(core.lookupType(type));
    }

    // Note: manually written subclass for each builtin works better with Truffle DSL than one
    // generic class that would parametrize the 'factory' field

    public static final class UnaryBuiltinInfo extends BuiltinMethodInfo {
        public UnaryBuiltinInfo(NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type, SpecialMethodSlot slot) {
            super(factory, type, slot);
        }

        @SuppressWarnings("unchecked")
        public NodeFactory<? extends PythonUnaryBuiltinNode> getUnaryFactory() {
            return (NodeFactory<? extends PythonUnaryBuiltinNode>) getFactory();
        }

        public PythonUnaryBuiltinNode createNode() {
            return (PythonUnaryBuiltinNode) getFactory().createNode();
        }
    }

    public static final class BinaryBuiltinInfo extends BuiltinMethodInfo {
        public BinaryBuiltinInfo(NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type, SpecialMethodSlot slot) {
            super(factory, type, slot);
        }

        public PythonBinaryBuiltinNode createNode() {
            return (PythonBinaryBuiltinNode) getFactory().createNode();
        }
    }

    public static final class TernaryBuiltinInfo extends BuiltinMethodInfo {
        public TernaryBuiltinInfo(NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type, SpecialMethodSlot slot) {
            super(factory, type, slot);
        }

        public PythonTernaryBuiltinNode createNode() {
            return (PythonTernaryBuiltinNode) getFactory().createNode();
        }
    }
}
