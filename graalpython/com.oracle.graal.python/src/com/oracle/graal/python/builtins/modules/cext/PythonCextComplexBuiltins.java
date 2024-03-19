/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOAT__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.ComplexNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextComplexBuiltins {

    @CApiBuiltin(ret = Int, args = {PyObject, Pointer}, call = Ignored)
    abstract static class PyTruffleComplex_AsCComplex extends CApiBinaryBuiltinNode {
        @Specialization
        static int asComplex(PComplex c, Object out,
                        @Shared @Cached CStructAccess.WriteDoubleNode writeDoubleNode) {
            writeDoubleNode.write(out, CFields.Py_complex__real, c.getReal());
            writeDoubleNode.write(out, CFields.Py_complex__imag, c.getImag());
            return 0;
        }

        @Specialization(guards = "!isPComplex(obj)")
        static int doGeneric(Object obj, Object out,
                        @Cached ComplexNode complexNode,
                        @Shared @Cached CStructAccess.WriteDoubleNode writeDoubleNode) {
            PComplex c = (PComplex) complexNode.execute(null, PythonBuiltinClassType.PComplex, obj, PNone.NO_VALUE);
            writeDoubleNode.write(out, CFields.Py_complex__real, c.getReal());
            writeDoubleNode.write(out, CFields.Py_complex__imag, c.getImag());
            return 0;
        }
    }

    static boolean isComplexSubtype(Node inliningTarget, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
        return isSubtypeNode.execute(getClassNode.execute(inliningTarget, obj), PythonBuiltinClassType.PComplex);
    }

    @CApiBuiltin(ret = ArgDescriptor.Double, args = {PyObject}, call = Ignored)
    @ImportStatic(PythonCextComplexBuiltins.class)
    abstract static class PyTruffleComplex_RealAsDouble extends CApiUnaryBuiltinNode {

        public static final TruffleString T_REAL = tsLiteral("real");

        @Specialization
        static double asDouble(PComplex d) {
            return d.getReal();
        }

        @Specialization(guards = "!isPComplex(obj)")
        static Object asDouble(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile isComplexSubtypeProfile,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached CallNode callNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString name;
            if (isComplexSubtypeProfile.profile(inliningTarget, isComplexSubtype(inliningTarget, obj, getClassNode, isSubtypeNode))) {
                name = T_REAL;
            } else {
                name = T___FLOAT__;
            }
            try {
                return callNode.execute(getAttr.execute(null, inliningTarget, obj, name));
            } catch (PException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError);
            }
        }
    }

    @CApiBuiltin(ret = ArgDescriptor.Double, args = {PyObject}, call = Ignored)
    @ImportStatic(PythonCextComplexBuiltins.class)
    abstract static class PyTruffleComplex_ImagAsDouble extends CApiUnaryBuiltinNode {

        public static final TruffleString T_IMAG = tsLiteral("imag");

        @Specialization
        static double asDouble(PComplex d) {
            return d.getImag();
        }

        @Specialization(guards = {"!isPComplex(obj)", "isComplexSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)"})
        static Object asDouble(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached CallNode callNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return callNode.execute(getAttr.execute(null, inliningTarget, obj, T_IMAG));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isPComplex(obj)", "!isComplexSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)"})
        static Object asDouble(Object obj,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return 0.0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ArgDescriptor.Double, ArgDescriptor.Double}, call = Direct)
    abstract static class PyComplex_FromDoubles extends CApiBinaryBuiltinNode {

        @Specialization
        static PComplex asDouble(double r, double i,
                        @Cached PythonObjectFactory factory) {
            return factory.createComplex(r, i);
        }
    }
}
