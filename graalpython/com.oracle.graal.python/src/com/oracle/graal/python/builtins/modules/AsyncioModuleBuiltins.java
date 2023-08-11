/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.BuiltinNames.J__ASYNCIO;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ADD;
import static com.oracle.graal.python.nodes.BuiltinNames.T_DISCARD;
import static com.oracle.graal.python.nodes.BuiltinNames.T__ASYNCIO;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyDictDelItem;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__ASYNCIO)
public final class AsyncioModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AsyncioModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "get_running_loop")
    @GenerateNodeFactory
    public abstract static class GetRunningLoop extends PythonBuiltinNode {
        @Specialization
        public Object getCurrentLoop(
                        @Cached PRaiseNode raise) {
            Object eventLoop = getContext().getThreadState(getLanguage()).getRunningEventLoop();
            if (eventLoop == null) {
                throw raise.raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.NO_RUNNING_EVENT_LOOP);
            } else {
                return eventLoop;
            }
        }
    }

    @Builtin(name = "_get_running_loop")
    @GenerateNodeFactory
    public abstract static class InternalGetRunningLoop extends PythonBuiltinNode {
        @Specialization
        public Object getCurrentLoop() {
            Object eventLoop = getContext().getThreadState(getLanguage()).getRunningEventLoop();

            return eventLoop == null ? PNone.NONE : eventLoop;
        }
    }

    @Builtin(name = "_set_running_loop", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class InternalSetRunningLoop extends PythonUnaryBuiltinNode {
        @Specialization
        public Object setCurrentLoop(Object loop) {
            getContext().getThreadState(getLanguage()).setRunningEventLoop(loop == PNone.NONE ? null : loop);
            return PNone.NONE;
        }
    }

    public static final TruffleString T_ASYNCIO_EVENTS = tsLiteral("asyncio");
    public static final TruffleString T_GET_EVENT_LOOP_POLICY = tsLiteral("get_event_loop_policy");
    public static final TruffleString T_GET_EVENT_LOOP = tsLiteral("get_event_loop");

    @Builtin(name = "get_event_loop", declaresExplicitSelf = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(AsyncioModuleBuiltins.class)
    public abstract static class GetEventLoop extends PythonUnaryBuiltinNode {

        @Specialization
        public Object getCurrentLoop(VirtualFrame frame, Object ignored,
                        @Cached CallNode callGetPolicy,
                        @Cached CallNode callGetLoop,
                        @Cached AbstractImportNode.ImportName importName,
                        @Cached(parameters = "T_GET_EVENT_LOOP") GetAttributeNode.GetFixedAttributeNode getGetLoop,
                        @Cached(parameters = "T_GET_EVENT_LOOP_POLICY") GetAttributeNode.GetFixedAttributeNode getGetLoopPolicy) {
            Object eventLoop = getContext().getThreadState(getLanguage()).getRunningEventLoop();
            if (eventLoop == null) {
                Object asyncio = importName.execute(frame, getContext(), getContext().getBuiltins(), T_ASYNCIO_EVENTS, PNone.NONE, PythonUtils.EMPTY_TRUFFLESTRING_ARRAY, 0);
                Object asyncioGetPolicy = getGetLoopPolicy.execute(frame, asyncio);
                Object policy = callGetPolicy.execute(frame, asyncioGetPolicy);
                Object getLoop = getGetLoop.execute(frame, policy);
                return callGetLoop.execute(frame, getLoop);
            } else {
                return eventLoop;
            }
        }
    }

    @Builtin(name = "_get_event_loop", parameterNames = "stacklevel")
    @GenerateNodeFactory
    public abstract static class DeprGetEventLoop extends PythonUnaryBuiltinNode {

        @Specialization
        public Object getCurrentLoop(VirtualFrame frame, Object stacklevel,
                        @Cached GetEventLoop getLoop) {
            return getLoop.execute(frame, null);
        }
    }

    public static final TruffleString CURRENT_TASKS_ATTR = tsLiteral("_current_tasks");

    @Builtin(name = "_enter_task", declaresExplicitSelf = true, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class EnterTask extends PythonBuiltinNode {
        @Specialization
        public Object enterTask(VirtualFrame frame, PythonModule self, Object loop, Object task,
                        @Bind("this") Node inliningTarget,
                        @Cached PyDictSetItem set,
                        @Cached PyDictGetItem get,
                        @Cached PRaiseNode raise) {
            PDict dict = (PDict) self.getAttribute(CURRENT_TASKS_ATTR);
            Object item = get.execute(frame, inliningTarget, dict, loop);
            if (item == null) {
                set.execute(frame, inliningTarget, dict, loop, task);
            } else {
                throw raise.raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.CANT_ENTER_TASK_ALREADY_RUNNING, task, item);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "_leave_task", declaresExplicitSelf = true, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class LeaveTask extends PythonBuiltinNode {
        @Specialization
        public Object leaveTask(VirtualFrame frame, PythonModule self, Object loop, Object task,
                        @Bind("this") Node inliningTarget,
                        @Cached PyDictDelItem del,
                        @Cached PyDictGetItem get,
                        @Cached PRaiseNode raise) {
            PDict dict = (PDict) self.getAttribute(CURRENT_TASKS_ATTR);
            Object item = get.execute(frame, inliningTarget, dict, loop);
            if (item == null) {
                item = PNone.NONE;
            }
            if (item != task) {
                throw raise.raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.TASK_NOT_ENTERED, task, item);
            }
            del.execute(frame, inliningTarget, dict, loop);
            return PNone.NONE;
        }
    }

    @Builtin(name = "_register_task", declaresExplicitSelf = true, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RegisterTask extends PythonBuiltinNode {
        private static final TruffleString ADD = tsLiteral("add");

        @Specialization
        public Object registerTask(VirtualFrame frame, PythonModule self, Object task,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callmethod) {
            Object weakset = self.getAttribute(ALL_TASKS_ATTR);
            callmethod.execute(frame, inliningTarget, weakset, T_ADD, task);
            return PNone.NONE;
        }
    }

    @Builtin(name = "_unregister_task", declaresExplicitSelf = true, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class UnregisterTask extends PythonBuiltinNode {

        @Specialization
        public Object unregisterTask(VirtualFrame frame, PythonModule self, Object task,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object weakset = self.getAttribute(ALL_TASKS_ATTR);
            callMethod.execute(frame, inliningTarget, weakset, T_DISCARD, task);
            return PNone.NONE;
        }
    }

    public static final TruffleString ALL_TASKS_ATTR = tsLiteral("_all_tasks");

    private static final TruffleString WEAKREF = tsLiteral("weakref");
    private static final TruffleString WEAKSET = tsLiteral("WeakSet");

    @Override
    public void postInitialize(Python3Core core) {
        PythonObjectSlowPathFactory factory = core.factory();
        PythonModule self = core.lookupBuiltinModule(T__ASYNCIO);
        self.setAttribute(CURRENT_TASKS_ATTR, factory.createDict());
        Object weakref = AbstractImportNode.importModule(WEAKREF);
        Object weakSetCls = PyObjectGetAttr.executeUncached(weakref, WEAKSET);
        Object weakSet = CallNode.getUncached().execute(weakSetCls);
        self.setAttribute(ALL_TASKS_ATTR, weakSet);
    }
}
