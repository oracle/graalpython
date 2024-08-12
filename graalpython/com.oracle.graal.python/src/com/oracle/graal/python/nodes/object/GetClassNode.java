/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

@TypeSystemReference(PythonTypes.class)
@ImportStatic({PGuards.class})
@GenerateUncached
@GenerateInline(inlineByDefault = true)
public abstract class GetClassNode extends PNodeWithContext {

    public static GetClassNode getUncached() {
        return GetClassNodeGen.getUncached();
    }

    @NeverDefault
    public static GetClassNode create() {
        return GetClassNodeGen.create();
    }

    public abstract Object execute(Node inliningTarget, Object object);

    public final Object executeCached(Object object) {
        return execute(this, object);
    }

    public static Object executeUncached(Object object) {
        return GetClassNodeGen.getUncached().execute(null, object);
    }

    @SuppressWarnings("static-method")
    public final Object execute(@SuppressWarnings("unused") int i) {
        return PythonBuiltinClassType.PInt;
    }

    @SuppressWarnings("static-method")
    public final Object execute(@SuppressWarnings("unused") double d) {
        return PythonBuiltinClassType.PFloat;
    }

    @Specialization
    static Object getBoolean(@SuppressWarnings("unused") Boolean object) {
        return PythonBuiltinClassType.Boolean;
    }

    @Specialization
    static Object getInt(@SuppressWarnings("unused") Integer object) {
        return PythonBuiltinClassType.PInt;
    }

    @Specialization
    static Object getLong(@SuppressWarnings("unused") Long object) {
        return PythonBuiltinClassType.PInt;
    }

    @Specialization
    static Object getDouble(@SuppressWarnings("unused") Double object) {
        return PythonBuiltinClassType.PFloat;
    }

    @Specialization
    static Object getString(@SuppressWarnings("unused") TruffleString object) {
        return PythonBuiltinClassType.PString;
    }

    @Specialization
    static Object getNone(@SuppressWarnings("unused") PNone object) {
        return PythonBuiltinClassType.PNone;
    }

    @Specialization
    static Object getBuiltinClass(PythonBuiltinClass object) {
        return object.getInitialPythonClass();
    }

    @Specialization
    static Object getFunction(@SuppressWarnings("unused") PFunction object) {
        return object.getInitialPythonClass();
    }

    @Specialization
    static Object getBuiltinFunction(@SuppressWarnings("unused") PBuiltinFunction object) {
        return object.getInitialPythonClass();
    }

    // GetPythonObjectClassNode needs 3 references, so we do not inline it here, to save footprint
    // if only the fast-path specializations are activated

    @Specialization(guards = "isPythonObject(object) || isNativeObject(object)")
    static Object getPythonObjectOrNative(PythonAbstractObject object,
                    @Cached(inline = false) GetPythonObjectClassNode getClassNode) {
        return getClassNode.executeCached(object);
    }

    /*
     * Many nodes already specialize on Python[Abstract]Object subclasses, like PTuple. Add
     * shortcuts that avoid interpreter overhead of testing the object for all the primitive types
     * when not necessary.
     */
    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetPythonObjectClassNode extends PNodeWithContext {
        public static Object executeUncached(PythonObject object) {
            return GetClassNodeGen.getUncached().execute(null, object);
        }

        // Intended only for internal usage in this node. The caller must make sure that the object
        // is of one of the expected Java types.
        final Object executeCached(PythonAbstractObject object) {
            assert object instanceof PythonObject || object instanceof PythonAbstractNativeObject;
            return executeImpl(this, object);
        }

        abstract Object executeImpl(Node inliningTarget, PythonAbstractObject object);

        public abstract Object execute(Node inliningTarget, PythonObject object);

        public abstract Object execute(Node inliningTarget, PythonAbstractNativeObject object);

        @Specialization(guards = {"isSingleContext()", "klass != null", "object.getShape() == cachedShape", "hasInitialClass(cachedShape)"}, limit = "1")
        static Object getPythonObjectConstantClass(@SuppressWarnings("unused") PythonObject object,
                        @SuppressWarnings("unused") @Cached(value = "object.getShape()") Shape cachedShape,
                        @Cached(value = "object.getInitialPythonClass()", weak = true) Object klass) {
            return klass;
        }

        @Specialization(guards = "hasInitialClass(object.getShape())")
        static Object getPythonObject(@SuppressWarnings("unused") PythonObject object,
                        @Bind("object.getInitialPythonClass()") Object klass) {
            assert klass != null;
            return klass;
        }

        @InliningCutoff
        @Specialization(guards = "!hasInitialClass(object.getShape())", replaces = "getPythonObjectConstantClass")
        static Object getPythonObject(Node inliningTarget, PythonObject object,
                        @Cached HiddenAttr.ReadNode readHiddenAttrNode) {
            return readHiddenAttrNode.execute(inliningTarget, object, HiddenAttr.CLASS, object.getInitialPythonClass());
        }

        @InliningCutoff
        @Specialization
        static Object getNativeObject(Node inliningTarget, PythonAbstractNativeObject object,
                        @Cached CExtNodes.GetNativeClassNode getNativeClassNode) {
            return getNativeClassNode.execute(inliningTarget, object);
        }

        @Idempotent
        protected static boolean hasInitialClass(Shape shape) {
            return (shape.getFlags() & PythonObject.CLASS_CHANGED_FLAG) == 0;
        }
    }

    @Specialization
    static Object getPBCT(@SuppressWarnings("unused") PythonBuiltinClassType object) {
        return PythonBuiltinClassType.PythonClass;
    }

    @Specialization
    static Object getNotImplemented(@SuppressWarnings("unused") PNotImplemented object) {
        return PythonBuiltinClassType.PNotImplemented;
    }

    @Specialization
    static Object getEllipsis(@SuppressWarnings("unused") PEllipsis object) {
        return PythonBuiltinClassType.PEllipsis;
    }

    @Specialization
    static Object getCell(@SuppressWarnings("unused") PCell object) {
        return PythonBuiltinClassType.PCell;
    }

    @Specialization
    static Object getNativeVoidPtr(@SuppressWarnings("unused") PythonNativeVoidPtr object) {
        return PythonBuiltinClassType.PInt;
    }

    @Specialization
    static Object getTruffleException(@SuppressWarnings("unused") AbstractTruffleException object) {
        /*
         * Special case: if Python code asks for the class of a foreign exception, we return a
         * Python type that inherits from BaseException. We do this because Python users usually
         * expect that every exception inherits from BaseException.
         */
        assert !(object instanceof PException);
        return PythonBuiltinClassType.PForeignException;
    }

    @Fallback
    static Object getForeign(Object object,
                    @Cached(inline = false) GetRegisteredClassNode getRegisteredClassNode) {
        return getRegisteredClassNode.execute(object);
    }
}
