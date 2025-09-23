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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;

public final class NfiSignature {

    static final MethodType INTEROP_METHOD_TYPE = MethodType.methodType(Object.class, Object.class, Object[].class);
    static final MethodType DIRECT_METHOD_TYPE = MethodType.methodType(Object.class, Object[].class);
    static final MethodType DOWNCALL_METHOD_TYPE = MethodType.methodType(Object.class, new Class<?>[]{MemorySegment.class, Object[].class});

    private final NfiType resType;
    private final NfiType[] argTypes;
    private MethodHandle downcallMethodHandle;
    private FunctionDescriptor functionDescriptor;
    private MethodType interopUpcallMethodType;
    private MethodType directUpcallMethodType;

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
    @TruffleBoundary
    public String toString() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < argTypes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(argTypes[i]);
        }
        sb.append("): ");
        sb.append(resType);
        return sb.toString();
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
            convertedArgs[i] = argTypes[i].getConvertArgJavaToNativeNodeUncached().execute(args[i]);
        }
        return convertedArgs;
    }

    Object convertResult(Object r) {
        if (resType == NfiType.POINTER) {
            // TODO(NFI2) migrate to RAWPOINTER and remove this wrapping
            return new NativePointer((long) r);
        }
        return r;
    }

    @TruffleBoundary
    public Object invokeUncached(long function, Object... args) throws ArityException, UnsupportedTypeException {
        try {
            return convertResult(getDowncallMethodHandle().invokeExact(MemorySegment.ofAddress(function), convertArgs(args)));
        } catch (Throwable e) {
            // TODO(NFI2) proper exception handling
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @SuppressWarnings("restricted")
    @TruffleBoundary
    public long createInteropClosureUncached(Object executable) {
        // TODO(NFI2) remove once we are sure that all closures are direct nodes instead of interop
        // executables
        RootNode rootNode = new NfiInteropClosureRootNode(this);
        // TODO(NFI2) SVM needs this handle to be a static method
        MethodHandle handle = handle_CallTarget_call.bindTo(rootNode.getCallTarget());
        handle = handle.asCollector(Object[].class, 2).asType(INTEROP_METHOD_TYPE).asVarargsCollector(Object[].class);
        if (interopUpcallMethodType == null) {
            Class<?>[] javaArgTypes = new Class<?>[argTypes.length + 1];
            javaArgTypes[0] = Object.class;
            for (int i = 0; i < argTypes.length; i++) {
                javaArgTypes[i + 1] = argTypes[i].asJavaType();
            }
            interopUpcallMethodType = MethodType.methodType(resType.asJavaType(), javaArgTypes);
        }
        handle = handle.asType(interopUpcallMethodType);
        handle = handle.bindTo(executable);
        // TODO(NFI2) per-context or closure-specific Arena
        return Linker.nativeLinker().upcallStub(handle, getFunctionDescriptor(), Arena.global()).address();
    }

    @SuppressWarnings("restricted")
    @TruffleBoundary
    public long createDirectClosureUncached(Supplier<NfiClosureBaseNode> closureNode) {
        RootNode rootNode = new NfiDirectClosureRootNode(closureNode, this);
        // TODO(NFI2) SVM needs this handle to be a static method
        MethodHandle handle = handle_CallTarget_call.bindTo(rootNode.getCallTarget());
        handle = handle.asType(DIRECT_METHOD_TYPE).asVarargsCollector(Object[].class);
        if (directUpcallMethodType == null) {
            Class<?>[] javaArgTypes = new Class<?>[argTypes.length];
            for (int i = 0; i < argTypes.length; i++) {
                javaArgTypes[i] = argTypes[i].asJavaType();
            }
            directUpcallMethodType = MethodType.methodType(resType.asJavaType(), javaArgTypes);
        }
        handle = handle.asType(directUpcallMethodType);
        // TODO(NFI2) per-context or closure-specific Arena
        return Linker.nativeLinker().upcallStub(handle, getFunctionDescriptor(), Arena.global()).address();
    }

    @SuppressWarnings("restricted")
    MethodHandle getDowncallMethodHandle() {
        if (downcallMethodHandle == null) {
            MethodHandle methodHandle = Linker.nativeLinker().downcallHandle(getFunctionDescriptor());
            methodHandle = methodHandle.asSpreader(Object[].class, argTypes.length);
            methodHandle = methodHandle.asType(DOWNCALL_METHOD_TYPE);
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

    static final MethodHandle handle_CallTarget_call;

    static {
        MethodType callType = MethodType.methodType(Object.class, Object[].class);
        try {
            handle_CallTarget_call = MethodHandles.lookup().findVirtual(CallTarget.class, "call", callType);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw CompilerDirectives.shouldNotReachHere(ex);
        }
    }
}
