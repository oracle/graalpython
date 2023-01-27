/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ImportError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltins.PyCapsuleIsValidNode.PyCapsuleIsValid;
import static com.oracle.graal.python.nodes.ErrorMessages.CALLED_WITH_INVALID_PY_CAPSULE_OBJECT;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.ErrorMessages.PY_CAPSULE_IMPORT_S_IS_NOT_VALID;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.statement.AbstractImportNode.T_IMPORT_ALL;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;
import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextCapsuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextCapsuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    public abstract static class NameMatchesNode extends Node {
        abstract boolean execute(Object name1, Object name2);

        @Specialization(guards = "ignoredName2 == null")
        static boolean common(PNone ignoredName1, Object ignoredName2) {
            return true;
        }

        @Specialization
        static boolean ts(TruffleString n1, TruffleString n2,
                        @Cached TruffleString.EqualNode equalNode) {
            if (n1 == null && n2 == null) {
                return true;
            }
            if (n1 == null || n2 == null) {
                return false;
            }
            return equalNode.execute(n1, n2, TS_ENCODING);
        }

        @Fallback
        static boolean fallback(Object name1, Object name2,
                        @Cached CastToTruffleStringNode cast,
                        @Cached CExtNodes.FromCharPointerNode fromCharPtr,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached TruffleString.EqualNode equalNode) {
            TruffleString n1 = name1 instanceof TruffleString ? (TruffleString) name1 : null;
            TruffleString n2 = name2 instanceof TruffleString ? (TruffleString) name2 : null;
            if (n1 == null) {
                n1 = lib.isNull(name1) ? null : cast.execute(fromCharPtr.execute(name1));
            }
            if (n2 == null) {
                n2 = lib.isNull(name2) ? null : cast.execute(fromCharPtr.execute(name2));
            }
            return ts(n1, n2, equalNode);
        }
    }

    // PyCapsule_New(void *pointer, const char *name, PyCapsule_Destructor destructor)
    @Builtin(name = "PyCapsule_New", maxNumOfPositionalArgs = 3, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyCapsuleNewNode extends PythonTernaryBuiltinNode {
        @Specialization
        public Object doit(Object pointer, Object name, Object destructor,
                        @Cached CExtNodes.ToNewRefNode toSulongNode,
                        @CachedLibrary(limit = "2") InteropLibrary interopLibrary,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (interopLibrary.isNull(pointer)) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT);
                }
                Object n = interopLibrary.isNull(name) ? null : name;
                PyCapsule capsule = factory().createCapsule(pointer, n, destructor);
                return toSulongNode.execute(capsule);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getContext().getNativeNull());
            }
        }
    }

    // PyCapsule_IsValid(PyObject *o, const char *name)
    @Builtin(name = "PyCapsule_IsValid", maxNumOfPositionalArgs = 2, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyCapsuleIsValidNode extends PythonBinaryBuiltinNode {
        @Specialization
        public static int PyCapsuleIsValid(PyCapsule o, TruffleString name,
                        @Cached NameMatchesNode nameMatchesNode) {
            if (o.getPointer() == null) {
                return 0;
            }
            if (!nameMatchesNode.execute(name, o.getName())) {
                return 0;
            }
            return 1;
        }

        @Fallback
        public Object doit(VirtualFrame ignoredFrame, Object ignoredo, Object ignoredname) {
            return 0;
        }
    }

    // PyCapsule_GetPointer(PyObject *o, const char *name)
    @Builtin(name = "PyCapsule_GetPointer", maxNumOfPositionalArgs = 2, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyCapsuleGetPointerNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object doit(PyCapsule o, Object name,
                        @Cached NameMatchesNode nameMatchesNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (o.getPointer() == null) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetPointer");
                }
                if (!nameMatchesNode.execute(name, o.getName())) {
                    throw raise(ValueError, PY_CAPSULE_IMPORT_S_IS_NOT_VALID);
                }
                return o.getPointer();
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Fallback
        public Object doit(VirtualFrame ignoredFrame, Object ignoredo, Object ignoredname) {
            try {
                throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetPointer");
            } catch (PException e) {
                CExtNodesFactory.TransformExceptionToNativeNodeGen.getUncached().execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    // PyCapsule_GetName(PyObject *o)
    @Builtin(name = "PyCapsule_GetName", maxNumOfPositionalArgs = 1, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyCapsuleGetNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doit(PyCapsule o,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (o.getPointer() == null) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetName");
                }
                if (o.getName() == null) {
                    return getContext().getNativeNull();
                }
                if (o.getName() instanceof TruffleString) {
                    return new CArrayWrappers.CStringWrapper((TruffleString) o.getName());
                }
                return o.getName();
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Fallback
        public Object doit(VirtualFrame ignoredFrame, Object ignoredo) {
            try {
                throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetName");
            } catch (PException e) {
                CExtNodesFactory.TransformExceptionToNativeNodeGen.getUncached().execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    // PyCapsule_GetDestructor(PyObject *o)
    @Builtin(name = "PyCapsule_GetDestructor", maxNumOfPositionalArgs = 1, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyCapsuleGetDestructorNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doit(PyCapsule o,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (o.getPointer() == null) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetDestructor");
                }
                if (o.getDestructor() == null) {
                    return getContext().getNativeNull();
                }
                return o.getDestructor();
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Fallback
        public Object doit(VirtualFrame ignoredFrame, Object ignoredo) {
            try {
                throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetPointer");
            } catch (PException e) {
                CExtNodesFactory.TransformExceptionToNativeNodeGen.getUncached().execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    // PyCapsule_GetContext(PyObject *o)
    @Builtin(name = "PyCapsule_GetContext", maxNumOfPositionalArgs = 1, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyCapsuleGetContextNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doit(PyCapsule o,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (o.getPointer() == null) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetContext");
                }
                if (o.getContext() == null) {
                    return getContext().getNativeNull();
                }
                return o.getContext();
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Fallback
        public Object doit(VirtualFrame ignoredFrame, Object ignoredo) {
            try {
                throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_GetPointer");
            } catch (PException e) {
                CExtNodesFactory.TransformExceptionToNativeNodeGen.getUncached().execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    // PyCapsule_SetPointer(PyObject *o, void *pointer)
    @Builtin(name = "PyCapsule_SetPointer", maxNumOfPositionalArgs = 2, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyCapsuleSetPointerNode extends PythonBinaryBuiltinNode {
        @Specialization
        public int doit(Object obj, Object pointer,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode,
                        @CachedLibrary(limit = "2") InteropLibrary interopLibrary,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object p = asPythonObjectNode.execute(obj);
                if (!(p instanceof PyCapsule)) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetPointer");
                }
                if (interopLibrary.isNull(pointer)) {
                    throw raise(ValueError, PY_CAPSULE_IMPORT_S_IS_NOT_VALID);
                }

                PyCapsule o = (PyCapsule) p;
                if (o.getPointer() == null) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetPointer");
                }

                o.setPointer(pointer);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }
    }

    // PyCapsule_SetName(PyObject *o, const char *name)
    @Builtin(name = "PyCapsule_SetName", maxNumOfPositionalArgs = 2, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyCapsuleSetNameNode extends PythonBinaryBuiltinNode {
        @Specialization
        public int doit(PyCapsule o, TruffleString name,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (o.getPointer() == null) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetName");
                }
                Object n = lib.isNull(name) ? null : name;
                o.setName(n);
                return 0;

            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = "isNoValue(name)")
        public int doit(PyCapsule o, @SuppressWarnings("unused") PNone name,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (o.getPointer() == null) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetName");
                }
                o.setName(null);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Fallback
        public Object doit(VirtualFrame ignoredFrame, Object ignoredo, Object ignored) {
            try {
                throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetName");
            } catch (PException e) {
                CExtNodesFactory.TransformExceptionToNativeNodeGen.getUncached().execute(e);
                return -1;
            }
        }
    }

    // PyCapsule_SetDestructor(PyObject *o, PyCapsule_Destructor destructor)
    @Builtin(name = "PyCapsule_SetDestructor", maxNumOfPositionalArgs = 2, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyCapsuleSetDestructorNode extends PythonBinaryBuiltinNode {
        @Specialization
        public int doit(Object obj, Object destructor,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object p = asPythonObjectNode.execute(obj);
                if (!(p instanceof PyCapsule)) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetDestructor");
                }
                PyCapsule o = (PyCapsule) p;
                if (o.getPointer() == null) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetDestructor");
                }
                o.setDestructor(destructor);
                return 0;

            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }
    }

    // PyCapsule_SetContext(PyObject *o, void *context)
    @Builtin(name = "PyCapsule_SetContext", maxNumOfPositionalArgs = 2, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyCapsuleSetContextNode extends PythonBinaryBuiltinNode {
        @Specialization
        public int doit(Object obj, Object context,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object p = asPythonObjectNode.execute(obj);
                if (!(p instanceof PyCapsule)) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetContext");
                }
                PyCapsule o = (PyCapsule) p;
                if (o.getPointer() == null) {
                    throw raise(ValueError, CALLED_WITH_INVALID_PY_CAPSULE_OBJECT, "PyCapsule_SetContext");
                }
                o.setContext(context);
                return 0;

            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }
    }

    static final TruffleString dotChar = tsLiteral(".");

    // PyCapsule_Import(const char *name, int no_block)
    @Builtin(name = "PyCapsule_Import", maxNumOfPositionalArgs = 2, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyCapsuleImportNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object doit(TruffleString name, int noBlock,
                        @Cached NameMatchesNode nameMatchesNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfStringNode indexOfStringNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached ReadAttributeFromObjectNode getAttrNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                TruffleString trace = name;
                Object object = null;
                while (trace != null) {
                    int traceLen = codePointLengthNode.execute(trace, TS_ENCODING);
                    int dotIdx = indexOfStringNode.execute(trace, dotChar, 0, traceLen, TS_ENCODING);
                    TruffleString dot = null;
                    if (dotIdx >= 0) {
                        dot = substringNode.execute(trace, dotIdx + 1, traceLen - dotIdx - 1, TS_ENCODING, false);
                        trace = substringNode.execute(trace, 0, dotIdx, TS_ENCODING, false);
                    }
                    if (object == null) {
                        if (noBlock == 1) {
                            // object = PyImport_ImportModuleNoBlock(trace);
                            throw raise(SystemError, NOT_IMPLEMENTED);
                        } else {
                            object = AbstractImportNode.importModule(trace, T_IMPORT_ALL);
                            if (object == PNone.NO_VALUE) {
                                throw raise(ImportError, PY_CAPSULE_IMPORT_S_IS_NOT_VALID, trace);
                            }
                        }
                    } else {
                        object = getAttrNode.execute(object, trace);
                    }
                    trace = dot;
                }

                /* compare attribute name to module.name by hand */
                PyCapsule capsule = object instanceof PyCapsule ? (PyCapsule) object : null;
                if (capsule != null && PyCapsuleIsValid(capsule, name, nameMatchesNode) == 1) {
                    return capsule.getPointer();
                } else {
                    throw raise(AttributeError, PY_CAPSULE_IMPORT_S_IS_NOT_VALID, name);
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        public static TruffleString getNameAsTruffleString(Object name,
                        CastToTruffleStringNode cast,
                        CExtNodes.FromCharPointerNode fromCharPtr) {
            if (name == null) {
                return null;
            }

            if (name instanceof TruffleString) {
                return (TruffleString) name;
            }
            return cast.execute(fromCharPtr.execute(name));
        }

        @Specialization
        @TruffleBoundary
        public static TruffleString repr(PyCapsule self,
                        @Cached CastToTruffleStringNode cast,
                        @Cached CExtNodes.FromCharPointerNode fromCharPtr) {
            String quote, n;
            if (self.getName() != null) {
                quote = "\"";
                n = getNameAsTruffleString(self.getName(), cast, fromCharPtr).toJavaStringUncached();
            } else {
                quote = "";
                n = "NULL";
            }
            return tsLiteral(String.format("<capsule object %s%s%s at %x>", quote, n, quote, self.hashCode()));
        }
    }
}
