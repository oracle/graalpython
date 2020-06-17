/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/**
 * Implements cpython://Objects/typeobject.c#tp_new_wrapper.
 */
public abstract class PythonTpNewBuiltinNode extends PythonBuiltinNode {
    @Child IsTypeNode isType;
    @Child IsSubtypeNode isSubtype;
    @Child GetMroNode getMro;
    @CompilationFinal byte state = 0;
    @CompilationFinal PythonBuiltinClassType owner;

    private static final short NOT_SUBTP_STATE = 0b10000000;
    private static final short NOT_CLASS_STATE = 0b01000000;
    private static final short IS_UNSAFE_STATE = 0b00100000;
    private static final short NOTCONSTANT_MRO = 0b00010000;
    private static final short MRO_LENGTH_MASK = 0b00001111;

    @Override
    public final Object execute(VirtualFrame frame) {
        if (PArguments.isDirectCallToSlot(frame)) {
            return executeUnwrapped(frame);
        }
        // having these nodes acts as branch profile here
        if (isType == null || isSubtype == null || getMro == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            reportPolymorphicSpecialize();
            isType = insert(IsTypeNode.create());
            isSubtype = insert(IsSubtypeNode.create());
            getMro = insert(GetMroNode.create());
        }
        Object arg0;
        try {
            arg0 = PArguments.getArgument(frame, 0); // should always succeed, since the signature check was already done
        } catch (ArrayIndexOutOfBoundsException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException(getOwner().getName() + ".__new__ called without arguments");
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
        // TODO (tfel): not quite correct, since we should just be walking the bases, not the entire
        // MRO
        PythonAbstractClass[] mro = getMro.execute(arg0);
        if ((state & MRO_LENGTH_MASK) == 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            int length = mro.length;
            if (length < MRO_LENGTH_MASK) {
                state |= length;
            } else {
                state |= MRO_LENGTH_MASK;
            }
        }
        boolean isSafeNew = true;
        if ((state & MRO_LENGTH_MASK) == mro.length) {
            // cached mro, explode loop
            isSafeNew = checkSafeNew(mro, state & MRO_LENGTH_MASK);
        } else {
            if ((state & NOTCONSTANT_MRO) == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                state |= NOTCONSTANT_MRO;
            }
            // mro too long to cache or different from the cached one, no explode loop
            isSafeNew = checkSafeNew(mro);
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
        return executeUnwrapped(frame);
    }

    @ExplodeLoop
    private boolean checkSafeNew(PythonAbstractClass[] mro, int length) {
        for (int i = 0; i < length; i++) {
            PythonAbstractClass base = mro[i];
            if (base instanceof PythonBuiltinClass) {
                // TODO: tfel not correct, since the base may not be overriding __new__
                return ((PythonBuiltinClass) base).getType() == getOwner();
            } else if (PythonNativeClass.isInstance(base)) {
                // should have called the native tp_new in any case
                return false;
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("there is no non-heap type in the mro, broken class");
    }

    private boolean checkSafeNew(PythonAbstractClass[] mro) {
        for (int i = 0; i < mro.length; i++) {
            PythonAbstractClass base = mro[i];
            if (base instanceof PythonBuiltinClass) {
                return ((PythonBuiltinClass) base).getType() == getOwner();
            } else if (PythonNativeClass.isInstance(base)) {
                // should have called the native tp_new in any case
                return false;
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("there is no non-heap type in the mro, broken class");
    }

    private PythonBuiltinClassType getOwner() {
        if (owner == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Builtin[] builtins = null;
            Class<?> cls = getClass();
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

    protected abstract Object executeUnwrapped(VirtualFrame frame);
}
