package com.oracle.graal.python.nodes.statement;

import java.util.ArrayList;

import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = TryExceptNode.class)
class TryExceptNodeMessageResolution {
    @Resolve(message = "HAS_KEYS")
    abstract static class HasKeysNode extends Node {
        Object access(@SuppressWarnings("unused") TryExceptNode object) {
            return true;
        }
    }

    @Resolve(message = "KEYS")
    abstract static class KeysNode extends Node {
        Object access(TryExceptNode object) {
            return object.getContext().getEnv().asGuestValue(new String[]{StandardTags.TryBlockTag.CATCHES});
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class KeyInfoNode extends Node {
        Object access(@SuppressWarnings("unused") TryExceptNode object, String name) {
            if (name.equals(StandardTags.TryBlockTag.CATCHES)) {
                return KeyInfo.INVOCABLE | KeyInfo.READABLE;
            } else {
                return KeyInfo.NONE;
            }
        }
    }

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        @Child GetAttributeNode getAttr = GetAttributeNode.create();
        @Child PythonObjectFactory factory = PythonObjectFactory.create();

        CatchesFunction access(TryExceptNode object, String name) {
            return doit(object, name, getAttr, factory);
        }

        static CatchesFunction doit(TryExceptNode object, String name, GetAttributeNode getAttr, PythonObjectFactory factory) {
            if (name.equals(StandardTags.TryBlockTag.CATCHES)) {
                if (object.getCatchesFunction() == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    ArrayList<Object> literalCatches = new ArrayList<>();
                    ExceptNode[] exceptNodes = object.getExceptNodes();
                    PythonModule builtins = object.getContext().getBuiltins();

                    for (ExceptNode node : exceptNodes) {
                        PNode exceptType = node.getExceptType();
                        if (exceptType instanceof ReadGlobalOrBuiltinNode) {
                            try {
                                literalCatches.add(getAttr.execute(builtins, ((ReadGlobalOrBuiltinNode) exceptType).getAttributeId()));
                            } catch (PException e) {
                            }
                        } else if (exceptType instanceof TupleLiteralNode) {
                            for (PNode tupleValue : ((TupleLiteralNode) exceptType).getValues()) {
                                if (tupleValue instanceof ReadGlobalOrBuiltinNode) {
                                    try {
                                        literalCatches.add(getAttr.execute(builtins, ((ReadGlobalOrBuiltinNode) tupleValue).getAttributeId()));
                                    } catch (PException e) {
                                    }
                                }
                            }
                        }
                    }

                    Object isinstanceFunc = getAttr.execute(builtins, BuiltinNames.ISINSTANCE);
                    PTuple caughtClasses = factory.createTuple(literalCatches.toArray());

                    if (isinstanceFunc instanceof PBuiltinFunction) {
                        RootCallTarget callTarget = ((PBuiltinFunction) isinstanceFunc).getCallTarget();
                        object.setCatchesFunction(new CatchesFunction(callTarget, caughtClasses));
                    } else {
                        throw new IllegalStateException("isinstance was redefined, cannot check exceptions");
                    }
                }
                return object.getCatchesFunction();
            } else {
                throw UnknownIdentifierException.raise(name);
            }
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class InvokeNode extends Node {
        @Child GetAttributeNode getAttr = GetAttributeNode.create();
        @Child PythonObjectFactory factory = PythonObjectFactory.create();

        Object access(TryExceptNode object, String name, Object[] arguments) {
            CatchesFunction catchesFunction = ReadNode.doit(object, name, getAttr, factory);
            if (arguments.length != 1) {
                throw ArityException.raise(1, arguments.length);
            }
            return catchesFunction.catches(arguments[0]);
        }
    }

    @CanResolve
    abstract static class CheckFunction extends Node {
        protected static boolean test(TruffleObject receiver) {
            return receiver instanceof TryExceptNode;
        }
    }

    static class CatchesFunction implements TruffleObject {
        private final RootCallTarget isInstance;
        private final Object[] args = PArguments.create(2);

        CatchesFunction(RootCallTarget callTarget, PTuple caughtClasses) {
            this.isInstance = callTarget;
            PArguments.setArgument(args, 1, caughtClasses);
        }

        @ExplodeLoop
        boolean catches(Object exception) {
            if (exception instanceof PBaseException) {
                PArguments.setArgument(args, 0, exception);
                try {
                    return isInstance.call(args) == Boolean.TRUE;
                } catch (PException e) {
                }
            }
            return false;
        }

        public ForeignAccess getForeignAccess() {
            return null;
        }
    }
}
