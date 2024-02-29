/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.logging.Level;

import com.oracle.graal.python.builtins.modules.cext.PythonCextMethodBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.FreeNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Helper class to allocate, initialize, and free a {@code PyMethodDef} struct.
 * <p>
 * In CPython, {@code PyMethodDef} is a definition for how to create built-in function or method
 * objects. Previously, this class used to be a wrapper for our classes representing
 * {@code builtin_function_or_method} (i.e. {@link PBuiltinFunction}, {@link PBuiltinMethod}, and
 * {@link com.oracle.graal.python.builtins.objects.method.PMethod}) but there is no point in keeping
 * a bi-direction connection between the method/function objects and the {@code PyMethodDef}. It is
 * not possible to resolve a {@code PyMethodDef} to an object. Furthermore, {@code PyMethodDef}
 * structures are mainly static and if not, they are assumed to be immortal in CPython. Therefore, a
 * {@code PyMethodDef} we *MUST NOT* keep a reference to any runtime objects because they would
 * leak.
 * </p>
 * 
 * @param name The name of the function or method object.
 * @param meth A reference to the executable function. In CPython, this is a function pointer of
 *            type {@code PyCFunction}. In our case, it can either be a native function pointer or a
 *            {@link CallTarget}
 * @param flags The method flags effectively determining the function's signature.
 * @param doc The doc string for the function or method object.
 */
public record PyMethodDefHelper(TruffleString name, Object meth, int flags, TruffleString doc) {

    private static final TruffleLogger LOGGER = CApiContext.getLogger(PyMethodDefHelper.class);

    private static Object getMethFromBuiltinFunction(CApiContext cApiContext, PBuiltinFunction object) {
        PKeyword[] kwDefaults = object.getKwDefaults();
        for (int i = 0; i < kwDefaults.length; i++) {
            if (ExternalFunctionNodes.KW_CALLABLE.equals(kwDefaults[i].getName())) {
                LOGGER.warning("re-creating PyMethodDef for native function " + object);
                return kwDefaults[i].getValue();
            }
        }
        return PyCFunctionWrapper.createFromBuiltinFunction(cApiContext, object);
    }

    @TruffleBoundary
    public static Object create(CApiContext cApiContext, PBuiltinFunction builtinFunction) {
        Object docObj = builtinFunction.getAttribute(SpecialAttributeNames.T___DOC__);
        TruffleString doc;
        if (docObj instanceof PNone) {
            doc = null;
        } else {
            try {
                doc = CastToTruffleStringNode.executeUncached(docObj);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
        PyMethodDefHelper pyMethodDef = new PyMethodDefHelper(builtinFunction.getName(), getMethFromBuiltinFunction(cApiContext, builtinFunction), builtinFunction.getFlags(), doc);
        Object result = cApiContext.getOrAllocateNativePyMethodDef(pyMethodDef);
        // store the PyMethodDef pointer to the built-in function object for fast access
        DynamicObjectLibrary dylib = DynamicObjectLibrary.getFactory().getUncached(builtinFunction);
        dylib.put(builtinFunction, PythonCextMethodBuiltins.METHOD_DEF_PTR, result);
        return result;
    }

    public static Object create(CApiContext cApiContext, PBuiltinMethod builtinMethod) {
        return create(cApiContext, builtinMethod.getBuiltinFunction());
    }

    /**
     * Allocates a native {@code PyMethodDef} struct and initializes it.
     *
     * <pre>
     * struct PyMethodDef {
     *     const char  *ml_name;
     *     PyCFunction ml_meth;
     *     int ml_flags;
     *     const char  *ml_doc;
     * };
     * </pre>
     *
     * @return The pointer object of the allocated struct.
     */
    @TruffleBoundary
    Object allocate() {
        CStructAccess.AllocateNode allocNode = CStructAccessFactory.AllocateNodeGen.getUncached();
        CStructAccess.WritePointerNode writePointerNode = CStructAccessFactory.WritePointerNodeGen.getUncached();
        CStructAccess.WriteIntNode writeIntNode = CStructAccessFactory.WriteIntNodeGen.getUncached();

        assert name != null;
        CStringWrapper nameWrapper;
        try {
            nameWrapper = new CStringWrapper(name);
        } catch (CannotCastException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }

        Object docWrapper;
        if (doc == null) {
            docWrapper = PythonContext.get(null).getNativeNull();
        } else {
            try {
                docWrapper = new CStringWrapper(doc);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
        Object mem = allocNode.alloc(CStructs.PyMethodDef);
        writePointerNode.write(mem, PyMethodDef__ml_name, nameWrapper);
        writePointerNode.write(mem, PyMethodDef__ml_meth, meth);
        writeIntNode.write(mem, PyMethodDef__ml_flags, flags);
        writePointerNode.write(mem, PyMethodDef__ml_doc, docWrapper);
        LOGGER.fine(() -> String.format("Allocated PyMethodDef(%s, %s, %d, %s) at %s", name, meth, flags, doc, PythonUtils.formatPointer(mem)));
        return mem;
    }

    static void free(Object pointer) {
        CompilerAsserts.neverPartOfCompilation();
        CStructAccess.ReadPointerNode readPointerNode = CStructAccess.ReadPointerNode.getUncached();

        LOGGER.fine(() -> "Freeing PyMethodDef at " + PythonUtils.formatPointer(pointer));

        Object namePointer = readPointerNode.read(pointer, PyMethodDef__ml_name);
        Object docPointer = readPointerNode.read(pointer, PyMethodDef__ml_doc);

        // we only read the other fields and decode strings for logging
        if (LOGGER.isLoggable(Level.FINER)) {
            CStructAccess.ReadI32Node readIntNode = CStructAccessFactory.ReadI32NodeGen.getUncached();
            assert !PGuards.isNullOrZero(namePointer, InteropLibrary.getUncached());
            TruffleString name = FromCharPointerNodeGen.getUncached().execute(namePointer, false);
            TruffleString doc = null;
            if (PGuards.isNullOrZero(namePointer, InteropLibrary.getUncached())) {
                doc = FromCharPointerNodeGen.getUncached().execute(docPointer, false);
            }
            int flags = readIntNode.read(pointer, PyMethodDef__ml_flags);
            Object methPointer = readPointerNode.read(pointer, PyMethodDef__ml_meth);
            Object meth;
            InteropLibrary methPointerLib = InteropLibrary.getUncached(methPointer);
            if (methPointerLib.isPointer(methPointer)) {
                try {
                    meth = PythonContext.get(null).getCApiContext().getClosureDelegate(methPointerLib.asPointer(methPointer));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                // managed case: it is still the wrapper
                assert methPointer instanceof PythonNativeWrapper;
                meth = methPointer;
            }
            LOGGER.finer(String.format("PyMethodDef(%s, %s, %d, %s) at %s freed.", name, meth, flags, doc, PythonUtils.formatPointer(pointer)));
        }
        // managed case: 'const char *' is represented by CStringWrapper
        if (namePointer instanceof CStringWrapper nameWrapper) {
            nameWrapper.free();
        } else {
            FreeNode.executeUncached(namePointer);
        }
        if (docPointer instanceof CStringWrapper docWrapper) {
            docWrapper.free();
        } else {
            if (PGuards.isNullOrZero(docPointer, InteropLibrary.getUncached())) {
                FreeNode.executeUncached(docPointer);
            }
        }
        FreeNode.executeUncached(pointer);
    }
}
