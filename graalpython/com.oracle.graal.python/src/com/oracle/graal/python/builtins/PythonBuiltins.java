/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.ensureNoJavaString;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.PythonOS;
import com.oracle.graal.python.builtins.modules.ImpModuleBuiltins.ExecBuiltin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.BiConsumer;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class PythonBuiltins {
    private final Map<TruffleString, Object> builtinConstants = new HashMap<>();
    private final Map<TruffleString, BoundBuiltinCallable<?>> builtinFunctions = new HashMap<>();

    protected abstract List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories();

    private boolean initialized;

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    /**
     * Initialize everything that is truly independent of commandline arguments and that can be
     * initialized and frozen into an SVM image. When in a subclass, any modifications to
     * {@link #builtinConstants} or such should be made before calling
     * {@code super.initialize(core)}.
     */
    public void initialize(Python3Core core) {
        if (builtinFunctions.size() > 0) {
            return;
        }
        initializeEachFactoryWith((factory, builtin) -> {
            CoreFunctions annotation = getClass().getAnnotation(CoreFunctions.class);
            final boolean declaresExplicitSelf;
            if ((annotation.defineModule().length() > 0 || annotation.extendsModule().length() > 0)) {
                assert !builtin.isGetter();
                assert !builtin.isSetter();
                assert annotation.extendClasses().length == 0;
                // for module functions, explicit self is false by default
                declaresExplicitSelf = builtin.declaresExplicitSelf();
            } else {
                declaresExplicitSelf = true;
            }
            TruffleString tsName = toTruffleStringUncached(builtin.name());
            PythonLanguage language = core.getLanguage();
            boolean lazyCallTargets = core.getContext().getOption(PythonOptions.BuiltinLazyCallTargets);
            PBuiltinFunction function;
            if (lazyCallTargets) {
                Signature signature = BuiltinFunctionRootNode.createSignature(factory, builtin, declaresExplicitSelf, false);
                int flags = PBuiltinFunction.getFlags(builtin, signature);
                var callTargetSupplier = new CachedLazyCalltargetSupplier(() -> language.initBuiltinCallTarget(l -> new BuiltinFunctionRootNode(l, signature, builtin, factory, declaresExplicitSelf, null), factory.getNodeClass(),
                        builtin.name()));
                function = PFactory.createBuiltinFunction(language, tsName, numDefaults(builtin),signature, flags, callTargetSupplier);
            } else {
                RootCallTarget callTarget = language.initBuiltinCallTarget(l -> new BuiltinFunctionRootNode(l, builtin, factory, declaresExplicitSelf), factory.getNodeClass(),
                        builtin.name());
                int flags = PBuiltinFunction.getFlags(builtin, callTarget);
                function = PFactory.createBuiltinFunction(language, tsName, null, numDefaults(builtin), flags, callTarget);
            }

            Object builtinDoc = builtin.doc().isEmpty() ? PNone.NONE : toTruffleStringUncached(builtin.doc());
            function.setAttribute(T___DOC__, builtinDoc);
            BoundBuiltinCallable<?> callable = function;
            if (builtin.isGetter() || builtin.isSetter()) {
                assert !builtin.isClassmethod() && !builtin.isStaticmethod();
                PBuiltinFunction get = builtin.isGetter() ? function : null;
                PBuiltinFunction set = builtin.isSetter() ? function : null;
                callable = PFactory.createGetSetDescriptor(language, get, set, tsName, null, builtin.allowsDelete());
            } else if (builtin.isClassmethod()) {
                assert !builtin.isStaticmethod();
                callable = PFactory.createBuiltinClassmethodFromCallableObj(language, function);
            } else if (builtin.isStaticmethod()) {
                callable = PFactory.createStaticmethodFromCallableObj(language, function);
            }
            builtinFunctions.put(toTruffleStringUncached(builtin.name()), callable);
        });
    }

    /**
     * Run any actions that can only be run in the post-initialization step, that is, if we're
     * actually going to start running rather than just pre-initializing.
     * <p>
     * See {@link Python3Core#postInitialize(Env)} as postInitialize() is only run under some
     * conditions. See also {@link ExecBuiltin}, tough that does not get called if the built-in
     * module is actually shadowed by a frozen one, or if the built-in module is actually added to
     * sys.modules during context initialization before the importlib exists, etc.
     */
    public void postInitialize(@SuppressWarnings("unused") Python3Core core) {
        // nothing to do by default
    }

    private void initializeEachFactoryWith(BiConsumer<NodeFactory<? extends PythonBuiltinBaseNode>, Builtin> func) {
        List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> factories = getNodeFactories();
        assert factories != null : "No factories found. Override getFactories() to resolve this.";
        PythonOS currentOs = PythonLanguage.getPythonOS();
        for (NodeFactory<? extends PythonBuiltinBaseNode> factory : factories) {
            Boolean needsFrame = null;
            boolean initialized = false;
            for (Builtin builtin : factory.getNodeClass().getAnnotationsByType(Builtin.class)) {
                if (!builtin.autoRegister()) {
                    assert !initialized : "Builtin annotations on " + factory.getNodeClass().getName() + " do not agree on 'autoInitialize' property.";
                    break;
                }
                if (builtin.os() == PythonOS.PLATFORM_ANY || builtin.os() == currentOs) {
                    if (needsFrame == null) {
                        needsFrame = builtin.needsFrame();
                    } else if (needsFrame != builtin.needsFrame()) {
                        throw new IllegalStateException(String.format("Implementation error in %s: all @Builtin annotations must agree if the node needs a frame.", factory.getNodeClass().getName()));
                    }
                    func.accept(factory, builtin);
                }
            }
        }
    }

    /**
     * Determines the number of required default values for the given builtin.
     */
    public static int numDefaults(Builtin builtin) {
        int parameterNameCount = builtin.parameterNames().length;
        int maxNumPosArgs = Math.max(builtin.minNumOfPositionalArgs(), parameterNameCount);
        if (builtin.maxNumOfPositionalArgs() >= 0) {
            maxNumPosArgs = builtin.maxNumOfPositionalArgs();
            assert parameterNameCount == 0 : "either give all parameter names explicitly, or define the max number: " + builtin.name();
        }
        return maxNumPosArgs - builtin.minNumOfPositionalArgs();
    }

    /**
     * May only be used in {@link #initialize} or before. Use {@link PythonObject#setAttribute}
     * instead in {@link #postInitialize}.
     */
    protected final void addBuiltinConstant(String name, Object value) {
        addBuiltinConstant(toTruffleStringUncached(name), value);
    }

    /**
     * May only be used in {@link #initialize} or before. Use {@link PythonObject#setAttribute}
     * instead in {@link #postInitialize}.
     */
    protected final void addBuiltinConstant(TruffleString name, Object value) {
        builtinConstants.put(name, ensureNoJavaString(value));
    }

    protected Object getBuiltinConstant(TruffleString name) {
        return builtinConstants.get(name);
    }

    void addConstantsToModuleObject(PythonObject obj) {
        for (Map.Entry<TruffleString, Object> entry : builtinConstants.entrySet()) {
            Object value = assertNoJavaString(entry.getValue());
            obj.setAttribute(entry.getKey(), value);
        }
    }

    void addFunctionsToModuleObject(PythonObject obj, PythonLanguage language) {
        addFunctionsToModuleObject(builtinFunctions, obj, language);
    }

    static void addFunctionsToModuleObject(Map<TruffleString, BoundBuiltinCallable<?>> builtinFunctions, PythonObject obj, PythonLanguage language) {
        assert obj instanceof PythonModule || obj instanceof PythonBuiltinClass : "unexpected object while adding builtins";
        for (Entry<TruffleString, BoundBuiltinCallable<?>> entry : builtinFunctions.entrySet()) {
            Object value;
            if (obj instanceof PythonModule) {
                value = PFactory.createBuiltinMethod(language, obj, (PBuiltinFunction) entry.getValue());
            } else {
                value = entry.getValue().boundToObject(((PythonBuiltinClass) obj).getType(), language);
            }
            obj.setAttribute(entry.getKey(), value);
        }
    }
}
