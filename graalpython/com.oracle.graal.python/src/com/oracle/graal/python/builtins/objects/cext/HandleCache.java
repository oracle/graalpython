package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.builtins.objects.cext.HandleCacheFactory.HandleCacheMRFactory.GetOrInsertNodeGen;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

public final class HandleCache implements TruffleObject {
    private static final int CACHE_SIZE = 10;

    final long[] keys;
    final Object[] values;
    private final TruffleObject ptrToResolveHandle;

    int pos = 0;

    public HandleCache(TruffleObject ptrToResolveHandle) {
        keys = new long[CACHE_SIZE];
        values = new Object[CACHE_SIZE];
        this.ptrToResolveHandle = ptrToResolveHandle;
    }

    protected int len() {
        return keys.length;
    }

    protected TruffleObject getPtrToResolveHandle() {
        return ptrToResolveHandle;
    }

    public ForeignAccess getForeignAccess() {
        return HandleCacheMRForeign.ACCESS;
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof HandleCache;
    }

    @MessageResolution(receiverType = HandleCache.class)
    static class HandleCacheMR {

        @Resolve(message = "EXECUTE")
        abstract static class ExecuteNode extends Node {
            @Child private GetOrInsertNode getOrInsertNode = GetOrInsertNodeGen.create();

            private final BranchProfile invalidArgCountProfile = BranchProfile.create();

            Object access(HandleCache receiver, Object[] args) {
                if (args.length != 1) {
                    invalidArgCountProfile.enter();
                    throw ArityException.raise(1, args.length);
                }
                return getOrInsertNode.execute(receiver, (long) args[0]);
            }

        }

        abstract static class GetOrInsertNode extends Node {
            @Child private Node executeNode;

            private final BranchProfile errorProfile = BranchProfile.create();

            public abstract Object execute(HandleCache cache, long handle);

            @ExplodeLoop
            @Specialization(guards = {"cache.len() == cachedLen", "cache.getPtrToResolveHandle() == ptrToResolveHandle"})
            Object doIt(HandleCache cache, long handle,
                            @Cached("cache.len()") int cachedLen,
                            @Cached("cache.getPtrToResolveHandle()") TruffleObject ptrToResolveHandle) {
                for (int i = 0; i < cachedLen; i++) {
                    if (cache.keys[i] == handle) {
                        return cache.values[i];
                    }
                }

                try {
                    Object resolved = ForeignAccess.sendExecute(getExecuteNode(), ptrToResolveHandle, handle);

                    cache.keys[cache.pos] = handle;
                    cache.values[cache.pos] = resolved;
                    cache.pos = (cache.pos + 1) % cache.len();

                    return resolved;
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    errorProfile.enter();
                    throw e.raise();
                }
            }

            private Node getExecuteNode() {
                if (executeNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    executeNode = insert(Message.EXECUTE.createNode());
                }
                return executeNode;
            }
        }
    }
}