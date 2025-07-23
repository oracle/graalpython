/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.truffle.api.dsl.NodeFactory;

@CoreFunctions(defineModule = "mmap")
public final class MMapModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return Collections.emptyList();
    }

    public MMapModuleBuiltins() {
        addBuiltinConstant("ACCESS_DEFAULT", PMMap.ACCESS_DEFAULT);
        addBuiltinConstant("ACCESS_READ", PMMap.ACCESS_READ);
        addBuiltinConstant("ACCESS_WRITE", PMMap.ACCESS_WRITE);
        addBuiltinConstant("ACCESS_COPY", PMMap.ACCESS_COPY);
        // 'PROT_EXEC': 4,
        // 'PROT_READ': 1,
        // 'PROT_WRITE': 2,
        // 'MAP_SHARED': 1,
        // 'MAP_PRIVATE': 2,
        // 'MAP_ANON': 4096,
        // 'MAP_ANONYMOUS': 4096,
        // 'MADV_NORMAL': 0,
        // 'MADV_RANDOM': 1,
        // 'MADV_SEQUENTIAL': 2,
        // 'MADV_WILLNEED': 3,
        // 'MADV_DONTNEED': 4,
        // 'MADV_FREE': 5

        addBuiltinConstant("ALLOCATIONGRANULARITY", 4096);
        addBuiltinConstant("PAGESIZE", 4096);

        for (PosixConstants.IntConstant c : PosixConstants.mmapFlags) {
            if (c.defined) {
                addBuiltinConstant(c.name, c.getValueIfDefined());
            }
        }

        for (PosixConstants.IntConstant c : PosixConstants.mmapProtection) {
            if (c.defined) {
                addBuiltinConstant(c.name, c.getValueIfDefined());
            }
        }
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        core.getContext().registerCApiHook(() -> {
            CExtNodes.PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_MMAP_INIT_BUFFERPROTOCOL, PythonToNativeNode.executeUncached(PythonBuiltinClassType.PMMap));
        });
    }
}
