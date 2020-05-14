/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.object;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@TypeSystemReference(PythonTypes.class)
@ImportStatic({PGuards.class})
@GenerateUncached
public abstract class GetClassNode extends PNodeWithContext {
    public static GetClassNode create() {
        return GetClassNodeGen.create();
    }

    public static GetClassNode getUncached() {
        return GetClassNodeGen.getUncached();
    }

    public abstract PythonAbstractClass execute(boolean object);

    public abstract PythonAbstractClass execute(int object);

    public abstract PythonAbstractClass execute(long object);

    public abstract PythonAbstractClass execute(double object);

    public abstract PythonAbstractClass execute(Object object);

    protected PythonBuiltinClass lookupType(PythonContext context, LazyPythonClass klass) {
        return context.getCore().lookupType((PythonBuiltinClassType) klass);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonBuiltinClass getBooleanCached(@SuppressWarnings("unused") boolean object,
                    @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                    @Cached("getBoolean(object, contextRef)") PythonBuiltinClass klass) {
        return klass;
    }

    @Specialization
    protected PythonBuiltinClass getBoolean(@SuppressWarnings("unused") boolean object,
                    @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
        return contextRef.get().getCore().lookupType(PythonBuiltinClassType.Boolean);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonBuiltinClass getIntCached(@SuppressWarnings("unused") int object,
                    @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                    @Cached("getInt(object, contextRef)") PythonBuiltinClass klass) {
        return klass;
    }

    @Specialization
    protected PythonBuiltinClass getInt(@SuppressWarnings("unused") int object,
                    @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
        return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PInt);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonBuiltinClass getLongCached(@SuppressWarnings("unused") long object,
                    @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                    @Cached("getLong(object, contextRef)") PythonBuiltinClass klass) {
        return klass;
    }

    @Specialization
    protected PythonBuiltinClass getLong(@SuppressWarnings("unused") long object,
                    @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
        return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PInt);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonBuiltinClass getDoubleCached(@SuppressWarnings("unused") double object,
                    @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                    @Cached("getDouble(object, contextRef)") PythonBuiltinClass klass) {
        return klass;
    }

    @Specialization
    protected PythonBuiltinClass getDouble(@SuppressWarnings("unused") double object,
                    @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
        return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PFloat);
    }

    @Specialization(guards = {
                    "cachedLazyClass == getLazyClass.execute(object)",
                    "isPythonBuiltinClassType(cachedLazyClass)"
    }, replaces = {"getBooleanCached", "getIntCached", "getLongCached", "getDoubleCached"}, assumptions = "singleContextAssumption()", limit = "3")
    protected PythonAbstractClass getPythonClassCached(@SuppressWarnings("unused") Object object,
                    @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                    @SuppressWarnings("unused") @Cached GetLazyClassNode getLazyClass,
                    @SuppressWarnings("unused") @Cached("getLazyClass.execute(object)") LazyPythonClass cachedLazyClass,
                    @Cached("lookupType(context, cachedLazyClass)") PythonBuiltinClass klass) {
        return klass;
    }

    @Specialization(replaces = {
                    "getBooleanCached", "getIntCached", "getLongCached", "getDoubleCached",
                    "getBoolean", "getInt", "getLong", "getDouble",
                    "getPythonClassCached",
    })
    protected PythonAbstractClass getPythonClassGeneric(Object object,
                    @Cached GetLazyClassNode getLazyClass,
                    @Cached("createBinaryProfile()") ConditionProfile getClassProfile,
                    @Cached("createClassProfile()") ValueProfile classProfile,
                    @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
        LazyPythonClass lazyClass = getLazyClass.execute(object);
        if (getClassProfile.profile(lazyClass instanceof PythonBuiltinClassType)) {
            return classProfile.profile(contextRef.get().getCore().lookupType((PythonBuiltinClassType) lazyClass));
        } else {
            return PythonAbstractClass.cast(classProfile.profile(lazyClass));
        }
    }
}
