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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.ErrorMessages.BASE_MUST_BE;
import java.util.List;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.AbsNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.BinNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.DivModNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.HexNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.OctNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(defineModule = PythonCextNumberBuiltins.PYTHON_CEXT_NUMBER)
@GenerateNodeFactory
public class PythonCextNumberBuiltins extends PythonBuiltins {

    public static final String PYTHON_CEXT_NUMBER = "python_cext_number";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextNumberBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    ///////////// number /////////////

    @Builtin(name = "PyNumber_Check", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PyNumberCheckNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object check(VirtualFrame frame, Object obj,
                        @Cached com.oracle.graal.python.lib.PyNumberCheckNode checkNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return checkNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyNumber_Index", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyNumberIndexNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object index(VirtualFrame frame, Object obj,
                        @Cached com.oracle.graal.python.lib.PyNumberIndexNode indexNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return indexNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyNumber_Long", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyNumberLongNode extends PythonUnaryBuiltinNode {

        @Specialization
        int nlong(int i) {
            return i;
        }

        @Specialization
        long nlong(long i) {
            return i;
        }

        @Fallback
        Object nlong(VirtualFrame frame, Object obj,
                        @Cached com.oracle.graal.python.builtins.modules.BuiltinConstructors.IntNode intNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return intNode.executeWith(frame, obj, PNone.NO_VALUE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyNumber_Absolute", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyNumberAbsoluteNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object abs(VirtualFrame frame, Object obj,
                        @Cached AbsNode absNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return absNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyNumber_Divmod", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyNumberDivmodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object div(VirtualFrame frame, Object a, Object b,
                        @Cached DivModNode divNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return divNode.execute(frame, a, b);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyNumber_ToBase", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyNumberToBaseNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "base == 2")
        Object toBase(VirtualFrame frame, Object n, @SuppressWarnings("unused") int base,
                        @Cached com.oracle.graal.python.lib.PyNumberIndexNode indexNode,
                        @Cached BinNode binNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object i = indexNode.execute(frame, n);
                return binNode.execute(frame, i);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "base == 8")
        Object toBase(VirtualFrame frame, Object n, @SuppressWarnings("unused") int base,
                        @Cached OctNode octNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return octNode.execute(frame, n);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "base == 10")
        Object toBase(VirtualFrame frame, Object n, @SuppressWarnings("unused") int base,
                        @Cached com.oracle.graal.python.lib.PyNumberIndexNode indexNode,
                        @Cached StrNode strNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object i = indexNode.execute(frame, n);
                if (i instanceof Boolean) {
                    i = ((boolean) i) ? 1 : 0;
                }
                return strNode.executeWith(frame, i);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "base == 16")
        Object toBase(VirtualFrame frame, Object n, @SuppressWarnings("unused") int base,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached HexNode hexNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object i = indexNode.execute(frame, n);
                return hexNode.execute(frame, i);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "!checkBase(base)")
        Object toBase(VirtualFrame frame, @SuppressWarnings("unused") Object n, @SuppressWarnings("unused") int base,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), SystemError, BASE_MUST_BE);
        }

        protected boolean checkBase(int base) {
            return base == 2 || base == 8 || base == 10 || base == 16;
        }
    }
}
