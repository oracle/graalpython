/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_posixshmem")
public final class PosixShMemModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PosixShMemModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "shm_open", minNumOfPositionalArgs = 2, parameterNames = {"path", "flags", "mode"})
    @ArgumentClinic(name = "path", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "flags", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int, defaultValue = "0777")
    @GenerateNodeFactory
    public abstract static class ShmOpenNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixShMemModuleBuiltinsClinicProviders.ShmOpenNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int shmOpen(VirtualFrame frame, TruffleString path, int flags, int mode,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                Object name = posixLib.createCStringFromString(context.getPosixSupport(), path);
                return posixLib.shmOpen(context.getPosixSupport(), name, flags, mode);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path);
            }
        }
    }

    @Builtin(name = "shm_unlink", minNumOfPositionalArgs = 1, parameterNames = {"path"})
    @ArgumentClinic(name = "path", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class ShmUnlinkNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixShMemModuleBuiltinsClinicProviders.ShmUnlinkNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone shmUnlink(VirtualFrame frame, TruffleString path,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                Object name = posixLib.createCStringFromString(context.getPosixSupport(), path);
                posixLib.shmUnlink(context.getPosixSupport(), name);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path);
            }
            return PNone.NONE;
        }
    }

}
