/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import java.util.List;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins.IntNode;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins.NegNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(defineModule = PythonCextLongBuiltins.PYTHON_CEXT_LONG)
@GenerateNodeFactory
public class PythonCextLongBuiltins extends PythonBuiltins {

    public static final String PYTHON_CEXT_LONG = "python_cext_long";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextLongBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "_PyLong_Sign", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyLongSignNode extends PythonUnaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = "n == 0")
        int sign(int n) {
            return 0;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n < 0")
        int signNeg(int n) {
            return -1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n > 0")
        int signPos(int n) {
            return 1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n == 0")
        int sign(long n) {
            return 0;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n < 0")
        int signNeg(long n) {
            return -1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n > 0")
        int signPos(long n) {
            return 1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "b")
        int signTrue(boolean b) {
            return 1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!b")
        int signFalse(boolean b) {
            return 0;
        }

        @Specialization
        int sign(PInt n,
                        @Cached BranchProfile zeroProfile,
                        @Cached BranchProfile negProfile) {
            if (n.isNegative()) {
                negProfile.enter();
                return -1;
            } else if (n.isZero()) {
                zeroProfile.enter();
                return 0;
            } else {
                return 1;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!canBeInteger(obj)", "isPIntSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object signNative(VirtualFrame frame, Object obj,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            // function returns int, but -1 is expected result for 'n < 0'
            throw CompilerDirectives.shouldNotReachHere("not yet implemented");
        }

        @Specialization(guards = {"!isInteger(obj)", "!isPInt(obj)", "!isPIntSubtype(frame, obj,getClassNode,isSubtypeNode)"})
        public Object sign(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            // assert(PyLong_Check(v));
            throw CompilerDirectives.shouldNotReachHere();
        }

        protected boolean isPIntSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PInt);
        }
    }

    @Builtin(name = "PyLong_FromDouble", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyLongFromDoubleNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object fromDouble(VirtualFrame frame, double d,
                        @Cached IntNode intNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return intNode.execute(frame, d);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyLong_FromString", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PyLongFromStringNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "negative == 0")
        Object fromString(VirtualFrame frame, String s, long base, @SuppressWarnings("unused") long negative,
                        @Cached com.oracle.graal.python.builtins.modules.BuiltinConstructors.IntNode intNode,
                        @Shared("transforEx") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Shared("nativeNull") @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return intNode.executeWith(frame, s, base);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "negative != 0")
        Object fromString(VirtualFrame frame, String s, long base, @SuppressWarnings("unused") long negative,
                        @Cached com.oracle.graal.python.builtins.modules.BuiltinConstructors.IntNode intNode,
                        @Cached NegNode negNode,
                        @Shared("transforEx") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Shared("nativeNull") @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return negNode.execute(frame, intNode.executeWith(frame, s, base));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }
}
