/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.arrow.vector;

import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.arrow.ArrowSchema;
import com.oracle.graal.python.runtime.arrow.ArrowVectorSupport;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import static com.oracle.graal.python.nodes.arrow.ArrowArray.NULL;

@GenerateCached(false)
@GenerateInline
public abstract class VectorToArrowSchemaNode extends PNodeWithContext {

    public abstract ArrowSchema execute(Node inliningTarget, Object vector);

    @Specialization(guards = "arrowVectorSupport.isFixedWidthVector(vector)")
    static ArrowSchema doIntVector(Node inliningTarget, Object vector,
                    @Bind("getContext(inliningTarget)") PythonContext ctx,
                    @Bind("ctx.arrowVectorSupport") ArrowVectorSupport arrowVectorSupport,
                    @Cached GetFormatFromVectorNode formatNode) {
        Object hostVector = ctx.getEnv().asHostObject(vector);
        var unsafe = ctx.getUnsafe();
        var snapshot = new ArrowSchema.Snapshot();
        // + 1 NULL terminator
        snapshot.format = unsafe.allocateMemory(2);
        unsafe.putByte(snapshot.format, formatNode.execute(inliningTarget, hostVector));
        unsafe.putByte(snapshot.format + 1, NULL);
        snapshot.release = ctx.arrowSupport.getArrowSchemaReleaseCallback();

        return ArrowSchema.allocateFromSnapshot(snapshot);
    }

    @Fallback
    static ArrowSchema doError(Node inliningTarget, Object object) {
        throw CompilerDirectives.shouldNotReachHere();
    }
}
