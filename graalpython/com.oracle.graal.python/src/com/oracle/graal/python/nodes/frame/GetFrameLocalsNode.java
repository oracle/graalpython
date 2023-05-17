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
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.lib.PyDictDelItem;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.nodes.bytecode.BytecodeFrameInfo;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLFrameInfo;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
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
@GenerateInline(inlineByDefault = true)
@GenerateCached
public abstract class GetFrameLocalsNode extends Node {
    public abstract Object execute(Node inliningTarget, PFrame pyFrame);

    public final Object executeCached(PFrame pyFrame) {
        return execute(this, pyFrame);
    }

    public static Object executeUncached(PFrame pyFrame) {
        return GetFrameLocalsNodeGen.getUncached().execute(null, pyFrame);
    }

    @Specialization(guards = "!pyFrame.hasCustomLocals()")
    static Object doLoop(PFrame pyFrame,
                    @Cached(inline = false) PythonObjectFactory factory,
                    @Cached(inline = false) CopyLocalsToDict copyLocalsToDict) {
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
    @SuppressWarnings("truffle-inlining")       // footprint reduction 104 -> 86
    abstract static class CopyLocalsToDict extends Node {
        abstract void execute(MaterializedFrame locals, PDict dict);

        @Specialization(guards = {"cachedFd == locals.getFrameDescriptor()", "count < 32"}, limit = "1")
        @ExplodeLoop
        void doCachedFd(MaterializedFrame locals, PDict dict,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached("locals.getFrameDescriptor()") FrameDescriptor cachedFd,
                        @Bind("getInfo(cachedFd)") FrameInfo info,
                        @Bind("info.getVariableCount()") int count,
                        @Shared("setItem") @Cached PyDictSetItem setItem,
                        @Shared("delItem") @Cached PyDictDelItem delItem) {
            int regularVarCount = info.getRegularVariableCount();

            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                BytecodeDSLFrameInfo bytecodeDSLFrameInfo = (BytecodeDSLFrameInfo) info;
                PBytecodeDSLRootNode rootNode = bytecodeDSLFrameInfo.getRootNode();
                Object[] localsArray = rootNode.getLocals(locals);
                for (int i = 0; i < count; i++) {
                    copyItem(inliningTarget, localsArray[i], info, dict, setItem, delItem, i, i >= regularVarCount);
                }
            } else {
                for (int i = 0; i < count; i++) {
                    copyItem(inliningTarget, locals.getValue(i), info, dict, setItem, delItem, i, i >= regularVarCount);
                }
            }
        }

        @Specialization(replaces = "doCachedFd")
        void doGeneric(MaterializedFrame locals, PDict dict,
                        @Bind("this") Node inliningTarget,
                        @Shared("setItem") @Cached PyDictSetItem setItem,
                        @Shared("delItem") @Cached PyDictDelItem delItem) {
            FrameInfo info = getInfo(locals.getFrameDescriptor());
            int count = info.getVariableCount();
            int regularVarCount = info.getRegularVariableCount();

            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                BytecodeDSLFrameInfo bytecodeDSLFrameInfo = (BytecodeDSLFrameInfo) info;
                PBytecodeDSLRootNode rootNode = bytecodeDSLFrameInfo.getRootNode();
                Object[] localsArray = rootNode.getLocals(locals);
                for (int i = 0; i < count; i++) {
                    copyItem(inliningTarget, localsArray[i], info, dict, setItem, delItem, i, i >= regularVarCount);
                }
            } else {
                for (int i = 0; i < count; i++) {
                    copyItem(inliningTarget, locals.getValue(i), info, dict, setItem, delItem, i, i >= regularVarCount);
                }
            }
        }

        private static void copyItem(Node inliningTarget, Object localValue, FrameInfo info, PDict dict, PyDictSetItem setItem, PyDictDelItem delItem, int i, boolean deref) {
            TruffleString name = info.getVariableName(i);
            Object value = localValue;
            if (deref && value != null) {
                value = ((PCell) value).getRef();
            }
            if (value == null) {
                delItem.execute(inliningTarget, dict, name);
            } else {
                setItem.execute(inliningTarget, dict, name, value);
            }
        }

        @Idempotent
        protected static FrameInfo getInfo(FrameDescriptor fd) {
            return (FrameInfo) fd.getInfo();
        }
    }

    /**
     * Equivalent of CPython's {@code PyFrame_LocalsToFast}
     */
    public static void syncLocalsBackToFrame(CodeUnit co, PFrame pyFrame, Frame localFrame) {
        if (!pyFrame.hasCustomLocals()) {
            PDict localsDict = (PDict) pyFrame.getLocalsDict();
            copyLocalsArray(localFrame, localsDict, co.varnames, 0, false);
            copyLocalsArray(localFrame, localsDict, co.cellvars, co.varnames.length, true);
            copyLocalsArray(localFrame, localsDict, co.freevars, co.varnames.length + co.cellvars.length, true);
        }
    }

    private static void copyLocalsArray(Frame localFrame, PDict localsDict, TruffleString[] namesArray, int offset, boolean deref) {
        for (int i = 0; i < namesArray.length; i++) {
            TruffleString varname = namesArray[i];
            Object value = PyDictGetItem.executeUncached(localsDict, varname);
            if (deref) {
                PCell cell = (PCell) localFrame.getObject(offset + i);
                cell.setRef(value);
            } else {
                localFrame.setObject(offset + i, value);
            }
        }
    }

    @NeverDefault
    public static GetFrameLocalsNode create() {
        return GetFrameLocalsNodeGen.create();
    }
}
