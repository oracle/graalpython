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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.modules.ctypes.StgDictObject.DICTFLAG_FINAL;
import static com.oracle.graal.python.nodes.ErrorMessages.BUFFER_SIZE_TOO_SMALL_D_INSTEAD_OF_AT_LEAST_D_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.CTYPES_OBJECT_STRUCTURE_TOO_DEEP;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_P_INSTANCE_GOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_P_INSTANCE_INSTEAD_OF_P;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_P_INSTANCE_INSTEAD_OF_POINTER_TO_P;
import static com.oracle.graal.python.nodes.ErrorMessages.INCOMPATIBLE_TYPES_P_INSTANCE_INSTEAD_OF_P_INSTANCE;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_A_CTYPE_INSTANCE;
import static com.oracle.graal.python.nodes.ErrorMessages.OFFSET_CANNOT_BE_NEGATIVE;
import static com.oracle.graal.python.nodes.ErrorMessages.THE_HANDLE_ATTRIBUTE_OF_THE_SECOND_ARGUMENT_MUST_BE_AN_INTEGER;
import static com.oracle.graal.python.nodes.ErrorMessages.UNDERLYING_BUFFER_IS_NOT_C_CONTIGUOUS;
import static com.oracle.graal.python.nodes.ErrorMessages.UNDERLYING_BUFFER_IS_NOT_WRITABLE;
import static com.oracle.graal.python.nodes.StringLiterals.T_COLON;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.AuditNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.GetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.SetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.CtypesDlSymNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldGet;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldSet;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerNodes;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = {
                PythonBuiltinClassType.PyCStructType,
                PythonBuiltinClassType.UnionType,
                PythonBuiltinClassType.PyCArrayType,
                PythonBuiltinClassType.PyCFuncPtrType,
                PythonBuiltinClassType.PyCPointerType,
                PythonBuiltinClassType.PyCSimpleType,
})
public final class CDataTypeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CDataTypeBuiltinsFactory.getFactories();
    }

    protected static final String J_FROM_PARAM = "from_param";
    protected static final TruffleString T_FROM_PARAM = tsLiteral(J_FROM_PARAM);
    protected static final String J_FROM_ADDRESS = "from_address";
    protected static final String J_FROM_BUFFER = "from_buffer";
    protected static final String J_FROM_BUFFER_COPY = "from_buffer_copy";
    protected static final String J_IN_DLL = "in_dll";

    protected static final TruffleString T__AS_PARAMETER_ = tsLiteral("_as_parameter_");
    protected static final String J__HANDLE = "_handle";
    protected static final TruffleString T__HANDLE = tsLiteral(J__HANDLE);

    @ImportStatic(CDataTypeBuiltins.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 72 -> 53
    protected abstract static class CDataTypeFromParamNode extends Node {

        abstract Object execute(VirtualFrame frame, Object type, Object value);

        @Specialization
        static Object CDataType_from_param(VirtualFrame frame, Object type, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (isInstanceNode.executeWith(frame, value, type)) {
                return value;
            }
            if (PGuards.isPyCArg(value)) {
                PyCArgObject p = (PyCArgObject) value;
                Object ob = p.obj;
                StgDictObject dict = pyTypeStgDictNode.execute(inliningTarget, type);

                /*
                 * If we got a PyCArgObject, we must check if the object packed in it is an instance
                 * of the type's dict.proto
                 */
                if (dict != null && ob != null) {
                    if (isInstanceNode.executeWith(frame, ob, dict.proto)) {
                        return value;
                    }
                }
                throw raiseNode.get(inliningTarget).raise(TypeError, EXPECTED_P_INSTANCE_INSTEAD_OF_POINTER_TO_P, type, ob != null ? ob : PNone.NONE);
            }

            Object as_parameter = lookupAttr.execute(frame, inliningTarget, value, T__AS_PARAMETER_);

            if (as_parameter != PNone.NO_VALUE) {
                return CDataType_from_param(frame, type, as_parameter, inliningTarget,
                                pyTypeStgDictNode, lookupAttr, isInstanceNode, raiseNode);
            }
            throw raiseNode.get(inliningTarget).raise(TypeError, EXPECTED_P_INSTANCE_INSTEAD_OF_P, type, value);
        }
    }

    @ImportStatic(CDataTypeBuiltins.class)
    @Builtin(name = J_FROM_PARAM, minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class FromParamNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object CDataType_from_param(VirtualFrame frame, Object type, Object value,
                        @Cached CDataTypeFromParamNode fromParamNode) {
            return fromParamNode.execute(frame, type, value);
        }
    }

    @Builtin(name = J_FROM_ADDRESS, minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class FromAddressNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object CDataType_from_address(Object type, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PointerNodes.PointerFromLongNode pointerFromLongNode,
                        @Cached PyCDataAtAddress atAddress) {
            return atAddress.execute(type, pointerFromLongNode.execute(inliningTarget, value));
        }
    }

    // PyArg_ParseTuple(args, "O|n:from_buffer", &obj, &offset);
    @Builtin(name = J_FROM_BUFFER, minNumOfPositionalArgs = 2, parameterNames = {"self", "buffer", "offset"}, declaresExplicitSelf = true)
    @ArgumentClinic(name = "offset", conversion = ClinicConversion.Int, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class FromBufferNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CDataTypeBuiltinsClinicProviders.FromBufferNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object CDataType_from_buffer(VirtualFrame frame, Object type, Object obj, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached BuiltinConstructors.MemoryViewNode memoryViewNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyCDataAtAddress atAddress,
                        @Cached KeepRefNode keepRefNode,
                        @Cached AuditNode auditNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(inliningTarget, type, raiseNode);

            PMemoryView mv = memoryViewNode.execute(frame, obj);

            if (mv.isReadOnly()) {
                throw raiseNode.get(inliningTarget).raise(TypeError, UNDERLYING_BUFFER_IS_NOT_WRITABLE);
            }

            if (!mv.isCContiguous()) {
                throw raiseNode.get(inliningTarget).raise(TypeError, UNDERLYING_BUFFER_IS_NOT_C_CONTIGUOUS);
            }

            if (offset < 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, OFFSET_CANNOT_BE_NEGATIVE);
            }

            if (dict.size > mv.getLength() - offset) {
                throw raiseNode.get(inliningTarget).raise(ValueError, BUFFER_SIZE_TOO_SMALL_D_INSTEAD_OF_AT_LEAST_D_BYTES, mv.getLength(), dict.size + offset);
            }

            auditNode.audit(inliningTarget, "ctypes.cdata/buffer", mv, mv.getLength(), offset);

            CDataObject result = atAddress.execute(type, Pointer.memoryView(mv).withOffset(offset));

            keepRefNode.execute(frame, inliningTarget, result, -1, mv);

            return result;
        }
    }

    // PyArg_ParseTuple(args, "y*|n:from_buffer_copy", &buffer, &offset);
    @Builtin(name = J_FROM_BUFFER_COPY, minNumOfPositionalArgs = 2, parameterNames = {"self", "buffer", "offset"})
    @ArgumentClinic(name = "buffer", conversion = ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "offset", conversion = ClinicConversion.Int, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class FromBufferCopyNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CDataTypeBuiltinsClinicProviders.FromBufferCopyNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static Object CDataType_from_buffer_copy(Object type, Object buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached PointerNodes.WriteBytesNode writeBytesNode,
                        @Cached AuditNode auditNode,
                        @Cached CtypesNodes.GenericPyCDataNewNode pyCDataNewNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(inliningTarget, type, raiseNode);

                if (offset < 0) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, OFFSET_CANNOT_BE_NEGATIVE);
                }

                int bufferLen = bufferLib.getBufferLength(buffer);

                if (dict.size > bufferLen - offset) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, BUFFER_SIZE_TOO_SMALL_D_INSTEAD_OF_AT_LEAST_D_BYTES, bufferLen, dict.size + offset);
                }

                // This prints the raw pointer in C, so just print 0
                auditNode.audit(inliningTarget, "ctypes.cdata/buffer", 0, bufferLen, offset);

                CDataObject result = pyCDataNewNode.execute(inliningTarget, type, dict);
                byte[] slice = new byte[dict.size];
                bufferLib.readIntoByteArray(buffer, offset, slice, 0, dict.size);
                writeBytesNode.execute(inliningTarget, result.b_ptr, slice);
                return result;
            } finally {
                bufferLib.release(buffer);
            }
        }
    }

    // PyArg_ParseTuple(args, "Os:in_dll", &dll, &name);
    @ImportStatic(CDataTypeBuiltins.class)
    @Builtin(name = J_IN_DLL, minNumOfPositionalArgs = 1, parameterNames = {"type", "dll", "name"}, declaresExplicitSelf = true)
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class InDllNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CDataTypeBuiltinsClinicProviders.InDllNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object CDataType_in_dll(VirtualFrame frame, Object type, Object dll, TruffleString name,
                        @Bind("this") Node inliningTarget,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached("create(T__HANDLE)") GetAttributeNode getAttributeNode,
                        @Cached PyCDataAtAddress atAddress,
                        @Cached AuditNode auditNode,
                        @Cached PointerNodes.PointerFromLongNode pointerFromLongNode,
                        @Cached CtypesDlSymNode dlSymNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            auditNode.audit(inliningTarget, "ctypes.dlsym", dll, name);
            Object obj = getAttributeNode.executeObject(frame, dll);
            if (!longCheckNode.execute(inliningTarget, obj)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, THE_HANDLE_ATTRIBUTE_OF_THE_SECOND_ARGUMENT_MUST_BE_AN_INTEGER);
            }
            Pointer handlePtr;
            try {
                handlePtr = pointerFromLongNode.execute(inliningTarget, obj);
            } catch (PException e) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.COULD_NOT_CONVERT_THE_HANDLE_ATTRIBUTE_TO_A_POINTER);
            }
            Object address = dlSymNode.execute(frame, handlePtr, name, ValueError);
            if (address instanceof PythonNativeVoidPtr ptr) {
                address = ptr.getPointerObject();
            }
            return atAddress.execute(type, Pointer.nativeMemory(address));
        }
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 40 -> 21
    protected abstract static class PyCDataAtAddress extends Node {

        abstract CDataObject execute(Object type, Pointer pointer);

        /*
         * Box a memory block into a CData instance.
         */
        @Specialization
        static CDataObject PyCData_AtAddress(Object type, Pointer pointer,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached CtypesNodes.CreateCDataObjectNode createCDataObjectNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            // auditNode.audit("ctypes.cdata", buf);
            // assert(PyType_Check(type));
            StgDictObject stgdict = pyTypeStgDictNode.checkAbstractClass(inliningTarget, type, raiseNode);
            stgdict.flags |= DICTFLAG_FINAL;

            CDataObject pd = createCDataObjectNode.execute(inliningTarget, type, pointer, stgdict.size, false);
            assert pyTypeCheck.isCDataObject(inliningTarget, pd);
            pd.b_length = stgdict.length;
            return pd;
        }
    }

    // corresponds to PyCData_get
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(FieldGet.class)
    protected abstract static class PyCDataGetNode extends Node {
        protected abstract Object execute(Node inliningTarget, Object type, FieldGet getfunc, CDataObject src, int index, int size, Pointer adr);

        @Specialization(guards = "getfunc != nil")
        @SuppressWarnings("unused")
        static Object withFunc(Object type, FieldGet getfunc, CDataObject src, int index, int size, Pointer adr,
                        @Shared @Cached(inline = false) GetFuncNode getFuncNode) {
            return getFuncNode.execute(getfunc, adr, size);
        }

        @Specialization(guards = "getfunc == nil")
        static Object withoutFunc(Node inliningTarget, Object type, @SuppressWarnings("unused") FieldGet getfunc, CDataObject src, int index, int size, Pointer adr,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Shared @Cached(inline = false) GetFuncNode getFuncNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached CtypesNodes.PyCDataFromBaseObjNode fromBaseObjNode) {
            StgDictObject dict = pyTypeStgDictNode.execute(inliningTarget, type);
            if (dict != null && dict.getfunc != FieldGet.nil && !pyTypeCheck.ctypesSimpleInstance(inliningTarget, type, getBaseClassNode, isSameTypeNode)) {
                return getFuncNode.execute(dict.getfunc, adr, size);
            }
            return fromBaseObjNode.execute(inliningTarget, type, src, index, adr);
        }
    }

    /*
     * Set a slice in object 'dst', which has the type 'type', to the value 'value'.
     */
    @SuppressWarnings("truffle-inlining")       // footprint reduction 64 -> 46
    protected abstract static class PyCDataSetNode extends Node {

        abstract void execute(VirtualFrame frame, CDataObject dst, Object type, FieldSet setfunc, Object value, int index, int size, Pointer ptr);

        @Specialization
        static void PyCData_set(VirtualFrame frame, CDataObject dst, Object type, FieldSet setfunc, Object value, int index, int size, Pointer ptr,
                        @Bind("this") Node inliningTarget,
                        @Cached SetFuncNode setFuncNode,
                        @Cached CallNode callNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached KeepRefNode keepRefNode,
                        @Cached PointerNodes.MemcpyNode memcpyNode,
                        @Cached PointerNodes.WritePointerNode writePointerNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!pyTypeCheck.isCDataObject(inliningTarget, dst)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, NOT_A_CTYPE_INSTANCE);
            }

            Object result = PyCDataSetInternal(frame, inliningTarget, type, setfunc, value, size, ptr,
                            factory,
                            pyTypeCheck,
                            setFuncNode,
                            callNode,
                            isInstanceNode,
                            pyTypeStgDictNode,
                            pyObjectStgDictNode,
                            memcpyNode,
                            writePointerNode,
                            raiseNode);

            keepRefNode.execute(frame, inliningTarget, dst, index, result);
        }

        /*
         * Helper function for PyCData_set below.
         */
        // corresponds to _PyCData_set
        static Object PyCDataSetInternal(VirtualFrame frame, Node inliningTarget, Object type, FieldSet setfunc, Object value, int size, Pointer ptr,
                        PythonObjectFactory factory,
                        PyTypeCheck pyTypeCheck,
                        SetFuncNode setFuncNode,
                        CallNode callNode,
                        IsInstanceNode isInstanceNode,
                        PyTypeStgDictNode pyTypeStgDictNode,
                        PyObjectStgDictNode pyObjectStgDictNode,
                        PointerNodes.MemcpyNode memcpyNode,
                        PointerNodes.WritePointerNode writePointerNode,
                        PRaiseNode.Lazy raiseNode) {
            if (setfunc != FieldSet.nil) {
                return setFuncNode.execute(frame, setfunc, ptr, value, size);
            }

            if (!pyTypeCheck.isCDataObject(inliningTarget, value)) {
                StgDictObject dict = pyTypeStgDictNode.execute(inliningTarget, type);
                if (dict != null && dict.setfunc != FieldSet.nil) {
                    return setFuncNode.execute(frame, dict.setfunc, ptr, value, size);
                }
                /*
                 * If value is a tuple, we try to call the type with the tuple and use the result!
                 */
                if (PGuards.isPTuple(value)) {
                    Object ob = callNode.execute(frame, type, value);
                    return PyCDataSetInternal(frame, inliningTarget, type, setfunc, ob, size, ptr,
                                    factory,
                                    pyTypeCheck,
                                    setFuncNode,
                                    callNode,
                                    isInstanceNode,
                                    pyTypeStgDictNode,
                                    pyObjectStgDictNode,
                                    memcpyNode,
                                    writePointerNode,
                                    raiseNode);
                } else if (value instanceof PNone && pyTypeCheck.isPyCPointerTypeObject(inliningTarget, type)) {
                    writePointerNode.execute(inliningTarget, ptr, Pointer.NULL);
                    return PNone.NONE;
                } else {
                    throw raiseNode.get(inliningTarget).raise(TypeError, EXPECTED_P_INSTANCE_GOT_P, type, value);
                }
            }
            CDataObject src = (CDataObject) value;

            if (isInstanceNode.executeWith(frame, value, type)) {
                memcpyNode.execute(inliningTarget, ptr, src.b_ptr, size);
                return GetKeepedObjects(src, factory);
            }

            if (pyTypeCheck.isPyCPointerTypeObject(inliningTarget, type) && pyTypeCheck.isArrayObject(inliningTarget, value)) {
                StgDictObject p1 = pyObjectStgDictNode.execute(inliningTarget, value);
                assert p1 != null : "Cannot be NULL for array instances";
                StgDictObject p2 = pyTypeStgDictNode.execute(inliningTarget, type);
                assert p2 != null : "Cannot be NULL for pointer types";

                if (p1.proto != p2.proto) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, INCOMPATIBLE_TYPES_P_INSTANCE_INSTEAD_OF_P_INSTANCE, value, type);
                }

                writePointerNode.execute(inliningTarget, ptr, src.b_ptr);

                Object keep = GetKeepedObjects(src, factory);

                /*
                 * We are assigning an array object to a field which represents a pointer. This has
                 * the same effect as converting an array into a pointer. So, again, we have to keep
                 * the whole object pointed to (which is the array in this case) alive, and not only
                 * it's object list. So we create a tuple, containing b_objects list PLUS the array
                 * itself, and return that!
                 */
                return factory.createTuple(new Object[]{keep, value});
            }
            throw raiseNode.get(inliningTarget).raise(TypeError, INCOMPATIBLE_TYPES_P_INSTANCE_INSTEAD_OF_P_INSTANCE, value, type);
        }

    }

    /*****************************************************************
     * Code to keep needed objects alive
     */

    protected static CDataObject PyCData_GetContainer(CDataObject leaf, PythonObjectFactory factory) {
        CDataObject self = leaf;
        while (self.b_base != null) {
            self = self.b_base;
        }
        if (self.b_objects == null) {
            if (self.b_length != 0) {
                self.b_objects = factory.createDict();
            } else {
                self.b_objects = PNone.NONE;
            }
        }
        return self;
    }

    static Object GetKeepedObjects(CDataObject target, PythonObjectFactory factory) {
        return PyCData_GetContainer(target, factory).b_objects;
    }

    /*
     * Keep a reference to 'keep' in the 'target', at index 'index'.
     *
     * If 'keep' is None, do nothing.
     *
     * Otherwise create a dictionary (if it does not yet exist) id the root objects 'b_objects'
     * item, which will store the 'keep' object under a unique key.
     *
     * The unique_key helper travels the target's b_base pointer down to the root, building a string
     * containing hex-formatted indexes found during traversal, separated by colons.
     *
     * The index tuple is used as a key into the root object's b_objects dict.
     *
     * Note: This function steals a refcount of the third argument, even if it fails!
     */
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    protected abstract static class KeepRefNode extends Node {

        abstract void execute(VirtualFrame frame, Node inliningTarget, CDataObject target, int index, Object keep);

        @Specialization
        @SuppressWarnings("unused")
        static void none(CDataObject target, int index, PNone keep) {
            /* Optimization: no need to store None */
        }

        @Specialization(guards = "!isNone(keep)")
        static void KeepRef(VirtualFrame frame, Node inliningTarget, CDataObject target, int index, Object keep,
                        @Cached(inline = false) TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached(inline = false) TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached(inline = false) TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached HashingStorageSetItem setItem,
                        @Cached(inline = false) PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            CDataObject ob = PyCData_GetContainer(target, factory);
            if (!PGuards.isDict(ob.b_objects)) {
                ob.b_objects = keep;
                return;
            }
            PDict dict = (PDict) ob.b_objects;
            Object key = unique_key(inliningTarget, target, index, raiseNode, appendStringNode, toStringNode, fromJavaStringNode);
            dict.setDictStorage(setItem.execute(frame, inliningTarget, dict.getDictStorage(), key, keep));
        }
    }

    private static final int MAX_KEY_SIZE = 256;

    static TruffleString unique_key(Node inliningTarget, CDataObject cdata, int index,
                    PRaiseNode.Lazy raiseNode, TruffleStringBuilder.AppendStringNode appendStringNode,
                    TruffleStringBuilder.ToStringNode toStringNode, TruffleString.FromJavaStringNode fromJavaStringNode) {
        assert TS_ENCODING == Encoding.UTF_32;
        final int bytesPerCodepoint = 4;      // assumes utf-32

        CDataObject target = cdata;
        TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
        appendStringNode.execute(sb, fromJavaStringNode.execute(toHex(index), TS_ENCODING));
        while (target.b_base != null) {
            int bytesLeft = MAX_KEY_SIZE - sb.byteLength() / bytesPerCodepoint - 1;
            /* Hex format needs 2 characters per byte */
            if (bytesLeft < Integer.BYTES * 2) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CTYPES_OBJECT_STRUCTURE_TOO_DEEP);
            }
            appendStringNode.execute(sb, T_COLON);
            appendStringNode.execute(sb, fromJavaStringNode.execute(toHex(target.b_index), TS_ENCODING));
            target = target.b_base;
        }
        return toStringNode.execute(sb);
    }

    @TruffleBoundary
    private static String toHex(int value) {
        return Integer.toHexString(value);
    }
}
