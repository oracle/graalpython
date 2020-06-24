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
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ValueProfile;

/**
 * Implements cpython://Objects/typeobject.c#tp_new_wrapper.
 */
public final class WrapTpNew extends SlotWrapper {
    @Child IsTypeNode isType;
    @Child IsSubtypeNode isSubtype;
    @Child PRaiseNode raiseNode;
    @Child LookupAttributeInMRONode lookupNewNode;
    @CompilationFinal ValueProfile builtinProfile;
    @CompilationFinal byte state = 0;
    @CompilationFinal PythonBuiltinClassType owner;

    private static final short NOT_SUBTP_STATE = 0b10000000;
    private static final short NOT_CLASS_STATE = 0b01000000;
    private static final short IS_UNSAFE_STATE = 0b00100000;

    public WrapTpNew(BuiltinCallNode func) {
        super(func);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object arg0;
        try {
            arg0 = PArguments.getArgument(frame, 0); // should always succeed, since the signature
                                                     // check was already done
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
                                "%s.__new__(X): X is not a type object (%p)", getOwner().getName(), arg0);
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
            // CPython walks the bases and checks that the first non-heaptype base has the new that
            // we're in. We have our optimizations for this lookup that the compiler can then
            // (hopefully) merge with the initial lookup of the new method before entering it.
            if (lookupNewNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                lookupNewNode = insert(LookupAttributeInMRONode.createForLookupOfUnmanagedClasses(SpecialMethodNames.__NEW__));
            }
            Object newMethod = lookupNewNode.execute(arg0);
            if (newMethod instanceof PBuiltinFunction) {
                if (builtinProfile == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    builtinProfile = ValueProfile.createIdentityProfile();
                }
                NodeFactory<? extends PythonBuiltinBaseNode> factory = ((PBuiltinFunction) builtinProfile.profile(newMethod)).getBuiltinNodeFactory();
                if (factory != null) {
                    if (!factory.getNodeClass().isAssignableFrom(getNode().getClass())) {
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
                // we explicitly allow non-Java functions to pass here, since a PythonBuiltinClass
                // with a non-java function is explicitly written in the core to allow this
            }
        }
        return super.execute(frame);
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
            while (cls != null) {
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
        if (isType == null) {
            // only run with owner
            return NodeCost.MONOMORPHIC;
        } else if (state == 0) {
            // no error states, but we did see a subtype
            return NodeCost.POLYMORPHIC;
        } else {
            // error states
            return NodeCost.MEGAMORPHIC;
        }
    }
}
