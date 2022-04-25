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
    public abstract int execute(Frame virtualFrame, int stackTop, Frame localFrame);

    @Specialization
    static int merge(VirtualFrame virtualFrame, int initialStackTop, Frame localFrame,
                    @Cached ConcatKeywordsNode.ConcatDictToStorageNode concatNode,
                    @Cached PRaiseNode raise,
                    @Cached BranchProfile keywordsError,
                    @Cached StringNodes.CastToJavaStringCheckedNode castToStringNode,
                    @Cached PyObjectFunctionStr functionStr) {
        int stackTop = initialStackTop;
        Object mapping = localFrame.getObject(stackTop);
        localFrame.setObject(stackTop--, null);
        PDict dict = (PDict) localFrame.getObject(stackTop);
        try {
            HashingStorage resultStorage = concatNode.execute(virtualFrame, dict.getDictStorage(), mapping);
            dict.setDictStorage(resultStorage);
        } catch (SameDictKeyException e) {
            keywordsError.enter();
            Object functionName = getFunctionName(virtualFrame, stackTop, localFrame, functionStr);
            String keyName = castToStringNode.execute(e.getKey(), ErrorMessages.KEYWORDS_S_MUST_BE_STRINGS, new Object[]{functionName});
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.GOT_MULTIPLE_VALUES_FOR_KEYWORD_ARG, functionName, keyName);
        } catch (NonMappingException e) {
            keywordsError.enter();
            Object functionName = getFunctionName(virtualFrame, stackTop, localFrame, functionStr);
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_AFTER_MUST_BE_MAPPING, functionName, e.getObject());
        }
        return stackTop;
    }

    private static Object getFunctionName(VirtualFrame virtualFrame, int stackTop, Frame localFrame, PyObjectFunctionStr functionStr) {
        /*
         * The instruction is only emitted when generating CALL_FUNCTION_KW. The stack layout at
         * this point is [kwargs dict, varargs, callable].
         */
        Object callable = localFrame.getObject(stackTop - 2);
        return functionStr.execute(virtualFrame, callable);
    }

    public static KwargsMergeNode create() {
        return KwargsMergeNodeGen.create();
    }

    public static KwargsMergeNode getUncached() {
        return KwargsMergeNodeGen.getUncached();
    }
}
