/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
// skip GIL
package com.oracle.graal.python.builtins.objects.cext.hpy;

import static jdk.incubator.foreign.CLinker.C_INT;
import static jdk.incubator.foreign.CLinker.C_LONG;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.ResourceScope;

/**
 * This object is used to override specific native upcall pointers in the HPyContext. This is
 * queried for every member of HPyContext by {@code graal_hpy_context_to_native}, and overrides the
 * original values (which are NFI closures for functions in {@code hpy.c}, subsequently calling into
 * {@link GraalHPyContextFunctions}.
 */
@ExportLibrary(InteropLibrary.class)
public final class GraalHPyJNIContext implements TruffleObject {

    private static final HashMap<Class<?>, MemoryLayout> CLINKER_VALUE_LAYOUTS = new HashMap<>();
    static {
        CLINKER_VALUE_LAYOUTS.put(int.class, C_INT);
        CLINKER_VALUE_LAYOUTS.put(long.class, C_LONG);
        CLINKER_VALUE_LAYOUTS.put(double.class, CLinker.C_DOUBLE);
    }

    private final GraalHPyContext context;

    public GraalHPyJNIContext(GraalHPyContext context) {
        this.context = context;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return HPyContextMember.KEYS;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    @TruffleBoundary
    boolean isMemberReadable(String key) {
        return HPyContextMember.getIndex(key) != -1;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    @TruffleBoundary
    Object readMember(String key) {
        if (MODE != HPyMode.CLINKER) {
            return new HPyContextNativePointer(0l);
        }
        HPyContextMember member = HPyContextMember.MEMBERS.get(key);
        HPyContextSignature signature = member.signature;
        if (signature == null) {
            return new HPyContextNativePointer(0l);
        }
        Class<?>[] params = new Class<?>[signature.parameterTypes.length];
        MemoryLayout[] layouts = new MemoryLayout[params.length + 1];
        layouts[0] = C_LONG; // context parameter
        for (int i = 0; i < params.length; i++) {
            params[i] = signature.parameterTypes[i].jniType;
            layouts[i + 1] = CLINKER_VALUE_LAYOUTS.get(params[i]);
        }
        String javaName = member.name.replace("_", ""); // remove "_"
        try {
            Class<?> ret = signature.returnType.jniType;
            MethodHandle handle = MethodHandles.lookup().bind(context, javaName, MethodType.methodType(ret, params));
            // drop "HPyContext" argument (this is already available with the bound receiver)
            handle = MethodHandles.dropArguments(handle, 0, long.class);

            FunctionDescriptor desc = ret == void.class ? FunctionDescriptor.ofVoid(layouts) : FunctionDescriptor.of(CLINKER_VALUE_LAYOUTS.get(ret), layouts);

            System.out.println("linking CLINKER " + key);
            return new HPyContextNativePointer(CLinker.getInstance().upcallStub(handle, desc, ResourceScope.globalScope()).address().toRawLongValue());
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
