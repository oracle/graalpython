/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.function;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.NodeFactory;

/**
 * Context independent wrapper of a method that can be stored in special method slots. These
 * wrappers are context and also language instance independent.
 *
 * @see com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot
 */
public abstract class BuiltinMethodDescriptor {

    /**
     * Size of this cache is limited by the number of builtins in GraalPython. First few contexts
     * may, in theory, experience lock contention while this cache is being filled up, but after
     * that there should be no cache misses and no locking to update the cache.
     *
     * Another way to look at this is that it is a map of all builtins, like
     * {@link PythonBuiltinClassType} is list of all builtin types, but initialized at runtime.
     *
     * Not having this cache per {@link com.oracle.graal.python.PythonLanguage} allows to save the
     * indirection when comparing to some well known {@link BuiltinMethodDescriptor} in guards.
     */
    private static final ConcurrentHashMap<BuiltinMethodDescriptor, BuiltinMethodDescriptor> CACHE = new ConcurrentHashMap<>();

    /**
     * First caller of this method within given {@code PythonLanguage} instance should add a cache
     * entry for this builtin's call target.
     */
    public static BuiltinMethodDescriptor get(PBuiltinFunction function) {
        CompilerAsserts.neverPartOfCompilation();
        NodeFactory<? extends PythonBuiltinBaseNode> factory = function.getBuiltinNodeFactory();
        if (factory == null) {
            return null;
        }
        Builtin builtinAnnotation = findBuiltinAnnotation(function.getName().toJavaStringUncached(), factory);
        if (builtinAnnotation.needsFrame()) {
            return null;
        }

        PythonBuiltinClassType type = null;
        Object enclosing = function.getEnclosingType();
        if (enclosing instanceof PythonBuiltinClassType) {
            type = (PythonBuiltinClassType) enclosing;
        } else if (enclosing instanceof PythonBuiltinClass) {
            type = ((PythonBuiltinClass) enclosing).getType();
        } else {
            assert enclosing == null;
        }

        return get(function.getName().toJavaStringUncached(), factory, type);
    }

    static BuiltinMethodDescriptor get(String name, NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type) {
        Builtin builtinAnnotation = findBuiltinAnnotation(name, factory);
        assert !builtinAnnotation.needsFrame();
        return get(name, factory, type, builtinAnnotation);
    }

    private static BuiltinMethodDescriptor get(String name, NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type, Builtin builtinAnnotation) {
        CompilerAsserts.neverPartOfCompilation();
        Class<? extends PythonBuiltinBaseNode> nodeClass = factory.getNodeClass();
        BuiltinMethodDescriptor result = null;
        if (PythonUnaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
            result = new UnaryBuiltinDescriptor(name, factory, type, builtinAnnotation);
            assert result.getBuiltinAnnotation().minNumOfPositionalArgs() <= 1 : name;
        } else if (PythonBinaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
            result = new BinaryBuiltinDescriptor(name, factory, type, builtinAnnotation);
            assert result.getBuiltinAnnotation().minNumOfPositionalArgs() <= 2 : name;
        } else if (PythonTernaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
            result = new TernaryBuiltinDescriptor(name, factory, type, builtinAnnotation);
            assert result.getBuiltinAnnotation().minNumOfPositionalArgs() <= 3 : name;
        }
        if (result != null) {
            return CACHE.computeIfAbsent(result, x -> x);
        }
        return null;
    }

    public static boolean isInstance(Object obj) {
        return obj instanceof BuiltinMethodDescriptor;
    }

    private static Builtin findBuiltinAnnotation(String name, NodeFactory<? extends PythonBuiltinBaseNode> factory) {
        for (Builtin builtin : factory.getNodeClass().getAnnotationsByType(Builtin.class)) {
            if (builtin.name().equals(name)) {
                return builtin;
            }
        }
        throw new IllegalStateException(String.format(
                        "Cannot find corresponding builtin annotation on class %s for builtin '%s'",
                        factory.getNodeClass().getSimpleName(), name));
    }

    private final NodeFactory<? extends PythonBuiltinBaseNode> factory;
    private final PythonBuiltinClassType type;
    // The builtin annotation allows us to differentiate between builtins shared for reversible
    // operations, such as int.__mul__ and int.__rmul__, which have the same node factory
    private final Builtin builtinAnnotation;
    // Shortcuts for fields of builtinAnnotation that are accessed on a fast-path
    private final String name;
    private final boolean isReverseOperation;
    private final int minNumOfPositionalArgs;

    private BuiltinMethodDescriptor(String name, NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type, Builtin builtinAnnotation) {
        assert name.equals(builtinAnnotation.name());
        this.name = name;
        this.factory = factory;
        this.type = type;
        this.builtinAnnotation = builtinAnnotation;
        this.isReverseOperation = builtinAnnotation.reverseOperation();
        this.minNumOfPositionalArgs = builtinAnnotation.minNumOfPositionalArgs();
    }

    public final NodeFactory<? extends PythonBuiltinBaseNode> getFactory() {
        return factory;
    }

    public final boolean isDescriptorOf(PBuiltinFunction fun) {
        return fun.getDescriptor() == this;
    }

    public final PythonBuiltinClassType getEnclosingType() {
        return type;
    }

    public final String getName() {
        return name;
    }

    public final boolean isReverseOperation() {
        return isReverseOperation;
    }

    public final int minNumOfPositionalArgs() {
        return minNumOfPositionalArgs;
    }

    public final Builtin getBuiltinAnnotation() {
        return builtinAnnotation;
    }

    @Override
    public final boolean equals(Object o) {
        CompilerAsserts.neverPartOfCompilation();
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BuiltinMethodDescriptor that = (BuiltinMethodDescriptor) o;
        return factory == that.factory && type == that.type && name.equals(that.name);
    }

    @Override
    public final int hashCode() {
        CompilerAsserts.neverPartOfCompilation();
        return Objects.hash(factory, type, name);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return getClass().getSimpleName() + "{" + type + "." + name + '}';
    }

    // Note: manually written subclass for each builtin works better with Truffle DSL than one
    // generic class that would parametrize the 'factory' field

    public static final class UnaryBuiltinDescriptor extends BuiltinMethodDescriptor {
        public UnaryBuiltinDescriptor(String name, NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type, Builtin builtinAnnotation) {
            super(name, factory, type, builtinAnnotation);
        }

        public PythonUnaryBuiltinNode createNode() {
            return (PythonUnaryBuiltinNode) getFactory().createNode();
        }
    }

    public static final class BinaryBuiltinDescriptor extends BuiltinMethodDescriptor {
        public BinaryBuiltinDescriptor(String name, NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type, Builtin builtinAnnotation) {
            super(name, factory, type, builtinAnnotation);
        }

        public PythonBinaryBuiltinNode createNode() {
            return (PythonBinaryBuiltinNode) getFactory().createNode();
        }
    }

    public static final class TernaryBuiltinDescriptor extends BuiltinMethodDescriptor {
        public TernaryBuiltinDescriptor(String name, NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type, Builtin builtinAnnotation) {
            super(name, factory, type, builtinAnnotation);
        }

        public PythonTernaryBuiltinNode createNode() {
            return (PythonTernaryBuiltinNode) getFactory().createNode();
        }
    }
}
