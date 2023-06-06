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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.nodes.SpecialMethodNames.T_RICHCMP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ILSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INVERT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ISUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___XOR__;

import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.BinaryFuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.DescrGetFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.GetAttrWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.HashfuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.InitWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.InquiryWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.LenfuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.RichcmpFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.SetAttrWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.SsizeargfuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.TernaryFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.UnaryFuncWrapper;
import com.oracle.graal.python.builtins.objects.type.MethodsFlags;
import com.oracle.graal.python.util.Function;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.strings.TruffleString;

public enum SlotMethodDef {
    TP_CALL(T___CALL__, TernaryFunctionWrapper::new),
    TP_GETATTRO(T___GETATTRIBUTE__, GetAttrWrapper::new),
    TP_HASH(T___HASH__, HashfuncWrapper::new),
    TP_INIT(T___INIT__, InitWrapper::new),
    TP_ITER(T___ITER__, UnaryFuncWrapper::new),
    TP_ITERNEXT(T___NEXT__, UnaryFuncWrapper::new),
    TP_REPR(T___REPR__, UnaryFuncWrapper::new),
    TP_RICHCOMPARE(T_RICHCMP, RichcmpFunctionWrapper::new),
    TP_SETATTRO(T___SETATTR__, SetAttrWrapper::new),
    TP_STR(T___STR__, UnaryFuncWrapper::new),
    TP_DESCR_GET(T___GET__, DescrGetFunctionWrapper::new),
    TP_DESCR_SET(T___SET__, SetAttrWrapper::new),

    MP_LENGTH(T___LEN__, LenfuncWrapper::new, MethodsFlags.MP_LENGTH),
    MP_SUBSCRIPT(T___GETITEM__, BinaryFuncWrapper::new, MethodsFlags.MP_SUBSCRIPT),
    MP_ASS_SUBSCRIPT(T___SETITEM__, SetAttrWrapper::new, MethodsFlags.MP_ASS_SUBSCRIPT),

    SQ_LENGTH(T___LEN__, LenfuncWrapper::new, MethodsFlags.SQ_LENGTH),
    SQ_ITEM(T___GETITEM__, SsizeargfuncWrapper::new, MethodsFlags.SQ_ITEM),
    SQ_REPEAT(T___MUL__, SsizeargfuncWrapper::new, MethodsFlags.SQ_REPEAT),
    SQ_CONCAT(T___ADD__, BinaryFuncWrapper::new, MethodsFlags.SQ_CONCAT),

    NB_ABSOLUTE(T___ABS__, UnaryFuncWrapper::new, MethodsFlags.NB_ABSOLUTE),
    NB_ADD(T___ADD__, BinaryFuncWrapper::new, MethodsFlags.NB_ADD),
    NB_AND(T___AND__, BinaryFuncWrapper::new, MethodsFlags.NB_AND),
    NB_BOOL(T___BOOL__, InquiryWrapper::new, MethodsFlags.NB_BOOL),
    NB_DIVMOD(T___DIVMOD__, BinaryFuncWrapper::new, MethodsFlags.NB_DIVMOD),
    NB_FLOAT(T___FLOAT__, UnaryFuncWrapper::new, MethodsFlags.NB_FLOAT),
    NB_FLOOR_DIVIDE(T___FLOORDIV__, BinaryFuncWrapper::new, MethodsFlags.NB_FLOOR_DIVIDE),
    NB_INDEX(T___INDEX__, UnaryFuncWrapper::new, MethodsFlags.NB_INDEX),
    NB_INPLACE_ADD(T___IADD__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_ADD),
    NB_INPLACE_AND(T___IAND__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_AND),
    NB_INPLACE_FLOOR_DIVIDE(T___IFLOORDIV__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_FLOOR_DIVIDE),
    NB_INPLACE_LSHIFT(T___ILSHIFT__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_LSHIFT),
    NB_INPLACE_MULTIPLY(T___IMUL__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_MULTIPLY),
    NB_INPLACE_OR(T___IOR__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_OR),
    NB_INPLACE_POWER(T___IPOW__, TernaryFunctionWrapper::new, MethodsFlags.NB_INPLACE_POWER),
    NB_INPLACE_REMAINDER(T___IMOD__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_REMAINDER),
    NB_INPLACE_RSHIFT(T___IRSHIFT__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_RSHIFT),
    NB_INPLACE_SUBTRACT(T___ISUB__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_SUBTRACT),
    NB_INPLACE_TRUE_DIVIDE(T___ITRUEDIV__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_TRUE_DIVIDE),
    NB_INPLACE_XOR(T___IXOR__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_XOR),
    NB_INT(T___INT__, UnaryFuncWrapper::new, MethodsFlags.NB_INT),
    NB_INVERT(T___INVERT__, UnaryFuncWrapper::new, MethodsFlags.NB_INVERT),
    NB_LSHIFT(T___LSHIFT__, BinaryFuncWrapper::new, MethodsFlags.NB_LSHIFT),
    NB_MULTIPLY(T___MUL__, BinaryFuncWrapper::new, MethodsFlags.NB_MULTIPLY),
    NB_NEGATIVE(T___NEG__, UnaryFuncWrapper::new, MethodsFlags.NB_NEGATIVE),
    NB_OR(T___OR__, BinaryFuncWrapper::new, MethodsFlags.NB_OR),
    NB_POSITIVE(T___POS__, UnaryFuncWrapper::new, MethodsFlags.NB_POSITIVE),
    NB_POWER(T___POW__, TernaryFunctionWrapper::new, MethodsFlags.NB_POWER),
    NB_REMAINDER(T___MOD__, BinaryFuncWrapper::new, MethodsFlags.NB_REMAINDER),
    NB_RSHIFT(T___RSHIFT__, BinaryFuncWrapper::new, MethodsFlags.NB_RSHIFT),
    NB_SUBTRACT(T___SUB__, BinaryFuncWrapper::new, MethodsFlags.NB_SUBTRACT),
    NB_TRUE_DIVIDE(T___TRUEDIV__, BinaryFuncWrapper::new, MethodsFlags.NB_TRUE_DIVIDE),
    NB_XOR(T___XOR__, BinaryFuncWrapper::new, MethodsFlags.NB_XOR);

    public final NativeMember nativeMember;
    public final TruffleString methodName;
    public final Function<Object, PyProcsWrapper> wrapperFactory;
    public final long methodFlag;

    SlotMethodDef(TruffleString methodName, Function<Object, PyProcsWrapper> wrapperFactory) {
        this(methodName, wrapperFactory, 0);
    }

    SlotMethodDef(TruffleString methodName, Function<Object, PyProcsWrapper> wrapperFactory, long methodFlag) {
        this.methodName = methodName;
        this.wrapperFactory = wrapperFactory;
        this.nativeMember = NativeMember.valueOf(name());
        this.methodFlag = methodFlag;
    }

    public String getMemberNameJavaString() {
        return nativeMember.getMemberNameJavaString();
    }

    public NativeCAPISymbol getGetter() {
        return nativeMember.getGetterFunctionName();
    }

    public enum SlotGroup {
        AS_SEQUENCE(SQ_LENGTH, SQ_ITEM, SQ_REPEAT, SQ_CONCAT),
        AS_MAPPING(MP_LENGTH, MP_SUBSCRIPT, MP_ASS_SUBSCRIPT),
        AS_NUMBER(
                        NB_ABSOLUTE,
                        NB_ADD,
                        NB_AND,
                        NB_BOOL,
                        NB_DIVMOD,
                        NB_FLOAT,
                        NB_FLOOR_DIVIDE,
                        NB_INDEX,
                        NB_INPLACE_ADD,
                        NB_INPLACE_AND,
                        NB_INPLACE_FLOOR_DIVIDE,
                        NB_INPLACE_LSHIFT,
                        NB_INPLACE_MULTIPLY,
                        NB_INPLACE_OR,
                        NB_INPLACE_POWER,
                        NB_INPLACE_REMAINDER,
                        NB_INPLACE_RSHIFT,
                        NB_INPLACE_SUBTRACT,
                        NB_INPLACE_TRUE_DIVIDE,
                        NB_INPLACE_XOR,
                        NB_INT,
                        NB_INVERT,
                        NB_LSHIFT,
                        NB_MULTIPLY,
                        NB_NEGATIVE,
                        NB_OR,
                        NB_POSITIVE,
                        NB_POWER,
                        NB_REMAINDER,
                        NB_RSHIFT,
                        NB_SUBTRACT,
                        NB_TRUE_DIVIDE,
                        NB_XOR);

        @CompilationFinal(dimensions = 1) public final SlotMethodDef[] slots;

        SlotGroup(SlotMethodDef... slots) {
            this.slots = slots;
        }
    }
}
