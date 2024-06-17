/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.function.builtins;

import org.graalvm.collections.Pair;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ValueProfile;

/**
 * Implements cpython://Objects/typeobject.c#tp_new_wrapper.
 */
public final class WrapTpNew extends SlotWrapper {
    @Child private IsTypeNode isType;
    @Child private IsSubtypeNode isSubtype;
    @Child private PRaiseNode raiseNode;
    @Child private LookupAttributeInMRONode lookupNewNode;
    @CompilationFinal private ValueProfile builtinProfile;
    @CompilationFinal private byte state = 0;
    private final PythonBuiltinClassType owner;
    // we cache two node classes here, otherwise we use a truffle boundary lookup
    @CompilationFinal(dimensions = 1) private final Pair<?, ?>[] cachedFactoriesNodeClasses = new Pair<?, ?>[2];

    private static final byte NOT_SUBTP_STATE = 0b100;
    private static final byte NOT_CLASS_STATE = 0b010;
    private static final byte IS_UNSAFE_STATE = 0b001;

    public WrapTpNew(BuiltinCallNode func, PythonBuiltinClassType owner) {
        super(func);
        this.owner = owner;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object cls;
        try {
            // should always succeed, since the signature check was already done
            cls = PArguments.getArgument(frame, 1);
        } catch (ArrayIndexOutOfBoundsException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException(owner.getName() + ".__new__ called without arguments");
        }
        if (cls != owner) {
            if (isType == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                isType = insert(IsTypeNode.create());
            }
            if (!isType.executeCached(cls)) {
                if ((state & NOT_CLASS_STATE) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    reportPolymorphicSpecialize();
                    state |= NOT_CLASS_STATE;
                }
                throw getRaiseNode().raise(PythonBuiltinClassType.TypeError, ErrorMessages.NEW_X_ISNT_TYPE_OBJ, owner.getName(), cls);
            }
            if (isSubtype == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                isSubtype = insert(IsSubtypeNode.create());
            }
            if (!isSubtype.execute(cls, owner)) {
                if ((state & NOT_SUBTP_STATE) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    reportPolymorphicSpecialize();
                    state |= NOT_SUBTP_STATE;
                }
                throw getRaiseNode().raise(PythonBuiltinClassType.TypeError,
                                ErrorMessages.IS_NOT_SUBTYPE_OF,
                                owner.getName(), cls, cls, owner.getName());
            }
            // CPython walks the bases and checks that the first non-heaptype base has the new that
            // we're in. We have our optimizations for this lookup that the compiler can then
            // (hopefully) merge with the initial lookup of the new method before entering it.
            if (lookupNewNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                lookupNewNode = insert(LookupAttributeInMRONode.createForLookupOfUnmanagedClasses(SpecialMethodNames.T___NEW__));
            }
            Object newMethod = lookupNewNode.execute(cls);
            if (newMethod instanceof PBuiltinMethod) {
                if (builtinProfile == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    builtinProfile = PythonUtils.createValueIdentityProfile();
                }
                NodeFactory<? extends PythonBuiltinBaseNode> factory = ((PBuiltinMethod) builtinProfile.profile(newMethod)).getBuiltinFunction().getBuiltinNodeFactory();
                if (factory != null) {
                    if (!getFactoryNodeClass(factory).isInstance(getNode())) {
                        if ((state & IS_UNSAFE_STATE) == 0) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            reportPolymorphicSpecialize();
                            state |= IS_UNSAFE_STATE;
                        }
                        throw getRaiseNode().raise(PythonBuiltinClassType.TypeError, ErrorMessages.NEW_IS_NOT_SAFE_USE_ELSE, owner.getName(), cls, cls);
                    }
                }
                // we explicitly allow non-Java functions to pass here, since a PythonBuiltinClass
                // with a non-java function is explicitly written in the core to allow this
            }
        }
        return super.execute(frame);
    }

    @ExplodeLoop
    @SuppressWarnings("unchecked")
    private final Class<? extends PythonBuiltinBaseNode> getFactoryNodeClass(NodeFactory<? extends PythonBuiltinBaseNode> factory) {
        for (int i = 0; i < cachedFactoriesNodeClasses.length; i++) {
            Pair<NodeFactory<? extends PythonBuiltinBaseNode>, Class<? extends PythonBuiltinBaseNode>> pair = (Pair<NodeFactory<? extends PythonBuiltinBaseNode>, Class<? extends PythonBuiltinBaseNode>>) cachedFactoriesNodeClasses[i];
            if (pair == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                Class<? extends PythonBuiltinBaseNode> nodeclass = factory.getNodeClass();
                cachedFactoriesNodeClasses[i] = Pair.create(factory, nodeclass);
                return nodeclass;
            } else if (pair.getLeft() == factory) {
                return pair.getRight();
            }
        }
        return getFactoryNodeClassUncached(factory);
    }

    @TruffleBoundary
    private static final Class<? extends PythonBuiltinBaseNode> getFactoryNodeClassUncached(NodeFactory<? extends PythonBuiltinBaseNode> factory) {
        return factory.getNodeClass();
    }

    private PRaiseNode getRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode;
    }
}
