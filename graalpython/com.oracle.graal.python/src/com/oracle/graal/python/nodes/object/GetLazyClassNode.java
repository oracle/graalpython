/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeClassNode;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.profiles.ValueProfile;

@TypeSystemReference(PythonTypes.class)
@ImportStatic({PGuards.class})
public abstract class GetLazyClassNode extends PNodeWithContext {
    private final ValueProfile classProfile = ValueProfile.createClassProfile();

    /*
     * =============== Changes in this class must be mirrored in GetClassNode ===============
     */

    public static GetLazyClassNode create() {
        return GetLazyClassNodeGen.create();
    }

    public abstract LazyPythonClass execute(boolean object);

    public abstract LazyPythonClass execute(int object);

    public abstract LazyPythonClass execute(long object);

    public abstract LazyPythonClass execute(double object);

    public final LazyPythonClass execute(Object object) {
        return executeGetClass(classProfile.profile(object));
    }

    protected abstract LazyPythonClass executeGetClass(Object object);

    @Specialization
    protected LazyPythonClass getIt(@SuppressWarnings("unused") GetSetDescriptor object) {
        return PythonBuiltinClassType.GetSetDescriptor;
    }

    @Specialization
    protected LazyPythonClass getIt(@SuppressWarnings("unused") PNone object) {
        return PythonBuiltinClassType.PNone;
    }

    @Specialization
    protected LazyPythonClass getIt(@SuppressWarnings("unused") PNotImplemented object) {
        return PythonBuiltinClassType.PNotImplemented;
    }

    @Specialization
    protected LazyPythonClass getIt(@SuppressWarnings("unused") PEllipsis object) {
        return PythonBuiltinClassType.PEllipsis;
    }

    @Specialization
    protected LazyPythonClass getIt(@SuppressWarnings("unused") boolean object) {
        return PythonBuiltinClassType.Boolean;
    }

    @Specialization
    protected LazyPythonClass getIt(@SuppressWarnings("unused") int object) {
        return PythonBuiltinClassType.PInt;
    }

    @Specialization
    protected LazyPythonClass getIt(@SuppressWarnings("unused") long object) {
        return PythonBuiltinClassType.PInt;
    }

    @Specialization
    protected LazyPythonClass getIt(@SuppressWarnings("unused") double object) {
        return PythonBuiltinClassType.PFloat;
    }

    @Specialization
    protected LazyPythonClass getIt(@SuppressWarnings("unused") String object) {
        return PythonBuiltinClassType.PString;
    }

    @Specialization
    protected PythonClass getIt(PythonNativeObject object,
                    @Cached("create()") GetNativeClassNode getNativeClassNode) {
        return getNativeClassNode.execute(object);
    }

    @Specialization
    protected LazyPythonClass getIt(@SuppressWarnings("unused") PythonNativeVoidPtr object) {
        return PythonBuiltinClassType.PInt;
    }

    @Specialization
    protected LazyPythonClass getPythonClassGeneric(PythonObject object) {
        return object.getLazyPythonClass();
    }

    @Specialization(guards = "isForeignObject(object)")
    protected LazyPythonClass getIt(@SuppressWarnings("unused") TruffleObject object) {
        return PythonBuiltinClassType.TruffleObject;
    }

    @TruffleBoundary
    public static LazyPythonClass getItSlowPath(Object o) {
        if (PGuards.isForeignObject(o)) {
            return PythonBuiltinClassType.TruffleObject;
        } else if (o instanceof String) {
            return PythonBuiltinClassType.PString;
        } else if (o instanceof Boolean) {
            return PythonBuiltinClassType.Boolean;
        } else if (o instanceof Double || o instanceof Float) {
            return PythonBuiltinClassType.PFloat;
        } else if (o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Byte) {
            return PythonBuiltinClassType.PInt;
        } else if (o instanceof PythonObject) {
            return ((PythonObject) o).getLazyPythonClass();
        } else if (o instanceof PEllipsis) {
            return PythonBuiltinClassType.PEllipsis;
        } else if (o instanceof PNotImplemented) {
            return PythonBuiltinClassType.PNotImplemented;
        } else if (o instanceof PNone) {
            return PythonBuiltinClassType.PNone;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("unknown type " + o.getClass().getName());
        }
    }

    @TruffleBoundary
    public static String getNameSlowPath(Object o) {
        if (PGuards.isForeignObject(o)) {
            return BuiltinNames.FOREIGN;
        }
        return getItSlowPath(o).getName();
    }
}
