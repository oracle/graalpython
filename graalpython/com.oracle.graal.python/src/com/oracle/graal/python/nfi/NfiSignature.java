/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nfi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

public final class NfiSignature {

    private final NfiType resType;
    private final NfiType[] argTypes;
    private MethodHandle downcallMethodHandle;
    private FunctionDescriptor functionDescriptor;

    NfiSignature(NfiType resType, NfiType[] argTypes) {
        this.resType = resType;
        this.argTypes = argTypes;
    }

    NfiType getResType() {
        return resType;
    }

    NfiType[] getArgTypes() {
        return argTypes;
    }

    @Override
    public String toString() {
        return Stream.of(argTypes).map(NfiType::toString).collect(Collectors.joining(", ", "(", ")")) + ": " + resType;
    }

    public NfiBoundFunction bind(long pointer) {
        return new NfiBoundFunction(pointer, getDowncallMethodHandle().bindTo(MemorySegment.ofAddress(pointer)), this);
    }

    Object[] convertArgs(Object[] args) throws ArityException, UnsupportedTypeException {
        if (args.length != argTypes.length) {
            throw ArityException.create(argTypes.length, argTypes.length, args.length);
        }
        Object[] convertedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            convertedArgs[i] = argTypes[i].convertArg(args[i]);
        }
        return convertedArgs;
    }

    public Object invokeUncached(long function, Object... args) throws ArityException, UnsupportedTypeException {
        try {
            Object r = getDowncallMethodHandle().invokeExact(MemorySegment.ofAddress(function), convertArgs(args));
            if (resType == NfiType.POINTER) {
                // TODO(NFI2) migrate to RAWPOINTER and remove this wrapping
                r = new NativePointer((long) r);
            }
            return r;
        } catch (Throwable e) {
            // TODO(NFI2) proper exception handling
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("restricted")
    MethodHandle getDowncallMethodHandle() {
        if (downcallMethodHandle == null) {
            MethodHandle methodHandle = Linker.nativeLinker().downcallHandle(getFunctionDescriptor());
            methodHandle = methodHandle.asSpreader(Object[].class, argTypes.length);
            methodHandle = methodHandle.asType(MethodType.methodType(Object.class, new Class<?>[]{MemorySegment.class, Object[].class}));
            downcallMethodHandle = methodHandle;
        }
        return downcallMethodHandle;
    }

    private FunctionDescriptor getFunctionDescriptor() {
        if (functionDescriptor == null) {
            MemoryLayout[] argLayouts = new MemoryLayout[argTypes.length];
            for (int i = 0; i < argTypes.length; i++) {
                argLayouts[i] = argTypes[i].asLayout();
            }
            functionDescriptor = resType == NfiType.VOID ? FunctionDescriptor.ofVoid(argLayouts) : FunctionDescriptor.of(resType.asLayout(), argLayouts);
        }
        return functionDescriptor;
    }
}
