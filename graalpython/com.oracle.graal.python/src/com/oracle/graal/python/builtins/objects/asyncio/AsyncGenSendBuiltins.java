package com.oracle.graal.python.builtins.objects.asyncio;

import static com.oracle.graal.python.builtins.objects.asyncio.PAsyncGenASend.AwaitableState;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___AWAIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.generator.CommonGeneratorBuiltins;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PAsyncGenASend)
public class AsyncGenSendBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AsyncGenSendBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___AWAIT__, minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public static abstract class Await extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doAwait(PAsyncGenASend self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public static abstract class Next extends PythonUnaryBuiltinNode {
        @Specialization
        public Object next(VirtualFrame frame, PAsyncGenASend self,
                        @Cached Send send) {
            return send.execute(frame, self, PNone.NONE);
        }
    }

    @Builtin(name = "send", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public static abstract class Send extends PythonBinaryBuiltinNode {
        @Specialization
        public Object send(VirtualFrame frame, PAsyncGenASend self, Object sent,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raiseReuse,
                        @Cached PRaiseNode raiseAlreadyRunning,
                        @Cached CommonGeneratorBuiltins.SendNode send,
                        @Cached BuiltinClassProfiles.IsBuiltinObjectProfile isStopIteration,
                        @Cached BuiltinClassProfiles.IsBuiltinObjectProfile isGenExit,
                        @Cached BuiltinClassProfiles.IsBuiltinObjectProfile isAsyncGenWrappedValue,
                        @Cached PRaiseNode raiseStopAsyncIteration) {
            Object result;
            if (self.getState() == AwaitableState.CLOSED) {
                throw raiseReuse.raise(PythonBuiltinClassType.RuntimeError); // todo error msg
            }

            if (self.getState() == AwaitableState.INIT) {
                if (self.receiver.isRunningAsync()) {
                    throw raiseAlreadyRunning.raise(PythonBuiltinClassType.RuntimeError); // todo
                                                                                          // error
                                                                                          // msg
                }
                if (sent == null || sent == PNone.NONE) {
                    sent = self.message;
                }
                self.setState(AwaitableState.ITER);
            }
            self.receiver.setRunningAsync(true);
            try {
                result = send.execute(frame, self.receiver, sent);
                if (result == null) {
                    self.receiver.markAsFinished();
                    self.receiver.setRunningAsync(false);
                    self.setState(AwaitableState.CLOSED);
                    throw raiseStopAsyncIteration.raise(PythonBuiltinClassType.StopAsyncIteration);
                }
                if (isAsyncGenWrappedValue.profileObject(inliningTarget, result, PythonBuiltinClassType.PAsyncGenAWrappedValue)) {
                    self.receiver.setRunningAsync(false);
                    self.setState(AwaitableState.CLOSED);
                    throw raiseStopAsyncIteration.raise(PythonBuiltinClassType.StopIteration, new Object[]{((PAsyncGenWrappedValue) result).getWrapped()});
                }
            } catch (PException e) {
                if (isStopIteration.profileException(inliningTarget, e, PythonBuiltinClassType.StopAsyncIteration) ||
                                isGenExit.profileException(inliningTarget, e, PythonBuiltinClassType.GeneratorExit)) {
                    self.receiver.markAsFinished();
                }
                self.receiver.setRunningAsync(false);
                self.setState(AwaitableState.CLOSED);
                throw e;
            }
            return result;
        }
    }
}
