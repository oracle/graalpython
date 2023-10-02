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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PyObject_IsTrue}. Converts object to a boolean value using its
 * {@code __bool__} special method. Falls back to comparing {@code __len__} result with 0. Defaults
 * to true if neither is defined.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyObjectIsTrueNode extends PNodeWithContext {
    public final boolean executeCached(Frame frame, Object object) {
        return execute(frame, this, object);
    }

    public abstract boolean execute(Frame frame, Node inliningTarget, Object object);

    protected abstract Object executeObject(Frame frame, Node inliningTarget, Object object);

    @Specialization
    static boolean doBoolean(boolean object) {
        return object;
    }

    @Specialization
    static boolean doNone(@SuppressWarnings("unused") PNone object) {
        return false;
    }

    @Specialization
    static boolean doInt(int object) {
        return object != 0;
    }

    @Specialization
    static boolean doLong(long object) {
        return object != 0;
    }

    @Specialization
    static boolean doDouble(double object) {
        return object != 0.0;
    }

    @Specialization
    static boolean doString(TruffleString object) {
        return !object.isEmpty();
    }

    @Specialization(guards = "cannotBeOverriddenForImmutableType(object)")
    static boolean doList(Node inliningTarget, PList object) {
        return object.getSequenceStorage().length() != 0;
    }

    @Specialization(guards = "cannotBeOverriddenForImmutableType(object)")
    static boolean doTuple(Node inliningTarget, PTuple object) {
        return object.getSequenceStorage().length() != 0;
    }

    @Specialization(guards = "cannotBeOverriddenForImmutableType(object)")
    static boolean doDict(Node inliningTarget, PDict object,
                    @Shared("hashingStorageLen") @Cached HashingStorageLen lenNode) {
        return lenNode.execute(inliningTarget, object.getDictStorage()) != 0;
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)", limit = "1")
    @InliningCutoff
    static boolean doSet(Node inliningTarget, PSet object,
                    @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                    @Shared("hashingStorageLen") @Cached HashingStorageLen lenNode) {
        return lenNode.execute(inliningTarget, object.getDictStorage()) != 0;
    }

    @Fallback
    @InliningCutoff
    static boolean doOthers(Frame frame, Object object,
                    @Cached(inline = false) PyObjectIsTrueNodeGeneric internalNode) {
        // Cached PyObjectItTrue nodes used in PBytecodeRootNode are significant contributors to
        // footprint, so we use indirection to save all the fields for the nodes used in the generic
        // variant
        return internalNode.execute(frame, object);
    }

    @GenerateInline(false)
    @GenerateUncached
    public abstract static class PyObjectIsTrueNodeGeneric extends PNodeWithContext {
        public abstract boolean execute(Frame frame, Object object);

        protected abstract Object executeObject(Frame frame, Object object);

        @Specialization(guards = {"!isBoolean(object)", "!isPNone(object)", "!isInteger(object)", "!isDouble(object)"}, rewriteOn = UnexpectedResultException.class)
        static boolean doObjectUnboxed(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Shared("lookupBool") @Cached(parameters = "Bool") LookupSpecialMethodSlotNode lookupBool,
                        @Shared("lookupLen") @Cached(parameters = "Len") LookupSpecialMethodSlotNode lookupLen,
                        @Shared("callBool") @Cached CallUnaryMethodNode callBool,
                        @Shared("callLen") @Cached CallUnaryMethodNode callLen,
                        @Shared("cast") @Cached CastToJavaBooleanNode cast,
                        @Shared("index") @Cached PyNumberIndexNode indexNode,
                        @Shared("castLossy") @Cached CastToJavaIntLossyNode castLossy,
                        @Shared("asSize") @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared("raise") @Cached PRaiseNode.Lazy raiseNode) throws UnexpectedResultException {
            Object type = getClassNode.execute(inliningTarget, object);
            Object boolDescr = lookupBool.execute(frame, type, object);
            if (boolDescr != PNone.NO_VALUE) {
                try {
                    return PGuards.expectBoolean(callBool.executeObject(frame, boolDescr, object));
                } catch (UnexpectedResultException e) {
                    throw new UnexpectedResultException(checkBoolResult(inliningTarget, cast, raiseNode.get(inliningTarget), e.getResult()));
                }
            }
            Object lenDescr = lookupLen.execute(frame, type, object);
            if (lenDescr != PNone.NO_VALUE) {
                try {
                    return PyObjectSizeNode.checkLen(raiseNode.get(inliningTarget), PGuards.expectInteger(callLen.executeObject(frame, lenDescr, object))) != 0;
                } catch (UnexpectedResultException e) {
                    int len = PyObjectSizeNode.convertAndCheckLen(frame, inliningTarget, e.getResult(), indexNode, castLossy, asSizeNode, raiseNode.get(inliningTarget));
                    throw new UnexpectedResultException(len != 0);
                }
            }
            return true;
        }

        @Specialization(guards = {"!isBoolean(object)", "!isPNone(object)", "!isInteger(object)", "!isDouble(object)"}, replaces = "doObjectUnboxed")
        static boolean doObject(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Shared("lookupBool") @Cached(parameters = "Bool") LookupSpecialMethodSlotNode lookupBool,
                        @Shared("lookupLen") @Cached(parameters = "Len") LookupSpecialMethodSlotNode lookupLen,
                        @Shared("callBool") @Cached CallUnaryMethodNode callBool,
                        @Shared("callLen") @Cached CallUnaryMethodNode callLen,
                        @Shared("cast") @Cached CastToJavaBooleanNode cast,
                        @Shared("index") @Cached PyNumberIndexNode indexNode,
                        @Shared("castLossy") @Cached CastToJavaIntLossyNode castLossy,
                        @Shared("asSize") @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared("raise") @Cached PRaiseNode.Lazy raiseNode) {
            Object type = getClassNode.execute(inliningTarget, object);
            Object boolDescr = lookupBool.execute(frame, type, object);
            if (boolDescr != PNone.NO_VALUE) {
                Object result = callBool.executeObject(frame, boolDescr, object);
                return checkBoolResult(inliningTarget, cast, raiseNode.get(inliningTarget), result);
            }
            Object lenDescr = lookupLen.execute(frame, type, object);
            if (lenDescr != PNone.NO_VALUE) {
                Object result = callLen.executeObject(frame, lenDescr, object);
                int len = PyObjectSizeNode.convertAndCheckLen(frame, inliningTarget, result, indexNode, castLossy, asSizeNode, raiseNode.get(inliningTarget));
                return len != 0;
            }
            return true;
        }
    }

    private static boolean checkBoolResult(Node inliningTarget, CastToJavaBooleanNode cast, PRaiseNode raiseNode, Object result) {
        try {
            return cast.execute(inliningTarget, result);
        } catch (CannotCastException e) {
            throw raiseNode.raise(TypeError, ErrorMessages.BOOL_SHOULD_RETURN_BOOL, result);
        }
    }

    @NeverDefault
    public static PyObjectIsTrueNode create() {
        return PyObjectIsTrueNodeGen.create();
    }

    public static PyObjectIsTrueNode getUncached() {
        return PyObjectIsTrueNodeGen.getUncached();
    }
}
