/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMethodDef__ml_doc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMethodDef__ml_flags;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMethodDef__ml_meth;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMethodDef__ml_name;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObjectFactory.PInteropGetAttributeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Wrapper object for {@code PyMethodDef}.
 */
public final class PyMethodDefWrapper extends PythonReplacingNativeWrapper {

    public PyMethodDefWrapper(PythonObject delegate) {
        super(delegate);
    }

    private static Object getMethFromBuiltinMethod(PBuiltinMethod object, PythonToNativeNode toSulongNode) {
        return getMethFromBuiltinFunction(object.getBuiltinFunction(), toSulongNode);
    }

    private static Object getMethFromBuiltinFunction(PBuiltinFunction object, PythonToNativeNode toSulongNode) {
        PKeyword[] kwDefaults = object.getKwDefaults();
        for (int i = 0; i < kwDefaults.length; i++) {
            if (ExternalFunctionNodes.KW_CALLABLE.equals(kwDefaults[i].getName())) {
                return kwDefaults[i].getValue();
            }
        }
        return createFunctionWrapper(object, toSulongNode);
    }

    private static Object getMeth(PythonObject object, PythonToNativeNode toSulongNode) {
        if (object instanceof PBuiltinMethod) {
            return getMethFromBuiltinMethod((PBuiltinMethod) object, toSulongNode);
        } else if (object instanceof PBuiltinFunction) {
            return getMethFromBuiltinFunction((PBuiltinFunction) object, toSulongNode);
        }
        return createFunctionWrapper(object, toSulongNode);
    }

    @TruffleBoundary
    private static Object createFunctionWrapper(PythonObject object, PythonToNativeNode toSulongNode) {
        int flags = getFlags(object);
        PythonNativeWrapper wrapper;
        if (CExtContext.isMethNoArgs(flags)) {
            wrapper = PyProcsWrapper.createUnaryFuncWrapper(object);
        } else if (CExtContext.isMethO(flags)) {
            wrapper = PyProcsWrapper.createBinaryFuncWrapper(object);
        } else if (CExtContext.isMethVarargsWithKeywords(flags)) {
            wrapper = PyProcsWrapper.createVarargKeywordWrapper(object);
        } else if (CExtContext.isMethVarargs(flags)) {
            wrapper = PyProcsWrapper.createVarargWrapper(object);
        } else {
            throw CompilerDirectives.shouldNotReachHere("other signature " + Integer.toHexString(flags));
        }
        return toSulongNode.execute(wrapper);
    }

    private static int getFlags(PythonObject object) {
        if (object instanceof PBuiltinFunction) {
            return ((PBuiltinFunction) object).getFlags();
        } else if (object instanceof PBuiltinMethod) {
            return ((PBuiltinMethod) object).getBuiltinFunction().getFlags();
        }
        return 0;
    }

    @Override
    protected Object allocateReplacememtObject() {
        PythonObject obj = (PythonObject) getDelegate();

        CStructAccess.AllocateNode allocNode = CStructAccessFactory.AllocateNodeGen.getUncached();
        CStructAccess.WritePointerNode writePointerNode = CStructAccessFactory.WritePointerNodeGen.getUncached();
        CStructAccess.WriteIntNode writeIntNode = CStructAccessFactory.WriteIntNodeGen.getUncached();
        PythonAbstractObject.PInteropGetAttributeNode getAttrNode = PInteropGetAttributeNodeGen.getUncached();
        PythonToNativeNode toSulongNode = PythonToNativeNodeGen.getUncached();
        CastToTruffleStringNode castToStringNode = CastToTruffleStringNode.getUncached();
        Object mem = allocNode.alloc(CStructs.PyMethodDef);

        Object nullValue = PythonContext.get(null).getNativeNull().getPtr();

        Object name = getAttrNode.execute(obj, SpecialAttributeNames.T___NAME__);
        if (PGuards.isPNone(name)) {
            name = nullValue;
        } else {
            try {
                name = new CStringWrapper(castToStringNode.execute(name));
            } catch (CannotCastException e) {
                // fall through
            }
        }
        writePointerNode.write(mem, PyMethodDef__ml_name, name);

        writePointerNode.write(mem, PyMethodDef__ml_meth, getMeth(obj, toSulongNode));
        writeIntNode.write(mem, PyMethodDef__ml_flags, getFlags(obj));

        Object doc = getAttrNode.execute(obj, T___DOC__);
        if (PGuards.isPNone(doc)) {
            doc = nullValue;
        } else {
            try {
                doc = new CStringWrapper(castToStringNode.execute(doc));
            } catch (CannotCastException e) {
                doc = nullValue;
            }
        }
        writePointerNode.write(mem, PyMethodDef__ml_doc, doc);

        return mem;
    }
}
