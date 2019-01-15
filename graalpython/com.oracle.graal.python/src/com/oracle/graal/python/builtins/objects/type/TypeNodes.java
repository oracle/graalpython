/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.type;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetMroNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetNameNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetTypeFlagsNodeGen;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class TypeNodes {

    public abstract static class GetTypeFlagsNode extends PNodeWithContext {

        public abstract long execute(PythonClass clazz);

        @Specialization(guards = "isInitialized(clazz)")
        long doInitialized(PythonClass clazz) {
            return clazz.getFlagsContainer().flags;
        }

        @Specialization
        long doGeneric(PythonClass clazz) {
            if (!isInitialized(clazz)) {
                return clazz.getFlags();
            }
            return clazz.getFlagsContainer().flags;
        }

        protected static boolean isInitialized(PythonClass clazz) {
            return clazz.getFlagsContainer().initialDominantBase == null;
        }

        public static GetTypeFlagsNode create() {
            return GetTypeFlagsNodeGen.create();
        }
    }

    public abstract static class GetMroNode extends PNodeWithContext {

        public abstract PythonClass[] execute(Object obj);

        @Specialization
        PythonClass[] doPythonClass(PythonClass obj) {
            return obj.getMethodResolutionOrder();
        }

        @Specialization
        PythonClass[] doPythonClass(PythonBuiltinClassType obj) {
            return getBuiltinPythonClass(obj).getMethodResolutionOrder();
        }

        @TruffleBoundary
        public static PythonClass[] doSlowPath(Object obj) {
            if (obj instanceof PythonClass) {
                return ((PythonClass) obj).getMethodResolutionOrder();
            } else if (obj instanceof PythonBuiltinClassType) {
                return PythonLanguage.getCore().lookupType((PythonBuiltinClassType) obj).getMethodResolutionOrder();
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("unknown type " + obj.getClass().getName());
        }

        public static GetMroNode create() {
            return GetMroNodeGen.create();
        }
    }

    public abstract static class GetNameNode extends PNodeWithContext {

        public abstract String execute(Object obj);

        @Specialization
        String doPythonClass(PythonClass obj) {
            return obj.getName();
        }

        @Specialization
        String doPythonClass(PythonBuiltinClassType obj) {
            return obj.getName();
        }

        @TruffleBoundary
        public static String doSlowPath(Object obj) {
            if (obj instanceof PythonClass) {
                return ((PythonClass) obj).getName();
            } else if (obj instanceof PythonBuiltinClassType) {
                // TODO(fa): remove this special case
                if (obj == PythonBuiltinClassType.TruffleObject) {
                    return BuiltinNames.FOREIGN;
                }
                return ((PythonBuiltinClassType) obj).getName();
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("unknown type " + obj.getClass().getName());
        }

        public static GetNameNode create() {
            return GetNameNodeGen.create();
        }

    }

}
