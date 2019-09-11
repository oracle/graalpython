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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateUncached
abstract class PGetDynamicTypeNode extends PNodeWithContext {

    public abstract Object execute(PythonNativeWrapper obj);

    @Specialization(guards = "obj.isIntLike()")
    Object doIntLike(@SuppressWarnings("unused") DynamicObjectNativeWrapper.PrimitiveNativeWrapper obj,
                    @Cached(value = "getLongobjectType()", allowUncached = true) Object cachedSulongType) {
        return cachedSulongType;
    }

    @Specialization(guards = "obj.isBool()")
    Object doBool(@SuppressWarnings("unused") DynamicObjectNativeWrapper.PrimitiveNativeWrapper obj,
                    @Cached(value = "getBoolobjectType()", allowUncached = true) Object cachedSulongType) {
        return cachedSulongType;
    }

    @Specialization(guards = "obj.isDouble()")
    Object doDouble(@SuppressWarnings("unused") DynamicObjectNativeWrapper.PrimitiveNativeWrapper obj,
                    @Cached(value = "getFloatobjectType()", allowUncached = true) Object cachedSulongType) {
        return cachedSulongType;
    }

    @Specialization
    Object doGeneric(PythonNativeWrapper obj,
                    @Cached GetSulongTypeNode getSulongTypeNode,
                    @Cached AsPythonObjectNode getDelegate,
                    @Cached GetLazyClassNode getLazyClassNode) {
        return getSulongTypeNode.execute(getLazyClassNode.execute(getDelegate.execute(obj)));
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
}

@GenerateUncached
abstract class GetSulongTypeNode extends PNodeWithContext {

    public abstract Object execute(LazyPythonClass clazz);

    @Specialization(guards = "clazz == cachedClass", limit = "10")
    Object doBuiltinCached(@SuppressWarnings("unused") PythonBuiltinClassType clazz,
                    @Cached("clazz") @SuppressWarnings("unused") PythonBuiltinClassType cachedClass,
                    @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                    @Cached("getSulongTypeForBuiltinClass(clazz, context)") Object sulongType) {
        return sulongType;
    }

    @Specialization(replaces = "doBuiltinCached")
    Object doBuiltinGeneric(PythonBuiltinClassType clazz,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        return getSulongTypeForBuiltinClass(clazz, context);
    }

    @Specialization(assumptions = "singleContextAssumption()", guards = "clazz == cachedClass")
    Object doGeneric(@SuppressWarnings("unused") PythonManagedClass clazz,
                    @Cached("clazz") @SuppressWarnings("unused") PythonManagedClass cachedClass,
                    @Cached(value = "doGeneric(clazz)", allowUncached = true) Object sulongType) {
        return sulongType;
    }

    @Specialization
    Object doGeneric(PythonManagedClass clazz) {
        return getSulongTypeForClass(clazz);
    }

    protected Object getSulongTypeForBuiltinClass(PythonBuiltinClassType clazz, PythonContext context) {
        PythonBuiltinClass pythonClass = context.getCore().lookupType(clazz);
        return getSulongTypeForClass(pythonClass);
    }

    private static Object getSulongTypeForClass(PythonManagedClass pythonClass) {
        Object sulongType = pythonClass.getSulongType();
        if (sulongType == null) {
            CompilerDirectives.transferToInterpreter();
            sulongType = findBuiltinClass(pythonClass);
            if (sulongType == null) {
                throw new IllegalStateException("sulong type for " + GetNameNode.getUncached().execute(pythonClass) + " was not registered");
            }
        }
        return sulongType;
    }

    private static Object findBuiltinClass(PythonManagedClass pythonClass) {
        PythonAbstractClass[] mro = GetMroNode.getUncached().execute(pythonClass);
        Object sulongType = null;
        for (PythonAbstractClass superClass : mro) {
            if (superClass instanceof PythonManagedClass) {
                sulongType = ((PythonManagedClass) superClass).getSulongType();
                if (sulongType != null) {
                    pythonClass.setSulongType(sulongType);
                    break;
                }
            }
        }
        return sulongType;
    }
}
