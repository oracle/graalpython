/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.PythonOS;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "_winapi", os = PythonOS.PLATFORM_WIN32)
public final class WinapiModuleBuiltins extends PythonBuiltins {
    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("NULL", 0);
        addBuiltinConstant("INFINITE", 0xFFFFFFFFL);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WinapiModuleBuiltinsFactory.getFactories();
    }

    // Managed fallback for native kernel32.GetACP call so encodings work sandboxed
    @Builtin(name = "GetACP", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetfullpathnameNode extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        int getacp(@Bind PythonContext context) {
            var ts = SysModuleBuiltins.GetFileSystemEncodingNode.getFileSystemEncoding();
            var cp = StringUtils.toLowerCase(ts.toJavaStringUncached());
            if (cp.startsWith("cp")) {
                try {
                    return Integer.valueOf(cp.substring(2));
                } catch (Exception e) {
                    // pass
                }
            }
            return 0;
        }
    }

    @Builtin(name = "CloseHandle", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseHandleNode extends PythonBuiltinNode {
        @Specialization
        static Object closeHandle(@SuppressWarnings("unused") Object handle) {
            return PNone.NONE;
        }
    }
}
