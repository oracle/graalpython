package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;
import java.util.Objects;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.FunctionInvokeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_asyncio")
public class AsyncioModuleBuiltins extends PythonBuiltins {
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

            return Objects.requireNonNullElse(eventLoop, PNone.NONE);
        }
    }

    @Builtin(name = "_set_running_loop", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class InternalSetRunningLoop extends PythonUnaryBuiltinNode {
        @Specialization
        public Object setCurrentLoop(Object loop) {
            getContext().getThreadState(getLanguage()).setRunningEventLoop(loop);

            return PNone.NONE;
        }
    }

    public static final TruffleString T_ASYNCIO_EVENTS = tsLiteral("asyncio");
    public static final TruffleString T_GET_EVENT_LOOP_POLICY = tsLiteral("get_event_loop_policy");
    public static final TruffleString T_GET_EVENT_LOOP = tsLiteral("get_event_loop");

    @Builtin(name = "get_event_loop")
    @GenerateNodeFactory
    @ImportStatic(AsyncioModuleBuiltins.class)
    public abstract static class GetEventLoop extends PythonBuiltinNode {
        @Specialization
        public Object getCurrentLoop(VirtualFrame frame,
                        @Cached CallNode callGetPolicy,
                        @Cached CallNode callGetLoop,
                        @Cached(parameters = "T_GET_EVENT_LOOP") GetAttributeNode.GetFixedAttributeNode getGetLoop,
                        @Cached(parameters = "T_GET_EVENT_LOOP_POLICY") GetAttributeNode.GetFixedAttributeNode getGetLoopPolicy) {
            Object eventLoop = getContext().getThreadState(getLanguage()).getRunningEventLoop();
            if (eventLoop == null) {
                Object asyncio = AbstractImportNode.importModule(T_ASYNCIO_EVENTS);
                Object asyncioGetPolicy = getGetLoopPolicy.execute(frame, asyncio);
                Object policy = callGetPolicy.execute(asyncioGetPolicy);
                Object getLoop = getGetLoop.execute(frame, policy);
                return callGetLoop.execute(getLoop);
            } else {
                return eventLoop;
            }
        }
    }
}
