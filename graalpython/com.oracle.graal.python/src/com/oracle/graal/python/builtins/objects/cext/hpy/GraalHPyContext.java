/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyClose;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDup;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;

@ExportLibrary(InteropLibrary.class)
public final class GraalHPyContext implements TruffleObject {

    /**
     * int ctx_version; HPy h_None; HPy h_True; HPy h_False; HPy h_ValueError; HPy h_TypeError; HPy
     * (*ctx_Module_Create)(HPyContext ctx, HPyModuleDef *def); HPy (*ctx_Dup)(HPyContext ctx, HPy
     * h); void (*ctx_Close)(HPyContext ctx, HPy h); HPy (*ctx_Long_FromLong)(HPyContext ctx, long
     * value); HPy (*ctx_Long_FromLongLong)(HPyContext ctx, long long v); HPy
     * (*ctx_Long_FromUnsignedLongLong)(HPyContext ctx, unsigned long long v); long
     * (*ctx_Long_AsLong)(HPyContext ctx, HPy h); HPy (*ctx_Float_FromDouble)(HPyContext ctx, double
     * v); int (*ctx_Arg_Parse)(HPyContext ctx, HPy *args, HPy_ssize_t nargs, const char *fmt,
     * va_list _vl); HPy (*ctx_Number_Add)(HPyContext ctx, HPy h1, HPy h2); void
     * (*ctx_Err_SetString)(HPyContext ctx, HPy h_type, const char *message); int
     * (*ctx_Bytes_Check)(HPyContext ctx, HPy h); HPy_ssize_t (*ctx_Bytes_Size)(HPyContext ctx, HPy
     * h); HPy_ssize_t (*ctx_Bytes_GET_SIZE)(HPyContext ctx, HPy h); char
     * *(*ctx_Bytes_AsString)(HPyContext ctx, HPy h); char *(*ctx_Bytes_AS_STRING)(HPyContext ctx,
     * HPy h); HPy (*ctx_Unicode_FromString)(HPyContext ctx, const char *utf8); int
     * (*ctx_Unicode_Check)(HPyContext ctx, HPy h); HPy (*ctx_Unicode_AsUTF8String)(HPyContext ctx,
     * HPy h); HPy (*ctx_Unicode_FromWideChar)(HPyContext ctx, const wchar_t *w, HPy_ssize_t size);
     * HPy (*ctx_List_New)(HPyContext ctx, HPy_ssize_t len); int (*ctx_List_Append)(HPyContext ctx,
     * HPy h_list, HPy h_item); HPy (*ctx_Dict_New)(HPyContext ctx); int
     * (*ctx_Dict_SetItem)(HPyContext ctx, HPy h_dict, HPy h_key, HPy h_val); HPy
     * (*ctx_FromPyObject)(HPyContext ctx, struct _object *obj); struct _object
     * *(*ctx_AsPyObject)(HPyContext ctx, HPy h); struct _object
     * *(*ctx_CallRealFunctionFromTrampoline)(HPyContext ctx, struct _object *self, struct _object
     * *args, void *func, int ml_flags);
     */
    public enum HPyContextMembers {

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
        CTX_ARG_PARSE("ctx_Arg_Parse"),
        CTX_NUMBER_ADD("ctx_Number_Add"),
        CTX_ERR_SETSTRING("ctx_Err_SetString"),
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
                if (s.getType() == String.class) {
                    try {
                        values[i] = ((HPyContextMembers) s.get(HPyContextMembers.class)).name;
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
    }

    private Object[] hpyHandleTable = new Object[0];
    private final PythonContext context;
    @CompilationFinal(dimensions = 1) private final Object[] hpyContextMembers;

    public GraalHPyContext(PythonContext context) {
        this.context = context;
        this.hpyContextMembers = createMembers(context);
    }

    public PythonContext getContext() {
        return context;
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
        return readMemberNode.execute(this, key);

    }

    @GenerateUncached
    @ImportStatic(HPyContextMembers.class)
    abstract static class GraalHPyReadMemberNode extends Node {

        public abstract Object execute(GraalHPyContext hpyContext, String key);

        @Specialization(guards = "cachedKey.equals(key)")
        static Object doMember(GraalHPyContext hpyContext, @SuppressWarnings("unused") String key,
                        @Cached(value = "key", allowUncached = true) @SuppressWarnings("unused") String cachedKey,
                        @Cached(value = "getIndex(key)", allowUncached = true) int cachedIdx) {
            return hpyContext.hpyContextMembers[cachedIdx];
        }

        static int getIndex(String key) {
            return HPyContextMembers.valueOf(key).ordinal();
        }

    }

    private static Object[] createMembers(PythonContext context) {
        Object[] members = new Object[HPyContextMembers.values().length];
        members[HPyContextMembers.H_NONE.ordinal()] = new GraalHPyHandle(PNone.NONE);
        members[HPyContextMembers.H_TRUE.ordinal()] = new GraalHPyHandle(context.getCore().getTrue());
        members[HPyContextMembers.H_FALSE.ordinal()] = new GraalHPyHandle(context.getCore().getFalse());
        members[HPyContextMembers.H_VALUE_ERROR.ordinal()] = new GraalHPyHandle(context.getCore().lookupType(PythonBuiltinClassType.ValueError));
        members[HPyContextMembers.H_TYPE_ERROR.ordinal()] = new GraalHPyHandle(context.getCore().lookupType(PythonBuiltinClassType.TypeError));
        members[HPyContextMembers.CTX_DUP.ordinal()] = new GraalHPyDup();
        members[HPyContextMembers.CTX_CLOSE.ordinal()] = new GraalHPyClose();
        return members;
    }


    private int allocateHandle(Object object) {
        for(int i=0; i < hpyHandleTable.length; i++) {
            if(hpyHandleTable[i] != null) {
                hpyHandleTable[i] = object;
                // TODO(fa) log new handle allocation
                return i;
            }
        }
        return -1;
    }

    public int getHPyHandleForObject(Object object) {
        // find free association
        int handle = allocateHandle(object);
        if(handle == -1) {
            // resize
            hpyHandleTable = Arrays.copyOf(hpyHandleTable, Math.max(16, hpyHandleTable.length * 2));
            // TODO(fa) log array resize
        }
        handle = allocateHandle(object);
        assert handle != -1;
        return handle;
    }

    public void releaseHPyHandleForObject(int handle) {
        assert hpyHandleTable[handle] != null : "releasing handle that has already been released: " + handle;
        // TODO(fa) log handle dealloc
        hpyHandleTable[handle] = null;
    }

}
