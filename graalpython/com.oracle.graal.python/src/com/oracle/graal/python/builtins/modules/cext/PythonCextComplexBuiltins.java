/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOAT__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.ComplexNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextComplexBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextComplexBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    ///////////// complex /////////////

    @Builtin(name = "PyComplex_AsCComplex", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyComplexAsCComplexNode extends PythonUnaryBuiltinNode {
        @Specialization
        PTuple asComplex(PComplex c) {
            return factory().createTuple(new Object[]{c.getReal(), c.getImag()});
        }

        @Specialization(guards = "!isPComplex(obj)")
        Object asComplex(VirtualFrame frame, Object obj,
                        @Cached ComplexNode complexNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                PComplex c = (PComplex) complexNode.execute(frame, PythonBuiltinClassType.PComplex, obj, PNone.NO_VALUE);
                return factory().createTuple(new Object[]{c.getReal(), c.getImag()});
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyComplex_RealAsDouble", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyComplexRealAsDoubleNode extends PythonUnaryBuiltinNode {

        public static final TruffleString T_REAL = tsLiteral("real");

        @Specialization
        static double asDouble(PComplex d) {
            return d.getReal();
        }

        @Specialization(guards = {"!isPComplex(obj)", "isComplexSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object asDouble(VirtualFrame frame, Object obj,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached CallNode callNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return callNode.execute(getAttr.execute(frame, obj, T_REAL));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1.0;
            }
        }

        @Specialization(guards = {"!isPComplex(obj)", "!isComplexSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object asDoubleFloat(VirtualFrame frame, Object obj,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached CallNode callNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return callNode.execute(getAttr.execute(frame, obj, T___FLOAT__));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1.0;
            }
        }

        protected boolean isComplexSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PComplex);
        }
    }

    @Builtin(name = "PyComplex_ImagAsDouble", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyComplexImagAsDoubleNode extends PythonUnaryBuiltinNode {

        public static final TruffleString T_IMAG = tsLiteral("imag");

        @Specialization
        static double asDouble(PComplex d) {
            return d.getImag();
        }

        @Specialization(guards = {"!isPComplex(obj)", "isComplexSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object asDouble(VirtualFrame frame, Object obj,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached CallNode callNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return callNode.execute(getAttr.execute(frame, obj, T_IMAG));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isPComplex(obj)", "!isComplexSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object asDouble(VirtualFrame frame, Object obj,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            return 0.0;
        }

        protected boolean isComplexSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PComplex);
        }
    }

    @Builtin(name = "PyComplex_FromDoubles", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyComplexFromDoublesNode extends PythonBinaryBuiltinNode {

        @Specialization
        public PComplex asDouble(double r, double i) {
            return factory().createComplex(r, i);
        }
    }
}
