/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_CAPSULE_DESTRUCTOR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectRawPointer;
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nfi2.NativeMemory.readByteArrayElement;
import static com.oracle.graal.python.nodes.ErrorMessages.CALLED_WITH_INCORRECT_NAME;
import static com.oracle.graal.python.nodes.ErrorMessages.CALLED_WITH_INVALID_PY_CAPSULE_OBJECT;
import static com.oracle.graal.python.nodes.ErrorMessages.CALLED_WITH_NULL_POINTER;
import static com.oracle.graal.python.nodes.ErrorMessages.PY_CAPSULE_IMPORT_S_IS_NOT_VALID;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsuleNewNodeGen;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextCapsuleBuiltins {

    @CApiBuiltin(ret = PyObjectRawPointer, args = {Pointer, ConstCharPtr, PY_CAPSULE_DESTRUCTOR}, call = Direct)
    static long PyCapsule_New(long pointer, long namePtr, long destructor) {
        PyCapsule capsule = PyCapsuleNewNode.executeUncached(pointer, namePtr, destructor);
        return PythonToNativeNewRefNode.executeLongUncached(capsule);
    }

    @GenerateCached(false)
    @GenerateInline
    @GenerateUncached
    public abstract static class PyCapsuleNewNode extends Node {

        public abstract PyCapsule execute(Node inliningTarget, long pointer, long name, long destructor);

        @TruffleBoundary
        public static PyCapsule executeUncached(long pointer, long name, long destructor) {
            return PyCapsuleNewNodeGen.getUncached().execute(null, pointer, name, destructor);
        }

        @Specialization
        static PyCapsule doGeneric(Node inliningTarget, long pointer, long namePtr, long destructor,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            if (pointer == NULLPTR) {
                throw raiseNode.raise(inliningTarget, ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT);
            }
            PyCapsule capsule = PFactory.createCapsuleNativeName(language, pointer, namePtr);
            if (destructor != NULLPTR) {
                capsule.registerDestructor(destructor);
            }
            return capsule;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, ConstCharPtr}, call = Direct)
    static int PyCapsule_IsValid(long oPtr, long namePtr) {
        Object obj = NativeToPythonNode.executeRawUncached(oPtr);
        if (!(obj instanceof PyCapsule capsule)) {
            return 0;
        }
        if (capsule.getPointer() == NULLPTR) {
            return 0;
        }
        if (!capsuleNameMatches(namePtr, capsule.getNamePtr())) {
            return 0;
        }
        return 1;
    }

    @CApiBuiltin(ret = Pointer, args = {PyObjectRawPointer, ConstCharPtr}, call = Direct)
    static long PyCapsule_GetPointer(long oPtr, long namePtr) {
        Object capsule = NativeToPythonNode.executeRawUncached(oPtr);
        return PyCapsuleGetPointerNode.executeUncached(capsule, namePtr);
    }

    @GenerateCached(false)
    @GenerateInline
    @GenerateUncached
    public abstract static class PyCapsuleGetPointerNode extends Node {

        public abstract long execute(Node inliningTarget, Object capsule, long name);

        public static PyCapsuleGetPointerNode getUncached() {
            return PythonCextCapsuleBuiltinsFactory.PyCapsuleGetPointerNodeGen.getUncached();
        }

        @TruffleBoundary
        public static long executeUncached(Object capsuleObj, long name) {
            return getUncached().execute(null, capsuleObj, name);
        }

        @Specialization
        static long doCapsule(Node inliningTarget, PyCapsule o, long name,
                        @Cached PRaiseNode raiseNode) {
            if (o.getPointer() == NULLPTR) {
                throw raiseNode.raise(inliningTarget, ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetPointer");
            }
            if (!capsuleNameMatches(name, o.getNamePtr())) {
                throw raiseNode.raise(inliningTarget, ValueError, CALLED_WITH_INCORRECT_NAME, "PyCapsule_GetPointer");
            }
            return o.getPointer();
        }

        @Fallback
        static long doError(Node inliningTarget, @SuppressWarnings("unused") Object o, @SuppressWarnings("unused") long name) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetPointer");
        }
    }

    @CApiBuiltin(ret = ConstCharPtr, args = {PyObjectRawPointer}, call = Direct)
    static long PyCapsule_GetName(long oPtr) {
        PyCapsule capsule = expectCapsule(oPtr, "PyCapsule_GetName");
        return capsule.getNamePtr();
    }

    @CApiBuiltin(ret = PY_CAPSULE_DESTRUCTOR, args = {PyObjectRawPointer}, call = Direct)
    static long PyCapsule_GetDestructor(long oPtr) {
        PyCapsule capsule = expectCapsule(oPtr, "PyCapsule_GetDestructor");
        return capsule.getDestructor();
    }

    @CApiBuiltin(ret = Pointer, args = {PyObjectRawPointer}, call = Direct)
    static long PyCapsule_GetContext(long oPtr) {
        PyCapsule capsule = expectCapsule(oPtr, "PyCapsule_GetContext");
        return capsule.getContext();
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, Pointer}, call = Direct)
    static int PyCapsule_SetPointer(long oPtr, long pointer) {
        if (pointer == NULLPTR) {
            throw PRaiseNode.raiseStatic(null, ValueError, CALLED_WITH_NULL_POINTER, "PyCapsule_SetPointer");
        }
        PyCapsule capsule = expectCapsule(oPtr, "PyCapsule_SetPointer");
        capsule.setPointer(pointer);
        return 0;
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, ConstCharPtr}, call = Direct)
    static int PyCapsule_SetName(long oPtr, long namePtr) {
        PyCapsule capsule = expectCapsule(oPtr, "PyCapsule_SetName");
        capsule.setNamePtr(namePtr);
        return 0;
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, PY_CAPSULE_DESTRUCTOR}, call = Direct)
    static int PyCapsule_SetDestructor(long oPtr, long destructor) {
        PyCapsule capsule = expectCapsule(oPtr, "PyCapsule_SetDestructor");
        capsule.registerDestructor(destructor);
        return 0;
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, Pointer}, call = Direct)
    static int PyCapsule_SetContext(long oPtr, long context) {
        PyCapsule capsule = expectCapsule(oPtr, "PyCapsule_SetContext");
        capsule.setContext(context);
        return 0;
    }

    @CApiBuiltin(ret = Pointer, args = {ConstCharPtr, Int}, call = Direct)
    static long PyCapsule_Import(long namePtr, @SuppressWarnings("unused") int noBlock) {
        TruffleString name = FromCharPointerNode.executeUncached(namePtr, true);
        TruffleString trace = name;
        Object object = null;
        while (trace != null) {
            int traceLen = trace.codePointLengthUncached(TS_ENCODING);
            int dotIdx = trace.indexOfStringUncached(StringLiterals.T_DOT, 0, traceLen, TS_ENCODING);
            TruffleString dot = null;
            if (dotIdx >= 0) {
                dot = trace.substringUncached(dotIdx + 1, traceLen - dotIdx - 1, TS_ENCODING, false);
                trace = trace.substringUncached(0, dotIdx, TS_ENCODING, false);
            }
            if (object == null) {
                // noBlock has no effect anymore since 3.3
                object = AbstractImportNode.importModuleBoundary(trace);
            } else {
                object = ReadAttributeFromObjectNode.getUncached().execute(object, trace);
            }
            trace = dot;
        }

        /* compare attribute name to module.name by hand */
        PyCapsule capsule = object instanceof PyCapsule ? (PyCapsule) object : null;
        if (capsule != null && capsule.getPointer() != NULLPTR && capsuleNameMatches(namePtr, capsule.getNamePtr())) {
            return capsule.getPointer();
        }
        throw PRaiseNode.raiseStatic(null, AttributeError, PY_CAPSULE_IMPORT_S_IS_NOT_VALID, name);
    }

    /**
     * Compares two names according to the semantics of PyCapsule's {@code name_matches} function
     * (see C code snippet below). The names must be native pointers (or {@code NULLPTR}).
     *
     * <pre>
     *     static int
     *     name_matches(const char *name1, const char *name2) {
     *         // if either is NULL
     *         if (!name1 || !name2) {
     *             // they're only the same if they're both NULL.
     *             return name1 == name2;
     *         }
     *         return !strcmp(name1, name2);
     *     }
     * </pre>
     */
    static boolean capsuleNameMatches(long name1, long name2) {
        if (name1 == NULLPTR || name2 == NULLPTR) {
            return name1 == name2;
        }
        if (name1 == name2) {
            return true;
        }
        for (int i = 0;; i++) {
            byte b1 = readByteArrayElement(name1, i);
            byte b2 = readByteArrayElement(name2, i);
            if (b1 != b2) {
                return false;
            }
            if (b1 == 0) {
                return true;
            }
        }
    }

    private static PyCapsule expectCapsule(long oPtr, String builtinName) {
        Object obj = NativeToPythonNode.executeRawUncached(oPtr);
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, !(obj instanceof PyCapsule capsule) || capsule.getPointer() == NULLPTR)) {
            throw PRaiseNode.raiseStatic(null, ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, builtinName);
        }
        return (PyCapsule) obj;
    }
}
