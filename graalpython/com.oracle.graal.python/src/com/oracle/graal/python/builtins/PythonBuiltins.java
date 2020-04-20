/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.oracle.graal.python.util.BiConsumer;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;

public abstract class PythonBuiltins {
    protected final Map<String, Object> builtinConstants = new HashMap<>();
    private final Map<String, BoundBuiltinCallable<?>> builtinFunctions = new HashMap<>();
    private final Map<PythonBuiltinClass, Map.Entry<PythonBuiltinClassType[], Boolean>> builtinClasses = new HashMap<>();

    protected abstract List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories();

    /**
     * Initialize everything that is truly independent of commandline arguments and that can be
     * initialized and frozen into an SVM image. When in a subclass, any modifications to
     * {@link #builtinConstants} or such should be made before calling
     * {@code super.initialize(core)}.
     */
    public void initialize(PythonCore core) {
        if (builtinFunctions.size() > 0) {
            return;
        }
        initializeEachFactoryWith((factory, builtin) -> {
            CoreFunctions annotation = getClass().getAnnotation(CoreFunctions.class);
            final boolean declaresExplicitSelf;
            if (annotation.defineModule().length() > 0 && builtin.constructsClass().length == 0) {
                assert !builtin.isGetter();
                assert !builtin.isSetter();
                assert annotation.extendClasses().length == 0;
                // for module functions, explicit self is false by default
                declaresExplicitSelf = builtin.declaresExplicitSelf();
            } else {
                declaresExplicitSelf = true;
            }
            RootCallTarget callTarget = core.getLanguage().builtinCallTargetCache.computeIfAbsent(factory.getNodeClass(),
                            (b) -> Truffle.getRuntime().createCallTarget(new BuiltinFunctionRootNode(core.getLanguage(), builtin, factory, declaresExplicitSelf)));
            Object builtinDoc = builtin.doc().isEmpty() ? PNone.NONE : builtin.doc();
            if (builtin.constructsClass().length > 0) {
                assert !builtin.isGetter() && !builtin.isSetter() && !builtin.isClassmethod() && !builtin.isStaticmethod();
                PBuiltinFunction newFunc = core.factory().createBuiltinFunction(__NEW__, null, numDefaults(builtin), callTarget);
                for (PythonBuiltinClassType type : builtin.constructsClass()) {
                    PythonBuiltinClass builtinClass = core.lookupType(type);
                    builtinClass.setAttributeUnsafe(__NEW__, newFunc);
                    builtinClass.setAttribute(__DOC__, builtinDoc);
                }
            } else {
                PBuiltinFunction function = core.factory().createBuiltinFunction(builtin.name(), null, numDefaults(builtin), callTarget);
                function.setAttribute(__DOC__, builtinDoc);
                BoundBuiltinCallable<?> callable = function;
                if (builtin.isGetter() || builtin.isSetter()) {
                    assert !builtin.isClassmethod() && !builtin.isStaticmethod();
                    PBuiltinFunction get = builtin.isGetter() ? function : null;
                    PBuiltinFunction set = builtin.isSetter() ? function : null;
                    callable = core.factory().createGetSetDescriptor(get, set, builtin.name(), null);
                } else if (builtin.isClassmethod()) {
                    assert !builtin.isStaticmethod();
                    callable = core.factory().createClassmethod(function);
                } else if (builtin.isStaticmethod()) {
                    callable = core.factory().createStaticmethod(function);
                }
                setBuiltinFunction(builtin.name(), callable);
            }
        });
    }

    /**
     * Run any actions that can only be run in the post-initialization step, that is, if we're
     * actually going to start running rather than just pre-initializing.
     */
    public void postInitialize(@SuppressWarnings("unused") PythonCore core) {
        // nothing to do by default
    }

    private void initializeEachFactoryWith(BiConsumer<NodeFactory<? extends PythonBuiltinBaseNode>, Builtin> func) {
        List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> factories = getNodeFactories();
        assert factories != null : "No factories found. Override getFactories() to resolve this.";
        for (NodeFactory<? extends PythonBuiltinBaseNode> factory : factories) {
            Builtin builtin = factory.getNodeClass().getAnnotation(Builtin.class);
            func.accept(factory, builtin);
        }
    }

    private static int numDefaults(Builtin builtin) {
        String[] parameterNames = builtin.parameterNames();
        int maxNumPosArgs = Math.max(builtin.minNumOfPositionalArgs(), parameterNames.length);
        if (builtin.maxNumOfPositionalArgs() >= 0) {
            maxNumPosArgs = builtin.maxNumOfPositionalArgs();
            assert parameterNames.length == 0 : "either give all parameter names explicitly, or define the max number: " + builtin.name();
        }
        return maxNumPosArgs - builtin.minNumOfPositionalArgs();
    }

    private void setBuiltinFunction(String name, BoundBuiltinCallable<?> function) {
        builtinFunctions.put(name, function);
    }

    protected Map<String, BoundBuiltinCallable<?>> getBuiltinFunctions() {
        return builtinFunctions;
    }

    protected Map<PythonBuiltinClass, Entry<PythonBuiltinClassType[], Boolean>> getBuiltinClasses() {
        Map<PythonBuiltinClass, Entry<PythonBuiltinClassType[], Boolean>> tmp = builtinClasses;
        assert (tmp = Collections.unmodifiableMap(tmp)) != null;
        return tmp;
    }

    protected Map<String, Object> getBuiltinConstants() {
        return builtinConstants;
    }
}
