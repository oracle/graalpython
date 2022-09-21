/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.function;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import static com.oracle.graal.python.util.PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;

public class ClassBodyRootNode extends FunctionRootNode {
    private static final Signature SIGNATURE = new Signature(-1, false, -1, false, EMPTY_TRUFFLESTRING_ARRAY, EMPTY_TRUFFLESTRING_ARRAY);

    public ClassBodyRootNode(PythonLanguage language, SourceSection sourceSection, String functionName, FrameDescriptor frameDescriptor, ExpressionNode body, ExecutionCellSlots executionCellSlots) {
        super(language, sourceSection, functionName, false, false, frameDescriptor, body, executionCellSlots, SIGNATURE, null);
    }

    /**
     * Used to keep the shape hierarchy of the objects created in this class body alive.
     */
    @CompilationFinal private Object cachedShape;

    private static final Object NO_CACHED_SHAPE = "<none>";

    @Override
    public Object execute(VirtualFrame frame) {
        Object customLocals = null;
        if (cachedShape == null) {
            customLocals = PArguments.getSpecialArgument(frame);
        }
        try {
            return super.execute(frame);
        } finally {
            if (cachedShape == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedShape = NO_CACHED_SHAPE;
                if (customLocals instanceof PDict) {
                    Object storage = ((PDict) customLocals).getDictStorage();
                    if (storage instanceof DynamicObjectStorage) {
                        cachedShape = ((DynamicObjectStorage) storage).getStoreShape();
                    }
                }
            }
        }
    }
}
