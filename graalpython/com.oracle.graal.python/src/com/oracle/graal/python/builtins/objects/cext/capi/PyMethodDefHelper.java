/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CoerceNativePointerToLongNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.nfi2.NativeMemory;
import com.oracle.graal.python.nodes.HiddenAttr;
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
                // This can happen for slot wrapper methods of native slots
                return kwDefaults[i].getValue();
            }
        }
        return PyCFunctionWrapper.createFromBuiltinFunction(cApiContext, object);
    }

    @TruffleBoundary
    public static long create(CApiContext cApiContext, PBuiltinFunction builtinFunction) {
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
        long result = cApiContext.getOrAllocateNativePyMethodDef(pyMethodDef);
        // store the PyMethodDef pointer to the built-in function object for fast access
        HiddenAttr.WriteLongNode.executeUncached(builtinFunction, HiddenAttr.METHOD_DEF_PTR, result);
        return result;
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
    long allocate() {
        assert name != null;
        PythonContext pythonContext = PythonContext.get(null);
        long nativeName = pythonContext.stringToNativeUtf8Bytes(name, false);
        long nativeDoc = doc != null ? pythonContext.stringToNativeUtf8Bytes(doc, false) : 0L;
        long nativeMeth = CoerceNativePointerToLongNode.executeUncached(meth);

        long mem = CStructAccess.allocate(CStructs.PyMethodDef);
        CStructAccess.writePtrField(mem, PyMethodDef__ml_name, nativeName);
        CStructAccess.writePtrField(mem, PyMethodDef__ml_meth, nativeMeth);
        CStructAccess.writeIntField(mem, PyMethodDef__ml_flags, flags);
        CStructAccess.writePtrField(mem, PyMethodDef__ml_doc, nativeDoc);
        LOGGER.fine(() -> String.format("Allocated PyMethodDef(%s, %s, %d, %s) at %s", name, meth, flags, doc, PythonUtils.formatPointer(mem)));
        return mem;
    }

    static void free(long pointer) {
        CompilerAsserts.neverPartOfCompilation();
        LOGGER.fine(() -> "Freeing PyMethodDef at " + PythonUtils.formatPointer(pointer));

        long namePointer = CStructAccess.readPtrField(pointer, PyMethodDef__ml_name);
        long docPointer = CStructAccess.readPtrField(pointer, PyMethodDef__ml_doc);

        // we only read the other fields and decode strings for logging
        if (LOGGER.isLoggable(Level.FINER)) {
            assert namePointer != 0L;
            TruffleString name = FromCharPointerNode.executeUncached(namePointer, false);
            TruffleString doc = null;
            if (docPointer != 0L) {
                doc = FromCharPointerNodeGen.getUncached().execute(docPointer, false);
            }
            int flags = CStructAccess.readIntField(pointer, PyMethodDef__ml_flags);
            long methPointer = CStructAccess.readPtrField(pointer, PyMethodDef__ml_meth);
            Object meth = PythonContext.get(null).getCApiContext().getClosureDelegate(methPointer);
            LOGGER.finer(String.format("PyMethodDef(%s, %s, %d, %s) at %s freed.", name, meth, flags, doc, PythonUtils.formatPointer(pointer)));
        }
        NativeMemory.free(namePointer);
        NativeMemory.free(docPointer);
        NativeMemory.free(pointer);
    }
}
