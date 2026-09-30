/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.frame;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.lib.PyObjectGetItem.PyObjectGetItemOrNull;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromPythonObjectNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.OperationProxy.Proxyable;
import com.oracle.truffle.api.bytecode.StoreBytecodeIndex;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.PropertyGetter;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@GenerateInline(false)       // footprint reduction 48 -> 30
@Proxyable(storeBytecodeIndex = false, allowUncached = true)
@ConstantOperand(type = TruffleString.class)
@ImportStatic({PGuards.class, PNodeWithContext.class, PythonUtils.class})
public abstract class ReadGlobalOrBuiltinNode extends Node {
    public abstract Object execute(VirtualFrame frame, TruffleString name);

    public Object read(Frame frame, Object globals, TruffleString name) {
        CompilerAsserts.partialEvaluationConstant(name);
        // reloading globals is not efficient, but this entry point is here just because it is used
        // from the manual interpreter and only for the time being until the manual interpreter is
        // removed
        assert PArguments.getGlobals(frame) == globals;
        return execute((VirtualFrame) frame, name);
    }

    @NeverDefault
    public static ReadGlobalOrBuiltinNode create() {
        return ReadGlobalOrBuiltinNodeGen.create();
    }

    public static ReadGlobalOrBuiltinNode getUncached() {
        return ReadGlobalOrBuiltinNodeGen.getUncached();
    }

    /**
     * If globals are a dictionary owned by {@link PythonModule}, then using the shape flag
     * {@link PythonObject#HAS_MATERIALIZED_DICT} as part of shape check, we can detect when the dictionary storage
     * of that module has changed, and we must invalidate our cache. This allows us to check only identity of
     * the globals object and its shape and in the runtime guards avoid the pointer chasing done in this method,
     * which should be used only at specialization time.
     */
    public static PythonModule getGlobalsOwner(VirtualFrame frame) {
        CompilerAsserts.neverPartOfCompilation();
        Object obj = PArguments.getGlobals(frame);
        if (obj instanceof PDict dict && dict.getDictStorage() instanceof DynamicObjectStorage dom && dom.getStore() instanceof PythonModule module) {
            return module;
        }
        return null;
    }

    public static Shape getGlobalsStorageShape(VirtualFrame frame) {
        Object obj = PArguments.getGlobals(frame);
        if (obj instanceof PDict dict && dict.getDictStorage() instanceof DynamicObjectStorage dom) {
            return dom.getStore().getShape();
        }
        return null;
    }

    public static Shape getGlobalsStorageShapeIfPropMissing(VirtualFrame frame, TruffleString name) {
        Object obj = PArguments.getGlobals(frame);
        if (obj instanceof PDict dict && dict.getDictStorage() instanceof DynamicObjectStorage dom) {
            Shape shape = dom.getStore().getShape();
            if (!shape.hasProperty(name)) {
                return shape;
            }
        }
        return null;
    }

    @ForceQuickening
    @Specialization(guards = {
                    /* static: */ "isSingleContext(inliningTarget)", "globalsOwner != null", "globalsShape != null", //
                    /* static: */ "!hasMaterializedDict(globalsShape)", "builtinGetter != null", //
                    /* dynamic: */ "getGlobals(frame) == cachedGlobals", "globalsShape == getGlobalsOwnerShape(globalsOwner)", //
                    /* dynamic: */ "getterAccepts(builtinGetter, builtins)", "!isNoValue(result)"}, //
                    excludeForUncached = true, limit = "1")
    public static Object readBuiltinFastPath(VirtualFrame frame, TruffleString attributeId,
                    @Bind Node inliningTarget,
                    @Cached("getGlobals(frame)") Object cachedGlobals,
                    @Cached("getGlobalsOwner(frame)") PythonModule globalsOwner,
                    @Cached("getGlobalsStorageShapeIfPropMissing(frame, attributeId)") Shape globalsShape,
                    @Cached("getBuiltins(inliningTarget)") PythonModule builtins,
                    @Cached("getBuiltinGetter(builtins, attributeId)") PropertyGetter builtinGetter,
                    @Bind("getValue(builtins, builtinGetter)") Object result) {
        // Note: this is inlined version of ReadBuiltinNode#returnBuiltinFromConstantModule, keep in sync
        // Both shape checks also guard against replacement of the module-backed dict storages.
        return result;
    }

    public static PythonModule getBuiltins(Node node) {
        CompilerAsserts.neverPartOfCompilation();
        PythonContext context = PythonContext.get(node);
        return context.isInitialized() ? context.getBuiltins() : context.lookupBuiltinModule(BuiltinNames.T_BUILTINS);
    }

    public static PropertyGetter getBuiltinGetter(PythonModule builtins, TruffleString name) {
        // The getter retains the shape, so no separate cached builtins shape is needed.
        Shape shape = builtins.getShape();
        return PGuards.hasMaterializedDict(shape) ? null : PythonUtils.getPropertyGetterWithFinalAssumption(shape, name);
    }

    @ForceQuickening
    @Specialization(guards = {"cachedGlobalsShape != null", "cachedGlobalsShape == getGlobalsStorageShape(frame)"}, //
                    replaces = "readBuiltinFastPath", excludeForUncached = true, limit = "1")
    public static Object readBuiltinFromStorage(VirtualFrame frame, TruffleString attributeId,
                    @Cached("getGlobalsStorageShapeIfPropMissing(frame, attributeId)") Shape cachedGlobalsShape,
                    @Shared("readFromBuiltinsNode") @Cached ReadBuiltinNode readFromBuiltinsNode) {
        return readFromBuiltinsNode.execute(attributeId);
    }

    public static Object readFastFromGlobalStore(VirtualFrame frame, TruffleString name, ReadAttributeFromPythonObjectNode readNode) {
        Object obj = PArguments.getGlobals(frame);
        if (obj instanceof PDict dict && dict.getDictStorage() instanceof DynamicObjectStorage dom) {
            return readNode.execute(dom.getStore(), name, PNone.NO_VALUE);
        }
        return PNone.NO_VALUE;
    }

    @NonIdempotent
    public static Object getValue(PythonModule m, PropertyGetter getter) {
        assert m.checkDictFlags();
        return getter.get(m);
    }

    @NonIdempotent
    public static Object getGlobals(VirtualFrame frame) {
        return PArguments.getGlobals(frame);
    }

    @NonIdempotent
    public static Shape getGlobalsOwnerShape(PythonModule module) {
        return module.getShape();
    }

    @NonIdempotent
    public static boolean getterAccepts(PropertyGetter getter, PythonModule module) {
        return getter.accepts(module);
    }

    @ForceQuickening
    @Specialization(guards = {
                    /* static: */ "isSingleContext(inliningTarget)", "globalsOwner != null", "!hasMaterializedDict(globalsShape)", "getter != null", //
                    /* dynamic: */ "getGlobals(frame) == cachedGlobals", "getterAccepts(getter, globalsOwner)", "!isNoValue(result)"}, //
                    replaces = "readBuiltinFromStorage", excludeForUncached = true, limit = "1")
    public static Object readGlobalFastPath(VirtualFrame frame, TruffleString attributeId,
                    @Bind Node inliningTarget,
                    @Cached("getGlobals(frame)") Object cachedGlobals,
                    @Cached("getGlobalsOwner(frame)") PythonModule globalsOwner,
                    @Cached("globalsOwner.getShape()") Shape globalsShape,
                    @Cached("getPropertyGetterWithFinalAssumption(globalsShape, attributeId)") PropertyGetter getter,
                    @Bind("getValue(globalsOwner, getter)") Object result) {
        CompilerAsserts.partialEvaluationConstant(attributeId);
        // since the shape does not have MATERIALIZED_DICT shape, and we do shape check on the owner,
        // the dict storage must not have been replaced
        assert cachedGlobals instanceof PDict d && //
                        d.getDictStorage() instanceof DynamicObjectStorage s && //
                        s.getStore() == globalsOwner;
        return result;
    }

    @ForceQuickening
    @Specialization(guards = "!isNoValue(result)", replaces = "readGlobalFastPath", excludeForUncached = true, limit = "1")
    public static Object readGlobalFromStorage(VirtualFrame frame, TruffleString attributeId,
                    @Cached(inline = false) ReadAttributeFromPythonObjectNode readNode,
                    @Bind("readFastFromGlobalStore(frame, attributeId, readNode)") Object result) {
        return result;
    }

    @ForceQuickening
    @StoreBytecodeIndex
    @Specialization(replaces = "readGlobalFromStorage")
    public static Object readGlobalOrBuiltinGeneric(VirtualFrame frame, TruffleString attributeId,
                    @Bind Node inliningTarget,
                    @Shared("readFromBuiltinsNode") @Cached ReadBuiltinNode readFromBuiltinsNode,
                    @Exclusive @Cached InlinedBranchProfile wasReadFromModule,
                    @Cached PyObjectGetItemOrNull getItemNode) {
        PythonObject globalsObj = PArguments.getGlobals(frame);
        if (!(globalsObj instanceof PDict globals)) {
            throw raiseSystemError(inliningTarget);
        }
        Object result = getItemNode.execute(frame, inliningTarget, globals, attributeId);
        if (result != null) {
            wasReadFromModule.enter(inliningTarget);
            return result;
        } else {
            return readFromBuiltinsNode.execute(attributeId);
        }
    }

    @InliningCutoff
    private static PException raiseSystemError(Node inliningTarget) {
        CompilerDirectives.transferToInterpreter();
        throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
    }
}
