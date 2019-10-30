/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.thread.PSemLock;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "_multiprocessing")
public class MultiprocessingModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MultiprocessingModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        // TODO: add necessary entries to the dict
        builtinConstants.put("flags", core.factory().createDict());
        super.initialize(core);
    }

    @Builtin(name = "SemLock", parameterNames = {"cls", "kind", "value", "maxvalue", "name", "unlink"}, constructsClass = PythonBuiltinClassType.PSemLock)
    @GenerateNodeFactory
    abstract static class ConstructSemLockNode extends PythonBuiltinNode {
        @Specialization
        PSemLock construct(LazyPythonClass cls, Object kindObj, Object valueObj, Object maxvalueObj, Object nameObj, Object unlinkObj,
                        @Cached CastToJavaIntNode castKindToIntNode,
                        @Cached CastToJavaIntNode castValueToIntNode,
                        @Cached CastToJavaIntNode castMaxvalueToIntNode,
                        @Cached CastToJavaIntNode castUnlinkToIntNode) {
            int kind = castKindToIntNode.execute(kindObj);
            if (kind != PSemLock.RECURSIVE_MUTEX && kind != PSemLock.SEMAPHORE) {
                throw raise(PythonBuiltinClassType.ValueError, "unrecognized kind");
            }
            int value = castValueToIntNode.execute(valueObj);
            int maxvalue = castMaxvalueToIntNode.execute(maxvalueObj);
            int unlink = castUnlinkToIntNode.execute(unlinkObj);
            if (unlink != 0) {
                throw raise(PythonBuiltinClassType.SystemError, "semaphore unlinking is not yet implemented");
            }
            return factory().createSemLock(cls, kind, value, maxvalue, nameObj);
        }
    }
}
