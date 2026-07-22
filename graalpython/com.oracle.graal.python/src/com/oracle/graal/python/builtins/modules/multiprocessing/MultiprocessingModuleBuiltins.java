/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.multiprocessing;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.PythonOS;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.IndirectCallData.InteropCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_multiprocessing")
public class MultiprocessingModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MultiprocessingModuleBuiltinsFactory.getFactories();
    }

    @GenerateNodeFactory
    @Builtin(name = "recv", minNumOfPositionalArgs = 2, parameterNames = {"handle", "size"}, os = PythonOS.PLATFORM_WIN32)
    @ArgumentClinic(name = "handle", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int)
    abstract static class Recv extends PythonBinaryClinicBuiltinNode {
        @Specialization
        PBytes doit(VirtualFrame frame, int handle, int size,
                        @Bind PythonContext context,
                        @Bind PythonLanguage language,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            byte[] buffer = new byte[size];
            try {
                int received = posixLib.recv(context.getPosixSupport(), handle, buffer, 0, size, 0);
                if (received == size) {
                    return PFactory.createBytes(language, buffer);
                }
                byte[] result = new byte[received];
                System.arraycopy(buffer, 0, result, 0, received);
                return PFactory.createBytes(language, result);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MultiprocessingModuleBuiltinsClinicProviders.RecvClinicProviderGen.INSTANCE;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "send", minNumOfPositionalArgs = 2, parameterNames = {"handle", "data"}, os = PythonOS.PLATFORM_WIN32)
    @ArgumentClinic(name = "handle", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    abstract static class Send extends PythonBinaryClinicBuiltinNode {
        @Specialization(limit = "3")
        static int doit(VirtualFrame frame, int handle, Object buffer,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") InteropCallData callData,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                return posixLib.send(context.getPosixSupport(), handle, bytes, 0, bufferLib.getBufferLength(buffer), 0);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } finally {
                bufferLib.release(buffer, frame, callData);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MultiprocessingModuleBuiltinsClinicProviders.SendClinicProviderGen.INSTANCE;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "closesocket", minNumOfPositionalArgs = 1, parameterNames = {"handle"}, os = PythonOS.PLATFORM_WIN32)
    @ArgumentClinic(name = "handle", conversion = ArgumentClinic.ClinicConversion.Int)
    abstract static class CloseSocket extends PythonUnaryClinicBuiltinNode {
        @Specialization
        PNone doit(VirtualFrame frame, int handle,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.close(context.getPosixSupport(), handle);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MultiprocessingModuleBuiltinsClinicProviders.CloseSocketClinicProviderGen.INSTANCE;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "sem_unlink", parameterNames = {"name"})
    abstract static class SemUnlink extends PythonUnaryBuiltinNode {
        @Specialization
        PNone doit(VirtualFrame frame, TruffleString name,
                        @Bind("getPosixSupport()") PosixSupport posixSupport,
                        @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                        @Bind Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.semUnlink(posixSupport, posixLib.createCStringFromString(posixSupport, name));
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }
}
