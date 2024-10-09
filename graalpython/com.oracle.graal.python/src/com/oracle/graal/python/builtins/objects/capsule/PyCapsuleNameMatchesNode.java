/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Compares two names according to the semantics of PyCapsule's {@code name_matches} function (see C
 * code snippet below). The name objects can be native pointer objects, or {@code null}.
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

    @Specialization
    static boolean compare(Node inliningTarget, Object name1, Object name2,
                    @CachedLibrary(limit = "2") InteropLibrary lib,
                    @Cached ReadByteNode readByteNode) {
        try {
            if (name1 != null && lib.isNull(name1)) {
                name1 = null;
            }
            if (name2 != null && lib.isNull(name2)) {
                name2 = null;
            }
            if (name1 == null || name2 == null) {
                return name1 == name2;
            }
            if (lib.isPointer(name1) && lib.isPointer(name2) && lib.asPointer(name1) == lib.asPointer(name2)) {
                return true;
            }
            for (int i = 0;; i++) {
                byte b1 = readByteNode.execute(inliningTarget, name1, i);
                byte b2 = readByteNode.execute(inliningTarget, name2, i);
                if (b1 != b2) {
                    return false;
                }
                if (b1 == 0) {
                    return true;
                }
            }
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class ReadByteNode extends Node {
        public abstract byte execute(Node inliningTarget, Object ptr, int i);

        @Specialization
        static byte doManaged(CArrayWrappers.CByteArrayWrapper wrapper, int i) {
            byte[] bytes = wrapper.getByteArray();
            if (i < bytes.length) {
                return bytes[i];
            }
            return 0;
        }

        @Fallback
        static byte doNative(Object ptr, int i,
                        @Cached(inline = false) CStructAccess.ReadByteNode readByteNode) {
            return readByteNode.readArrayElement(ptr, i);
        }
    }
}
