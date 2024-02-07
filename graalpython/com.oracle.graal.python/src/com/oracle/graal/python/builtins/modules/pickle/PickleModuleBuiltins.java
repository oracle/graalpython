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
package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_DEFAULT_PICKLE_PROTOCOL;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_pickle")
public final class PickleModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PickleModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PickleState state = new PickleState();
        final PickleState.PickleStateInitNode initNode = PickleStateFactory.PickleStateInitNodeGen.getUncached();
        initNode.execute(state);
        HiddenAttr.WriteNode.executeUncached(core.lookupType(PythonBuiltinClassType.Pickler), HiddenAttr.PICKLE_STATE, state);
    }

    // types
    @Builtin(name = "PickleBuffer", minNumOfPositionalArgs = 2, parameterNames = {"$cls", "object"}, constructsClass = PythonBuiltinClassType.PickleBuffer)
    @GenerateNodeFactory
    abstract static class ConstructPickleBufferNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        static PPickleBuffer construct(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object object,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("object") PythonBufferAcquireLibrary acquireLib,
                        @Cached PythonObjectFactory factory) {
            Object buffer = acquireLib.acquire(object, BufferFlags.PyBUF_FULL_RO, frame, indirectCallData);
            return factory.createPickleBuffer(buffer);
        }
    }

    @Builtin(name = "Pickler", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.Pickler, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ConstructPicklerNode extends PythonVarargsBuiltinNode {
        @Specialization
        PPickler construct(Object cls, @SuppressWarnings("unused") Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached PythonObjectFactory factory) {
            return factory.createPickler(cls);
        }
    }

    @Builtin(name = "PicklerMemoProxy", minNumOfPositionalArgs = 2, parameterNames = {"$cls", "pickler"}, constructsClass = PythonBuiltinClassType.PicklerMemoProxy)
    @GenerateNodeFactory
    abstract static class ConstructPicklerMemoProxyNode extends PythonBinaryBuiltinNode {
        @Specialization
        PPicklerMemoProxy construct(Object cls, PPickler pickler,
                        @Cached PythonObjectFactory factory) {
            return factory.createPicklerMemoProxy(pickler, cls);
        }
    }

    @Builtin(name = "UnpicklerMemoProxy", minNumOfPositionalArgs = 2, parameterNames = {"$cls", "unpickler"}, constructsClass = PythonBuiltinClassType.UnpicklerMemoProxy)
    @GenerateNodeFactory
    abstract static class ConstructUnpicklerMemoProxyNode extends PythonBinaryBuiltinNode {
        @Specialization
        PUnpicklerMemoProxy construct(Object cls, PUnpickler unpickler,
                        @Cached PythonObjectFactory factory) {
            return factory.createUnpicklerMemoProxy(unpickler, cls);
        }
    }

    @Builtin(name = "Unpickler", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.Unpickler, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ConstructUnpicklerNode extends PythonVarargsBuiltinNode {
        @Specialization
        PUnpickler construct(Object cls, @SuppressWarnings("unused") Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached PythonObjectFactory factory) {
            return factory.createUnpickler(cls);
        }
    }

    // methods
    @Builtin(name = "dump", minNumOfPositionalArgs = 3, declaresExplicitSelf = true, varArgsMarker = true, //
                    parameterNames = {"$self", "obj", "file", "protocol"}, //
                    keywordOnlyNames = {"fix_imports", "buffer_callback"})
    @ArgumentClinic(name = "protocol", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = J_DEFAULT_PICKLE_PROTOCOL, useDefaultForNone = true)
    @ArgumentClinic(name = "fix_imports", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class PickleDumpNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PickleModuleBuiltinsClinicProviders.PickleDumpNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object dump(VirtualFrame frame, @SuppressWarnings("unused") PythonModule self, Object obj, Object file, int protocol, boolean fixImports, Object bufferCallback,
                        @Bind("this") Node inliningTarget,
                        @Cached PPickler.DumpNode dumpNode,
                        @Cached PPickler.FlushToFileNode flushToFileNode,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PPickler pickler = factory.createPickler();
            pickler.setProtocol(inliningTarget, raiseNode, protocol, fixImports);
            pickler.setOutputStream(frame, inliningTarget, raiseNode, lookup, file);
            pickler.setBufferCallback(inliningTarget, raiseNode, bufferCallback);
            dumpNode.execute(frame, pickler, obj);
            flushToFileNode.execute(frame, pickler);
            return PNone.NONE;
        }
    }

    @Builtin(name = "dumps", minNumOfPositionalArgs = 2, declaresExplicitSelf = true, varArgsMarker = true, //
                    parameterNames = {"$self", "obj", "protocol"}, //
                    keywordOnlyNames = {"fix_imports", "buffer_callback"})
    @ArgumentClinic(name = "protocol", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = J_DEFAULT_PICKLE_PROTOCOL, useDefaultForNone = true)
    @ArgumentClinic(name = "fix_imports", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class PickleDumpsNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PickleModuleBuiltinsClinicProviders.PickleDumpsNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object dump(VirtualFrame frame, @SuppressWarnings("unused") PythonModule self, Object obj, int protocol, boolean fixImports, Object bufferCallback,
                        @Bind("this") Node inliningTarget,
                        @Cached PPickler.DumpNode dumpNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PPickler pickler = factory.createPickler();
            pickler.setProtocol(inliningTarget, raiseNode, protocol, fixImports);
            pickler.setBufferCallback(inliningTarget, raiseNode, bufferCallback);
            dumpNode.execute(frame, pickler, obj);
            return pickler.getString(factory);
        }
    }

    @Builtin(name = "load", minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"$self", "file"}, varArgsMarker = true, keywordOnlyNames = {"fix_imports", "encoding", "errors",
                    "buffers"})
    @ArgumentClinic(name = "fix_imports", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_ASCII_UPPERCASE")
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT")
    @GenerateNodeFactory
    abstract static class PickleLoadNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PickleModuleBuiltinsClinicProviders.PickleLoadNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object load(VirtualFrame frame, @SuppressWarnings("unused") PythonModule self, Object file, @SuppressWarnings("unused") boolean fixImports, TruffleString encoding, TruffleString errors,
                        Object buffers,
                        @Bind("this") Node inliningTarget,
                        @Cached PUnpickler.LoadNode loadNode,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PyObjectGetIter getIter,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PUnpickler unpickler = factory.createUnpickler();
            unpickler.setInputStream(frame, inliningTarget, raiseNode, lookup, file);
            unpickler.setInputEncoding(encoding, errors);
            unpickler.setBuffers(frame, inliningTarget, getIter, buffers);
            return loadNode.execute(frame, unpickler);
        }
    }

    @Builtin(name = "loads", minNumOfPositionalArgs = 2, declaresExplicitSelf = true, varArgsMarker = true, //
                    parameterNames = {"$self", "data"}, //
                    keywordOnlyNames = {"fix_imports", "encoding", "errors", "buffers"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "fix_imports", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_ASCII_UPPERCASE")
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT")
    @GenerateNodeFactory
    abstract static class PickleLoadsNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PickleModuleBuiltinsClinicProviders.PickleLoadsNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static Object loads(VirtualFrame frame, @SuppressWarnings("unused") PythonModule self, Object buffer, boolean fixImports, TruffleString encoding, TruffleString errors, Object buffers,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached PUnpickler.LoadNode loadNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached PythonObjectFactory factory) {
            try {
                PUnpickler unpickler = factory.createUnpickler();
                byte[] data = bufferLib.getCopiedByteArray(buffer);
                unpickler.setStringInput(data, data.length);
                unpickler.setInputEncoding(encoding, errors);
                unpickler.setBuffers(frame, inliningTarget, getIter, buffers);
                unpickler.setFixImports(fixImports);
                return loadNode.execute(frame, unpickler);
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }
    }
}
