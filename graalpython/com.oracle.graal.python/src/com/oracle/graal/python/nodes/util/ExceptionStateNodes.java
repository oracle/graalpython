package com.oracle.graal.python.nodes.util;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.graal.python.nodes.util.ExceptionStateNodesFactory.GetCaughtExceptionNodeGen;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class ExceptionStateNodes {

    public static final class SetCaughtExceptionNode extends Node {

        @CompilationFinal private FrameSlot excSlot;
        @CompilationFinal private ContextReference<PythonContext> contextRef;

        public void execute(VirtualFrame frame, PException e) {
            RootNode rootNode = getRootNode();
            if (rootNode != null && !shouldStoreException(rootNode)) {
                doFrame(frame, e, rootNode);
            } else {
                doContext(e);
            }
        }

        private void doFrame(VirtualFrame frame, PException e, RootNode rootNode) {
            if (excSlot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                FrameDescriptor frameDescriptor = rootNode.getFrameDescriptor();

                FrameSlot frameSlot = frameDescriptor.findOrAddFrameSlot(FrameSlotIDs.CAUGHT_EXCEPTION, FrameSlotKind.Object);
                if (frame.getFrameDescriptor().getFrameSlotKind(frameSlot) != FrameSlotKind.Object) {
                    frame.getFrameDescriptor().setFrameSlotKind(frameSlot, FrameSlotKind.Object);
                }
                excSlot = frameSlot;
            }
            frame.setObject(excSlot, e);
        }

        private void doContext(PException e) {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = PythonLanguage.getContextRef();
            }
            contextRef.get().setCaughtException(e);
        }

        private static boolean shouldStoreException(RootNode rootNode) {
            if (rootNode instanceof PRootNode) {
                return ((PRootNode) rootNode).storeExceptionState();
            }
            return true;
        }

        public static SetCaughtExceptionNode create() {
            return new SetCaughtExceptionNode();
        }
    }

    abstract static class ReadExceptionStateFromFrameNode extends Node {

        public abstract PException execute(Frame frame);

        @Specialization(guards = "frame.getFrameDescriptor() == fd")
        PException doFrameCached(Frame frame,
                        @Cached("frame.getFrameDescriptor()") @SuppressWarnings("unused") FrameDescriptor fd,
                        @Cached("findSlot(fd)") FrameSlot excSlot) {
            if (excSlot != null) {
                return (PException) FrameUtil.getObjectSafe(frame, excSlot);
            }
            return null;
        }

        @Specialization
        PException doFrameGeneric(Frame frame) {
            return doFrameCached(frame, null, findSlot(frame.getFrameDescriptor()));
        }

        @TruffleBoundary
        protected static FrameSlot findSlot(FrameDescriptor frameDescriptor) {
            FrameSlot excSlot = frameDescriptor.findFrameSlot(FrameSlotIDs.CAUGHT_EXCEPTION);
            assert excSlot == null || frameDescriptor.getFrameSlotKind(excSlot) == FrameSlotKind.Object;
            return excSlot;
        }

    }

    public abstract static class GetCaughtExceptionNode extends Node {

        private static final ExceptionStateException INSTANCE = new ExceptionStateException();

        public abstract PException execute(VirtualFrame frame);

        @Specialization(guards = "excSlot != null", rewriteOn = ExceptionStateException.class)
        PException doFrame(VirtualFrame frame,
                        @Cached("findSlot(frame)") FrameSlot excSlot) throws ExceptionStateException {
            PException e = (PException) FrameUtil.getObjectSafe(frame, excSlot);
            if (e != null) {
                return e;
            }
            throw INSTANCE;
        }

        @Specialization(guards = "excSlot != null", replaces = "doFrame")
        PException doFrameAndContext(VirtualFrame frame,
                        @Cached("findSlot(frame)") FrameSlot excSlot,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            PException e = (PException) FrameUtil.getObjectSafe(frame, excSlot);
            if (e != null) {
                return e;
            }
            return doContext(frame, null, context);
        }

        @Specialization(guards = "excSlot == null", replaces = "doFrame")
        PException doContext(@SuppressWarnings("unused") VirtualFrame frame,
                        @Cached("findSlot(frame)") @SuppressWarnings("unused") FrameSlot excSlot,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            PException e = context.getCaughtException();
            if (e == null) {
                // The very-slow path: This is the first time we want to fetch the exception state
                // from the context. The caller didn't know that it is necessary to provide the
                // exception in the context. So, we do a full stack walk until the first frame
                // having the exception state in the special slot. And we set the appropriate flag
                // on the root node such that the next time, we will find the exception state in the
                // context immediately.
                CompilerDirectives.transferToInterpreter();
                return fullStackWalk();
            }
            return e;
        }

        @TruffleBoundary
        private static PException fullStackWalk() {

            return Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<PException>() {
                public PException visitFrame(FrameInstance frameInstance) {
                    RootCallTarget target = (RootCallTarget) frameInstance.getCallTarget();
                    RootNode rootNode = target.getRootNode();
                    if (rootNode instanceof PRootNode) {
                        PRootNode pRootNode = (PRootNode) rootNode;
                        FrameDescriptor fd = rootNode.getFrameDescriptor();
                        FrameSlot excSlot = fd.findFrameSlot(FrameSlotIDs.CAUGHT_EXCEPTION);
                        if (excSlot != null) {
                            Frame frame = frameInstance.getFrame(FrameAccess.READ_ONLY);
                            try {
                                Object object = frame.getObject(excSlot);
                                if (object instanceof PException) {
                                    pRootNode.setStoreExceptionState();
                                    return (PException) object;
                                }
                            } catch (FrameSlotTypeException e) {
                                // fall through
                            }
                        }
                    }
                    return null;
                }
            });

        }

        protected FrameSlot findSlot(VirtualFrame frame) {
            RootNode rootNode = getRootNode();
            if (rootNode != null) {
                FrameDescriptor frameDescriptor = rootNode.getFrameDescriptor();

                FrameSlot excSlot = frameDescriptor.findFrameSlot(FrameSlotIDs.CAUGHT_EXCEPTION);
                assert excSlot == null || frame.getFrameDescriptor().getFrameSlotKind(excSlot) == FrameSlotKind.Object;
                return excSlot;
            }
            return null;
        }

        public static GetCaughtExceptionNode create() {
            return GetCaughtExceptionNodeGen.create();
        }

    }

    static final class ExceptionStateException extends Exception {
        private static final long serialVersionUID = 1L;
    }

}
