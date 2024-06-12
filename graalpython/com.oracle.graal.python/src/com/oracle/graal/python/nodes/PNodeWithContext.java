/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

@ImportStatic({PGuards.class, PythonOptions.class, SpecialMethodNames.class, SpecialAttributeNames.class, SpecialMethodSlot.class, BuiltinNames.class})
public abstract class PNodeWithContext extends Node {

    /**
     * @return {@code true} if this node can be shared statically.
     */
    @Idempotent
    // conflicting with Node.isUncached(). Should be migrated to Node.isUncached() in the future.
    protected final boolean isUncachedNode() {
        return !isAdoptable();
    }

    @TruffleBoundary
    public static void printStack() {
        // a convenience methods for debugging
        ExceptionUtils.printPythonLikeStackTrace();
    }

    @Idempotent
    public final PythonLanguage getLanguage() {
        return PythonLanguage.get(this);
    }

    @NonIdempotent
    public final PythonContext getContext() {
        return PythonContext.get(this);
    }

    @NonIdempotent
    public static PythonContext getContext(Node node) {
        return PythonContext.get(node);
    }

    @Idempotent
    public final boolean isSingleContext() {
        return getLanguage().isSingleContext();
    }

    @Idempotent
    public static boolean isSingleContext(Node node) {
        return PythonLanguage.get(node).isSingleContext();
    }

    public ValueProfile createValueIdentityProfile() {
        return getLanguage().isSingleContext() ? ValueProfile.createIdentityProfile() : ValueProfile.createClassProfile();
    }

    public final PosixSupport getPosixSupport() {
        return getContext().getPosixSupport();
    }
}
