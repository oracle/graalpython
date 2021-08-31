/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StructParam;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.ByteArrayStorage;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CArgObject)
public class CArgObjectBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CArgObjectBuiltinsFactory.getFactories();
    }

    @Builtin(name = "_obj", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ObjNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doit(PyCArgObject self) {
            return self.obj;
        }
    }

    // Corresponds to _PyUnicode_IsPrintable
    static boolean isPrintable(@SuppressWarnings("unused") char c) {
        return true; // TODO
    }

    // Corresponds to is_literal_char
    static boolean isLiteralChar(char c) {
        return c < 128 && isPrintable(c) && c != '\\' && c != '\'';
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object doit(PyCArgObject self) {
            switch (self.tag) {
                case 'b':
                case 'B':
                case 'h':
                case 'H':
                case 'i':
                case 'I':
                case 'l':
                case 'L':
                case 'q': // TODO big int
                case 'Q': // TODO big int
                    return PythonUtils.format("<cparam '%c' (%d)>", self.tag, self.value);
                case 'd':
                case 'f': {
                    return PythonUtils.format("<cparam '%c' (%f)>", self.tag, self.value);
                }
                case 'c':
                    byte[] bytes = ((ByteArrayStorage) self.value.ptr).value;
                    if (isLiteralChar((char) bytes[0])) {
                        return PythonUtils.format("<cparam '%c' ('%c')>", self.tag, self.value);
                    } else {
                        return PythonUtils.format("<cparam '%c' ('\\x%02x')>", self.tag, PythonAbstractObject.systemHashCode(self.value));
                    }

                case 'z':
                case 'Z':
                case 'P':
                    return PythonUtils.format("<cparam '%c' 0x%x>", self.tag, PythonAbstractObject.systemHashCode(self.value));

                default:
                    if (isLiteralChar(self.tag)) {
                        return PythonUtils.format("<cparam '%c' at 0x%x>", self.tag, PythonAbstractObject.systemHashCode(self));
                    } else {
                        return PythonUtils.format("<cparam 0x%02x at 0x%x>", self.tag, PythonAbstractObject.systemHashCode(self));
                    }
            }
        }
    }

    protected static final int PyCArrayTypeParamFunc = 1;
    protected static final int PyCFuncPtrTypeParamFunc = 2;
    protected static final int PyCPointerTypeParamFunc = 4;
    protected static final int PyCSimpleTypeParamFunc = 8;
    protected static final int StructUnionTypeParamFunc = 16;

    protected static PyCArgObject paramFunc(int f, CDataObject self, StgDictObject stgDict, PythonObjectFactory factory) {
        PyCArgObject parg = factory.createCArgObject();
        switch (f) {
            case PyCArrayTypeParamFunc: // Corresponds to PyCArrayType_paramfunc
                parg.tag = 'P';
                // parg.pffi_type = FFIType.ffi_type_pointer;
                parg.pffi_type = stgDict.ffi_type_pointer;
                parg.value = self.b_ptr;
                parg.obj = self;
                return parg;
            case PyCFuncPtrTypeParamFunc: // Corresponds to PyCFuncPtrType_paramfunc
            case PyCPointerTypeParamFunc: // Corresponds to PyCPointerType_paramfunc
                parg.tag = 'P';
                // parg.pffi_type = ffi_type_pointer;
                parg.pffi_type = stgDict.ffi_type_pointer;
                parg.obj = self;
                parg.value = self.b_ptr;
                return parg;
            case PyCSimpleTypeParamFunc: // Corresponds to PyCSimpleType_paramfunc
                assert stgDict != null : "Cannot be NULL for CDataObject instances";
                String fmt = (String) stgDict.proto;
                assert fmt != null;

                FieldDesc fd = FFIType._ctypes_get_fielddesc(PString.charAt(fmt, 0));
                assert fd != null;

                parg.tag = PString.charAt(fmt, 0);
                parg.pffi_type = fd.pffi_type;
                parg.obj = self;
                parg.value = self.b_ptr.copy(); // memcpy(parg.value, self.b_ptr, self.b_size);
                return parg;
            case StructUnionTypeParamFunc: // Corresponds to StructUnionType_paramfunc
                /*
                 * PyCStructType_Type - a meta type/class. Creating a new class using this one as
                 * __metaclass__ will call the constructor StructUnionType_new. It replaces the
                 * tp_dict member with a new instance of StgDict, and initializes the C accessible
                 * fields somehow.
                 */
                PtrValue ptr = self.b_ptr;
                Object obj = self;
                if (self.b_size > StgDictObject.VOID_PTR_SIZE) {
                    // ptr = PyMem_Malloc(self.b_size); TODO
                    // memcpy(ptr, self.b_ptr, self.b_size); TODO

                    /*
                     * Create a Python object which calls PyMem_Free(ptr) in its deallocator. The
                     * object will be destroyed at _ctypes_callproc() cleanup.
                     */
                    StructParamObject struct_param = factory.createStructParamObject(StructParam);
                    obj = struct_param;
                    struct_param.ptr = ptr;
                }

                assert stgDict != null : "Cannot be NULL for structure/union instances";
                parg.pffi_type = stgDict.ffi_type_pointer;
                parg.tag = 'V';
                parg.value = ptr;
                parg.size = self.b_size;
                parg.obj = obj;
                return parg;
        }
        throw CompilerDirectives.shouldNotReachHere("Unknown function parameter");
    }
}
