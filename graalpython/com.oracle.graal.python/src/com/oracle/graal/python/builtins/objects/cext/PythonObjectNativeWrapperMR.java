/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.GetSulongTypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.PGetDynamicTypeNodeGen;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

@MessageResolution(receiverType = PythonNativeWrapper.class)
public class PythonObjectNativeWrapperMR {
    private static final String GP_OBJECT = "gp_object";

    @SuppressWarnings("unknown-message")
    @Resolve(message = "com.oracle.truffle.llvm.spi.GetDynamicType")
    abstract static class GetDynamicTypeNode extends Node {
        @Child private PGetDynamicTypeNode getDynamicTypeNode = PGetDynamicTypeNode.create();

        public Object access(PythonNativeWrapper object) {
            return getDynamicTypeNode.execute(object);
        }
    }

    abstract static class PGetDynamicTypeNode extends PNodeWithContext {
        @Child private GetLazyClassNode getLazyClassNode = GetLazyClassNode.create();
        @Child private GetSulongTypeNode getSulongTypeNode = GetSulongTypeNode.create();
        @Child private AsPythonObjectNode getDelegate = AsPythonObjectNode.create();

        public abstract Object execute(PythonNativeWrapper obj);

        @Specialization(guards = "obj.isIntLike()")
        Object doIntLike(@SuppressWarnings("unused") PrimitiveNativeWrapper obj,
                        @Cached("getLongobjectType()") Object cachedSulongType) {
            return cachedSulongType;
        }

        @Specialization(guards = "obj.isBool()")
        Object doBool(@SuppressWarnings("unused") PrimitiveNativeWrapper obj,
                        @Cached("getBoolobjectType()") Object cachedSulongType) {
            return cachedSulongType;
        }

        @Specialization(guards = "obj.isDouble()")
        Object doDouble(@SuppressWarnings("unused") PrimitiveNativeWrapper obj,
                        @Cached("getFloatobjectType()") Object cachedSulongType) {
            return cachedSulongType;
        }

        @Specialization
        Object doGeneric(PythonNativeWrapper obj) {
            return getSulongTypeNode.execute(getLazyClassNode.execute(getDelegate.execute(obj)));
        }

        protected Object getLongobjectType() {
            return getSulongTypeNode.execute(PythonBuiltinClassType.PInt);
        }

        protected Object getBoolobjectType() {
            return getSulongTypeNode.execute(PythonBuiltinClassType.Boolean);
        }

        protected Object getFloatobjectType() {
            return getSulongTypeNode.execute(PythonBuiltinClassType.PFloat);
        }

        public static PGetDynamicTypeNode create() {
            return PGetDynamicTypeNodeGen.create();
        }
    }

    abstract static class GetSulongTypeNode extends PNodeWithContext {

        private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

        public abstract Object execute(LazyPythonClass clazz);

        @Specialization(guards = "clazz == cachedClass", limit = "10")
        Object doBuiltinCached(@SuppressWarnings("unused") PythonBuiltinClassType clazz,
                        @Cached("clazz") @SuppressWarnings("unused") PythonBuiltinClassType cachedClass,
                        @Cached("getSulongTypeForBuiltinClass(clazz)") Object sulongType) {
            return sulongType;
        }

        @Specialization(replaces = "doBuiltinCached")
        Object doBuiltinGeneric(PythonBuiltinClassType clazz) {
            return getSulongTypeForBuiltinClass(clazz);
        }

        @Specialization(assumptions = "singleContextAssumption()", guards = "clazz == cachedClass")
        Object doGeneric(@SuppressWarnings("unused") PythonClass clazz,
                        @Cached("clazz") @SuppressWarnings("unused") PythonClass cachedClass,
                        @Cached("doGeneric(clazz)") Object sulongType) {
            return sulongType;
        }

        @Specialization
        Object doGeneric(PythonClass clazz) {
            return getSulongTypeForClass(clazz);
        }

        protected Object getSulongTypeForBuiltinClass(PythonBuiltinClassType clazz) {
            PythonClass pythonClass = getPythonClass(clazz, profile);
            return getSulongTypeForClass(pythonClass);
        }

        private static Object getSulongTypeForClass(PythonClass klass) {
            Object sulongType = klass.getSulongType();
            if (sulongType == null) {
                CompilerDirectives.transferToInterpreter();
                sulongType = findBuiltinClass(klass);
                if (sulongType == null) {
                    throw new IllegalStateException("sulong type for " + klass.getName() + " was not registered");
                }
            }
            return sulongType;
        }

        private static Object findBuiltinClass(PythonClass klass) {
            PythonClass[] mro = klass.getMethodResolutionOrder();
            Object sulongType = null;
            for (PythonClass superClass : mro) {
                sulongType = superClass.getSulongType();
                if (sulongType != null) {
                    klass.setSulongType(sulongType);
                    break;
                }
            }
            return sulongType;
        }

        public static GetSulongTypeNode create() {
            return GetSulongTypeNodeGen.create();
        }

    }
}
