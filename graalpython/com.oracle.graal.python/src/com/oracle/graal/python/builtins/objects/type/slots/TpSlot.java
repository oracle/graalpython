/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.type.slots;

import static com.oracle.graal.python.builtins.PythonBuiltins.numDefaults;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;

import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.TpSlotWrapper;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.TpSlots.TpSlotMeta;
import com.oracle.graal.python.builtins.objects.type.slots.NodeFactoryUtils.NodeFactoryBase;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode.Dynamic;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TruffleWeakReference;

/**
 * Objects of this class represent slot values that can be stored in the
 * {@link com.oracle.graal.python.builtins.objects.type.TpSlots} object.
 */
public abstract class TpSlot {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(TpSlot.class);

    /**
     * Transforms the slot object to an interop object that can be sent to native.
     */
    public static Object toNative(TpSlotMeta slotMeta, TpSlot slot, Object defaultValue) {
        if (slot == null) {
            return defaultValue;
        } else if (slot instanceof TpSlotNative nativeSlot) {
            return nativeSlot.getCallable();
        } else if (slot instanceof TpSlotManaged managedSlot) {
            // This returns PyProcsWrapper, which will, in its toNative message, register the
            // pointer in C API context, such that we can map back from a pointer that we get from C
            // to the PyProcsWrapper and from that to the slot instance again in TpSlots#fromNative
            assert PythonContext.get(null).ownsGil(); // without GIL: use AtomicReference & CAS
            if (managedSlot.slotWrapper == null) {
                managedSlot.slotWrapper = slotMeta.createNativeWrapper(managedSlot);
            }
            return managedSlot.slotWrapper;
        } else {
            throw CompilerDirectives.shouldNotReachHere("TpSlotWrapper should wrap only managed slots. Native slots should go directly to native unwrapped.");
        }
    }

    /**
     * If the interop object represents a pointer to existing {@link TpSlot}, then returns that
     * slot, otherwise {@code null}.
     */
    public static TpSlot fromNative(PythonContext ctx, Object ptr, InteropLibrary interop) {
        if (interop.isPointer(ptr)) {
            try {
                Object delegate = ctx.getCApiContext().getClosureDelegate(interop.asPointer(ptr));
                if (delegate instanceof TpSlot s) {
                    return s;
                } else if (delegate != null) {
                    // This can happen for legacy slots where the delegate would be a PFunction
                    LOGGER.warning(() -> String.format("Unexpected delegate for slot pointer: %s", delegate));
                }
            } catch (UnsupportedMessageException e) {
                throw new IllegalStateException(e);
            }
        } else if (ptr instanceof TpSlotWrapper slotWrapper) {
            return slotWrapper.getSlot();
        }
        return null;
    }

    /**
     * Checks whether the slot represents the same "slot value" in CPython compatible way: i.e.,
     * Python magic method wrappers are same slots even if they are wrapping different
     * types/methods.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class IsSameSlotNode extends Node {
        public abstract boolean execute(Node inliningTarget, TpSlot a, TpSlot b);

        @Specialization
        static boolean pythonWrappers(TpSlotPython a, TpSlotPython b) {
            return true;
        }

        @Specialization
        static boolean nativeSlots(TpSlotNative a, TpSlotNative b,
                        @CachedLibrary(limit = "1") InteropLibrary interop) {
            return a.isSameCallable(b, interop);
        }

        @Fallback
        static boolean others(TpSlot a, TpSlot b) {
            // builtins are singletons, or if the types don't match the objects wouldn't either
            assert hasExpectedType(a) && hasExpectedType(b);
            return a == b;
        }

        private static boolean hasExpectedType(TpSlot x) {
            return x instanceof TpSlotBuiltin<?> || x instanceof TpSlotNative || x instanceof TpSlotPython;
        }
    }

    /**
     * Marker base class for managed slots: either builtin slots or user defined Python slots.
     */
    public abstract static sealed class TpSlotManaged extends TpSlot permits TpSlotBuiltin, TpSlotPython {
        private TpSlotWrapper slotWrapper;
    }

    /**
     * Represents a slot that should delegate to a Python callable. The slot field in
     * {@link com.oracle.graal.python.builtins.objects.type.TpSlots} where this is stored determines
     * the signature. On Java level we have to do a call with boxed arguments array and cannot
     * optimize for specific signature.
     * <p/>
     * These slots should reference their cached callables and other runtime objects weakly, because
     * the slot objects may be wrapped into a long-lived {@code PyCFunction} wrapper.
     */
    public abstract static non-sealed class TpSlotPython extends TpSlotManaged {
        /**
         * This slot may be inherited by a new type. Since the slot caches MRO lookup result, it may
         * be necessary to update the cached result. This method can return {@code this} if no cache
         * updates are necessary, otherwise it should construct a new instance with updated caches.
         * <p>
         * This is situation specific to GraalPy, because we cache the lookups in the slots.
         */
        public abstract TpSlotPython forNewType(Object klass);

        final Object safeGet(TruffleWeakReference<Object> weakRef) {
            if (weakRef == null) {
                return null;
            }
            Object result = weakRef.get();
            assert result != null : "Object cached in " + getClass().getSimpleName() + " disappeared";
            return result;
        }

        static TruffleWeakReference<Object> asWeakRef(Object value) {
            return value == null || value == PNone.NO_VALUE ? null : new TruffleWeakReference<>(value);
        }
    }

    /**
     * Represents a slot that should delegate to a native function. The slot field where this is
     * stored determines the signature. On Java level we have to do a call with boxed arguments
     * array and cannot optimize for specific signature.
     */
    public abstract static sealed class TpSlotNative extends TpSlot permits TpSlotCExtNative, TpSlotHPyNative {
        final Object callable;

        public TpSlotNative(Object callable) {
            this.callable = callable;
        }

        public static TpSlotNative createCExtSlot(Object callable) {
            return new TpSlotCExtNative(callable);
        }

        public static TpSlotNative createHPySlot(Object callable) {
            return new TpSlotHPyNative(callable);
        }

        public final boolean isSameCallable(TpSlotNative other, InteropLibrary interop) {
            if (this == other || this.callable == other.callable) {
                return true;
            }
            // NFISymbols do not implement isIdentical interop message, so we compare the pointers
            // Interop is going to be quite slow (in interpreter), should we eagerly request the
            // pointer in the ctor?
            interop.toNative(callable);
            interop.toNative(other.callable);
            try {
                return interop.asPointer(callable) == interop.asPointer(other.callable);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        /**
         * Bound callable that supports the execute interop message.
         */
        public final Object getCallable() {
            return callable;
        }
    }

    /**
     * Standard CPython C API slot that takes {@code PyObject*} arguments.
     */
    public static final class TpSlotCExtNative extends TpSlotNative {
        public TpSlotCExtNative(Object callable) {
            super(callable);
        }
    }

    /**
     * HPy slots currently "pretend" to be Python magic methods. They are added to a newly
     * constructed type in HPyCreateTypeFromSpecNode, which triggers {@code TpSlots#updateSlot} for
     * every added slot. In the future HPy slots should be treated like native slots, except that
     * they will have another dispatch method in CallXYZSlot nodes, see {@link TpSlotLen} for an
     * example.
     */
    public static final class TpSlotHPyNative extends TpSlotNative {
        public TpSlotHPyNative(Object callable) {
            super(callable);
        }
    }

    /**
     * Represents a slot that should delegate to a builtin node. Outside of this package, builtin
     * slots are opaque. The leaf subclasses of this class are specific to concrete signature of
     * some slot and can be called by executing appropriate CallSlotXYZNode.
     *
     * In general, builtin slots can be executed as uncached or as AST inlined nodes. All builtin
     * slots should be singletons shared across all contexts running on the JVM. Slot nodes should
     * also extend the appropriate {@link PythonBuiltinBaseNode}, so that they can be AST inlined
     * when invoked as regular Python functions.
     *
     * The actual insides of a builtin slot are only relevant to the corresponding CallSlotXYZNode
     * node. There are convenience base classes, which may or may not be used by concrete builtin
     * slot types.
     */
    public abstract static non-sealed class TpSlotBuiltin<T extends PythonBuiltinBaseNode> extends TpSlotManaged {
        private final NodeFactory<T> nodeFactory;

        protected TpSlotBuiltin(NodeFactory<T> nodeFactory) {
            assert nodeFactory != null;
            this.nodeFactory = nodeFactory;
        }

        /**
         * Slot nodes should extend one of the n-ary builtin nodes, but they should also provide
         * execute method with signature that matches the CPython slot signature, which can be more
         * narrow than the generic N-ary builtin execute method. The return type of this method
         * should be overriden to more specific slot node type.
         */
        protected final T createNode() {
            return nodeFactory.createNode();
        }

        protected final Class<? extends T> getNodeClass() {
            return nodeFactory.getNodeClass();
        }

        protected final NodeFactory<T> getNodeFactory() {
            return nodeFactory;
        }

        final SlotSignature getSlotSignatureAnnotation() {
            return nodeFactory.getNodeClass().getAnnotation(SlotSignature.class);
        }

        /**
         * Should perform any per-language initialization. May be called multiple times, but only
         * during {@link PythonLanguage} initialization and from a single thread.
         */
        public abstract void initialize(PythonLanguage language);

        /**
         * Creates a wrapper descriptor {@link PBuiltinFunction} in GraalPython wrapping the
         * builtin. Some slots produce multiple magic methods, the {@code wrapper} determines which
         * wrapper should be created.
         * <p>
         * If the signature or name are not compatible with given slot, then this method should
         * return {@code null}. This can happen if user puts incompatible builtin slot value into a
         * slot, e.g., puts {@code descrsetfunc} builtin into {@code tp_getattro} slot.
         */
        public abstract PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper);

        // helper method
        final PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, BuiltinSlotWrapperSignature signature, PExternalFunctionWrapper wrapper,
                        NodeFactory<? extends PythonBuiltinBaseNode> factory) {
            String name = tsName.toJavaStringUncached();
            RootCallTarget callTarget = createBuiltinCallTarget(core.getLanguage(), signature, factory, name);
            Builtin builtin = ((BuiltinFunctionRootNode) callTarget.getRootNode()).getBuiltin();
            PBuiltinFunction function = core.factory().createWrapperDescriptor(tsName, type, numDefaults(builtin), 0, callTarget, this, wrapper);
            function.setAttribute(T___DOC__, PNone.NONE);
            return function;
        }

        /**
         * Helper method for creating a {@link RootCallTarget}, it may be used in
         * {@link #initialize(PythonLanguage)} to create the slot call target if the slot node can
         * be wrapped by {@link BuiltinFunctionRootNode}.
         */
        static RootCallTarget createBuiltinCallTarget(PythonLanguage language, BuiltinSlotWrapperSignature signature, NodeFactory<? extends PythonBuiltinBaseNode> factory, String name) {
            SlotSignature slotSignature = factory.getNodeClass().getAnnotation(SlotSignature.class);
            Builtin builtin = new Slot2Builtin(slotSignature, name, signature);
            Class<?> nodeClass = NodeFactoryBase.getWrappedNodeClass(factory);
            assert nodeClass == factory.getNodeClass() || slotSignature == null : //
                            "@SlotSignature cannot be used for builtin slot nodes that are wrapped into multiple builtin magic methods";
            return language.createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, builtin, factory, true), factory.getNodeClass(), nodeClass, name);
        }

        public static boolean isSlotFactory(NodeFactory<?> factory) {
            return factory.getNodeClass().getAnnotationsByType(Slot.class).length > 0 ||
                            NodeFactoryUtils.NodeFactoryBase.class.isAssignableFrom(factory.getClass());
        }
    }

    // ------------------------------------------------------------------------
    // Convenience base classes for code sharing:

    /**
     * Convenience base class for Python slots that has only one callable.
     */
    public static final class TpSlotPythonSingle extends TpSlotPython {
        private final TruffleWeakReference<Object> callable;
        private final TruffleWeakReference<Object> type;
        private final TruffleString name;

        public TpSlotPythonSingle(Object callable, Object type, TruffleString name) {
            this(callable, new TruffleWeakReference<>(type), name);
        }

        private TpSlotPythonSingle(Object callable, TruffleWeakReference<Object> type, TruffleString name) {
            assert callable != null;
            this.callable = new TruffleWeakReference<>(callable);
            this.type = type;
            this.name = name;
        }

        @Override
        public TpSlotPython forNewType(Object klass) {
            Object newCallable = Dynamic.getUncached().execute(klass, name);
            return newCallable == callable.get() ? this : new TpSlotPythonSingle(newCallable, type, name);
        }

        public Object getCallable() {
            return safeGet(callable);
        }

        public Object getType() {
            return safeGet(type);
        }
    }

    /**
     * Convenience base class for slots that are based on one builtin node and have one wrapper,
     * which is a straightforward {@link PBuiltinFunction} created from the builtin node.
     */
    public abstract static class TpSlotBuiltinBase<T extends PythonBuiltinBaseNode> extends TpSlotBuiltin<T> {
        private final BuiltinSlotWrapperSignature signature;
        private final PExternalFunctionWrapper wrapper;

        protected TpSlotBuiltinBase(NodeFactory<T> nodeFactory, BuiltinSlotWrapperSignature signature, PExternalFunctionWrapper wrapper) {
            super(nodeFactory);
            this.signature = signature;
            this.wrapper = wrapper;
        }

        @Override
        public PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            if (wrapper != this.wrapper) {
                return null;
            }
            return createBuiltin(core, type, tsName, signature, wrapper, getNodeFactory());
        }
    }

    /**
     * Registry for builtin slots that need to provide a call target to call the builtin. If slots
     * tend to be complex or need thread state/frame/etc., we prefer to call the PE'd call target as
     * opposed to doing boundary call with appropriate call context to the uncached node.
     */
    abstract static class TpSlotBuiltinCallTargetRegistry {
        private static final AtomicInteger globalIndex = new AtomicInteger();
        private static boolean countRead = false;

        static int getNextCallTargetIndex() {
            assert !countRead : "Some builtin is initialized after the array with call targets was created, " +
                            "make sure that static initializers of all builtin slots run before context initialization " +
                            "and that all call target indexes are initialized when the static initializers are run.";
            return globalIndex.getAndIncrement();
        }

        private TpSlotBuiltinCallTargetRegistry() {
        }
    }

    public static int getBuiltinsCallTargetsCount() {
        TpSlotBuiltinCallTargetRegistry.countRead = true;
        return TpSlotBuiltinCallTargetRegistry.globalIndex.get();
    }

    /**
     * Convenience base class for slots that are known to be simple enough such that in the
     * polymorphic case we can just call the uncached node leading to a polymorphic boundary call.
     * <p>
     * If there is a new builtin slot implementation in the future that is not this simple, one
     * should just change the builtin slot class to support also "complex" builtin slot
     * implementations. See other slots for example how to do that.
     */
    public abstract static class TpSlotSimpleBuiltinBase<T extends PythonBuiltinBaseNode> extends TpSlotBuiltinBase<T> {
        protected TpSlotSimpleBuiltinBase(NodeFactory<T> nodeFactory, BuiltinSlotWrapperSignature signature, PExternalFunctionWrapper wrapper) {
            super(nodeFactory, signature, wrapper);
            assert getSlotSignatureAnnotation() == null;
        }

        @Override
        public void initialize(PythonLanguage language) {
            // nop
        }
    }
}
