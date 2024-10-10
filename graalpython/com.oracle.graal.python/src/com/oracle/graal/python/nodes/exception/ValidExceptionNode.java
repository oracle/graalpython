/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.exception;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 18
public abstract class ValidExceptionNode extends PNodeWithContext {
    public abstract boolean execute(Frame frame, Object type);

    protected static boolean isPythonExceptionType(PythonBuiltinClassType type) {
        PythonBuiltinClassType base = type;
        while (base != null) {
            if (base == PythonBuiltinClassType.PBaseException) {
                return true;
            }
            base = base.getBase();
        }
        return false;
    }

    @Specialization(guards = "cachedType == type", limit = "3")
    static boolean isPythonExceptionTypeCached(@SuppressWarnings("unused") PythonBuiltinClassType type,
                    @SuppressWarnings("unused") @Cached("type") PythonBuiltinClassType cachedType,
                    @Cached("isPythonExceptionType(type)") boolean isExceptionType) {
        return isExceptionType;
    }

    @Specialization(guards = "cachedType == klass.getType()", limit = "3")
    static boolean isPythonExceptionClassCached(@SuppressWarnings("unused") PythonBuiltinClass klass,
                    @SuppressWarnings("unused") @Cached("klass.getType()") PythonBuiltinClassType cachedType,
                    @Cached("isPythonExceptionType(cachedType)") boolean isExceptionType) {
        return isExceptionType;
    }

    @Specialization(guards = "isTypeNode.execute(inliningTarget, type)", limit = "1", replaces = {"isPythonExceptionTypeCached", "isPythonExceptionClassCached"})
    static boolean isPythonException(VirtualFrame frame, Object type,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                    @Cached IsSubtypeNode isSubtype) {
        return isSubtype.execute(frame, type, PythonBuiltinClassType.PBaseException);
    }

    @Specialization(guards = "isForeignObjectNode.execute(inliningTarget, type)", limit = "1")
    static boolean isForeign(Object type,
                    @Bind("this") Node inliningTarget,
                    @Cached IsForeignObjectNode isForeignObjectNode,
                    @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop) {
        /*
         * There is no way to tell if a meta object is some kind of foreign exception class, so we
         * allow any foreign meta object
         */
        return interop.isMetaObject(type);
    }

    @Fallback
    static boolean isAnException(@SuppressWarnings("unused") Object type) {
        return false;
    }
}
