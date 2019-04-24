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
package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.nodes.subscript.SetItemNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Read the locals from the passed frame, updating them from the frame if that
 * is needed. This does <emph>not</emph> let the frame escape.
 **/
public abstract class ReadLocalsNode extends Node {
    public abstract Object execute(Frame frame);

    protected static boolean inClassBody(Frame frame) {
        return PArguments.getSpecialArgument(frame) instanceof ClassBodyRootNode;
    }

    protected static PFrame getPFrame(Frame frame) {
        return PArguments.getPFrame(frame);
    }

    @Specialization(guards = {"getPFrame(frame) == null", "!inClassBody(frame)"})
    Object freshLocals(Frame frame,
                       @Shared("factory") @Cached PythonObjectFactory factory) {
        return factory.createDictLocals(frame);
    }

    @Specialization(guards = {"getPFrame(frame) == null", "inClassBody(frame)"})
    static Object freshLocalsInClassBody(Frame frame) {
        // the namespace argument stores the locals
        return PArguments.getArgument(frame, 0);
    }

    @Specialization(guards = {"getPFrame(frame) != null", "inClassBody(frame)"})
    Object frameInClassBody(Frame frame,
                           @Shared("factory") @Cached PythonObjectFactory factory) {
        return getPFrame(frame).getLocals(factory);
    }

    @Specialization(guards = {"getPFrame(frame) != null", "!inClassBody(frame)"})
    Object frameToUpdate(Frame frame,
                         @Shared("factory") @Cached PythonObjectFactory factory,
                         @Cached SetItemNode setItemNode) {
        Object storedLocals = getPFrame(frame).getLocals(factory);
        PDict currentDictLocals = factory.createDictLocals(frame);
        for (DictEntry entry : currentDictLocals.entries()) {
            setItemNode.executeWith(storedLocals, entry.getKey(), entry.getValue());
        }
        return storedLocals;
    }
}
