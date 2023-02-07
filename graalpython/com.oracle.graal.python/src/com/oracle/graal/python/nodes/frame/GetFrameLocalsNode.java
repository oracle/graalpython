/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.lib.PyDictDelItem;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Rough equivalent of CPython's {@code PyFrame_FastToLocalsWithError}. CPython copies the fast
 * locals to a dict. We first copy Truffle frame locals to PFrame locals in frame materialization.
 * Then, when requested, this node copies PFrame locals to a dict.
 */
@GenerateUncached
public abstract class GetFrameLocalsNode extends Node {
    public abstract Object execute(PFrame pyFrame);

    @Specialization(guards = "!pyFrame.hasCustomLocals()")
    static Object doLoop(PFrame pyFrame,
                    @Cached PythonObjectFactory factory,
                    @Cached CopyLocalsToDict copyLocalsToDict) {
        MaterializedFrame locals = pyFrame.getLocals();
        // It doesn't have custom locals, so it has to be a builtin dict or null
        PDict localsDict = (PDict) pyFrame.getLocalsDict();
        if (localsDict == null) {
            localsDict = factory.createDict();
            pyFrame.setLocalsDict(localsDict);
        }
        copyLocalsToDict.execute(locals, localsDict);
        return localsDict;
    }

    @Specialization(guards = "pyFrame.hasCustomLocals()")
    static Object doCustomLocals(PFrame pyFrame) {
        Object localsDict = pyFrame.getLocalsDict();
        assert localsDict != null;
        return localsDict;
    }

    @GenerateUncached
    abstract static class CopyLocalsToDict extends Node {
        abstract void execute(MaterializedFrame locals, PDict dict);

        @Specialization(guards = {"cachedFd == locals.getFrameDescriptor()", "count < 32"}, limit = "1")
        @ExplodeLoop
        void doCachedFd(MaterializedFrame locals, PDict dict,
                        @SuppressWarnings("unused") @Cached("locals.getFrameDescriptor()") FrameDescriptor cachedFd,
                        @Bind("getInfo(cachedFd)") FrameInfo info,
                        @Bind("info.getVariableCount()") int count,
                        @Shared("setItem") @Cached PyDictSetItem setItem,
                        @Shared("delItem") @Cached PyDictDelItem delItem) {
            for (int i = 0; i < count; i++) {
                copyItem(locals, info, dict, setItem, delItem, i);
            }
        }

        @Specialization(replaces = "doCachedFd")
        void doGeneric(MaterializedFrame locals, PDict dict,
                        @Shared("setItem") @Cached PyDictSetItem setItem,
                        @Shared("delItem") @Cached PyDictDelItem delItem) {
            FrameInfo info = getInfo(locals.getFrameDescriptor());
            int count = info.getVariableCount();
            for (int i = 0; i < count; i++) {
                copyItem(locals, info, dict, setItem, delItem, i);
            }
        }

        private static void copyItem(MaterializedFrame locals, FrameInfo info, PDict dict, PyDictSetItem setItem, PyDictDelItem delItem, int i) {
            TruffleString name = info.getVariableName(i);
            Object value = locals.getValue(i);
            if (value instanceof PCell) {
                value = ((PCell) value).getRef();
            }
            if (value == null) {
                delItem.execute(dict, name);
            } else {
                setItem.execute(dict, name, value);
            }
        }

        protected static FrameInfo getInfo(FrameDescriptor fd) {
            return (FrameInfo) fd.getInfo();
        }
    }

    @NeverDefault
    public static GetFrameLocalsNode create() {
        return GetFrameLocalsNodeGen.create();
    }
}
