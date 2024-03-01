/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;

import java.lang.invoke.VarHandle;
import java.util.Arrays;

import com.oracle.graal.python.builtins.BoundBuiltinCallable;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
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
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Represents an unbound builtin method. Corresponds to python types:
 * <ul>
 * <li>{@code method_descriptor} - Used for unbound methods on a type. Example:
 * {@code str.startswith}
 * <li>{@code wrapper_descriptor} - Used for unbound slot methods on a type. Example:
 * {@code str.__str__}
 * </ul>
 */
@ExportLibrary(InteropLibrary.class)
public final class PBuiltinFunction extends PythonBuiltinObject implements BoundBuiltinCallable<PBuiltinFunction> {

    private final PString name;
    private final TruffleString qualname;
    private final Object enclosingType;
    private final RootCallTarget callTarget;
    private final Signature signature;
    private final int flags;
    private BuiltinMethodDescriptor descriptor;
    @CompilationFinal(dimensions = 1) private final Object[] defaults;
    @CompilationFinal(dimensions = 1) private final PKeyword[] kwDefaults;

    public PBuiltinFunction(PythonBuiltinClassType cls, Shape shape, TruffleString name, Object enclosingType, Object[] defaults, PKeyword[] kwDefaults, int flags, RootCallTarget callTarget) {
        super(cls, shape);
        this.name = PythonUtils.toPString(name);
        if (enclosingType != null) {
            this.qualname = StringUtils.cat(GetNameNode.doSlowPath(enclosingType), T_DOT, name);
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
        TruffleString[] keywordNames = signature.getKeywordNames();
        PKeyword[] kwDefaults = PKeyword.create(keywordNames.length);
        for (int i = 0; i < keywordNames.length; i++) {
            kwDefaults[i] = new PKeyword(keywordNames[i], PNone.NO_VALUE);
        }
        return kwDefaults;
    }

    /**
     * Creates the array for default values. This will reuse {@link PythonUtils#EMPTY_OBJECT_ARRAY}
     * if {@code numDefaults} is {@code 0}.
     */
    public static Object[] generateDefaults(int numDefaults) {
        if (numDefaults == 0) {
            return PythonUtils.EMPTY_OBJECT_ARRAY;
        }
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

    public boolean needsDeclaringType() {
        return (flags & CExtContext.METH_METHOD) != 0;
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
        if (!signature.takesKeywordArgs() && !signature.takesVarArgs()) {
            int params = signature.getParameterIds().length;
            if (params == 1) {
                // only 'self'
                return flags | CExtContext.METH_NOARGS;
            } else if (params == 2) {
                return flags | CExtContext.METH_O;
            }
        }
        flags |= CExtContext.METH_VARARGS;
        if (signature.takesKeywordArgs()) {
            flags |= CExtContext.METH_KEYWORDS;
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

    public TruffleString getName() {
        return name.getMaterialized();
    }

    public PString getCApiName() {
        return name;
    }

    public TruffleString getQualname() {
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
            PBuiltinFunction func = factory.createBuiltinFunction(this, klass);
            func.setAttribute(T___DOC__, getAttribute(T___DOC__));
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
    TruffleString getExecutableName() {
        return getName();
    }

    public void setDescriptor(BuiltinMethodDescriptor value) {
        assert value.getName().equals(getName().toJavaStringUncached()) && getBuiltinNodeFactory() == value.getFactory() : getName() + " vs " + value;
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
