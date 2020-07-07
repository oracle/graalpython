/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBytes;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PString;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.INT32;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_CONTEXT_TO_NATIVE;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyAsPyObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBytesAsString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBytesGetSize;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCheckBuiltinType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyClose;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictGetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictSetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDup;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrOccurred;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrSetString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFloatFromDouble;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyGetAttr;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyGetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyHasAttr;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyIsTrue;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyListAppend;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyListNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyLongAsLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyLongFromLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyLongFromUnsignedLongLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyModuleCreate;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyNumberAdd;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPySetAttr;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPySetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeAsUTF8String;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeFromString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeFromWchar;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public final class GraalHPyContext extends CExtContext implements TruffleObject {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyContext.class);

    /**
     * An enum of the functions currently available in the HPy Context (see {@code public_api.h}).
     */
    enum HPyContextMembers {
        CTX_VERSION("ctx_version"),
        H_NONE("h_None"),
        H_TRUE("h_True"),
        H_FALSE("h_False"),
        H_VALUE_ERROR("h_ValueError"),
        H_TYPE_ERROR("h_TypeError"),
        CTX_MODULE_CREATE("ctx_Module_Create"),
        CTX_DUP("ctx_Dup"),
        CTX_CLOSE("ctx_Close"),
        CTX_LONG_FROMLONG("ctx_Long_FromLong"),
        CTX_LONG_FROMLONGLONG("ctx_Long_FromLongLong"),
        CTX_LONG_FROM_UNSIGNEDLONGLONG("ctx_Long_FromUnsignedLongLong"),
        CTX_LONG_ASLONG("ctx_Long_AsLong"),
        CTX_FLOAT_FROMDOUBLE("ctx_Float_FromDouble"),
        CTX_NUMBER_ADD("ctx_Number_Add"),
        CTX_ERR_SETSTRING("ctx_Err_SetString"),
        CTX_ERR_OCCURRED("ctx_Err_Occurred"),
        CTX_OBJECT_ISTRUE("ctx_Object_IsTrue"),
        CTX_GETATTR("ctx_GetAttr"),
        CTX_GETATTR_S("ctx_GetAttr_s"),
        CTX_HASATTR("ctx_HasAttr"),
        CTX_HASATTR_S("ctx_HasAttr_s"),
        CTX_SETATTR("ctx_SetAttr"),
        CTX_SETATTR_S("ctx_SetAttr_s"),
        CTX_GETITEM("ctx_GetItem"),
        CTX_GETITEM_I("ctx_GetItem_i"),
        CTX_GETITEM_S("ctx_GetItem_s"),
        CTX_SETITEM("ctx_SetItem"),
        CTX_SETITEM_I("ctx_SetItem_i"),
        CTX_SETITEM_S("ctx_SetItem_s"),
        CTX_BYTES_CHECK("ctx_Bytes_Check"),
        CTX_BYTES_SIZE("ctx_Bytes_Size"),
        CTX_BYTES_GET_SIZE("ctx_Bytes_GET_SIZE"),
        CTX_BYTES_ASSTRING("ctx_Bytes_AsString"),
        CTX_BYTES_AS_STRING("ctx_Bytes_AS_STRING"),
        CTX_UNICODE_FROMSTRING("ctx_Unicode_FromString"),
        CTX_UNICODE_CHECK("ctx_Unicode_Check"),
        CTX_UNICODE_ASUTF8STRING("ctx_Unicode_AsUTF8String"),
        CTX_UNICODE_FROMWIDECHAR("ctx_Unicode_FromWideChar"),
        CTX_LIST_NEW("ctx_List_New"),
        CTX_LIST_APPEND("ctx_List_Append"),
        CTX_DICT_NEW("ctx_Dict_New"),
        CTX_DICT_SETITEM("ctx_Dict_SetItem"),
        CTX_DICT_GETITEM("ctx_Dict_GetItem"),
        CTX_FROMPYOBJECT("ctx_FromPyObject"),
        CTX_ASPYOBJECT("ctx_AsPyObject"),
        CTX_CALLREALFUNCTIONFROMTRAMPOLINE("ctx_CallRealFunctionFromTrampoline");

        private final String name;

        HPyContextMembers(String name) {
            this.name = name;
        }

        @CompilationFinal(dimensions = 1) private static final String[] values;
        static {
            Field[] declaredFields = HPyContextMembers.class.getDeclaredFields();
            values = new String[declaredFields.length - 1]; // omit the values field
            for (int i = 0; i < declaredFields.length; i++) {
                Field s = declaredFields[i];
                if (s.getType() == HPyContextMembers.class) {
                    try {
                        HPyContextMembers member = ((HPyContextMembers) s.get(HPyContextMembers.class));
                        values[member.ordinal()] = member.name;
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                    }
                }
            }
        }

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        public static boolean isValid(String name) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(name)) {
                    return true;
                }
            }
            return false;
        }

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        public static int getOrdinal(String name) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(name)) {
                    return i;
                }
            }
            return -1;
        }
    }

    private GraalHPyHandle[] hpyHandleTable = new GraalHPyHandle[]{GraalHPyHandle.NULL_HANDLE};
    private final HandleStack freeStack = new HandleStack(16);
    Object nativePointer;

    @CompilationFinal(dimensions = 1) private final Object[] hpyContextMembers;
    @CompilationFinal private GraalHPyHandle hpyNullHandle;

    /** the native type ID of C struct 'HPyContext' */
    @CompilationFinal private Object hpyContextNativeTypeID;

    /** the native type ID of C struct 'HPy' */
    @CompilationFinal private Object hpyNativeTypeID;
    @CompilationFinal private Object hpyArrayNativeTypeID;
    @CompilationFinal private long wcharSize = -1;

    public GraalHPyContext(PythonContext context, Object hpyLibrary) {
        super(context, hpyLibrary, GraalHPyConversionNodeSupplier.INSTANCE);
        this.hpyContextMembers = createMembers(context);
    }

    void setHPyContextNativeType(Object nativeType) {
        this.hpyContextNativeTypeID = nativeType;
    }

    void setHPyNativeType(Object hpyNativeTypeID) {
        assert this.hpyNativeTypeID == null : "setting HPy native type ID a second time";
        this.hpyNativeTypeID = hpyNativeTypeID;
    }

    public Object getHPyNativeType() {
        assert this.hpyNativeTypeID != null : "HPy native type ID not available";
        return hpyNativeTypeID;
    }

    void setHPyArrayNativeType(Object hpyArrayNativeTypeID) {
        assert this.hpyArrayNativeTypeID == null : "setting HPy* native type ID a second time";
        this.hpyArrayNativeTypeID = hpyArrayNativeTypeID;
    }

    public Object getHPyArrayNativeType() {
        assert this.hpyArrayNativeTypeID != null : "HPy* native type ID not available";
        return hpyArrayNativeTypeID;
    }

    void setWcharSize(long wcharSize) {
        assert this.wcharSize == -1 : "setting wchar size a second time";
        this.wcharSize = wcharSize;
    }

    public long getWcharSize() {
        assert this.wcharSize >= 0 : "wchar size is not available";
        return wcharSize;
    }

    @ExportMessage
    boolean isPointer() {
        return nativePointer != null;
    }

    @ExportMessage(limit = "1")
    long asPointer(
                    @CachedLibrary("this.nativePointer") InteropLibrary interopLibrary) throws UnsupportedMessageException {
        if (isPointer()) {
            return interopLibrary.asPointer(nativePointer);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    void toNative(
                    @Cached PCallHPyFunction callContextToNativeNode) {
        if (!isPointer()) {
            nativePointer = callContextToNativeNode.call(this, GRAAL_HPY_CONTEXT_TO_NATIVE, this);
        }
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new PythonAbstractObject.Keys(HPyContextMembers.values);
    }

    @ExportMessage
    boolean isMemberReadable(String key) {
        return HPyContextMembers.isValid(key);
    }

    @ExportMessage
    Object readMember(String key,
                    @Cached GraalHPyReadMemberNode readMemberNode) {
        Object result = readMemberNode.execute(this, key);
        if (result == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException(String.format("context function %s not yet implemented: ", key));
        }
        return result;
    }

    @ExportMessage
    boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    Object getNativeType() {
        return hpyContextNativeTypeID;
    }

    @GenerateUncached
    @ImportStatic(HPyContextMembers.class)
    abstract static class GraalHPyReadMemberNode extends Node {

        public abstract Object execute(GraalHPyContext hpyContext, String key);

        @Specialization(guards = "cachedKey.equals(key)")
        static Object doMember(GraalHPyContext hpyContext, @SuppressWarnings("unused") String key,
                        @Cached(value = "key", allowUncached = true) @SuppressWarnings("unused") String cachedKey,
                        @Cached(value = "getIndex(key)", allowUncached = true) int cachedIdx) {
            // TODO(fa) once everything is implemented, remove this check
            if (cachedIdx != -1) {
                return hpyContext.hpyContextMembers[cachedIdx];
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("member not yet implemented");
        }

        static int getIndex(String key) {
            return HPyContextMembers.getOrdinal(key);
        }

    }

    private static Object[] createMembers(PythonContext context) {
        Object[] members = new Object[HPyContextMembers.values().length];
        members[HPyContextMembers.H_NONE.ordinal()] = new GraalHPyHandle(PNone.NONE);
        members[HPyContextMembers.H_TRUE.ordinal()] = new GraalHPyHandle(context.getCore().getTrue());
        members[HPyContextMembers.H_FALSE.ordinal()] = new GraalHPyHandle(context.getCore().getFalse());
        members[HPyContextMembers.H_VALUE_ERROR.ordinal()] = new GraalHPyHandle(context.getCore().lookupType(PythonBuiltinClassType.ValueError));
        members[HPyContextMembers.H_TYPE_ERROR.ordinal()] = new GraalHPyHandle(context.getCore().lookupType(PythonBuiltinClassType.TypeError));
        members[HPyContextMembers.CTX_ASPYOBJECT.ordinal()] = new GraalHPyAsPyObject();
        members[HPyContextMembers.CTX_DUP.ordinal()] = new GraalHPyDup();
        members[HPyContextMembers.CTX_CLOSE.ordinal()] = new GraalHPyClose();
        members[HPyContextMembers.CTX_MODULE_CREATE.ordinal()] = new GraalHPyModuleCreate();
        members[HPyContextMembers.CTX_LONG_FROMLONG.ordinal()] = new GraalHPyLongFromLong();
        members[HPyContextMembers.CTX_LONG_FROMLONGLONG.ordinal()] = new GraalHPyLongFromLong();
        members[HPyContextMembers.CTX_LONG_FROM_UNSIGNEDLONGLONG.ordinal()] = new GraalHPyLongFromUnsignedLongLong();
        members[HPyContextMembers.CTX_LONG_ASLONG.ordinal()] = new GraalHPyLongAsLong();
        members[HPyContextMembers.CTX_NUMBER_ADD.ordinal()] = new GraalHPyNumberAdd();
        members[HPyContextMembers.CTX_DICT_NEW.ordinal()] = new GraalHPyDictNew();
        members[HPyContextMembers.CTX_DICT_SETITEM.ordinal()] = new GraalHPyDictSetItem();
        members[HPyContextMembers.CTX_DICT_GETITEM.ordinal()] = new GraalHPyDictGetItem();
        members[HPyContextMembers.CTX_LIST_NEW.ordinal()] = new GraalHPyListNew();
        members[HPyContextMembers.CTX_LIST_APPEND.ordinal()] = new GraalHPyListAppend();
        members[HPyContextMembers.CTX_FLOAT_FROMDOUBLE.ordinal()] = new GraalHPyFloatFromDouble();
        members[HPyContextMembers.CTX_BYTES_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PBytes);
        members[HPyContextMembers.CTX_BYTES_GET_SIZE.ordinal()] = new GraalHPyBytesGetSize();
        members[HPyContextMembers.CTX_BYTES_SIZE.ordinal()] = new GraalHPyBytesGetSize();
        members[HPyContextMembers.CTX_BYTES_AS_STRING.ordinal()] = new GraalHPyBytesAsString();
        members[HPyContextMembers.CTX_BYTES_ASSTRING.ordinal()] = new GraalHPyBytesAsString();
        members[HPyContextMembers.CTX_ERR_SETSTRING.ordinal()] = new GraalHPyErrSetString();
        members[HPyContextMembers.CTX_ERR_OCCURRED.ordinal()] = new GraalHPyErrOccurred();
        members[HPyContextMembers.CTX_UNICODE_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PString);
        members[HPyContextMembers.CTX_OBJECT_ISTRUE.ordinal()] = new GraalHPyIsTrue();
        members[HPyContextMembers.CTX_UNICODE_ASUTF8STRING.ordinal()] = new GraalHPyUnicodeAsUTF8String();
        members[HPyContextMembers.CTX_UNICODE_FROMSTRING.ordinal()] = new GraalHPyUnicodeFromString();
        members[HPyContextMembers.CTX_UNICODE_FROMWIDECHAR.ordinal()] = new GraalHPyUnicodeFromWchar();
        members[HPyContextMembers.CTX_GETATTR.ordinal()] = new GraalHPyGetAttr(OBJECT);
        members[HPyContextMembers.CTX_GETATTR_S.ordinal()] = new GraalHPyGetAttr(CHAR_PTR);
        members[HPyContextMembers.CTX_HASATTR.ordinal()] = new GraalHPyHasAttr(OBJECT);
        members[HPyContextMembers.CTX_HASATTR_S.ordinal()] = new GraalHPyHasAttr(CHAR_PTR);
        members[HPyContextMembers.CTX_SETATTR.ordinal()] = new GraalHPySetAttr(OBJECT);
        members[HPyContextMembers.CTX_SETATTR_S.ordinal()] = new GraalHPySetAttr(CHAR_PTR);
        members[HPyContextMembers.CTX_GETITEM.ordinal()] = new GraalHPyGetItem(OBJECT);
        members[HPyContextMembers.CTX_GETITEM_S.ordinal()] = new GraalHPyGetItem(CHAR_PTR);
        members[HPyContextMembers.CTX_GETITEM_I.ordinal()] = new GraalHPyGetItem(INT32);
        members[HPyContextMembers.CTX_SETITEM.ordinal()] = new GraalHPySetItem(OBJECT);
        members[HPyContextMembers.CTX_SETITEM_S.ordinal()] = new GraalHPySetItem(CHAR_PTR);
        members[HPyContextMembers.CTX_SETITEM_I.ordinal()] = new GraalHPySetItem(INT32);
        return members;
    }

    @TruffleBoundary(allowInlining = true)
    private int allocateHandle() {
        int freeItem = freeStack.pop();
        if (freeItem != -1) {
            assert 0 <= freeItem && freeItem < hpyHandleTable.length;
            assert hpyHandleTable[freeItem] == null;
            return freeItem;
        }
        for (int i = 1; i < hpyHandleTable.length; i++) {
            if (hpyHandleTable[i] == null) {
                return i;
            }
        }
        return -1;
    }

    public int getHPyHandleForObject(GraalHPyHandle object) {
        // find free association
        int handle = allocateHandle();
        if (handle == -1) {
            // resize
            int newSize = Math.max(16, hpyHandleTable.length * 2);
            LOGGER.fine(() -> "resizing HPy handle table to " + newSize);
            hpyHandleTable = Arrays.copyOf(hpyHandleTable, newSize);
            handle = allocateHandle();
        }
        assert handle > 0;
        hpyHandleTable[handle] = object;
        if (LOGGER.isLoggable(Level.FINER)) {
            final int handleID = handle;
            LOGGER.finer(() -> String.format("allocating HPy handle %d (object: %s)", handleID, object));
        }
        return handle;
    }

    public GraalHPyHandle getObjectForHPyHandle(int handle) {
        // find free association
        return hpyHandleTable[handle];
    }

    public void releaseHPyHandleForObject(int handle) {
        assert hpyHandleTable[handle] != null : "releasing handle that has already been released: " + handle;
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer(() -> "releasing HPy handle " + handle);
        }
        hpyHandleTable[handle] = null;
        freeStack.push(handle);
    }

    // nb. keep in sync with 'meth.h'
    private static final int HPy_METH = 0x100000;

    // These methods could be static but they are deliberately implemented as member methods because
    // we may fetch the constants from the native library at initialization time.

    public boolean isHPyMeth(int flags) {
        return (flags & HPy_METH) != 0;
    }

    void setNullHandle(GraalHPyHandle hpyNullHandle) {
        this.hpyNullHandle = hpyNullHandle;
    }

    public GraalHPyHandle getNullHandle() {
        return hpyNullHandle;
    }

    private static final class HandleStack {
        private int[] handles;
        private int top = 0;

        public HandleStack(int initialCapacity) {
            handles = new int[initialCapacity];
        }

        void push(int i) {
            if (top >= handles.length) {
                handles = Arrays.copyOf(handles, handles.length * 2);
            }
            handles[top++] = i;
        }

        int pop() {
            if (top <= 0) {
                return -1;
            }
            return handles[--top];
        }

    }
}
