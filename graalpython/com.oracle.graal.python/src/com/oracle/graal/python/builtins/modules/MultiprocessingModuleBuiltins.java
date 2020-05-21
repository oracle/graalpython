/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.Semaphore;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.thread.PSemLock;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
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
                        @Cached CastToJavaStringNode castNameNode,
                        @Cached CastToJavaIntExactNode castKindToIntNode,
                        @Cached CastToJavaIntExactNode castValueToIntNode,
                        @Cached CastToJavaIntExactNode castMaxvalueToIntNode,
                        @Cached CastToJavaIntExactNode castUnlinkToIntNode,
                        @CachedLanguage PythonLanguage lang) {
            int kind = castKindToIntNode.execute(kindObj);
            if (kind != PSemLock.RECURSIVE_MUTEX && kind != PSemLock.SEMAPHORE) {
                throw raise(PythonBuiltinClassType.ValueError, "unrecognized kind");
            }
            int value = castValueToIntNode.execute(valueObj);
            castMaxvalueToIntNode.execute(maxvalueObj); // executed for the side-effect, but ignored
                                                        // on posix
            Semaphore semaphore = newSemaphore(value);
            int unlink = castUnlinkToIntNode.execute(unlinkObj);
            String name;
            try {
                name = castNameNode.execute(nameObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, "argument 4 must be str, not %p", nameObj);
            }
            if (unlink == 0) {
                // CPython creates a named semaphore, and if unlink != 0 unlinks
                // it directly so it cannot be access by other processes. We
                // have to explicitly link it, so we do that here if we
                // must. CPython always uses O_CREAT | O_EXCL for creating named
                // semaphores, so a conflict raises.
                if (semaphoreExists(lang, name)) {
                    throw raise(PythonBuiltinClassType.FileExistsError, "Semaphore name taken: '%s'", name);
                } else {
                    semaphorePut(lang, semaphore, name);
                }
            }
            return factory().createSemLock(cls, name, kind, semaphore);
        }

        @TruffleBoundary
        private static Object semaphorePut(PythonLanguage lang, Semaphore semaphore, String name) {
            return lang.namedSemaphores.put(name, semaphore);
        }

        @TruffleBoundary
        private static boolean semaphoreExists(PythonLanguage lang, String name) {
            return lang.namedSemaphores.containsKey(name);
        }

        @TruffleBoundary
        private static Semaphore newSemaphore(int value) {
            return new Semaphore(value);
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "sem_unlink", parameterNames = {"name"})
    abstract static class SemUnlink extends PythonUnaryBuiltinNode {
        @Specialization
        PNone doit(String name,
                        @CachedLanguage PythonLanguage lang) {
            Semaphore prev = semaphoreRemove(name, lang);
            if (prev == null) {
                throw raise(PythonBuiltinClassType.FileNotFoundError, "No such file or directory: 'semaphores:/%s'", name);
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private static Semaphore semaphoreRemove(String name, PythonLanguage lang) {
            return lang.namedSemaphores.remove(name);
        }
    }
}
