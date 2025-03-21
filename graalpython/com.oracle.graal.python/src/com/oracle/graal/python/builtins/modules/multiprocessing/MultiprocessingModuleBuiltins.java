/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.PosixConstants.O_CREAT;
import static com.oracle.graal.python.runtime.PosixConstants.O_EXCL;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
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

    @Builtin(name = "SemLock", parameterNames = {"$cls", "kind", "value", "maxvalue", "name", "unlink"}, constructsClass = PythonBuiltinClassType.PSemLock)
    @ArgumentClinic(name = "kind", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "value", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "maxvalue", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "unlink", conversion = ArgumentClinic.ClinicConversion.IntToBoolean)
    @GenerateNodeFactory
    abstract static class SemLockNode extends PythonClinicBuiltinNode {
        @Specialization
        static PSemLock construct(VirtualFrame frame, Object cls, int kind, int value, int maxValue, TruffleString name, boolean unlink,
                        @Bind("this") Node inliningTarget,
                        @Bind("getPosixSupport()") PosixSupport posixSupport,
                        @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            if (kind != PGraalPySemLock.RECURSIVE_MUTEX && kind != PGraalPySemLock.SEMAPHORE) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.UNRECOGNIZED_KIND);
            }
            Object posixName = posixLib.createPathFromString(posixSupport, name);
            long handle;
            try {
                handle = posixLib.semOpen(posixSupport, posixName, O_CREAT.value | O_EXCL.value, 0600, value);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            if (unlink) {
                try {
                    posixLib.semUnlink(posixSupport, posixName);
                } catch (PosixException e) {
                    try {
                        posixLib.semClose(posixSupport, handle);
                    } catch (PosixException ex) {
                        // Ignore, we're already on an error path
                    }
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            }
            return PFactory.createSemLock(language, cls, getInstanceShape.execute(cls), handle, kind, maxValue, name);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MultiprocessingModuleBuiltinsClinicProviders.SemLockNodeClinicProviderGen.INSTANCE;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "sem_unlink", parameterNames = {"name"})
    abstract static class SemUnlink extends PythonUnaryBuiltinNode {
        @Specialization
        PNone doit(VirtualFrame frame, TruffleString name,
                        @Bind("getPosixSupport()") PosixSupport posixSupport,
                        @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.semUnlink(posixSupport, posixLib.createPathFromString(posixSupport, name));
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }
}
