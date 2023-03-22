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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PyObject_Size} and {@code PyObject_Length} (alias of the former).
 * Returns length of objects by calling their {@code __len__}. Throws an exception if they don't
 * have any. Coerces the result to size integer using {@link PyNumberAsSizeNode}.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached()
public abstract class PyObjectSizeNode extends PNodeWithContext {
    public static int executeUncached(Frame frame, Object object) {
        return PyObjectSizeNodeGen.getUncached().execute(frame, null, object);
    }

    public static int executeUncached(Object object) {
        return PyObjectSizeNodeGen.getUncached().execute(null, null, object);
    }

    public final int executeCached(Frame frame, Object object) {
        return execute(frame, this, object);
    }

    public abstract int execute(Frame frame, Node inliningTarget, Object object);

    public abstract int execute(Frame frame, Node inliningTarget, PTuple object);

    protected abstract Object executeObject(Frame frame, Node inliningTarget, Object object);

    // Note: these fast-paths are duplicated in IteratorNodes$GetLength, because there is no simple
    // way to share them effectively without unnecessary indirections and overhead in the
    // interpreter

    @Specialization
    static int doTruffleString(TruffleString str,
                    @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode) {
        return codePointLengthNode.execute(str, TS_ENCODING);
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)", limit = "1")
    static int doList(Node inliningTarget, PList object,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode) {
        return object.getSequenceStorage().length();
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)", limit = "1")
    static int doTuple(Node inliningTarget, PTuple object,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode) {
        return object.getSequenceStorage().length();
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)", limit = "1")
    static int doDict(Node inliningTarget, PDict object,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                    @Shared("hashingStorageLen") @Cached HashingStorageLen lenNode) {
        return lenNode.execute(inliningTarget, object.getDictStorage());
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)", limit = "1")
    static int doSet(Node inliningTarget, PSet object,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                    @Shared("hashingStorageLen") @Cached HashingStorageLen lenNode) {
        return lenNode.execute(inliningTarget, object.getDictStorage());
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)", limit = "1")
    @InliningCutoff
    static int doPString(Node inliningTarget, PString object,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                    @Cached(inline = false) StringNodes.StringLenNode lenNode) {
        return lenNode.execute(object);
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)", limit = "1")
    static int doPBytes(Node inliningTarget, PBytesLike object,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode) {
        return object.getSequenceStorage().length();
    }

    @Fallback
    @InliningCutoff
    static int doOthers(VirtualFrame frame, Object object,
                    @Cached(inline = false) PyObjectSizeGenericNode genericNode) {
        return genericNode.execute(frame, object);
    }

    static int checkLen(PRaiseNode raiseNode, int len) {
        if (len < 0) {
            throw raiseNode.raise(ValueError, ErrorMessages.LEN_SHOULD_RETURN_GT_ZERO);
        }
        return len;
    }

    public static int convertAndCheckLen(VirtualFrame frame, Node inliningTarget, Object result, PyNumberIndexNode indexNode,
                    CastToJavaIntLossyNode castLossy, PyNumberAsSizeNode asSizeNode, PRaiseNode raiseNode) {
        int len;
        Object index = indexNode.execute(frame, inliningTarget, result);
        try {
            len = asSizeNode.executeExact(frame, inliningTarget, index);
        } catch (PException e) {
            /*
             * CPython first checks whether the number is negative before converting it to an
             * integer. Comparing PInts is not cheap for us, so we do the conversion first. If the
             * conversion overflowed, we need to do the negativity check before raising the overflow
             * error.
             */
            len = castLossy.execute(inliningTarget, index);
            checkLen(raiseNode, len);
            throw e;
        }
        return checkLen(raiseNode, len);
    }

    @NeverDefault
    public static PyObjectSizeNode create() {
        return PyObjectSizeNodeGen.create();
    }

    public static PyObjectSizeNode getUncached() {
        return PyObjectSizeNodeGen.getUncached();
    }
}
