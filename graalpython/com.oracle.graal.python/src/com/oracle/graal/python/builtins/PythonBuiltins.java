/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;

public abstract class PythonBuiltins {
    protected final Map<String, Object> builtinConstants = new HashMap<>();
    private final Map<String, PBuiltinFunction> builtinFunctions = new HashMap<>();
    private final Map<PythonBuiltinClass, Map.Entry<Class<?>[], Boolean>> builtinClasses = new HashMap<>();

    protected abstract List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories();

    public void initialize(PythonCore core) {
        if (builtinFunctions.size() > 0) {
            return;
        }
        initializeEachFactoryWith((factory, builtin) -> {
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new BuiltinFunctionRootNode(core.getLanguage(), builtin, factory));
            String name = builtin.name();
            if (builtin.constructsClass().length > 0) {
                name = __NEW__;
            }
            PBuiltinFunction function;
            if (this.getClass().getAnnotation(CoreFunctions.class).extendClasses().length == 0) {
                // builtin module functions are builtin-functions, i.e., they have no __get__
                function = core.factory().createBuiltinFunction(name, createArity(builtin), callTarget);
            } else {
                // builtin class functions are functions, i.e., they have a __get__
                function = core.factory().createFunction(name, createArity(builtin), callTarget);
            }
            PythonObject attribute = function;
            String doc = builtin.doc();
            if (builtin.constructsClass().length > 0) {
                PythonBuiltinClass builtinClass = createBuiltinClassFor(core, builtin);
                builtinClass.setAttributeUnsafe(__NEW__, function);
                attribute = builtinClass;
            } else if (builtin.isGetter() || builtin.isSetter()) {
                CoreFunctions annotation = getClass().getAnnotation(CoreFunctions.class);
                PythonBuiltinClass builtinClass = core.lookupType(annotation.extendClasses()[0]);
                if (builtin.isGetter() && !builtin.isSetter()) {
                    attribute = core.factory().createGetSetDescriptor(function, null, builtin.name(), builtinClass);
                } else if (!builtin.isGetter() && builtin.isSetter()) {
                    attribute = core.factory().createGetSetDescriptor(null, function, builtin.name(), builtinClass);
                } else {
                    attribute = core.factory().createGetSetDescriptor(function, function, builtin.name(), builtinClass);
                }
                builtinConstants.put(builtin.name(), attribute);
            } else {
                setBuiltinFunction(builtin.name(), function);
            }
            attribute.setAttribute(__DOC__, doc);
        });
    }

    public final void initializeClasses(PythonCore core) {
        assert builtinClasses.isEmpty();
        initializeEachFactoryWith((factory, builtin) -> {
            if (builtin.constructsClass().length > 0) {
                createBuiltinClassFor(core, builtin);
            }
        });
    }

    private PythonBuiltinClass createBuiltinClassFor(PythonCore core, Builtin builtin) {
        PythonBuiltinClass builtinClass = null;
        for (Class<?> klass : builtin.constructsClass()) {
            builtinClass = core.lookupType(klass);
            if (builtinClass != null) {
                break;
            }
        }
        if (builtinClass == null) {
            Class<?>[] bases = builtin.base();
            PythonBuiltinClass base = null;
            if (bases.length == 0) {
                base = core.getObjectClass();
            } else {
                assert bases.length == 1;
                // Search the "local scope" for builtin classes to inherit from
                outer: for (Entry<PythonBuiltinClass, Entry<Class<?>[], Boolean>> localClasses : builtinClasses.entrySet()) {
                    for (Class<?> o : localClasses.getValue().getKey()) {
                        if (o == bases[0]) {
                            base = localClasses.getKey();
                            break outer;
                        }
                    }
                }
                // Only take a globally known builtin class if we haven't found a local one
                if (base == null) {
                    base = core.lookupType(bases[0]);
                }
                assert base != null;
            }
            builtinClass = new PythonBuiltinClass(core.getTypeClass(), builtin.name(), base);
        }
        setBuiltinClass(builtinClass, builtin.constructsClass(), builtin.isPublic());
        return builtinClass;
    }

    private void initializeEachFactoryWith(BiConsumer<NodeFactory<? extends PythonBuiltinBaseNode>, Builtin> func) {
        List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> factories = getNodeFactories();
        assert factories != null : "No factories found. Override getFactories() to resolve this.";
        for (NodeFactory<? extends PythonBuiltinBaseNode> factory : factories) {
            Builtin builtin = factory.getNodeClass().getAnnotation(Builtin.class);
            func.accept(factory, builtin);
        }
    }

    private static Arity createArity(Builtin builtin) {
        int minNum = builtin.minNumOfArguments();
        int maxNum = Math.max(minNum, builtin.maxNumOfArguments());
        if (builtin.fixedNumOfArguments() > 0) {
            minNum = maxNum = builtin.fixedNumOfArguments();
        }
        if (!builtin.takesVariableArguments()) {
            maxNum += builtin.keywordArguments().length;
        }
        return new Arity(builtin.name(), minNum, maxNum, builtin.keywordArguments().length > 0 || builtin.takesVariableKeywords(), builtin.takesVariableArguments(),
                        Arrays.asList(new String[0]), Arrays.asList(builtin.keywordArguments()));
    }

    private void setBuiltinFunction(String name, PBuiltinFunction function) {
        builtinFunctions.put(name, function);
    }

    private void setBuiltinClass(PythonBuiltinClass builtinClass, Class<?>[] classes, boolean isPublic) {
        SimpleEntry<Class<?>[], Boolean> simpleEntry = new AbstractMap.SimpleEntry<>(classes, isPublic);
        builtinClasses.put(builtinClass, simpleEntry);
    }

    protected Map<String, PBuiltinFunction> getBuiltinFunctions() {
        return builtinFunctions;
    }

    protected Map<PythonBuiltinClass, Entry<Class<?>[], Boolean>> getBuiltinClasses() {
        Map<PythonBuiltinClass, Entry<Class<?>[], Boolean>> tmp = builtinClasses;
        assert (tmp = Collections.unmodifiableMap(tmp)) != null;
        return tmp;
    }

    protected Map<String, Object> getBuiltinConstants() {
        return builtinConstants;
    }

}
