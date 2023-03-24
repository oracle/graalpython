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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
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
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.util.Function;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
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

    MP_LENGTH(T___LEN__, LenfuncWrapper::new),
    MP_SUBSCRIPT(T___GETITEM__, BinaryFuncWrapper::new),
    MP_ASS_SUBSCRIPT(T___SETITEM__, SetAttrWrapper::new),

    SQ_LENGTH(T___LEN__, LenfuncWrapper::new),
    SQ_ITEM(T___GETITEM__, SsizeargfuncWrapper::new),
    SQ_REPEAT(T___MUL__, SsizeargfuncWrapper::new),
    SQ_CONCAT(T___ADD__, BinaryFuncWrapper::new),

    NB_ABSOLUTE(T___ABS__, UnaryFuncWrapper::new),
    NB_ADD(T___ADD__, BinaryFuncWrapper::new),
    NB_AND(T___AND__, BinaryFuncWrapper::new),
    NB_BOOL(T___BOOL__, InquiryWrapper::new),
    NB_DIVMOD(T___DIVMOD__, BinaryFuncWrapper::new),
    NB_FLOAT(T___FLOAT__, UnaryFuncWrapper::new),
    NB_FLOOR_DIVIDE(T___FLOORDIV__, BinaryFuncWrapper::new),
    NB_INDEX(T___INDEX__, UnaryFuncWrapper::new),
    NB_INPLACE_ADD(T___IADD__, BinaryFuncWrapper::new),
    NB_INPLACE_AND(T___IAND__, BinaryFuncWrapper::new),
    NB_INPLACE_FLOOR_DIVIDE(T___IFLOORDIV__, BinaryFuncWrapper::new),
    NB_INPLACE_LSHIFT(T___ILSHIFT__, BinaryFuncWrapper::new),
    NB_INPLACE_MULTIPLY(T___IMUL__, BinaryFuncWrapper::new),
    NB_INPLACE_OR(T___IOR__, BinaryFuncWrapper::new),
    NB_INPLACE_POWER(T___IPOW__, TernaryFunctionWrapper::new),
    NB_INPLACE_REMAINDER(T___IMOD__, BinaryFuncWrapper::new),
    NB_INPLACE_RSHIFT(T___IRSHIFT__, BinaryFuncWrapper::new),
    NB_INPLACE_SUBTRACT(T___ISUB__, BinaryFuncWrapper::new),
    NB_INPLACE_TRUE_DIVIDE(T___ITRUEDIV__, BinaryFuncWrapper::new),
    NB_INPLACE_XOR(T___IXOR__, BinaryFuncWrapper::new),
    NB_INT(T___INT__, UnaryFuncWrapper::new),
    NB_INVERT(T___INVERT__, UnaryFuncWrapper::new),
    NB_LSHIFT(T___LSHIFT__, BinaryFuncWrapper::new),
    NB_MULTIPLY(T___MUL__, BinaryFuncWrapper::new),
    NB_NEGATIVE(T___NEG__, UnaryFuncWrapper::new),
    NB_OR(T___OR__, BinaryFuncWrapper::new),
    NB_POSITIVE(T___POS__, UnaryFuncWrapper::new),
    NB_POWER(T___POW__, TernaryFunctionWrapper::new),
    NB_REMAINDER(T___MOD__, BinaryFuncWrapper::new),
    NB_RSHIFT(T___RSHIFT__, BinaryFuncWrapper::new),
    NB_SUBTRACT(T___SUB__, BinaryFuncWrapper::new),
    NB_TRUE_DIVIDE(T___TRUEDIV__, BinaryFuncWrapper::new),
    NB_XOR(T___XOR__, BinaryFuncWrapper::new);

    public final String jMemberName;
    public final TruffleString methodName;
    public final NativeCAPISymbol getter;
    public final Function<Object, Object> wrapperFactory;

    SlotMethodDef(TruffleString methodName, Function<Object, Object> wrapperFactory) {
        this.methodName = methodName;
        this.wrapperFactory = wrapperFactory;
        this.jMemberName = name().toLowerCase();
        this.getter = NativeCAPISymbol.getByName(StringLiterals.J_GET_ + name());
    }

    @TruffleBoundary
    public Object getProcsWrapper(PythonContext context, Object typeOrWrapper) {
        Object type = typeOrWrapper;
        if (typeOrWrapper instanceof PythonNativeWrapper wrapper) {
            type = wrapper.getDelegate();
        }
        MroSequenceStorage mro = GetMroStorageNode.getUncached().execute(type);
        for (int i = 0; i < mro.length(); i++) {
            Object currentType = mro.getItemNormalized(i);
            if (currentType instanceof PythonNativeClass) {
                Object value = PCallCapiFunction.getUncached().call(getter, ToSulongNode.getUncached().execute(currentType));
                if (!InteropLibrary.getUncached().isNull(value)) {
                    return value;
                }
            } else {
                Object value = ReadAttributeFromDynamicObjectNode.getUncached().execute(currentType, methodName);
                if (value != PNone.NO_VALUE) {
                    if (value == PNone.NONE) {
                        return context.getNativeNull().getPtr();
                    }
                    return context.getCApiContext().getOrCreateProcWrapper(this, value);
                }
            }
        }
        return context.getNativeNull().getPtr();
    }
}
