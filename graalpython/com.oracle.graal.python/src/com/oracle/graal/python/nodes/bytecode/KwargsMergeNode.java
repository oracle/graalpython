/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.lib.PyObjectFunctionStr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ConcatKeywordsNode;
import com.oracle.graal.python.nodes.argument.keywords.NonMappingException;
import com.oracle.graal.python.nodes.argument.keywords.SameDictKeyException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

@GenerateUncached
public abstract class KwargsMergeNode extends PNodeWithContext {
    public abstract int execute(Frame frame, int stackTop);

    @Specialization
    static int merge(VirtualFrame frame, int initialStackTop,
                    @Cached ConcatKeywordsNode.ConcatDictToStorageNode concatNode,
                    @Cached PRaiseNode raise,
                    @Cached BranchProfile keywordsError,
                    @Cached StringNodes.CastToJavaStringCheckedNode castToStringNode,
                    @Cached PyObjectFunctionStr functionStr) {
        int stackTop = initialStackTop;
        Object mapping = frame.getObject(stackTop);
        frame.setObject(stackTop--, null);
        PDict dict = (PDict) frame.getObject(stackTop);
        try {
            HashingStorage resultStorage = concatNode.execute(frame, dict.getDictStorage(), mapping);
            dict.setDictStorage(resultStorage);
        } catch (SameDictKeyException e) {
            keywordsError.enter();
            Object functionName = getFunctionName(frame, stackTop, functionStr);
            String keyName = castToStringNode.cast(e.getKey(), ErrorMessages.KEYWORDS_S_MUST_BE_STRINGS, new Object[]{functionName});
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.GOT_MULTIPLE_VALUES_FOR_KEYWORD_ARG, functionName, keyName);
        } catch (NonMappingException e) {
            keywordsError.enter();
            Object functionName = getFunctionName(frame, stackTop, functionStr);
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_AFTER_MUST_BE_MAPPING, functionName, e.getObject());
        }
        return stackTop;
    }

    private static Object getFunctionName(VirtualFrame frame, int stackTop, PyObjectFunctionStr functionStr) {
        /*
         * The instruction is only emitted when generating CALL_FUNCTION_KW. The stack layout at
         * this point is [kwargs dict, varargs, callable].
         */
        Object callable = frame.getObject(stackTop - 2);
        return functionStr.execute(frame, callable);
    }

    public static KwargsMergeNode create() {
        return KwargsMergeNodeGen.create();
    }

    public static KwargsMergeNode getUncached() {
        return KwargsMergeNodeGen.getUncached();
    }
}
