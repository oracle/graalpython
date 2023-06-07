package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCodeObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.nodes.frame.GetFrameLocalsNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

public final class PythonCextFrameBuiltins {
    @CApiBuiltin(ret = PyCodeObjectTransfer, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetCode extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PFrame frame,
                        @Cached FrameBuiltins.GetCodeNode getCodeNode) {
            return getCodeNode.executeObject(null, frame);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetLineNumber extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(PFrame frame,
                        @Cached FrameBuiltins.GetLinenoNode getLinenoNode) {
            return getLinenoNode.executeInt(null, frame);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetLasti extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(PFrame frame) {
            return frame.getLasti();
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetLocals extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PFrame frame,
                        @Cached GetFrameLocalsNode getFrameLocalsNode) {
            return getFrameLocalsNode.execute(frame);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetGlobals extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PFrame frame,
                        @Cached FrameBuiltins.GetGlobalsNode getGlobalsNode) {
            return getGlobalsNode.execute(null, frame);
        }
    }

    @CApiBuiltin(ret = PyFrameObjectTransfer, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetBack extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PFrame frame,
                        @Cached FrameBuiltins.GetBackrefNode getBackNode) {
            return getBackNode.execute(null, frame);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetBuiltins extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PFrame frame,
                        @Cached FrameBuiltins.GetBuiltinsNode getBuiltinsNode) {
            return getBuiltinsNode.execute(null, frame);
        }
    }
}
