/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUNC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TRUNC__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotUnaryFunc.CallSlotUnaryNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.MaybeBindDescriptorNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PyNumber_Long}. Converts objects to Python int using their
 * {@code __int__} or {@code __index__} or {@code __trunc__} methods. Raises {@code TypeError} if
 * they don't have any.
 */
@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class PyNumberLongNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Node inliningTarget, Object object);

    public static Object executeUncached(Object object) {
        return PyNumberLongNodeGen.getUncached().execute(null, null, object);
    }

    @Specialization
    static int doInt(int object) {
        return object;
    }

    @Specialization
    static long doLong(long object) {
        return object;
    }

    @Specialization(guards = "isBuiltinPInt(object)")
    static PInt doPInt(PInt object) {
        return object;
    }

    @Specialization
    static Object doDouble(Node inliningTarget, double object,
                    @Cached PyLongFromDoubleNode fromDoubleNode) {
        return fromDoubleNode.execute(inliningTarget, object);
    }

    @Specialization
    static Object doString(Node inliningTarget, TruffleString string,
                    @Cached PyLongFromUnicodeObject fromUnicodeObject) {
        return fromUnicodeObject.execute(inliningTarget, string, 10);
    }

    @Fallback
    @InliningCutoff
    static Object doOther(VirtualFrame frame, Node inliningTarget, Object object,
                    @Cached GetClassNode getClassNode,
                    @Cached GetCachedTpSlotsNode getSlots,
                    @Cached CallSlotUnaryNode callInt,
                    @Cached PyLongCheckExactNode longCheckExactNode,
                    @Cached PyNumberIndexNode indexNode,
                    @Cached HandleIntResult handleIntResult,
                    @Cached ComplexCasesHelperNode helperNode) {
        Object type = getClassNode.execute(inliningTarget, object);
        TpSlots slots = getSlots.execute(inliningTarget, type);
        if (slots.nb_int() != null) {
            Object result = callInt.execute(frame, inliningTarget, slots.nb_int(), object);
            if (longCheckExactNode.execute(inliningTarget, result)) {
                return result;
            }
            return handleIntResult(frame, object, handleIntResult, result);
        }
        if (slots.nb_index() != null) {
            return indexNode.execute(frame, inliningTarget, object);
        }
        return complexCase(frame, object, helperNode);
    }

    @InliningCutoff
    private static Object handleIntResult(VirtualFrame frame, Object object, HandleIntResult handleIntResult, Object result) {
        return handleIntResult.execute(frame, object, result);
    }

    @InliningCutoff
    private static Object complexCase(VirtualFrame frame, Object object, ComplexCasesHelperNode helperNode) {
        return helperNode.execute(frame, object);
    }

    @GenerateInline(false) // Slow path
    @GenerateUncached
    abstract static class HandleIntResult extends Node {
        abstract Object execute(VirtualFrame frame, Object original, Object result);

        @Specialization
        static Object doGeneric(VirtualFrame frame, Object original, Object result,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtype,
                        @Cached PRaiseNode raiseNode,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached PyLongCopy copy) {
            if (!isSubtype.execute(getClassNode.execute(inliningTarget, result), PythonBuiltinClassType.PInt)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.RETURNED_NON_INT, J___INT__, result);
            }
            warnNode.warnFormat(frame, null, PythonBuiltinClassType.DeprecationWarning, 1,
                            ErrorMessages.WARN_P_RETURNED_NON_P, original, J___INT__, "int", result, "int");
            return copy.execute(inliningTarget, result);
        }
    }

    @GenerateInline(false) // Slow path
    @GenerateUncached
    abstract static class ComplexCasesHelperNode extends Node {
        abstract Object execute(VirtualFrame frame, Object object);

        @Specialization
        static Object doGeneric(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookup,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached CallUnaryMethodNode call,
                        @Cached MaybeBindDescriptorNode bindDescriptorNode,
                        @Cached PyLongCheckExactNode longCheckExactNode,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached PyLongCopy longCopy,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached PyLongFromUnicodeObject fromUnicodeObject,
                        @Cached LongFromBufferNode fromBufferNode,
                        @Cached PRaiseNode raiseNode) {
            Object type = getClassNode.execute(inliningTarget, object);
            Object truncMethod = lookup.execute(type, T___TRUNC__);
            if (truncMethod != PNone.NO_VALUE) {
                truncMethod = bindDescriptorNode.execute(frame, inliningTarget, truncMethod, object, type);
                warnNode.warnEx(frame, PythonBuiltinClassType.DeprecationWarning,
                                ErrorMessages.WARN_DELEGATION_OF_INT_TO_TRUNC_IS_DEPRECATED, 1);
                Object result = call.executeObject(frame, truncMethod, object);
                if (longCheckExactNode.execute(inliningTarget, result)) {
                    return result;
                }
                if (longCheckNode.execute(inliningTarget, result)) {
                    return longCopy.execute(inliningTarget, result);
                }
                if (!indexCheckNode.execute(inliningTarget, result)) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.RETURNED_NON_INTEGRAL, J___TRUNC__, result);
                }
                return indexNode.execute(frame, inliningTarget, result);
            }

            if (unicodeCheckNode.execute(inliningTarget, object)) {
                return fromUnicodeObject.execute(inliningTarget, object, 10);
            }

            Object result = fromBufferNode.execute(frame, object, 10);
            if (result != null) {
                return result;
            }

            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ARG_MUST_BE_STRING_OR_BYTELIKE_OR_NUMBER, "int()", object);
        }
    }

    @GenerateInline(false) // Uncommon case
    @GenerateUncached
    public abstract static class LongFromBufferNode extends Node {
        public abstract Object execute(VirtualFrame frame, Object object, int base);

        @Specialization
        @InliningCutoff
        static Object doGeneric(VirtualFrame frame, Object object, int base,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached PyLongFromUnicodeObject fromString,
                        @Cached(value = "createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            Object buffer;
            try {
                buffer = acquireLib.acquireReadonly(object, frame, indirectCallData);
            } catch (AbstractTruffleException e) {
                return null;
            }
            try {
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int len = bufferLib.getBufferLength(buffer);
                TruffleString string = fromByteArrayNode.execute(bytes, 0, len, TruffleString.Encoding.US_ASCII, false);
                string = switchEncodingNode.execute(string, TS_ENCODING);
                return fromString.execute(inliningTarget, string, base, bytes, len);
            } finally {
                bufferLib.release(buffer);
            }
        }
    }
}
