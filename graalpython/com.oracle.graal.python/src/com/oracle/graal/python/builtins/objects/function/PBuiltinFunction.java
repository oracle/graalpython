/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.function;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;

import java.lang.invoke.VarHandle;
import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.BoundBuiltinCallable;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(InteropLibrary.class)
public final class PBuiltinFunction extends PythonBuiltinObject implements BoundBuiltinCallable<PBuiltinFunction> {

    private final String name;
    private final String qualname;
    private final Object enclosingType;
    private final RootCallTarget callTarget;
    private final Signature signature;
    private final int flags;
    private BuiltinMethodDescriptor descriptor;
    @CompilationFinal(dimensions = 1) private final Object[] defaults;
    @CompilationFinal(dimensions = 1) private final PKeyword[] kwDefaults;

    public PBuiltinFunction(PythonLanguage lang, String name, Object enclosingType, int numDefaults, int flags, RootCallTarget callTarget) {
        this(lang, name, enclosingType, generateDefaults(numDefaults), null, flags, callTarget);
    }

    public PBuiltinFunction(PythonLanguage lang, String name, Object enclosingType, Object[] defaults, PKeyword[] kwDefaults, int flags, RootCallTarget callTarget) {
        this(PythonBuiltinClassType.PBuiltinFunction, PythonBuiltinClassType.PBuiltinFunction.getInstanceShape(lang), name, enclosingType, defaults, kwDefaults, flags, callTarget);
    }

    public PBuiltinFunction(PythonBuiltinClassType cls, Shape shape, String name, Object enclosingType, Object[] defaults, PKeyword[] kwDefaults, int flags, RootCallTarget callTarget) {
        super(cls, shape);
        this.name = name;
        if (enclosingType != null) {
            this.qualname = PString.cat(GetNameNode.doSlowPath(enclosingType), ".", name);
        } else {
            this.qualname = name;
        }
        this.enclosingType = enclosingType;
        this.callTarget = callTarget;
        this.signature = ((PRootNode) callTarget.getRootNode()).getSignature();
        this.flags = flags;
        this.defaults = defaults;
        this.kwDefaults = kwDefaults != null ? kwDefaults : generateKwDefaults(signature);
    }

    private static PKeyword[] generateKwDefaults(Signature signature) {
        String[] keywordNames = signature.getKeywordNames();
        PKeyword[] kwDefaults = new PKeyword[keywordNames.length];
        for (int i = 0; i < keywordNames.length; i++) {
            kwDefaults[i] = new PKeyword(keywordNames[i], PNone.NO_VALUE);
        }
        return kwDefaults;
    }

    private static Object[] generateDefaults(int numDefaults) {
        Object[] defaults = new Object[numDefaults];
        Arrays.fill(defaults, PNone.NO_VALUE);
        return defaults;
    }

    public RootNode getFunctionRootNode() {
        return callTarget.getRootNode();
    }

    public NodeFactory<? extends PythonBuiltinBaseNode> getBuiltinNodeFactory() {
        RootNode functionRootNode = getFunctionRootNode();
        if (functionRootNode instanceof BuiltinFunctionRootNode) {
            return ((BuiltinFunctionRootNode) functionRootNode).getFactory();
        } else {
            return null;
        }
    }

    public boolean isReverseOperationSlot() {
        return isReverseOperationSlot(callTarget);
    }

    public static boolean isReverseOperationSlot(RootCallTarget ct) {
        RootNode functionRootNode = ct.getRootNode();
        if (functionRootNode instanceof BuiltinFunctionRootNode) {
            return ((BuiltinFunctionRootNode) functionRootNode).getBuiltin().reverseOperation();
        } else {
            return false;
        }
    }

    public int getFlags() {
        return flags;
    }

    public boolean isStatic() {
        return (flags & CExtContext.METH_STATIC) != 0;
    }

    @TruffleBoundary
    public static int getFlags(Builtin builtin, RootCallTarget callTarget) {
        return getFlags(builtin, ((PRootNode) callTarget.getRootNode()).getSignature());
    }

    @TruffleBoundary
    public static int getFlags(Builtin builtin, Signature signature) {
        if (builtin == null) {
            return 0;
        }
        int flags = 0;
        if (builtin.isClassmethod()) {
            flags |= CExtContext.METH_CLASS;
        }
        if (builtin.isStaticmethod()) {
            flags |= CExtContext.METH_STATIC;
        }
        int params = signature.getParameterIds().length;
        if (params == 1) {
            // only 'self'
            flags |= CExtContext.METH_NOARGS;
        } else if (params == 2) {
            flags |= CExtContext.METH_O;
        } else if (signature.takesKeywordArgs()) {
            flags |= CExtContext.METH_VARARGS;
        } else if (signature.takesVarArgs()) {
            flags |= CExtContext.METH_VARARGS;
        }
        return flags;
    }

    public Class<? extends PythonBuiltinBaseNode> getNodeClass() {
        return getBuiltinNodeFactory() != null ? getBuiltinNodeFactory().getNodeClass() : null;
    }

    public Signature getSignature() {
        return signature;
    }

    public RootCallTarget getCallTarget() {
        return callTarget;
    }

    public String getName() {
        return name;
    }

    public String getQualname() {
        return qualname;
    }

    public Object getEnclosingType() {
        return enclosingType;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("PBuiltinFunction %s at 0x%x", qualname, hashCode());
    }

    @Override
    public PBuiltinFunction boundToObject(PythonBuiltinClassType klass, PythonObjectFactory factory) {
        if (klass == enclosingType) {
            return this;
        } else {
            PBuiltinFunction func = factory.createBuiltinFunction(name, klass, defaults.length, flags, callTarget);
            func.setAttribute(__DOC__, getAttribute(__DOC__));
            return func;
        }
    }

    public Object[] getDefaults() {
        return defaults;
    }

    public PKeyword[] getKwDefaults() {
        return kwDefaults;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasExecutableName() {
        return true;
    }

    @ExportMessage
    String getExecutableName() {
        return getName();
    }

    public void setDescriptor(BuiltinMethodDescriptor value) {
        assert value.getName().equals(getName()) && getBuiltinNodeFactory() == value.getFactory() : getName() + " vs " + value;
        // Only make sure that info is fully initialized, otherwise it is fine if it is set multiple
        // times from different threads, all of them should set the same value
        VarHandle.storeStoreFence();
        BuiltinMethodDescriptor local = descriptor;
        assert local == null || local == value : value;
        this.descriptor = value;
    }

    /**
     * The descriptor is set lazily once this builtin function is stored in any special method slot.
     * I.e., one can assume that any builtin function looked up via special method slots has its
     * descriptor set.
     */
    public BuiltinMethodDescriptor getDescriptor() {
        return descriptor;
    }
}
