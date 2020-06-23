/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassesNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassesNodeGen;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeCost;

/**
 * Implements cpython://Objects/typeobject.c#tp_new_wrapper.
 */
public final class WrapTpNew extends SlotWrapper {
    @Child IsTypeNode isType;
    @Child IsSubtypeNode isSubtype;
    @Child GetBaseClassesNode getBases;
    @Child PRaiseNode raiseNode;
    @Child LookupAttributeInMRONode lookupNewNode;
    @CompilationFinal byte state = 0;
    @CompilationFinal PythonBuiltinClassType owner;

    private static final short NOT_SUBTP_STATE = 0b10000000;
    private static final short NOT_CLASS_STATE = 0b01000000;
    private static final short IS_UNSAFE_STATE = 0b00100000;
    private static final short NONCONSTANT_MRO = 0b00010000;
    private static final short MRO_LENGTH_MASK = 0b00001111;

    public WrapTpNew(BuiltinCallNode func) {
        super(func);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object arg0;
        try {
            arg0 = PArguments.getArgument(frame, 0); // should always succeed, since the signature check was already done
        } catch (ArrayIndexOutOfBoundsException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException(getOwner().getName() + ".__new__ called without arguments");
        }
        if (arg0 != getOwner()) {
            if (isType == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                isType = insert(IsTypeNode.create());
            }
            if (!isType.execute(arg0)) {
                if ((state & NOT_CLASS_STATE) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    reportPolymorphicSpecialize();
                    state |= NOT_CLASS_STATE;
                }
                throw getRaiseNode().raise(PythonBuiltinClassType.TypeError,
                                "%s.__new__(X): X is not a type object (%N)", getOwner().getName(), arg0);
            }
            if (isSubtype == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                isSubtype = insert(IsSubtypeNode.create());
            }
            if (!isSubtype.execute(arg0, getOwner())) {
                if ((state & NOT_SUBTP_STATE) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    reportPolymorphicSpecialize();
                    state |= NOT_SUBTP_STATE;
                }
                throw getRaiseNode().raise(PythonBuiltinClassType.TypeError,
                                "%s.__new__(%N): %N is not a subtype of %s",
                                getOwner().getName(), arg0, arg0, getOwner().getName());
            }
            if (getBases == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                getBases = insert(GetBaseClassesNodeGen.create());
            }
            PythonAbstractClass[] bases = getBases.execute(arg0);
            if ((state & MRO_LENGTH_MASK) == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                int length = bases.length;
                if (length < MRO_LENGTH_MASK) {
                    state |= length;
                } else {
                    state |= MRO_LENGTH_MASK;
                }
            }
            boolean isSafeNew = true;
            if ((state & MRO_LENGTH_MASK) == bases.length) {
                // cached mro, explode loop
                isSafeNew = checkSafeNew(bases, state & MRO_LENGTH_MASK);
            } else {
                if ((state & NONCONSTANT_MRO) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    reportPolymorphicSpecialize();
                    state |= NONCONSTANT_MRO;
                }
                // mro too long to cache or different from the cached one, no explode loop
                isSafeNew = checkSafeNew(bases);
            }
            if (!isSafeNew) {
                if ((state & IS_UNSAFE_STATE) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    reportPolymorphicSpecialize();
                    state |= IS_UNSAFE_STATE;
                }
                throw getRaiseNode().raise(PythonBuiltinClassType.TypeError,
                                "%s.__new__(%N) is not safe, use %N.__new__()",
                                getOwner().getName(), arg0, arg0);
            }
        }
        return super.execute(frame);
    }

    @ExplodeLoop
    private boolean checkSafeNew(PythonAbstractClass[] bases, int length) {
        for (int i = 0; i < length; i++) {
            byte safe = isSafe(bases, i);
            if (safe != -1) {
                return safe == 0 ? false : true;
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("there is no non-heap type in the mro, broken class");
    }

    private boolean checkSafeNew(PythonAbstractClass[] bases) {
        for (int i = 0; i < bases.length; i++) {
            byte safe = isSafe(bases, i);
            if (safe != -1) {
                return safe == 0 ? false : true;
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("there is no non-heap type in the mro, broken class");
    }

    private byte isSafe(PythonAbstractClass[] mro, int i) {
        PythonAbstractClass base = mro[i];
        if (base instanceof PythonBuiltinClass) {
            if (((PythonBuiltinClass) base).getType() == getOwner()) {
                return 1;
            } else {
                if (lookupNewNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    lookupNewNode = insert(LookupAttributeInMRONode.create(SpecialMethodNames.__NEW__));
                }
                Object newMethod = lookupNewNode.execute(base);
                if (newMethod instanceof PBuiltinFunction) {
                    NodeFactory<? extends PythonBuiltinBaseNode> factory = ((PBuiltinFunction) newMethod).getBuiltinNodeFactory();
                    if (factory != null) {
                        return factory.getNodeClass().isAssignableFrom(getNode().getClass()) ? (byte)1 : (byte)0;
                    }
                }
                // we explicitly allow non-Java builtin functions to pass, since a
                // PythonBuiltinClass with a non-java function is explicitly written in the core to
                // allow this
                return 1;
            }
        } else if (PythonNativeClass.isInstance(base)) {
            // should have called the native tp_new in any case
            return 0;
        }
        return -1;
    }

    private final PRaiseNode getRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode;
    }

    private PythonBuiltinClassType getOwner() {
        if (owner == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Builtin[] builtins = null;
            Class<?> cls = getNode().getClass();
            while (cls != null ) {
                builtins = cls.getAnnotationsByType(Builtin.class);
                if (builtins.length == 0) {
                    cls = cls.getSuperclass();
                } else {
                    break;
                }
            }
            for (Builtin builtin : builtins) {
                owner = builtin.constructsClass();
                if (owner != PythonBuiltinClassType.nil) {
                    // we have an assertion PythonBuiltins#initializeEachFactoryWith that ensures
                    // this is the only constructor @Builtin
                    break;
                }
            }
        }
        return owner;
    }

    @Override
    public NodeCost getCost() {
        if (state == 0) {
            return NodeCost.UNINITIALIZED;
        } else if ((state & ~MRO_LENGTH_MASK) == 0) {
            // no error states, single mro
            return NodeCost.MONOMORPHIC;
        } else if (((state & ~MRO_LENGTH_MASK) & NONCONSTANT_MRO) == NONCONSTANT_MRO) {
            // no error states, multiple mros
            return NodeCost.POLYMORPHIC;
        } else {
            // error states
            return NodeCost.MEGAMORPHIC;
        }
    }
}
