/* Copyright (c) 2020, 2025, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.BuiltinNames.J__STRUCT;
import static com.oracle.graal.python.nodes.BuiltinNames.T__STRUCT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StructError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsInternedLiteral;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.struct.PStruct;
import com.oracle.graal.python.builtins.objects.struct.StructBuiltins;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.util.LRUCache;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__STRUCT, isEager = true)
public class StructModuleBuiltins extends PythonBuiltins {
    private static final int DEFAULT_CACHE_SIZE = 100;
    private static final TruffleString T_ERROR = tsInternedLiteral("error");
    private final LRUStructCache cache = new LRUStructCache(DEFAULT_CACHE_SIZE);

    static class LRUStructCache extends LRUCache<Object, PStruct> {
        public LRUStructCache(int size) {
            super(size);
        }
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StructModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant(T_ERROR, StructError);
        super.initialize(core);
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule structModule = core.lookupBuiltinModule(T__STRUCT);
        structModule.setModuleState(cache);
    }

    protected static PStruct getStruct(Node location, PythonModule structModule, Object format, StructBuiltins.ConstructStructNode constructStructNode) {
        LRUStructCache cache = structModule.getModuleState(LRUStructCache.class);
        PStruct pStruct = cache.get(location, format);
        if (pStruct == null) {
            pStruct = constructStructNode.execute(format);
            cache.put(location, format, pStruct);
        }
        return pStruct;
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({Arrays.class})
    abstract static class GetStructNode extends PNodeWithContext {
        abstract PStruct execute(Node inliningTarget, PythonModule module, Object format, StructBuiltins.ConstructStructNode constructStructNode);

        protected PStruct getStructInternal(Node location, PythonModule module, Object format, StructBuiltins.ConstructStructNode constructStructNode) {
            return getStruct(location, module, format, constructStructNode);
        }

        protected boolean eq(TruffleString s1, TruffleString s2, TruffleString.EqualNode eqNode) {
            return eqNode.execute(s1, s2, TS_ENCODING);
        }

        @Specialization(guards = {"isSingleContext()", "eq(format, cachedFormat, eqNode)"}, limit = "1")
        @SuppressWarnings("unused")
        static PStruct doCachedString(PythonModule module, TruffleString format, StructBuiltins.ConstructStructNode constructStructNode,
                        @Cached("format") TruffleString cachedFormat,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached(value = "getStructInternal($node, module, format, constructStructNode)", weak = true) PStruct cachedStruct) {
            return cachedStruct;
        }

        @Specialization(guards = {"isSingleContext()", "equals(bufferLib.getCopiedByteArray(format), cachedFormat)"}, limit = "1")
        @SuppressWarnings("unused")
        static PStruct doCachedBytes(PythonModule module, PBytes format, StructBuiltins.ConstructStructNode constructStructNode,
                        @CachedLibrary("format") PythonBufferAccessLibrary bufferLib,
                        @Cached(value = "bufferLib.getCopiedByteArray(format)", dimensions = 1) byte[] cachedFormat,
                        @Cached(value = "getStructInternal($node, module, format, constructStructNode)", weak = true) PStruct cachedStruct) {
            return cachedStruct;
        }

        @Specialization(replaces = {"doCachedString", "doCachedBytes"})
        static PStruct doGeneric(PythonModule module, Object format, StructBuiltins.ConstructStructNode constructStructNode,
                        @Bind Node location) {
            return getStruct(location, module, format, constructStructNode);
        }
    }

    @Builtin(name = "pack", minNumOfPositionalArgs = 2, parameterNames = {"$self", "format"}, takesVarArgs = true, declaresExplicitSelf = true, forceSplitDirectCalls = true)
    @GenerateNodeFactory
    abstract static class PackNode extends PythonBuiltinNode {
        @Specialization
        static Object pack(VirtualFrame frame, PythonModule self, Object format, Object[] args,
                        @Bind Node inliningTarget,
                        @Cached StructBuiltins.ConstructStructNode constructStructNode,
                        @Cached GetStructNode getStructNode,
                        @Cached StructBuiltins.StructPackNode structPackNode) {
            PStruct struct = getStructNode.execute(inliningTarget, self, format, constructStructNode);
            return structPackNode.execute(frame, struct, args);
        }
    }

    @Builtin(name = "pack_into", minNumOfPositionalArgs = 4, parameterNames = {"$self", "format", "buffer", "offset"}, declaresExplicitSelf = true, takesVarArgs = true, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "offset", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class PackIntoNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructModuleBuiltinsClinicProviders.PackIntoNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object packInto(VirtualFrame frame, PythonModule self, Object format, Object buffer, int offset, Object[] args,
                        @Bind Node inliningTarget,
                        @Cached StructBuiltins.ConstructStructNode constructStructNode,
                        @Cached GetStructNode getStructNode,
                        @Cached StructBuiltins.StructPackIntoNode structPackNode) {
            PStruct struct = getStructNode.execute(inliningTarget, self, format, constructStructNode);
            return structPackNode.execute(frame, struct, buffer, offset, args);
        }
    }

    @Builtin(name = "unpack", minNumOfPositionalArgs = 3, parameterNames = {"$self", "format", "buffer"}, declaresExplicitSelf = true, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class UnpackNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructModuleBuiltinsClinicProviders.UnpackNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object unpack(VirtualFrame frame, PythonModule self, Object format, Object buffer,
                        @Bind Node inliningTarget,
                        @Cached GetStructNode getStructNode,
                        @Cached StructBuiltins.ConstructStructNode constructStructNode,
                        @Cached StructBuiltins.StructUnpackNode structUnpackNode) {
            PStruct struct = getStructNode.execute(inliningTarget, self, format, constructStructNode);
            return structUnpackNode.execute(frame, struct, buffer);
        }
    }

    @Builtin(name = "iter_unpack", minNumOfPositionalArgs = 3, parameterNames = {"$self", "format", "buffer"}, declaresExplicitSelf = true, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class IterUnpackNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructModuleBuiltinsClinicProviders.IterUnpackNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object iterUnpack(VirtualFrame frame, PythonModule self, Object format, Object buffer,
                        @Bind Node inliningTarget,
                        @Cached StructBuiltins.ConstructStructNode constructStructNode,
                        @Cached GetStructNode getStructNode,
                        @Cached StructBuiltins.StructIterUnpackNode iterUnpackNode) {
            PStruct struct = getStructNode.execute(inliningTarget, self, format, constructStructNode);
            return iterUnpackNode.execute(frame, struct, buffer);
        }
    }

    @Builtin(name = "unpack_from", minNumOfPositionalArgs = 3, parameterNames = {"$self", "format", "buffer", "offset"}, declaresExplicitSelf = true, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "offset", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class UnpackFromNode extends PythonQuaternaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructModuleBuiltinsClinicProviders.UnpackFromNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object unpackFrom(VirtualFrame frame, PythonModule self, Object format, Object buffer, int offset,
                        @Bind Node inliningTarget,
                        @Cached StructBuiltins.ConstructStructNode constructStructNode,
                        @Cached GetStructNode getStructNode,
                        @Cached StructBuiltins.StructUnpackFromNode structUnpackNode) {
            PStruct struct = getStructNode.execute(inliningTarget, self, format, constructStructNode);
            return structUnpackNode.execute(frame, struct, buffer, offset);
        }
    }

    @Builtin(name = "calcsize", minNumOfPositionalArgs = 2, parameterNames = {"$self", "format"}, declaresExplicitSelf = true, forceSplitDirectCalls = true)
    @GenerateNodeFactory
    abstract static class CalcSizeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object calcSize(PythonModule self, Object format,
                        @Bind Node inliningTarget,
                        @Cached StructBuiltins.ConstructStructNode constructStructNode,
                        @Cached GetStructNode getStructNode) {
            PStruct struct = getStructNode.execute(inliningTarget, self, format, constructStructNode);
            return struct.getSize();
        }
    }

    @Builtin(name = "_clearcache", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ClearCacheNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object clearCache(PythonModule self) {
            LRUStructCache cache = self.getModuleState(LRUStructCache.class);
            cache.clear(this);
            return PNone.NONE;
        }
    }

    public static boolean containsNullCharacter(byte[] value) {
        for (byte b : value) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }
}
