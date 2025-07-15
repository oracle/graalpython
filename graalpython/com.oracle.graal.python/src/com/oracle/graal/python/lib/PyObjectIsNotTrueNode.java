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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectIsTrueNode.PyObjectIsTrueNodeGeneric;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of a negation of CPython's {@code PyObject_IsTrue}. This class exists only so that we
 * can have quickening fast-paths for this operation. The fast-paths should be synchronized with
 * {@link PyObjectIsNotTrueNode}.
 */
@GenerateInline(false)
@OperationProxy.Proxyable
public abstract class PyObjectIsNotTrueNode extends PNodeWithContext {
    public abstract boolean execute(Frame frame, Object object);

    @Specialization
    public static boolean doBoolean(boolean object) {
        return !object;
    }

    @Specialization
    public static boolean doNone(@SuppressWarnings("unused") PNone object) {
        return true;
    }

    @Specialization
    public static boolean doInt(int object) {
        return object == 0;
    }

    @Specialization
    public static boolean doLong(long object) {
        return object == 0;
    }

    @Specialization
    public static boolean doDouble(double object) {
        return object == 0.0;
    }

    @Specialization
    public static boolean doString(TruffleString object) {
        return object.isEmpty();
    }

    @Specialization(guards = "isBuiltinList(object)")
    public static boolean doList(PList object) {
        return object.getSequenceStorage().length() == 0;
    }

    @Specialization(guards = "isBuiltinTuple(object)")
    public static boolean doTuple(PTuple object) {
        return object.getSequenceStorage().length() == 0;
    }

    @Specialization(guards = "isBuiltinDict(object)")
    public static boolean doDict(PDict object,
                    @Bind Node inliningTarget,
                    @Exclusive @Cached HashingStorageLen lenNode) {
        return lenNode.execute(inliningTarget, object.getDictStorage()) == 0;
    }

    @Specialization(guards = "isBuiltinAnySet(object)")
    @InliningCutoff
    public static boolean doSet(PBaseSet object,
                    @Bind Node inliningTarget,
                    @Exclusive @Cached HashingStorageLen lenNode) {
        return lenNode.execute(inliningTarget, object.getDictStorage()) == 0;
    }

    @Specialization(guards = {"!isBoolean(object)", "!isPNone(object)", "!isInt(object)", "!isLong(object)", "!isDouble(object)", "!isTruffleString(object)"}, //
                    replaces = {"doList", "doTuple", "doDict", "doSet"})
    @InliningCutoff
    public static boolean doOthers(VirtualFrame frame, Object object,
                    @Cached PyObjectIsTrueNodeGeneric internalNode) {
        return !internalNode.execute(frame, object);
    }

    @NeverDefault
    public static PyObjectIsNotTrueNode create() {
        return PyObjectIsNotTrueNodeGen.create();
    }
}
