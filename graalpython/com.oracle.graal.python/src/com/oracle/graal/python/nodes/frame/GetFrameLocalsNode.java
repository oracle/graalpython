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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Rough equivalent of CPython's {@code PyFrame_FastToLocalsWithError}. CPython copies the fast
 * locals to a dict. We first copy Truffle frame locals to PFrame locals in frame materialization.
 * Then, when requested, this node copies PFrame locals to a dict.
 */
@GenerateUncached
public abstract class GetFrameLocalsNode extends Node {
    public abstract Object execute(VirtualFrame frame, PFrame pyFrame);

    // TODO cache fd, explode
    @Specialization(guards = "hasLocals(pyFrame)")
    static Object doLoop(VirtualFrame frame, PFrame pyFrame,
                    @Cached PythonObjectFactory factory,
                    @Cached PyObjectSetItem setItem,
                    @Cached PyObjectDelItem delItem) {
        // TODO do argcells need special handling?
        MaterializedFrame locals = pyFrame.getLocals();
        Object localsDict = pyFrame.getLocalsDict();
        if (localsDict == null) {
            localsDict = factory.createDict();
            pyFrame.setLocalsDict(localsDict);
        }
        FrameInfo frameInfo = (FrameInfo) locals.getFrameDescriptor().getInfo();
        for (int i = 0; i < frameInfo.getVariableCount(); i++) {
            TruffleString name = frameInfo.getVariableName(i);
            Object value = locals.getValue(i);
            if (value instanceof PCell) {
                value = ((PCell) value).getRef();
            }
            if (value == null) {
                try {
                    delItem.execute(frame, localsDict, name);
                } catch (PException e) {
                    // TODO
                    e.expect(PythonBuiltinClassType.KeyError, IsBuiltinClassProfile.getUncached());
                }
            } else {
                setItem.execute(frame, localsDict, name, value);
            }
        }
        return localsDict;
    }

    @Specialization(guards = "!hasLocals(pyFrame)")
    static Object doCustomLocals(PFrame pyFrame) {
        Object localsDict = pyFrame.getLocalsDict();
        assert localsDict != null;
        return localsDict;
    }

    protected static boolean hasLocals(PFrame pyFrame) {
        return pyFrame.getLocals() != null;
    }

    @NeverDefault
    public static GetFrameLocalsNode create() {
        return GetFrameLocalsNodeGen.create();
    }
}
