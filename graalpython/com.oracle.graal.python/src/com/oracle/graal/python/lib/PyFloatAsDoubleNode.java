/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyFloatObject__ob_fval;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOAT__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromNativeSubclassNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {@code PyFloat_AsDouble}. Converts the argument to a Java {@code double}
 * using its {@code __float__} special method. If not available, falls back to {@code __index__}
 * special method. Otherwise, raises {@code TypeError}. Can raise {@code OverflowError} when using
 * {@code __index__} and the returned integer wouldn't fit into double.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyFloatAsDoubleNode extends PNodeWithContext {

    public static double executeUncached(Object object) {
        return PyFloatAsDoubleNodeGen.getUncached().execute(null, null, object);
    }

    public abstract double execute(VirtualFrame frame, Node inliningTarget, Object object);

    @Specialization
    static double doDouble(double object) {
        return object;
    }

    @Specialization
    static double doPFloat(PFloat object) {
        return object.getValue();
    }

    @Specialization
    static double doInt(int object) {
        return object;
    }

    @Specialization
    static double doLong(long object) {
        return object;
    }

    @Specialization
    static double doBoolean(boolean object) {
        return object ? 1.0 : 0.0;
    }

    @Specialization(guards = "isFloatSubtype(inliningTarget, object, getClassNode, isSubtype)")
    @InliningCutoff
    static double doNative(Node inliningTarget, PythonAbstractNativeObject object,
                    @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Exclusive @Cached IsSubtypeNode isSubtype,
                    @Cached CStructAccess.ReadDoubleNode read) {
        return read.readFromObj(object, PyFloatObject__ob_fval);
    }

    @Specialization(guards = {"!isDouble(object)", "!isInteger(object)", "!isBoolean(object)", "!isPFloat(object)",
                    "!isFloatSubtype(inliningTarget, object, getClassNode, isSubtype)"})
    @InliningCutoff
    static double doObject(VirtualFrame frame, Node inliningTarget, Object object,
                    @Exclusive @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Exclusive @Cached(inline = false) IsSubtypeNode isSubtype,
                    @Cached(parameters = "Float", inline = false) LookupSpecialMethodSlotNode lookup,
                    @Cached(inline = false) CallUnaryMethodNode call,
                    @Exclusive @Cached GetClassNode resultClassNode,
                    @Exclusive @Cached InlineIsBuiltinClassProfile resultProfile,
                    @Exclusive @Cached(inline = false) IsSubtypeNode resultSubtypeNode,
                    @Cached PyIndexCheckNode indexCheckNode,
                    @Cached PyNumberIndexNode indexNode,
                    @Cached CastToJavaDoubleNode cast,
                    @Cached(inline = false) WarningsModuleBuiltins.WarnNode warnNode,
                    @Cached PRaiseNode.Lazy raiseNode) {
        Object type = getClassNode.execute(inliningTarget, object);
        Object floatDescr = lookup.execute(frame, type, object);
        if (floatDescr != PNone.NO_VALUE) {
            Object result = call.executeObject(frame, floatDescr, object);
            Object resultType = resultClassNode.execute(inliningTarget, result);
            if (!resultProfile.profileClass(inliningTarget, resultType, PythonBuiltinClassType.PFloat)) {
                if (!resultSubtypeNode.execute(resultType, PythonBuiltinClassType.PFloat)) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.RETURNED_NON_FLOAT, object, result);
                } else {
                    warnNode.warnFormat(frame, null, DeprecationWarning, 1,
                                    ErrorMessages.WARN_P_RETURNED_NON_P, object, T___FLOAT__, "float", result, "float");
                }
            }
            return cast.execute(inliningTarget, result);
        }
        if (indexCheckNode.execute(inliningTarget, object)) {
            Object index = indexNode.execute(frame, inliningTarget, object);
            return cast.execute(inliningTarget, index);
        }
        throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.MUST_BE_REAL_NUMBER, object);
    }

    static boolean isFloatSubtype(Node inliningTarget, Object object, GetClassNode getClass, IsSubtypeNode isSubtype) {
        return FromNativeSubclassNode.isFloatSubtype(null, inliningTarget, object, getClass, isSubtype);
    }
}
