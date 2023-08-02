/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode.GetPythonObjectClassNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Intended as a base-class for nodes that can use fast-path size (in the sense of
 * {@code PyObject_Size} and {@code PyObject_Length}) computation for builtin objects. The
 * subclasses are supposed to provide further specializations and a fallback.
 */
@GenerateCached(false)
public abstract class GraalPyObjectSizeNode extends PNodeWithContext {
    @Specialization
    static int doTruffleString(TruffleString str,
                    @Cached TruffleString.CodePointLengthNode codePointLengthNode) {
        return codePointLengthNode.execute(str, TS_ENCODING);
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)")
    static int doList(PList object,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode) {
        return object.getSequenceStorage().length();
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)")
    static int doTuple(PTuple object,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode) {
        return object.getSequenceStorage().length();
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)")
    static int doDict(PDict object,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                    @Shared("hashingStorageLen") @Cached HashingStorageLen lenNode) {
        return lenNode.execute(object.getDictStorage());
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)")
    static int doSet(PSet object,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                    @Shared("hashingStorageLen") @Cached HashingStorageLen lenNode) {
        return lenNode.execute(object.getDictStorage());
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)")
    static int doPString(PString object,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                    @Cached StringNodes.StringLenNode lenNode) {
        return lenNode.execute(object);
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)")
    static int doPBytes(PBytesLike object,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode) {
        return object.getSequenceStorage().length();
    }
}
