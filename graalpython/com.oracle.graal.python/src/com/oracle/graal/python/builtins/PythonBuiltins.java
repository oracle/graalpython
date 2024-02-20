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
package com.oracle.graal.python.builtins;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___AITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ANEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___AWAIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DEL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ILSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IMATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INVERT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ISUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RDIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RLSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___XOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.ensureNoJavaString;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.graal.python.util.BiConsumer;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class PythonBuiltins {
    private final Map<Object, Object> builtinConstants = new HashMap<>();
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
            PythonBuiltinClassType constructsClass = builtin.constructsClass();
            if ((annotation.defineModule().length() > 0 || annotation.extendsModule().length() > 0) && constructsClass == PythonBuiltinClassType.nil) {
                assert !builtin.isGetter();
                assert !builtin.isSetter();
                assert annotation.extendClasses().length == 0;
                // for module functions, explicit self is false by default
                declaresExplicitSelf = builtin.declaresExplicitSelf();
            } else if (builtin.constructsClass() != PythonBuiltinClassType.nil) {
                declaresExplicitSelf = false;
            } else {
                declaresExplicitSelf = true;
            }
            TruffleString tsName = toTruffleStringUncached(builtin.name());
            RootCallTarget callTarget = core.getLanguage().createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, builtin, factory, declaresExplicitSelf), factory.getNodeClass(),
                            builtin.name());
            Object builtinDoc = builtin.doc().isEmpty() ? PNone.NONE : toTruffleStringUncached(builtin.doc());
            int flags = PBuiltinFunction.getFlags(builtin, callTarget);
            if (constructsClass != PythonBuiltinClassType.nil) {
                assert !builtin.isGetter() && !builtin.isSetter() && !builtin.isClassmethod() && !builtin.isStaticmethod();
                // we explicitly do not make these "staticmethods" here, since CPython also doesn't
                // for builtin types
                PBuiltinFunction newFunc = core.factory().createBuiltinFunction(T___NEW__, constructsClass, numDefaults(builtin), flags, callTarget);
                PBuiltinMethod newMethod = core.factory().createBuiltinMethod(constructsClass, newFunc);
                PythonBuiltinClass builtinClass = core.lookupType(constructsClass);
                builtinClass.setAttributeUnsafe(T___NEW__, newMethod);
                final Object currentBuiltinDoc = builtinClass.getAttribute(T___DOC__);
                if (PGuards.isPNone(currentBuiltinDoc)) {
                    builtinClass.setAttribute(T___DOC__, builtinDoc);
                }
            } else {
                PBuiltinFunction function;
                if (isSlotMethod(builtin)) {
                    function = core.factory().createWrapperDescriptor(tsName, null, numDefaults(builtin), flags, callTarget);
                } else {
                    function = core.factory().createBuiltinFunction(tsName, null, numDefaults(builtin), flags, callTarget);
                }
                function.setAttribute(T___DOC__, builtinDoc);
                BoundBuiltinCallable<?> callable = function;
                if (builtin.isGetter() || builtin.isSetter()) {
                    assert !builtin.isClassmethod() && !builtin.isStaticmethod();
                    PBuiltinFunction get = builtin.isGetter() ? function : null;
                    PBuiltinFunction set = builtin.isSetter() ? function : null;
                    callable = core.factory().createGetSetDescriptor(get, set, tsName, null, builtin.allowsDelete());
                } else if (builtin.isClassmethod()) {
                    assert !builtin.isStaticmethod();
                    callable = core.factory().createBuiltinClassmethodFromCallableObj(function);
                } else if (builtin.isStaticmethod()) {
                    callable = core.factory().createStaticmethodFromCallableObj(function);
                }
                builtinFunctions.put(toTruffleStringUncached(builtin.name()), callable);
            }
        });
    }

    // All methods that are really slots in CPython
    private static final Set<String> SLOTS = Set.of(J___ABS__, J___ADD__, J___AITER__, J___AND__, J___ANEXT__, J___AWAIT__, J___BOOL__, J___CALL__, J___CONTAINS__, J___DELATTR__, J___DELETE__,
                    J___DELITEM__, J___DEL__, J___DIVMOD__, J___EQ__, J___FLOAT__, J___FLOORDIV__, J___GETATTRIBUTE__, J___GETATTR__, J___GETITEM__, J___GET__, J___GE__, J___GT__, J___HASH__,
                    J___IADD__, J___IAND__, J___IFLOORDIV__, J___ILSHIFT__, J___IMATMUL__, J___IMOD__, J___IMUL__, J___INDEX__, J___INIT__, J___INT__, J___INVERT__, J___IOR__, J___IPOW__,
                    J___IRSHIFT__, J___ISUB__, J___ITER__, J___ITRUEDIV__, J___IXOR__, J___LEN__, J___LE__, J___LSHIFT__, J___LT__, J___MATMUL__, J___MOD__, J___MUL__, J___NEG__, J___NEW__,
                    J___NEXT__, J___NE__, J___OR__, J___POS__, J___POW__, J___RADD__, J___RAND__, J___RDIVMOD__, J___REPR__, J___RFLOORDIV__, J___RLSHIFT__, J___RMATMUL__, J___RMOD__, J___RMUL__,
                    J___ROR__, J___RPOW__, J___RRSHIFT__, J___RSHIFT__, J___RSUB__, J___RTRUEDIV__, J___RXOR__, J___SETATTR__, J___SETITEM__, J___SET__, J___STR__, J___SUB__, J___TRUEDIV__,
                    J___XOR__);

    private static boolean isSlotMethod(Builtin builtin) {
        return builtin.name().startsWith("__") && SLOTS.contains(builtin.name());
    }

    /**
     * Run any actions that can only be run in the post-initialization step, that is, if we're
     * actually going to start running rather than just pre-initializing.
     */
    public void postInitialize(@SuppressWarnings("unused") Python3Core core) {
        // nothing to do by default
    }

    private void initializeEachFactoryWith(BiConsumer<NodeFactory<? extends PythonBuiltinBaseNode>, Builtin> func) {
        List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> factories = getNodeFactories();
        assert factories != null : "No factories found. Override getFactories() to resolve this.";
        PythonOS currentOs = PythonOS.getPythonOS();
        for (NodeFactory<? extends PythonBuiltinBaseNode> factory : factories) {
            Boolean needsFrame = null;
            for (Builtin builtin : factory.getNodeClass().getAnnotationsByType(Builtin.class)) {
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

    private static int numDefaults(Builtin builtin) {
        int parameterNameCount = builtin.parameterNames().length;
        int maxNumPosArgs = Math.max(builtin.minNumOfPositionalArgs(), parameterNameCount);
        if (builtin.maxNumOfPositionalArgs() >= 0) {
            maxNumPosArgs = builtin.maxNumOfPositionalArgs();
            assert parameterNameCount == 0 : "either give all parameter names explicitly, or define the max number: " + builtin.name();
        }
        return maxNumPosArgs - builtin.minNumOfPositionalArgs();
    }

    private void addBuiltinConstantInternal(Object name, Object value) {
        assert name instanceof TruffleString || name instanceof HiddenAttr;
        builtinConstants.put(name, ensureNoJavaString(value));
    }

    protected final void addBuiltinConstant(HiddenAttr name, Object value) {
        addBuiltinConstantInternal(name, value);
    }

    protected final void addBuiltinConstant(String name, Object value) {
        addBuiltinConstantInternal(toTruffleStringUncached(name), value);
    }

    protected final void addBuiltinConstant(TruffleString name, Object value) {
        addBuiltinConstantInternal(name, value);
    }

    protected Object getBuiltinConstant(TruffleString name) {
        return builtinConstants.get(name);
    }

    void addConstantsToModuleObject(PythonObject obj) {
        for (Map.Entry<Object, Object> entry : builtinConstants.entrySet()) {
            Object constant = assertNoJavaString(entry.getKey());
            Object value = assertNoJavaString(entry.getValue());
            if (constant instanceof HiddenAttr attr) {
                HiddenAttr.WriteNode.executeUncached(obj, attr, value);
            } else {
                assert constant instanceof TruffleString;
                obj.setAttribute(constant, value);
            }
        }
    }

    void addFunctionsToModuleObject(PythonObject obj, PythonObjectSlowPathFactory factory) {
        for (Entry<TruffleString, BoundBuiltinCallable<?>> entry : builtinFunctions.entrySet()) {
            Object value;
            assert obj instanceof PythonModule || obj instanceof PythonBuiltinClass : "unexpected object while adding builtins";
            if (obj instanceof PythonModule) {
                value = factory.createBuiltinMethod(obj, (PBuiltinFunction) entry.getValue());
            } else {
                value = entry.getValue().boundToObject(((PythonBuiltinClass) obj).getType(), factory);
            }
            obj.setAttribute(entry.getKey(), value);
        }
    }
}
