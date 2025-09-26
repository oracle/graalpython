/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.modules.MathModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.ReadI64Node;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.CallSlotHashFunNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class PyObjectHashNode extends PNodeWithContext {
    public static long executeUncached(Object value) {
        return PyObjectHashNodeGen.getUncached().execute(null, null, value);
    }

    public abstract long execute(Frame frame, Node inliningTarget, Object object);

    public abstract long execute(Frame frame, Node inliningTarget, TruffleString object);

    public static long avoidNegative1(long hash) {
        // CPython uses -1 to signify error status
        return hash == -1 ? -2 : hash;
    }

    @Specialization
    @InliningCutoff
    public static long hash(TruffleString object,
                    @Shared @Cached TruffleString.HashCodeNode hashCodeNode) {
        return avoidNegative1(hashCodeNode.execute(object, TS_ENCODING));
    }

    @Specialization(guards = "isBuiltinPString(object)")
    @InliningCutoff
    static long hash(Node inliningTarget, PString object,
                    @Cached CastToTruffleStringNode cast,
                    @Shared @Cached TruffleString.HashCodeNode hashCodeNode) {
        return hash(cast.execute(inliningTarget, object), hashCodeNode);
    }

    @Specialization
    public static long hash(boolean object) {
        return object ? 1 : 0;
    }

    @Specialization
    public static long hash(int object) {
        return avoidNegative1(object);
    }

    @Specialization
    public static long hash(long object) {
        long h = object % SysModuleBuiltins.HASH_MODULUS;
        return avoidNegative1(h);
    }

    // Adapted from CPython _Py_HashDouble
    @Specialization
    public static long hash(double object) {
        if (!Double.isFinite(object)) {
            if (Double.isInfinite(object)) {
                return object > 0 ? SysModuleBuiltins.HASH_INF : -SysModuleBuiltins.HASH_INF;
            }
            return SysModuleBuiltins.HASH_NAN;
        }

        double[] frexpRes = MathModuleBuiltins.FrexpNode.frexp(object);
        double m = frexpRes[0];
        int e = (int) frexpRes[1];
        int sign = 1;
        if (m < 0) {
            sign = -1;
            m = -m;
        }
        long x = 0;
        while (m != 0.0) {
            x = ((x << 28) & SysModuleBuiltins.HASH_MODULUS) | x >> (SysModuleBuiltins.HASH_BITS - 28);
            m *= 268435456.0; /* 2**28 */
            e -= 28;
            long y = (long) m; /* pull out integer part */
            m -= y;
            x += y;
            if (x >= SysModuleBuiltins.HASH_MODULUS) {
                x -= SysModuleBuiltins.HASH_MODULUS;
            }
        }
        e = e >= 0 ? e % SysModuleBuiltins.HASH_BITS : SysModuleBuiltins.HASH_BITS - 1 - ((-1 - e) % SysModuleBuiltins.HASH_BITS);
        x = ((x << e) & SysModuleBuiltins.HASH_MODULUS) | x >> (SysModuleBuiltins.HASH_BITS - e);
        x = x * sign;
        return avoidNegative1(x);
    }

    @Fallback
    @InliningCutoff
    static long genericHash(VirtualFrame frame, Node inliningTarget, Object object,
                    @Cached GetClassNode getClassNode,
                    @Cached GetCachedTpSlotsNode getSlotsNode,
                    @Cached CStructAccess.ReadI64Node readTypeObjectFieldNode,
                    @Cached InlinedConditionProfile typeIsNotReadyProfile,
                    @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                    @Cached CallSlotHashFunNode callHashFun,
                    @Cached PRaiseNode raiseNode) {
        Object klass = getClassNode.execute(inliningTarget, object);
        TpSlots slots = getSlotsNode.execute(inliningTarget, klass);
        if (slots.tp_hash() == null) {
            slots = handleNoHash(frame, inliningTarget, object, readTypeObjectFieldNode,
                            typeIsNotReadyProfile, boundaryCallData, raiseNode, klass, slots);
        }
        return callHashFun.execute(frame, inliningTarget, slots.tp_hash(), object);
    }

    @InliningCutoff
    private static TpSlots handleNoHash(VirtualFrame frame, Node inliningTarget, Object object, ReadI64Node readTypeObjectFieldNode,
                    InlinedConditionProfile typeIsNotReadyProfile,
                    BoundaryCallData boundaryCallData, PRaiseNode raiseNode, Object klass, TpSlots slots) {
        boolean initialized = false;
        if (klass instanceof PythonAbstractNativeObject nativeKlass) {
            // Comment from CPython:
            /*
             * To keep to the general practice that inheriting solely from object in C code should
             * work without an explicit call to PyType_Ready, we implicitly call PyType_Ready here
             * and then check the tp_hash slot again
             */
            long flags = readTypeObjectFieldNode.readFromObj(nativeKlass, CFields.PyTypeObject__tp_flags);
            if (typeIsNotReadyProfile.profile(inliningTarget, (flags & TypeFlags.READY) == 0)) {
                Object savedState = BoundaryCallContext.enter(frame, boundaryCallData);
                try {
                    slots = callTypeReady(inliningTarget, object, nativeKlass);
                    initialized = true;
                } finally {
                    BoundaryCallContext.exit(frame, boundaryCallData, savedState);
                }
            }
        }
        if (!initialized) {
            throw raiseUnhashable(inliningTarget, object, raiseNode);
        }
        return slots;
    }

    @TruffleBoundary
    private static TpSlots callTypeReady(Node inliningTarget, Object object, PythonAbstractNativeObject klass) {
        int res = (int) PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_PY_TYPE_READY, PythonToNativeNode.executeUncached(klass));
        if (res < 0) {
            throw raiseSystemError(inliningTarget, klass);
        }
        TpSlots slots = GetTpSlotsNode.executeUncached(klass);
        if (slots.tp_hash() == null) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.UNHASHABLE_TYPE_P, object);
        }
        return slots;
    }

    private static PException raiseSystemError(Node inliningTarget, Object klass) {
        throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.LAZY_INITIALIZATION_FAILED, klass);
    }

    @InliningCutoff
    private static PException raiseUnhashable(Node inliningTarget, Object object, PRaiseNode raiseNode) {
        throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.UNHASHABLE_TYPE_P, object);
    }
}
