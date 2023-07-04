/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_OBJECT_GENERIC_GET_DICT;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;

@GenerateUncached
public abstract class GetDictIfExistsNode extends PNodeWithContext {
    public abstract PDict execute(Object object);

    public abstract PDict execute(PythonObject object);

    @Specialization(guards = {"object.getShape() == cachedShape", "hasNoDict(cachedShape)"}, limit = "1")
    static PDict getNoDictCachedShape(@SuppressWarnings("unused") PythonObject object,
                    @SuppressWarnings("unused") @Cached("object.getShape()") Shape cachedShape) {
        assert doPythonObject(object, DynamicObjectLibrary.getUncached()) == null;
        return null;
    }

    @Specialization(guards = "hasNoDict(object.getShape())", replaces = "getNoDictCachedShape")
    static PDict getNoDict(@SuppressWarnings("unused") PythonObject object) {
        assert doPythonObject(object, DynamicObjectLibrary.getUncached()) == null;
        return null;
    }

    @Idempotent
    protected static boolean hasNoDict(Shape shape) {
        return (shape.getFlags() & PythonObject.HAS_MATERIALIZED_DICT) == 0;
    }

    @Specialization(guards = {"isSingleContext()", "object == cached", "dictIsConstant(cached)", "dict != null"}, limit = "1")
    static PDict getConstant(@SuppressWarnings("unused") PythonObject object,
                    @SuppressWarnings("unused") @Cached(value = "object", weak = true) PythonObject cached,
                    @Cached(value = "getDictUncached(object)", weak = true) PDict dict) {
        return dict;
    }

    protected boolean dictIsConstant(PythonObject object) {
        return object instanceof PythonModule || object instanceof PythonManagedClass;
    }

    protected PDict getDictUncached(PythonObject object) {
        return (PDict) DynamicObjectLibrary.getUncached().getOrDefault(object, PythonObject.DICT, null);
    }

    @Specialization(replaces = "getConstant")
    static PDict doPythonObject(PythonObject object,
                    @CachedLibrary(limit = "4") DynamicObjectLibrary dylib) {
        return (PDict) dylib.getOrDefault(object, PythonObject.DICT, null);
    }

    @Specialization
    PDict doNativeObject(PythonAbstractNativeObject object,
                    @Cached PythonToNativeNode toSulong,
                    @Cached NativeToPythonNode toJava,
                    @Cached CExtNodes.PCallCapiFunction callGetDictNode) {
        Object javaDict = toJava.execute(callGetDictNode.call(FUN_PY_OBJECT_GENERIC_GET_DICT, toSulong.execute(object)));
        if (javaDict instanceof PDict) {
            return (PDict) javaDict;
        } else if (javaDict == PNone.NO_VALUE) {
            return null;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseUncached(this, SystemError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, javaDict);
        }
    }

    @Fallback
    static PDict doOther(@SuppressWarnings("unused") Object object) {
        return null;
    }

    public static GetDictIfExistsNode getUncached() {
        return GetDictIfExistsNodeGen.getUncached();
    }
}
