/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.queue;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.Empty;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.queue.SimpleQueueBuiltinsClinicProviders.SimpleQueueGetNodeClinicProviderGen;
import com.oracle.graal.python.lib.PyLongAsLongAndOverflowNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSimpleQueue)
public final class SimpleQueueBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SimpleQueueBuiltinsFactory.getFactories();
    }

    @Builtin(name = "empty", minNumOfPositionalArgs = 1, //
                    doc = "empty($self, /)\n--\n\nReturn True if the queue is empty, False otherwise (not reliable!).")
    @GenerateNodeFactory
    public abstract static class SimpleQueueEmptyNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean doGeneric(PSimpleQueue self) {
            return self.getQueueSize() == 0;
        }
    }

    @Builtin(name = "qsize", minNumOfPositionalArgs = 1, //
                    doc = "qsize($self, /)\n--\n\nReturn the approximate size of the queue (not reliable!).")
    @GenerateNodeFactory
    public abstract static class SimpleQueueQSizeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int doGeneric(PSimpleQueue self) {
            return self.getQueueSize();
        }
    }

    /**
     * For reference, see CPython's {@code _queuemodule.c: _queue_SimpleQueue_get_nowait_impl}.
     */
    @Builtin(name = "get_nowait", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, //
                    doc = "get_nowait($self, /)\n" +
                                    "--\n\n" +
                                    "Remove and return an item from the queue without blocking.\n" +
                                    "\n" +
                                    "Only get an item if one is immediately available. Otherwise\n" +
                                    "raise the Empty exception.")
    @GenerateNodeFactory
    public abstract static class SimpleQueueGetNoWaitNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doNoTimeout(PSimpleQueue self,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object result = self.poll();
            if (result != null) {
                return result;
            }
            throw raiseNode.get(inliningTarget).raise(Empty);
        }
    }

    /**
     * For reference, see CPython's {@code _queuemodule.c: _queue_SimpleQueue_get_impl}.
     */
    @Builtin(name = "get", minNumOfPositionalArgs = 1, parameterNames = {"$self", "block", "timeout"}, //
                    doc = "get($self, /, block=True, timeout=None)\n" +
                                    "--\n\n" +
                                    "Remove and return an item from the queue.\n" +
                                    "\n" +
                                    "If optional args 'block' is true and 'timeout' is None (the default),\n" +
                                    "block if necessary until an item is available. If 'timeout' is\n" +
                                    "a non-negative number, it blocks at most 'timeout' seconds and raises\n" +
                                    "the Empty exception if no item was available within that time.\n" +
                                    "Otherwise ('block' is false), return an item if one is immediately\n" +
                                    "available, else raise the Empty exception ('timeout' is ignored\n" +
                                    "in that case).")
    @GenerateNodeFactory
    @ArgumentClinic(name = "block", conversion = ClinicConversion.Boolean, defaultValue = "true")
    public abstract static class SimpleQueueGetNode extends PythonTernaryClinicBuiltinNode {
        @Child private GilNode gil;

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SimpleQueueGetNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "!withTimeout(block, timeout)")
        Object doNoTimeout(PSimpleQueue self, boolean block, @SuppressWarnings("unused") Object timeout,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            // CPython first tries a non-blocking get without releasing the GIL
            Object result = self.poll();
            if (result != null) {
                return result;
            }
            if (block) {
                try {
                    ensureGil().release(true);
                    return self.get();
                } catch (InterruptedException e) {
                    CompilerDirectives.transferToInterpreter();
                    Thread.currentThread().interrupt();
                    return PNone.NONE;
                } finally {
                    ensureGil().acquire();
                }
            }
            throw raiseNode.get(inliningTarget).raise(Empty);
        }

        @Specialization(guards = "withTimeout(block, timeout)")
        @SuppressWarnings("truffle-static-method")
        Object doTimeout(VirtualFrame frame, PSimpleQueue self, boolean block, Object timeout,
                        @Bind("this") Node inliningTarget,
                        @Cached PyLongAsLongAndOverflowNode asLongNode,
                        @Cached CastToJavaDoubleNode castToDouble,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            assert block;

            // convert timeout object (given in seconds) to a Java long in microseconds
            long ltimeout;
            try {
                ltimeout = (long) (castToDouble.execute(inliningTarget, timeout) * 1000000.0);
            } catch (CannotCastException e) {
                try {
                    ltimeout = PythonUtils.multiplyExact(asLongNode.execute(frame, inliningTarget, timeout), 1000000);
                } catch (OverflowException oe) {
                    throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.TIMEOUT_VALUE_TOO_LARGE);
                }
            }

            if (ltimeout < 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.TIMEOUT_MUST_BE_NON_NEG_NUM);
            }

            // CPython first tries a non-blocking get without releasing the GIL
            Object result = self.poll();
            if (result != null) {
                return result;
            }

            try {
                ensureGil().release(true);
                result = self.get(ltimeout);
                if (result != null) {
                    return result;
                }
            } catch (InterruptedException e) {
                CompilerDirectives.transferToInterpreter();
                Thread.currentThread().interrupt();
            } finally {
                ensureGil().acquire();
            }
            throw raiseNode.get(inliningTarget).raise(Empty);
        }

        private GilNode ensureGil() {
            if (gil == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                gil = insert(GilNode.create());
            }
            return gil;
        }

        static boolean withTimeout(boolean block, Object timeout) {
            return block && !(timeout instanceof PNone);
        }
    }

    /**
     * For reference, see CPython's {@code _queuemodule.c: _queue_SimpleQueue_put_impl}.
     */
    @Builtin(name = "put", minNumOfPositionalArgs = 2, parameterNames = {"$self", "item", "block", "timeout"}, //
                    doc = "put($self, /, item, block=True, timeout=None)\n" +
                                    "--\n\n" +
                                    "Put the item on the queue.\n" +
                                    "\n" +
                                    "The optional 'block' and 'timeout' arguments are ignored, as this method\n" +
                                    "never blocks.  They are provided for compatibility with the Queue class.")
    @GenerateNodeFactory
    public abstract static class SimpleQueuePutNode extends PythonQuaternaryBuiltinNode {

        @Specialization
        static PNone doGeneric(PSimpleQueue self, Object item, @SuppressWarnings("unused") Object block, @SuppressWarnings("unused") Object timeout,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!self.put(item)) {
                /*
                 * CPython uses a Python list as backing storage. This will throw an OverflowError
                 * if no more elements can be added to the list.
                 */
                throw raiseNode.get(inliningTarget).raise(OverflowError);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "put_nowait", minNumOfPositionalArgs = 2, parameterNames = {"$self", "item"}, //
                    doc = "put_nowait($self, /, item)\n" +
                                    "--\n\n" +
                                    "Put an item into the queue without blocking.\n" +
                                    "\n" +
                                    "This is exactly equivalent to `put(item)` and is only provided\n" +
                                    "for compatibility with the Queue class.")
    @GenerateNodeFactory
    public abstract static class SimpleQueuePutNoWaitNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PNone doGeneric(PSimpleQueue self, Object item,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!self.put(item)) {
                /*
                 * CPython uses a Python list as backing storage. This will throw an OverflowError
                 * if no more elements can be added to the list.
                 */
                throw raiseNode.get(inliningTarget).raise(OverflowError);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Cached PythonObjectFactory factory) {
            return factory.createGenericAlias(cls, key);
        }
    }
}
