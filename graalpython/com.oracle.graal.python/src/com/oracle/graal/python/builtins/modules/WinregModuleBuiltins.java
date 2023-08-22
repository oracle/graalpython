/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(defineModule = "winreg", isEager = true, os = PythonOS.PLATFORM_WIN32)
public final class WinregModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WinregModuleBuiltinsFactory.getFactories();
    }

    private static final int HKEY_CURRENT_USER = 1;
    private static final int HKEY_LOCAL_MACHINE = 2;
    private static final int HKEY_CLASSES_ROOT = 3;

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("HKEY_CURRENT_USER", HKEY_CURRENT_USER);
        addBuiltinConstant("HKEY_LOCAL_MACHINE", HKEY_LOCAL_MACHINE);
        addBuiltinConstant("HKEY_CLASSES_ROOT", HKEY_CLASSES_ROOT);
    }

    @Builtin(name = "OpenKey", minNumOfPositionalArgs = 2, parameterNames = {"key", "sub_key", "reserved", "access"})
    @ArgumentClinic(name = "reserved", defaultValue = "0")
    @ArgumentClinic(name = "access", defaultValue = "131097")
    @GenerateNodeFactory
    public abstract static class OpenKeyNode extends PythonQuaternaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return WinregModuleBuiltinsClinicProviders.OpenKeyNodeClinicProviderGen.INSTANCE;
        }

        @SuppressWarnings("unused")
        @Specialization
        static Object openKey(VirtualFrame frame, Object key, Object subKey, Object reserved, Object access,
                        @Bind("this") Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            if (key instanceof Integer intKey) {
                if (intKey == HKEY_CLASSES_ROOT) {
                    return factory.createLock();
                }
            }
            throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, OSErrorEnum.ENOENT);
        }
    }

    @Builtin(name = "EnumKey", minNumOfPositionalArgs = 2, parameterNames = {"key", "index"})
    @GenerateNodeFactory
    public abstract static class EnumKeyNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static Object enumKey(VirtualFrame frame, Object key, Object index,
                        @Bind("this") Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, OSErrorEnum.ENOENT);
        }
    }
}
