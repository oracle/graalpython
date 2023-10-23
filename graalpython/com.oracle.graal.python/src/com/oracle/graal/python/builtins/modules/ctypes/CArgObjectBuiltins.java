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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerNodes;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CArgObject)
public final class CArgObjectBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CArgObjectBuiltinsFactory.getFactories();
    }

    @Builtin(name = "_obj", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ObjNode extends PythonUnaryBuiltinNode {
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

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        TruffleString doit(PyCArgObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached PointerNodes.ReadByteNode readByteNode) {
            String ret;
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
                    ret = PythonUtils.formatJString("<cparam '%c' (%d)>", self.tag, self.valuePointer);
                    break;
                case 'd':
                case 'f': {
                    ret = PythonUtils.formatJString("<cparam '%c' (%f)>", self.tag, self.valuePointer);
                    break;
                }
                case 'c':
                    byte val = readByteNode.execute(inliningTarget, self.valuePointer);
                    if (isLiteralChar((char) val)) {
                        ret = PythonUtils.formatJString("<cparam '%c' ('%c')>", self.tag, self.valuePointer);
                    } else {
                        ret = PythonUtils.formatJString("<cparam '%c' ('\\x%02x')>", self.tag, PythonAbstractObject.systemHashCode(self.valuePointer));
                    }
                    break;
                case 'z':
                case 'Z':
                case 'P':
                    ret = PythonUtils.formatJString("<cparam '%c' 0x%x>", self.tag, PythonAbstractObject.systemHashCode(self.valuePointer));
                    break;
                default:
                    if (isLiteralChar(self.tag)) {
                        ret = PythonUtils.formatJString("<cparam '%c' at 0x%x>", self.tag, PythonAbstractObject.systemHashCode(self));
                    } else {
                        ret = PythonUtils.formatJString("<cparam 0x%02x at 0x%x>", self.tag, PythonAbstractObject.systemHashCode(self));
                    }
            }
            return fromJavaStringNode.execute(ret, TS_ENCODING);
        }
    }

    protected static final int PyCArrayTypeParamFunc = 1;
    protected static final int PyCFuncPtrTypeParamFunc = 2;
    protected static final int PyCPointerTypeParamFunc = 4;
    protected static final int PyCSimpleTypeParamFunc = 8;
    protected static final int StructUnionTypeParamFunc = 16;

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class ParamFuncNode extends Node {
        abstract PyCArgObject execute(Node inliningTarget, CDataObject self, StgDictObject stgDict);

        @Specialization
        static PyCArgObject paramFunc(CDataObject self, StgDictObject stgDict,
                        @Cached(inline = false) PythonObjectFactory factory,
                        @Cached(inline = false) TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
            PyCArgObject parg = factory.createCArgObject();
            switch (stgDict.paramfunc) {
                // Corresponds to PyCArrayType_paramfunc
                case PyCArrayTypeParamFunc -> {
                    parg.tag = 'P';
                    parg.pffi_type = FFIType.ffi_type_pointer;
                    parg.valuePointer = self.b_ptr.createReference();
                    parg.obj = self;
                    return parg;
                }
                // Corresponds to PyCFuncPtrType_paramfunc and PyCPointerType_paramfunc
                case PyCFuncPtrTypeParamFunc, PyCPointerTypeParamFunc -> {
                    parg.tag = 'P';
                    parg.pffi_type = FFIType.ffi_type_pointer;
                    parg.obj = self;
                    parg.valuePointer = self.b_ptr;
                    return parg;
                }
                // Corresponds to PyCSimpleType_paramfunc
                case PyCSimpleTypeParamFunc -> {
                    TruffleString fmt = (TruffleString) stgDict.proto;
                    assert fmt != null;
                    char code = (char) codePointAtIndexNode.execute(fmt, 0, TS_ENCODING);
                    FieldDesc fd = FFIType._ctypes_get_fielddesc(code);
                    assert fd != null;
                    parg.tag = code;
                    parg.pffi_type = fd.pffi_type;
                    parg.obj = self;
                    parg.valuePointer = self.b_ptr;
                    return parg;
                }
                // Corresponds to StructUnionType_paramfunc
                case StructUnionTypeParamFunc -> {
                    Pointer ptr = self.b_ptr;
                    parg.pffi_type = stgDict.ffi_type_pointer;
                    parg.tag = 'V';
                    parg.valuePointer = ptr;
                    parg.size = self.b_size;
                    parg.obj = self;
                    return parg;
                }
                default -> throw CompilerDirectives.shouldNotReachHere("Unknown function parameter");
            }
        }
    }
}
