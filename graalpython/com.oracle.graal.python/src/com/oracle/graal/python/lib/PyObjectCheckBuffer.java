/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.modules.io.PBytesIOBuffer;
import com.oracle.graal.python.builtins.modules.pickle.PPickleBuffer;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.util.LazyInteropLibrary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {@code PyObject_CheckBuffer}. Acquisition may still raise an exception.
 */
@GenerateUncached
@GenerateInline(false)
public abstract class PyObjectCheckBuffer extends PNodeWithContext {
    public abstract boolean execute(Object object);

    public static boolean executeUncached(Object object) {
        return PyObjectCheckBufferNodeGen.getUncached().execute(object);
    }

    @Specialization
    static boolean doPBytes(@SuppressWarnings("unused") PBytes object) {
        return true;
    }

    @Specialization
    static boolean doPByteArray(@SuppressWarnings("unused") PByteArray object) {
        return true;
    }

    @Specialization
    static boolean doPArray(@SuppressWarnings("unused") PArray object) {
        return true;
    }

    @Specialization
    static boolean doPMemoryView(@SuppressWarnings("unused") PMemoryView object) {
        return true;
    }

    @Specialization
    static boolean doPMMap(@SuppressWarnings("unused") PMMap object) {
        return true;
    }

    @Specialization
    static boolean doPPickleBuffer(@SuppressWarnings("unused") PPickleBuffer object) {
        return true;
    }

    @Specialization
    static boolean doPBytesIOBuffer(@SuppressWarnings("unused") PBytesIOBuffer object) {
        return true;
    }

    @Specialization
    static boolean doNative(PythonAbstractNativeObject object,
                    @Bind Node inliningTarget,
                    @Cached CExtNodes.HasNativeBufferNode hasNativeBuffer) {
        return hasNativeBuffer.execute(inliningTarget, object);
    }

    @Fallback
    static boolean doOther(Object object,
                    @Bind Node inliningTarget,
                    @Cached IsForeignObjectNode isForeignObjectNode,
                    @Cached LazyInteropLibrary interopLib) {
        if (isForeignObjectNode.execute(inliningTarget, object)) {
            return interopLib.get(inliningTarget).hasBufferElements(object);
        } else {
            return false;
        }
    }

    @NeverDefault
    public static PyObjectCheckBuffer create() {
        return PyObjectCheckBufferNodeGen.create();
    }

    public static PyObjectCheckBuffer getUncached() {
        return PyObjectCheckBufferNodeGen.getUncached();
    }
}
