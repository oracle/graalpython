/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.argument.keywords;

import java.util.Iterator;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PythonOptions.class)
@GenerateUncached
public abstract class CopyKeywordsNode extends Node {
    public abstract void execute(PDict starargs, PKeyword[] keywords);

    protected static boolean isBuiltinDict(Object object, IsBuiltinClassProfile profile) {
        return object instanceof PDict && profile.profileObject(object, PythonBuiltinClassType.PDict);
    }

    @Specialization(guards = "isBuiltinDict(starargs, classProfile)", limit = "getCallSiteInlineCacheMaxDepth()")
    void doBuiltinDict(PDict starargs, PKeyword[] keywords,
                    @Cached CastToJavaStringNode castToJavaStringNode,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile classProfile,
                    @CachedLibrary(value = "starargs.getDictStorage()") HashingStorageLibrary lib) {
        Iterator<HashingStorage.DictEntry> iterator = lib.entries(starargs.getDictStorage()).iterator();
        for (int i = 0; i < keywords.length; i++) {
            HashingStorage.DictEntry entry = iterator.next();
            keywords[i] = new PKeyword(castToJavaStringNode.execute(entry.getKey()), entry.getValue());
        }
    }

    @Specialization(guards = "!isBuiltinDict(starargs, classProfile)", limit = "getCallSiteInlineCacheMaxDepth()")
    void doDict(PDict starargs, PKeyword[] keywords,
                    @Cached GetIteratorExpressionNode.GetIteratorWithoutFrameNode getIteratorNode,
                    @Cached GetNextNode.GetNextWithoutFrameNode getNextNode,
                    @Cached CastToJavaStringNode castToJavaStringNode,
                    @Cached IsBuiltinClassProfile errorProfile,
                    @CachedLibrary(value = "starargs.getDictStorage()") HashingStorageLibrary lib,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile classProfile) {
        Object iter = getIteratorNode.executeWithGlobalState(starargs);
        int i = 0;
        while (true) {
            Object key;
            try {
                key = getNextNode.executeWithGlobalState(iter);
                Object value = lib.getItem(starargs.getDictStorage(), key);
                keywords[i++] = new PKeyword(castToJavaStringNode.execute(key), value);
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                break;
            }
        }
    }

    public static CopyKeywordsNode create() {
        return CopyKeywordsNodeGen.create();
    }
}
