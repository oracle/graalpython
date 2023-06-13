/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.capsule;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Compares two names according to the semantics of PyCapsule's {@code name_matches} function (see C
 * code snippet below). The name objects can be {@link TruffleString}, native pointer objects, or
 * {@code null}.
 *
 * <pre>
 *     static int
 *     name_matches(const char *name1, const char *name2) {
 *         // if either is NULL
 *         if (!name1 || !name2) {
 *             // they're only the same if they're both NULL.
 *             return name1 == name2;
 *         }
 *         return !strcmp(name1, name2);
 *     }
 * </pre>
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class PyCapsuleNameMatchesNode extends Node {
    public abstract boolean execute(Node inliningTarget, Object name1, Object name2);

    @Specialization(guards = "ignoredName2 == null")
    static boolean common(@SuppressWarnings("unused") PNone ignoredName1, @SuppressWarnings("unused") Object ignoredName2) {
        return true;
    }

    @Specialization
    static boolean ts(TruffleString n1, TruffleString n2,
                    @Shared @Cached(inline = false) TruffleString.EqualNode equalNode) {
        if (n1 == null && n2 == null) {
            return true;
        }
        if (n1 == null || n2 == null) {
            return false;
        }
        return equalNode.execute(n1, n2, TS_ENCODING);
    }

    @Fallback
    static boolean fallback(Object name1, Object name2,
                    @Cached(inline = false) CExtNodes.FromCharPointerNode fromCharPtr,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @Shared @Cached(inline = false) TruffleString.EqualNode equalNode) {
        TruffleString n1 = name1 instanceof TruffleString ? (TruffleString) name1 : null;
        TruffleString n2 = name2 instanceof TruffleString ? (TruffleString) name2 : null;
        if (n1 == null) {
            n1 = lib.isNull(name1) ? null : fromCharPtr.execute(name1, false);
        }
        if (n2 == null) {
            n2 = lib.isNull(name2) ? null : fromCharPtr.execute(name2, false);
        }
        return ts(n1, n2, equalNode);
    }
}
