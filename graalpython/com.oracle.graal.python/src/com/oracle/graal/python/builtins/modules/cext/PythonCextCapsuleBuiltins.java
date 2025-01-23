/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.nodes.ErrorMessages.CALLED_WITH_INCORRECT_NAME;
import static com.oracle.graal.python.nodes.ErrorMessages.CALLED_WITH_INVALID_PY_CAPSULE_OBJECT;
import static com.oracle.graal.python.nodes.ErrorMessages.CALLED_WITH_NULL_POINTER;
import static com.oracle.graal.python.nodes.ErrorMessages.PY_CAPSULE_IMPORT_S_IS_NOT_VALID;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsuleNameMatchesNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextCapsuleBuiltins {

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, ConstCharPtr, PY_CAPSULE_DESTRUCTOR}, call = Direct)
    abstract static class PyCapsule_New extends CApiTernaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object pointer, Object namePtr, Object destructor,
                        @Bind("this") Node inliningTarget,
                        @Cached PyCapsuleNewNode pyCapsuleNewNode) {
            return pyCapsuleNewNode.execute(inliningTarget, pointer, namePtr, destructor);
        }
    }

    @GenerateCached(false)
    @GenerateInline
    public abstract static class PyCapsuleNewNode extends Node {

        public abstract PyCapsule execute(Node inliningTarget, Object pointer, Object name, Object destructor);

        @Specialization
        static PyCapsule doGeneric(Node inliningTarget, Object pointer, Object namePtr, Object destructor,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (interopLibrary.isNull(pointer)) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT);
            }
            PyCapsule capsule = factory.createCapsuleNativeName(pointer, interopLibrary.isNull(namePtr) ? null : namePtr);
            if (!interopLibrary.isNull(destructor)) {
                capsule.registerDestructor(destructor);
            }
            return capsule;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, ConstCharPtr}, call = Direct)
    abstract static class PyCapsule_IsValid extends CApiBinaryBuiltinNode {
        @Specialization
        static int doCapsule(PyCapsule o, Object namePtr,
                        @Bind("this") Node inliningTarget,
                        @Cached PyCapsuleNameMatchesNode nameMatchesNode) {
            if (o.getPointer() == null) {
                return 0;
            }
            if (!nameMatchesNode.execute(inliningTarget, namePtr, o.getNamePtr())) {
                return 0;
            }
            return 1;
        }

        @Fallback
        static Object doError(@SuppressWarnings("unused") Object o, @SuppressWarnings("unused") Object name) {
            return 0;
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyObject, ConstCharPtr}, call = Direct)
    abstract static class PyCapsule_GetPointer extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doCapsule(Object o, Object name,
                        @Bind("this") Node inliningTarget,
                        @Cached PyCapsuleGetPointerNode pyCapsuleGetPointerNode) {
            return pyCapsuleGetPointerNode.execute(inliningTarget, o, name);
        }
    }

    @GenerateCached(false)
    @GenerateInline
    @GenerateUncached
    public abstract static class PyCapsuleGetPointerNode extends Node {

        public abstract Object execute(Node inliningTarget, Object capsule, Object name);

        @Specialization
        static Object doCapsule(Node inliningTarget, PyCapsule o, Object name,
                        @Cached PyCapsuleNameMatchesNode nameMatchesNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (o.getPointer() == null) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetPointer");
            }
            if (!nameMatchesNode.execute(inliningTarget, name, o.getNamePtr())) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CALLED_WITH_INCORRECT_NAME, "PyCapsule_GetPointer");
            }
            return o.getPointer();
        }

        @Fallback
        static Object doError(Node inliningTarget, @SuppressWarnings("unused") Object o, @SuppressWarnings("unused") Object name,
                        @Cached PRaiseNode.Lazy raiseNode) {
            throw raiseNode.get(inliningTarget).raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetPointer");
        }
    }

    @CApiBuiltin(ret = ConstCharPtr, args = {PyObject}, call = Direct)
    abstract static class PyCapsule_GetName extends CApiUnaryBuiltinNode {

        @Specialization
        Object get(PyCapsule o,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (o.getPointer() == null) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetName");
            }
            return o.getNamePtr() == null ? getNULL() : o.getNamePtr();
        }

        @Fallback
        static Object doit(@SuppressWarnings("unused") Object o,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetName");
        }
    }

    @CApiBuiltin(ret = PY_CAPSULE_DESTRUCTOR, args = {PyObject}, call = Direct)
    abstract static class PyCapsule_GetDestructor extends CApiUnaryBuiltinNode {
        @Specialization
        Object doCapsule(PyCapsule o,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (o.getPointer() == null) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetDestructor");
            }
            if (o.getDestructor() == null) {
                return getNULL();
            }
            return o.getDestructor();
        }

        @Fallback
        static Object doError(@SuppressWarnings("unused") Object o,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetPointer");
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyObject}, call = Direct)
    abstract static class PyCapsule_GetContext extends CApiUnaryBuiltinNode {
        @Specialization
        Object doCapsule(PyCapsule o,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (o.getPointer() == null) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetContext");
            }
            if (o.getContext() == null) {
                return getNULL();
            }
            return o.getContext();
        }

        @Fallback
        static Object doError(@SuppressWarnings("unused") Object o,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetPointer");
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Pointer}, call = Direct)
    abstract static class PyCapsule_SetPointer extends CApiBinaryBuiltinNode {
        @Specialization
        static int doCapsule(PyCapsule o, Object pointer,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "2") InteropLibrary interopLibrary,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (interopLibrary.isNull(pointer)) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CALLED_WITH_NULL_POINTER, "PyCapsule_SetPointer");
            }

            if (o.getPointer() == null) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetPointer");
            }

            o.setPointer(pointer);
            return 0;
        }

        @Fallback
        static Object doError(@SuppressWarnings("unused") Object o, @SuppressWarnings("unused") Object name,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetPointer");
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, ConstCharPtr}, call = Direct)
    abstract static class PyCapsule_SetName extends CApiBinaryBuiltinNode {
        @Specialization
        static int set(PyCapsule o, Object namePtr,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (o.getPointer() == null) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetName");
            }
            o.setNamePtr(lib.isNull(namePtr) ? null : namePtr);
            return 0;
        }

        @Fallback
        static Object doError(@SuppressWarnings("unused") Object o, @SuppressWarnings("unused") Object name,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetName");
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PY_CAPSULE_DESTRUCTOR}, call = Direct)
    abstract static class PyCapsule_SetDestructor extends CApiBinaryBuiltinNode {
        @Specialization
        static int doCapsule(PyCapsule o, Object destructor,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (o.getPointer() == null) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetDestructor");
            }
            o.registerDestructor(lib.isNull(destructor) ? null : destructor);
            return 0;
        }

        @Fallback
        static Object doError(@SuppressWarnings("unused") Object o, @SuppressWarnings("unused") Object name,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetDestructor");
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Pointer}, call = Direct)
    abstract static class PyCapsule_SetContext extends CApiBinaryBuiltinNode {
        @Specialization
        static int doCapsule(PyCapsule o, Object context,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (o.getPointer() == null) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetContext");
            }
            o.setContext(context);
            return 0;
        }

        @Fallback
        static Object doError(@SuppressWarnings("unused") Object o, @SuppressWarnings("unused") Object name,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetContext");
        }
    }

    @CApiBuiltin(ret = Pointer, args = {ConstCharPtr, Int}, call = Direct)
    abstract static class PyCapsule_Import extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object namePtr, @SuppressWarnings("unused") int noBlock,
                        @Bind("this") Node inliningTarget,
                        @Cached CApiTransitions.CharPtrToPythonNode charPtrToPythonNode,
                        @Cached PyCapsuleNameMatchesNode nameMatchesNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfStringNode indexOfStringNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached ReadAttributeFromObjectNode getAttrNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString name = (TruffleString) charPtrToPythonNode.execute(namePtr);
            TruffleString trace = name;
            Object object = null;
            while (trace != null) {
                int traceLen = codePointLengthNode.execute(trace, TS_ENCODING);
                int dotIdx = indexOfStringNode.execute(trace, StringLiterals.T_DOT, 0, traceLen, TS_ENCODING);
                TruffleString dot = null;
                if (dotIdx >= 0) {
                    dot = substringNode.execute(trace, dotIdx + 1, traceLen - dotIdx - 1, TS_ENCODING, false);
                    trace = substringNode.execute(trace, 0, dotIdx, TS_ENCODING, false);
                }
                if (object == null) {
                    // noBlock has no effect anymore since 3.3
                    object = AbstractImportNode.importModule(trace);
                } else {
                    object = getAttrNode.execute(object, trace);
                }
                trace = dot;
            }

            /* compare attribute name to module.name by hand */
            PyCapsule capsule = object instanceof PyCapsule ? (PyCapsule) object : null;
            if (capsule != null && PyCapsule_IsValid.doCapsule(capsule, namePtr, inliningTarget, nameMatchesNode) == 1) {
                return capsule.getPointer();
            } else {
                throw raiseNode.get(inliningTarget).raise(AttributeError, PY_CAPSULE_IMPORT_S_IS_NOT_VALID, name);
            }
        }
    }
}
