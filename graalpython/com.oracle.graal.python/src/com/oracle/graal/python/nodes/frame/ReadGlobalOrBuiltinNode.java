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
package com.oracle.graal.python.nodes.frame;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NameError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.instrumentation.NodeObjectDescriptor;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(shortName = "read_global")
public abstract class ReadGlobalOrBuiltinNode extends ExpressionNode implements ReadNode, GlobalNode {
    private static final ReadGlobalOrBuiltinNode UNCACHED = new ReadGlobalOrBuiltinNode(null) {
        @Override
        public Object executeWithGlobals(VirtualFrame frame, Object globals) {
            throw CompilerDirectives.shouldNotReachHere("uncached ReadGlobalOrBuiltinNode must be used with #read");
        }

        @Override
        public Object read(Frame frame, Object globals, String name) {
            Object result;
            if (globals instanceof PythonModule) {
                result = ReadAttributeFromObjectNode.getUncached().execute(globals, name);
            } else {
                result = PyDictGetItem.getUncached().execute(frame, (PDict) globals, name);
            }
            if (result == null || result == PNone.NO_VALUE) {
                PythonContext context = PythonContext.get(this);
                PythonModule builtins = context.getCore().lookupBuiltinModule(BuiltinNames.BUILTINS);
                result = ReadAttributeFromObjectNode.getUncached().execute(builtins, name);
            }
            if (result != PNone.NO_VALUE) {
                return result;
            } else {
                throw PRaiseNode.raiseUncached(this, NameError, ErrorMessages.NAME_NOT_DEFINED, name);
            }
        }
    };

    @CompilationFinal private boolean wasReadFromModule = false;
    @Child private ReadBuiltinNode readFromBuiltinsNode;

    @Override
    public final Object execute(VirtualFrame frame) {
        return executeWithGlobals(frame, getGlobals(frame));
    }

    @Override
    public final int executeInt(VirtualFrame frame) throws UnexpectedResultException {
        Object value = execute(frame);
        if (value instanceof Integer) {
            return (int) value;
        }
        throw new UnexpectedResultException(value);
    }

    @Override
    public final long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        Object value = execute(frame);
        if (value instanceof Long) {
            return (long) value;
        }
        throw new UnexpectedResultException(value);
    }

    @Override
    public final double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        Object o = execute(frame);
        if (o instanceof Double) {
            return (double) o;
        }
        throw new UnexpectedResultException(o);
    }

    @Override
    public final boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        Object o = execute(frame);
        if (o instanceof Boolean) {
            return (boolean) o;
        }
        throw new UnexpectedResultException(o);
    }

    public abstract Object executeWithGlobals(VirtualFrame frame, Object globals);

    public Object read(Frame frame, Object globals, String name) {
        assert name == attributeId : "cached ReadGlobalOrBuiltinNode can only be used with cached attributeId";
        return executeWithGlobals((VirtualFrame) frame, globals);
    }

    protected final String attributeId;

    protected ReadGlobalOrBuiltinNode(String attributeId) {
        this.attributeId = attributeId;
    }

    public static ReadGlobalOrBuiltinNode create(String attributeId) {
        return ReadGlobalOrBuiltinNodeGen.create(attributeId);
    }

    public static ReadGlobalOrBuiltinNode getUncached() {
        return UNCACHED;
    }

    @Override
    public StatementNode makeWriteNode(ExpressionNode rhs) {
        return WriteGlobalNode.create(attributeId, rhs);
    }

    @Specialization(guards = {"isSingleContext()", "globals == cachedGlobals"}, limit = "1")
    protected Object readGlobalCached(@SuppressWarnings("unused") PythonModule globals,
                    @Shared("readFromModule") @Cached ReadAttributeFromObjectNode readFromModuleNode,
                    @Cached(value = "globals", weak = true) PythonModule cachedGlobals) {
        Object result = readFromModuleNode.execute(cachedGlobals, attributeId);
        return returnGlobalOrBuiltin(result);
    }

    @Specialization(replaces = "readGlobalCached")
    protected Object readGlobal(PythonModule globals,
                    @Shared("readFromModule") @Cached ReadAttributeFromObjectNode readFromModuleNode) {
        Object result = readFromModuleNode.execute(globals, attributeId);
        return returnGlobalOrBuiltin(result);
    }

    @Specialization(guards = {"isSingleContext()", "globals == cachedGlobals", "isBuiltinDict(cachedGlobals)",
                    "cachedGlobals.getDictStorage() == cachedStorage"}, limit = "1")
    protected Object readGlobalBuiltinDictCachedUnchangedStorage(@SuppressWarnings("unused") PDict globals,
                    @SuppressWarnings("unused") @Cached(value = "globals", weak = true) PDict cachedGlobals,
                    @Cached(value = "globals.getDictStorage()", weak = true) HashingStorage cachedStorage,
                    @CachedLibrary("cachedStorage") HashingStorageLibrary hlib) {
        Object result = hlib.getItem(cachedStorage, attributeId);
        return returnGlobalOrBuiltin(result == null ? PNone.NO_VALUE : result);
    }

    @Specialization(guards = {"isSingleContext()", "globals == cachedGlobals",
                    "isBuiltinDict(cachedGlobals)"}, replaces = "readGlobalBuiltinDictCachedUnchangedStorage", limit = "1")
    protected Object readGlobalBuiltinDictCached(@SuppressWarnings("unused") PDict globals,
                    @Cached(value = "globals", weak = true) PDict cachedGlobals,
                    @CachedLibrary(value = "cachedGlobals.getDictStorage()") HashingStorageLibrary hlib) {
        Object result = hlib.getItem(cachedGlobals.getDictStorage(), attributeId);
        return returnGlobalOrBuiltin(result == null ? PNone.NO_VALUE : result);
    }

    @Specialization(guards = "isBuiltinDict(globals)", replaces = {"readGlobalBuiltinDictCached", "readGlobalBuiltinDictCachedUnchangedStorage"}, limit = "3")
    protected Object readGlobalBuiltinDict(@SuppressWarnings("unused") PDict globals,
                    @Bind("globals.getDictStorage()") HashingStorage storage,
                    @CachedLibrary("storage") HashingStorageLibrary hlib) {
        Object result = hlib.getItem(storage, attributeId);
        return returnGlobalOrBuiltin(result == null ? PNone.NO_VALUE : result);
    }

    @Specialization
    protected Object readGlobalDictGeneric(VirtualFrame frame, PDict globals,
                    @Cached GetItemNode getItemNode,
                    @Cached IsBuiltinClassProfile errorProfile) {
        try {
            Object result = getItemNode.execute(frame, globals, attributeId);
            return returnGlobalOrBuiltin(result);
        } catch (PException e) {
            e.expect(KeyError, errorProfile);
            return returnGlobalOrBuiltin(PNone.NO_VALUE);
        }
    }

    private Object returnGlobalOrBuiltin(Object result) {
        if (result != PNone.NO_VALUE) {
            if (!wasReadFromModule) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                wasReadFromModule = true;
            }
            return result;
        } else {
            if (readFromBuiltinsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readFromBuiltinsNode = insert(ReadBuiltinNodeGen.create(attributeId));
            }
            return readFromBuiltinsNode.execute();
        }
    }

    @Override
    public String getAttributeId() {
        return attributeId;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return StandardTags.ReadVariableTag.class == tag || super.hasTag(tag);
    }

    @Override
    public Object getNodeObject() {
        return NodeObjectDescriptor.createNodeObjectDescriptor(StandardTags.ReadVariableTag.NAME, attributeId);
    }
}

abstract class ReadBuiltinNode extends PNodeWithContext {
    protected static final Assumption singleCoreNotInitialized = Truffle.getRuntime().createAssumption();

    protected final ConditionProfile isBuiltinProfile = ConditionProfile.createBinaryProfile();
    protected final String attributeId;

    @CompilationFinal private ConditionProfile isCoreInitializedProfile;

    @Child protected ReadAttributeFromObjectNode readFromBuiltinsNode = ReadAttributeFromObjectNode.create();
    @Child private PRaiseNode raiseNode;

    public abstract Object execute();

    protected ReadBuiltinNode(String attributeId) {
        this.attributeId = attributeId;
    }

    // TODO: (tfel) Think about how we can globally catch writes to the builtin
    // module so we can treat anything read from it as constant here.
    @Specialization(guards = "isSingleContext()")
    Object returnBuiltinFromConstantModule(
                    @SuppressWarnings("unused") @Cached("getBuiltins()") PythonModule builtins) {
        Object builtin = readFromBuiltinsNode.execute(builtins, attributeId);
        if (isBuiltinProfile.profile(builtin != PNone.NO_VALUE)) {
            return builtin;
        } else {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            throw raiseNode.raise(NameError, ErrorMessages.NAME_NOT_DEFINED, attributeId);
        }
    }

    @Specialization
    Object returnBuiltin() {
        PythonModule builtins = getBuiltins();
        return returnBuiltinFromConstantModule(builtins);
    }

    protected PythonModule getBuiltins() {
        PythonContext context = PythonContext.get(this);
        if (ensureContextInitializedProfile().profile(context.isInitialized())) {
            return context.getBuiltins();
        } else {
            return context.lookupBuiltinModule(BuiltinNames.BUILTINS);
        }
    }

    private ConditionProfile ensureContextInitializedProfile() {
        if (isCoreInitializedProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            isCoreInitializedProfile = ConditionProfile.createBinaryProfile();
        }
        return isCoreInitializedProfile;
    }
}
