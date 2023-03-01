/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.PGetDynamicTypeNodeGen.GetSulongTypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetMroStorageNodeGen;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;

@GenerateUncached
abstract class PGetDynamicTypeNode extends PNodeWithContext {

    public abstract Object execute(PythonNativeWrapper obj);

    @Specialization(guards = {"isSingleContext()", "obj.isIntLike()"})
    static Object doIntLikeSingleContext(@SuppressWarnings("unused") DynamicObjectNativeWrapper.PrimitiveNativeWrapper obj,
                    @Cached(value = "getLongobjectType()", allowUncached = true) Object cachedSulongType) {
        return cachedSulongType;
    }

    @Specialization(guards = {"isSingleContext()", "obj.isBool()"})
    static Object doBoolSingleContext(@SuppressWarnings("unused") DynamicObjectNativeWrapper.PrimitiveNativeWrapper obj,
                    @Cached(value = "getBoolobjectType()", allowUncached = true) Object cachedSulongType) {
        return cachedSulongType;
    }

    @Specialization(guards = {"isSingleContext()", "obj.isDouble()"})
    static Object doDoubleSingleContext(@SuppressWarnings("unused") DynamicObjectNativeWrapper.PrimitiveNativeWrapper obj,
                    @Cached(value = "getFloatobjectType()", allowUncached = true) Object cachedSulongType) {
        return cachedSulongType;
    }

    @Specialization(guards = "obj.isIntLike()")
    static Object doIntLike(@SuppressWarnings("unused") DynamicObjectNativeWrapper.PrimitiveNativeWrapper obj,
                    @Shared("getSulongTypeNode") @Cached GetSulongTypeNode getSulongTypeNode) {
        return getSulongTypeNode.execute(PythonBuiltinClassType.PInt);
    }

    @Specialization(guards = "obj.isBool()")
    static Object doBool(@SuppressWarnings("unused") DynamicObjectNativeWrapper.PrimitiveNativeWrapper obj,
                    @Shared("getSulongTypeNode") @Cached GetSulongTypeNode getSulongTypeNode) {
        return getSulongTypeNode.execute(PythonBuiltinClassType.Boolean);
    }

    @Specialization(guards = "obj.isDouble()")
    static Object doDouble(@SuppressWarnings("unused") DynamicObjectNativeWrapper.PrimitiveNativeWrapper obj,
                    @Shared("getSulongTypeNode") @Cached GetSulongTypeNode getSulongTypeNode) {
        return getSulongTypeNode.execute(PythonBuiltinClassType.PFloat);
    }

    @Specialization(replaces = {"doIntLike", "doBool", "doDouble"})
    static Object doGeneric(PythonNativeWrapper obj,
                    @Cached GetSulongTypeNode getSulongTypeNode,
                    @Cached NativeToPythonNode getDelegate,
                    @Cached GetClassNode getClassNode) {
        return getSulongTypeNode.execute(getClassNode.execute(getDelegate.execute(obj)));
    }

    protected static Object getLongobjectType() {
        return GetSulongTypeNodeGen.getUncached().execute(PythonBuiltinClassType.PInt);
    }

    protected static Object getBoolobjectType() {
        return GetSulongTypeNodeGen.getUncached().execute(PythonBuiltinClassType.Boolean);
    }

    protected static Object getFloatobjectType() {
        return GetSulongTypeNodeGen.getUncached().execute(PythonBuiltinClassType.PFloat);
    }

    @GenerateUncached
    abstract static class GetSulongTypeNode extends PNodeWithContext {

        public abstract Object execute(Object clazz);

        @Specialization(guards = {"isSingleContext()", "clazz == cachedClass"}, limit = "10")
        static Object doBuiltinCachedResult(@SuppressWarnings("unused") PythonBuiltinClassType clazz,
                        @Cached("clazz") @SuppressWarnings("unused") PythonBuiltinClassType cachedClass,
                        @Cached("getLLVMTypeForBuiltinClass(clazz, getContext())") Object llvmType) {
            return llvmType;
        }

        @Specialization(guards = {"isSingleContext()", "clazz == cachedClass"}, limit = "1")
        Object doBuiltinCached(@SuppressWarnings("unused") PythonBuiltinClassType clazz,
                        @Cached("clazz") @SuppressWarnings("unused") PythonBuiltinClassType cachedClass) {
            return getLLVMTypeForBuiltinClass(cachedClass, getContext());
        }

        @Specialization(replaces = {"doBuiltinCachedResult", "doBuiltinCached"})
        Object doBuiltinGeneric(PythonBuiltinClassType clazz) {
            return getLLVMTypeForBuiltinClass(clazz, getContext());
        }

        @Specialization(guards = {"isSingleContext()", "clazz == cachedClass"})
        static Object doManagedClassCached(@SuppressWarnings("unused") PythonManagedClass clazz,
                        @Cached("clazz") @SuppressWarnings("unused") PythonManagedClass cachedClass,
                        @Cached("getLLVMTypeForClass(clazz)") Object llvmType) {
            return llvmType;
        }

        @Specialization(replaces = "doManagedClassCached")
        static Object doManagedClass(PythonManagedClass clazz) {
            return getLLVMTypeForClass(clazz);
        }

        @Specialization(guards = {"mro.length() == cachedLen"})
        static Object doNativeClassCachedLen(@SuppressWarnings("unused") PythonAbstractNativeObject clazz,
                        @Cached @SuppressWarnings("unused") GetMroStorageNode getMroStorageNode,
                        @Bind("getMroStorageNode.execute(clazz)") MroSequenceStorage mro,
                        @Cached("mro.length()") int cachedLen) {
            return findBuiltinClass(mro, cachedLen);
        }

        @Specialization(replaces = {"doNativeClassCachedLen"})
        static Object doNativeClass(PythonAbstractNativeObject clazz,
                        @Cached GetMroStorageNode getMroStorageNode) {
            MroSequenceStorage mro = getMroStorageNode.execute(clazz);
            return findBuiltinClass(mro, mro.length());
        }

        protected static Object getLLVMTypeForBuiltinClass(PythonBuiltinClassType clazz, PythonContext context) {
            PythonBuiltinClass pythonClass = context.lookupType(clazz);
            return getLLVMTypeForClass(pythonClass);
        }

        protected static Object getLLVMTypeForClass(PythonManagedClass pythonClass) {
            Object llvmType = pythonClass.getSulongType();
            if (llvmType == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                MroSequenceStorage mro = GetMroStorageNodeGen.getUncached().execute(pythonClass);
                llvmType = findBuiltinClass(mro, mro.length());
                if (llvmType != null) {
                    pythonClass.setSulongType(llvmType);
                } else {
                    throw CompilerDirectives.shouldNotReachHere("LLVM type for " + GetNameNode.getUncached().execute(pythonClass) + " was not registered");
                }
            }
            return llvmType;
        }

        @ExplodeLoop
        private static Object findBuiltinClass(MroSequenceStorage mro, int mroLength) {
            for (int i = 0; i < mroLength; i++) {
                Object superClass = mro.getItemNormalized(i);
                if (superClass instanceof PythonManagedClass) {
                    Object llvmType = ((PythonManagedClass) superClass).getSulongType();
                    if (llvmType != null) {
                        return llvmType;
                    }
                }
            }
            return null;
        }
    }
}
