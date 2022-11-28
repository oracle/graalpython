/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.DICTFLAG_FINAL;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.getHandleFromLongObject;
import static com.oracle.graal.python.nodes.ErrorMessages.BUFFER_SIZE_TOO_SMALL_D_INSTEAD_OF_AT_LEAST_D_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.CTYPES_OBJECT_STRUCTURE_TOO_DEEP;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_S_INSTANCE_GOT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_S_INSTANCE_INSTEAD_OF_POINTER_TO_S;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_S_INSTANCE_INSTEAD_OF_S;
import static com.oracle.graal.python.nodes.ErrorMessages.INCOMPATIBLE_TYPES_S_INSTANCE_INSTEAD_OF_S_INSTANCE;
import static com.oracle.graal.python.nodes.ErrorMessages.INTEGER_EXPECTED;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_A_CTYPE_INSTANCE;
import static com.oracle.graal.python.nodes.ErrorMessages.OFFSET_CANNOT_BE_NEGATIVE;
import static com.oracle.graal.python.nodes.ErrorMessages.THE_HANDLE_ATTRIBUTE_OF_THE_SECOND_ARGUMENT_MUST_BE_AN_INTEGER;
import static com.oracle.graal.python.nodes.ErrorMessages.UNDERLYING_BUFFER_IS_NOT_C_CONTIGUOUS;
import static com.oracle.graal.python.nodes.ErrorMessages.UNDERLYING_BUFFER_IS_NOT_WRITABLE;
import static com.oracle.graal.python.nodes.StringLiterals.T_COLON;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
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
import com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltins.PyLongAsVoidPtr;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.GetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.SetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.CtypesDlSymNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.DLHandler;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldGet;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldSet;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
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
public class CDataTypeBuiltins extends PythonBuiltins {

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
    protected abstract static class CDataTypeFromParamNode extends PNodeWithRaise {

        abstract Object execute(VirtualFrame frame, Object type, Object value);

        @Specialization
        Object CDataType_from_param(VirtualFrame frame, Object type, Object value,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached("create(T__AS_PARAMETER_)") LookupInheritedAttributeNode lookupAttrId,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached GetClassNode getClassNode,
                        @Cached GetNameNode getNameNode) {
            if (isInstanceNode.executeWith(frame, value, type)) {
                return value;
            }
            if (PGuards.isPyCArg(value)) {
                PyCArgObject p = (PyCArgObject) value;
                Object ob = p.obj;
                StgDictObject dict = pyTypeStgDictNode.execute(type);

                /*
                 * If we got a PyCArgObject, we must check if the object packed in it is an instance
                 * of the type's dict.proto
                 */
                if (dict != null && ob != null) {
                    if (isInstanceNode.executeWith(frame, ob, dict.proto)) {
                        return value;
                    }
                }
                throw raise(TypeError, EXPECTED_S_INSTANCE_INSTEAD_OF_POINTER_TO_S,
                                getNameNode.execute(getClassNode.execute(type)));
            }

            Object as_parameter = lookupAttrId.execute(value);

            if (as_parameter != null) {
                return CDataType_from_param(frame, type, as_parameter,
                                pyTypeStgDictNode, lookupAttrId, isInstanceNode, getClassNode, getNameNode);
            }
            throw raise(TypeError, EXPECTED_S_INSTANCE_INSTEAD_OF_S,
                            getNameNode.execute(getClassNode.execute(type)),
                            getNameNode.execute(getClassNode.execute(value)));
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
        Object CDataType_from_address(Object type, int value,
                        @Cached PyLongAsVoidPtr asVoidPtr,
                        @Cached PyCDataAtAddress atAddress) {
            Object buf = asVoidPtr.execute(value);
            return atAddress.execute(type, buf, 0, factory());
        }

        @SuppressWarnings("unused")
        @Fallback
        Object error(Object type, Object arg) {
            throw raise(TypeError, INTEGER_EXPECTED);
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
        Object CDataType_from_buffer(VirtualFrame frame, Object type, Object obj, int offset,
                        @Cached BuiltinConstructors.MemoryViewNode memoryViewNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyCDataAtAddress atAddress,
                        @Cached KeepRefNode keepRefNode,
                        @Cached AuditNode auditNode) {
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());

            PMemoryView buffer = memoryViewNode.execute(frame, obj);

            if (buffer.isReadOnly()) {
                throw raise(TypeError, UNDERLYING_BUFFER_IS_NOT_WRITABLE);
            }

            if (!buffer.isCContiguous()) {
                throw raise(TypeError, UNDERLYING_BUFFER_IS_NOT_C_CONTIGUOUS);
            }

            if (offset < 0) {
                throw raise(ValueError, OFFSET_CANNOT_BE_NEGATIVE);
            }

            if (dict.size > buffer.getLength() - offset) {
                throw raise(ValueError, BUFFER_SIZE_TOO_SMALL_D_INSTEAD_OF_AT_LEAST_D_BYTES, buffer.getLength(), dict.size + offset);
            }

            auditNode.audit("ctypes.cdata/buffer", buffer, buffer.getLength(), offset);

            CDataObject result = atAddress.execute(type, buffer, offset, factory());

            keepRefNode.execute(frame, result, -1, buffer, factory());

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
        Object CDataType_from_buffer_copy(Object type, Object buffer, int offset,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached AuditNode auditNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            try {
                StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());

                if (offset < 0) {
                    throw raise(ValueError, OFFSET_CANNOT_BE_NEGATIVE);
                }

                int bufferLen = bufferLib.getBufferLength(buffer);

                if (dict.size > bufferLen - offset) {
                    throw raise(ValueError, BUFFER_SIZE_TOO_SMALL_D_INSTEAD_OF_AT_LEAST_D_BYTES, bufferLen, dict.size + offset);
                }

                // This prints the raw pointer in C, so just print 0
                auditNode.audit("ctypes.cdata/buffer", 0, bufferLen, offset);

                CDataObject result = factory().createCDataObject(type);
                GenericPyCDataNew(dict, result);
                // memcpy(result.b_ptr, buffer.buf + offset, dict.size);
                byte[] slice = new byte[dict.size];
                bufferLib.readIntoByteArray(buffer, offset, slice, 0, dict.size);
                result.b_ptr = PtrValue.bytes(dict.ffi_type_pointer, slice);
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
        Object CDataType_in_dll(VirtualFrame frame, Object type, Object dll, TruffleString name,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached("create(T__HANDLE)") GetAttributeNode getAttributeNode,
                        @Cached PyCDataAtAddress atAddress,
                        @Cached AuditNode auditNode,
                        @Cached PyLongAsVoidPtr asVoidPtr,
                        @Cached CtypesDlSymNode dlSymNode) {
            auditNode.audit("ctypes.dlsym", dll, name);
            Object obj = getAttributeNode.executeObject(frame, dll);
            if (!longCheckNode.execute(obj)) {
                throw raise(TypeError, THE_HANDLE_ATTRIBUTE_OF_THE_SECOND_ARGUMENT_MUST_BE_AN_INTEGER);
            }
            DLHandler handle = getHandleFromLongObject(obj, getContext(), asVoidPtr, getRaiseNode());
            Object address = dlSymNode.execute(frame, handle, name, getContext(), factory(), ValueError);
            return atAddress.execute(type, address, 0, factory());
        }
    }

    protected abstract static class PyCDataAtAddress extends PNodeWithRaise {

        abstract CDataObject execute(Object type, Object obj, int offset, PythonObjectFactory factory);

        /*
         * Box a memory block into a CData instance.
         */
        @Specialization
        CDataObject PyCData_AtAddress_bytes(Object type, byte[] buf, int offset, PythonObjectFactory factory,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            // auditNode.audit("ctypes.cdata", buf);
            // assert(PyType_Check(type));
            StgDictObject stgdict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());
            stgdict.flags |= DICTFLAG_FINAL;

            CDataObject pd = factory.createCDataObject(type);
            assert pyTypeCheck.isCDataObject(pd);
            pd.b_ptr = PtrValue.bytes(stgdict.ffi_type_pointer, buf);
            pd.b_ptr.offset = offset;
            pd.b_length = stgdict.length;
            pd.b_size = stgdict.size;
            return pd;
        }

        protected static boolean isBytes(Object obj) {
            return obj instanceof byte[];
        }

        @Specialization(guards = "!isBytes(obj)")
        CDataObject PyCData_AtAddress(Object type, Object obj, @SuppressWarnings("unused") int offset, PythonObjectFactory factory,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached AuditNode auditNode) {
            auditNode.audit("ctypes.cdata", obj);
            // assert(PyType_Check(type));
            StgDictObject stgdict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());
            stgdict.flags |= DICTFLAG_FINAL;

            CDataObject pd = factory.createCDataObject(type);
            assert (pyTypeCheck.isCDataObject(pd));
            if (obj instanceof PMemoryView) {
                pd.b_ptr = PtrValue.memoryView((PMemoryView) obj);
            } else {
                // TODO get Objects from numeric pointers.
                throw raise(NotImplementedError, toTruffleStringUncached("Storage is not implemented."));
            }
            pd.b_length = stgdict.length;
            pd.b_size = stgdict.size;
            return pd;
        }

    }

    // corresponds to PyCData_get
    @ImportStatic(FieldGet.class)
    protected abstract static class PyCDataGetNode extends PNodeWithRaise {
        protected abstract Object execute(Object type, FieldGet getfunc, Object src, int index, int size, PtrValue adr, PythonObjectFactory factory);

        @Specialization(guards = "getfunc != nil")
        Object withFunc(@SuppressWarnings("unused") Object type,
                        FieldGet getfunc,
                        @SuppressWarnings("unused") Object src,
                        @SuppressWarnings("unused") int index,
                        int size, PtrValue adr,
                        PythonObjectFactory factory,
                        @Cached GetFuncNode getFuncNode) {
            return getFuncNode.execute(getfunc, adr, size, factory);
        }

        @Specialization(guards = "getfunc == nil")
        Object WithoutFunc(Object type,
                        @SuppressWarnings("unused") FieldGet getfunc,
                        Object src,
                        int index,
                        int size, PtrValue adr,
                        PythonObjectFactory factory,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached GetFuncNode getFuncNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            StgDictObject dict = pyTypeStgDictNode.execute(type);
            if (dict != null && dict.getfunc != FieldGet.nil && !pyTypeCheck.ctypesSimpleInstance(type, getBaseClassNode, isSameTypeNode)) {
                return getFuncNode.execute(dict.getfunc, adr, size, factory);
            }
            return PyCData_FromBaseObj(type, src, index, adr, factory, getRaiseNode(), pyTypeStgDictNode);
        }
    }

    /*
     * Set a slice in object 'dst', which has the type 'type', to the value 'value'.
     */
    protected abstract static class PyCDataSetNode extends PNodeWithRaise {

        abstract void execute(VirtualFrame frame, CDataObject dst, Object type, FieldSet setfunc, Object value, int index, int size, PtrValue ptr, PythonObjectFactory factory);

        @Specialization
        void PyCData_set(VirtualFrame frame, CDataObject dst, Object type, FieldSet setfunc, Object value, int index, int size, PtrValue ptr, PythonObjectFactory factory,
                        @Cached SetFuncNode setFuncNode,
                        @Cached CallNode callNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached GetNameNode getName,
                        @Cached KeepRefNode keepRefNode) {
            if (!pyTypeCheck.isCDataObject(dst)) {
                throw raise(TypeError, NOT_A_CTYPE_INSTANCE);
            }

            Object result = PyCDataSetInternal(frame, type, setfunc, value, size, ptr, factory,
                            pyTypeCheck,
                            setFuncNode,
                            callNode,
                            isInstanceNode,
                            pyTypeStgDictNode,
                            pyObjectStgDictNode,
                            getName);

            /* KeepRef steals a refcount from it's last argument */
            /*
             * If KeepRef fails, we are stumped. The dst memory block has already been changed
             */
            keepRefNode.execute(frame, dst, index, result, factory);
        }

        /*
         * Helper function for PyCData_set below.
         */
        // corresponds to _PyCData_set
        Object PyCDataSetInternal(VirtualFrame frame, Object type, FieldSet setfunc, Object value, int size, PtrValue ptr, PythonObjectFactory factory,
                        PyTypeCheck pyTypeCheck,
                        SetFuncNode setFuncNode,
                        CallNode callNode,
                        IsInstanceNode isInstanceNode,
                        PyTypeStgDictNode pyTypeStgDictNode,
                        PyObjectStgDictNode pyObjectStgDictNode,
                        GetNameNode getName) {
            if (setfunc != FieldSet.nil) {
                return setFuncNode.execute(frame, setfunc, ptr, value, size);
            }

            if (!pyTypeCheck.isCDataObject(value)) {
                StgDictObject dict = pyTypeStgDictNode.execute(type);
                if (dict != null && dict.setfunc != FieldSet.nil) {
                    return setFuncNode.execute(frame, dict.setfunc, ptr, value, size);
                }
                /*
                 * If value is a tuple, we try to call the type with the tuple and use the result!
                 */
                // assert(PyType_Check(type));
                if (PGuards.isPTuple(value)) {
                    Object ob = callNode.execute(frame, type, value);
                    // throw raise(RuntimeError, "(%s) ", getName.execute(type));
                    // XXX we never return `null` it will throw elsewhere.
                    return PyCDataSetInternal(frame, type, setfunc, ob, size, ptr, factory,
                                    pyTypeCheck,
                                    setFuncNode,
                                    callNode,
                                    isInstanceNode,
                                    pyTypeStgDictNode,
                                    pyObjectStgDictNode,
                                    getName);
                } else if (value instanceof PNone && pyTypeCheck.isPyCPointerTypeObject(type)) {
                    ptr.toNil(); // *(void **)ptr = NULL;
                    return PNone.NONE;
                } else {
                    throw raise(TypeError, EXPECTED_S_INSTANCE_GOT_S, getName.execute(type), getName.execute(value));
                }
            }
            CDataObject src = (CDataObject) value;

            if (isInstanceNode.executeWith(frame, value, type)) {
                // memcpy(ptr, src.b_ptr, size); TODO
                // PyCPointerTypeObject_Check(type);
                return GetKeepedObjects(src, factory);
            }

            if (pyTypeCheck.isPyCPointerTypeObject(type) && pyTypeCheck.isArrayObject(value)) {
                StgDictObject p1 = pyObjectStgDictNode.execute(value);
                assert p1 != null : "Cannot be NULL for array instances";
                StgDictObject p2 = pyTypeStgDictNode.execute(type);
                assert p2 != null : "Cannot be NULL for pointer types";

                if (p1.proto != p2.proto) {
                    throw raise(TypeError, INCOMPATIBLE_TYPES_S_INSTANCE_INSTEAD_OF_S_INSTANCE, getName.execute(value), getName.execute(type));
                }
                // *(void **)ptr = src.b_ptr; TODO

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
            throw raise(TypeError, INCOMPATIBLE_TYPES_S_INSTANCE_INSTEAD_OF_S_INSTANCE, getName.execute(value), getName.execute(type));
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
    @ImportStatic(PGuards.class)
    protected abstract static class KeepRefNode extends PNodeWithRaise {

        abstract void execute(VirtualFrame frame, CDataObject target, int index, Object keep, PythonObjectFactory factory);

        @Specialization
        @SuppressWarnings("unused")
        static void none(CDataObject target, int index, PNone keep, PythonObjectFactory factory) {
            /* Optimization: no need to store None */
        }

        @Specialization(guards = "!isNone(keep)")
        void KeepRef(VirtualFrame frame, CDataObject target, int index, Object keep, PythonObjectFactory factory,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached HashingStorageSetItem setItem) {
            CDataObject ob = PyCData_GetContainer(target, factory);
            if (!PGuards.isDict(ob.b_objects)) {
                ob.b_objects = keep; /* refcount consumed */
                return;
            }
            PDict dict = (PDict) ob.b_objects;
            Object key = unique_key(target, index, getRaiseNode(), appendStringNode, toStringNode, fromJavaStringNode);
            dict.setDictStorage(setItem.execute(frame, dict.getDictStorage(), key, keep));
        }
    }

    private static final int MAX_KEY_SIZE = 256;

    static TruffleString unique_key(CDataObject cdata, int index,
                    PRaiseNode raiseNode, TruffleStringBuilder.AppendStringNode appendStringNode,
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
                throw raiseNode.raise(ValueError, CTYPES_OBJECT_STRUCTURE_TOO_DEEP);
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

    static void PyCData_MallocBuffer(CDataObject obj, StgDictObject dict) {
        obj.b_ptr = PtrValue.allocate(dict.ffi_type_pointer, dict.size);
        /*- XXX: (mq) This might not be necessary in our end but will keep it until we fully support ctypes.
            if (dict.size <= sizeof(obj.b_value)) {
                /* No need to call malloc, can use the default buffer * /
                obj.b_ptr.ptr = obj.b_value;
                /*
                 * The b_needsfree flag does not mean that we actually did call PyMem_Malloc to allocate
                 * the memory block; instead it means we are the *owner* of the memory and are
                 * responsible for freeing resources associated with the memory. This is also the reason
                 * that b_needsfree is exposed to Python.
                 * /
                obj.b_needsfree = 1;
            } else {
                /*
                 * In python 2.4, and ctypes 0.9.6, the malloc call took about 33% of the creation time
                 * for c_int().
                 * /
                obj.b_ptr = (char *)PyMem_Malloc(dict.size);
                obj.b_needsfree = 1;
                memset(obj.b_ptr, 0, dict.size);
            }
        */
        obj.b_size = dict.size;
    }

    static CDataObject PyCData_FromBaseObj(Object type, Object base, int index, PtrValue adr,
                    PythonObjectFactory factory,
                    PRaiseNode raiseNode,
                    PyTypeStgDictNode pyTypeStgDictNode) {
        // assert(PyType_Check(type));
        StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(type, raiseNode);
        dict.flags |= DICTFLAG_FINAL;
        CDataObject cmem = factory.createCDataObject(type);

        cmem.b_length = dict.length;
        cmem.b_size = dict.size;
        if (base != null) { /* use base's buffer */
            cmem.b_ptr = adr;
            cmem.b_needsfree = 0;
            cmem.b_base = (CDataObject) base;
        } else { /* copy contents of adr */
            PyCData_MallocBuffer(cmem, dict);
            // memcpy(cmem.b_ptr, adr, dict.size); TODO
            cmem.b_ptr = adr;
        }
        cmem.b_index = index;
        return cmem;
    }

    // corresponds to GenericPyCData_new
    protected static CDataObject GenericPyCDataNew(StgDictObject dict, CDataObject obj) {
        dict.flags |= DICTFLAG_FINAL;
        obj.b_base = null;
        obj.b_index = 0;
        obj.b_objects = null;
        obj.b_length = dict.length;
        PyCData_MallocBuffer(obj, dict);
        return obj;
    }
}
