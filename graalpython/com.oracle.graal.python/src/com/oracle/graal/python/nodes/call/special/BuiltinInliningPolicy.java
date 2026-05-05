/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.call.special;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.PythonUtils.NodeCounterWithLimit;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

final class BuiltinInliningPolicy {

    enum CallerSizeCheck {
        OK,
        EXCEEDS_MAX_SIZE,
        DISABLED
    }

    private BuiltinInliningPolicy() {
    }

    static <T extends PythonBuiltinBaseNode> CallerSizeCheck checkCallerSize(Node caller, T builtinNode) {
        CompilerAsserts.neverPartOfCompilation();
        if (caller.isAdoptable()) {
            // To avoid building up ASTs of recursive builtin calls, check that the same builtin
            // isn't already the call node's parent.
            Class<? extends PythonBuiltinBaseNode> builtinClass = builtinNode.getClass();
            Node parent = caller.getParent();
            int recursiveCalls = 0;
            PythonLanguage language = PythonLanguage.get(caller);
            while (parent != null && !(parent instanceof RootNode)) {
                if (parent.getClass() == builtinClass) {
                    int recursionLimit = language.getEngineOption(PythonOptions.NodeRecursionLimit);
                    if (recursiveCalls == recursionLimit) {
                        return CallerSizeCheck.DISABLED;
                    }
                    recursiveCalls++;
                }
                parent = parent.getParent();
            }

            RootNode root = caller.getRootNode();
            // nb: option 'BuiltinsInliningMaxCallerSize' is defined as a compatible option, i.e.,
            // ASTs will only be shared between contexts that have the same value for this option.
            int maxSize = language.getEngineOption(PythonOptions.BuiltinsInliningMaxCallerSize);
            if (root instanceof PRootNode) {
                PRootNode pRoot = (PRootNode) root;
                int rootNodeCount = pRoot.getNodeCountForInlining();
                if (rootNodeCount < maxSize) {
                    NodeCounterWithLimit counter = new NodeCounterWithLimit(rootNodeCount, maxSize);
                    builtinNode.accept(counter);
                    if (counter.isOverLimit()) {
                        return CallerSizeCheck.EXCEEDS_MAX_SIZE;
                    }
                    pRoot.setNodeCountForInlining(counter.getCount());
                }
            } else {
                NodeCounterWithLimit counter = new NodeCounterWithLimit(maxSize);
                root.accept(counter);
                if (!counter.isOverLimit()) {
                    builtinNode.accept(counter);
                }
                if (counter.isOverLimit()) {
                    return CallerSizeCheck.EXCEEDS_MAX_SIZE;
                }
            }
            return CallerSizeCheck.OK;
        }
        return CallerSizeCheck.DISABLED;
    }

    static boolean exceedsCallerSize(CallerSizeCheck result) {
        return result != CallerSizeCheck.OK;
    }
}
