package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.builtins.objects.cext.HandleCacheFactory.HandleCacheMRFactory.GetOrInsertNodeGen;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

public final class HandleCache implements TruffleObject {
    public static final int CACHE_SIZE = 10;

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

        static class InvalidCacheEntryException extends ControlFlowException {
            private static final long serialVersionUID = 1L;
            public static final InvalidCacheEntryException INSTANCE = new InvalidCacheEntryException();
        }

        @ImportStatic(HandleCache.class)
        abstract static class GetOrInsertNode extends Node {
            @Child private Node executeNode;

            private final BranchProfile errorProfile = BranchProfile.create();

            public abstract Object execute(HandleCache cache, long handle);

            @Specialization(limit = "CACHE_SIZE", guards = {"cache.len() == cachedLen",
                            "handle == cachedHandle"}, rewriteOn = InvalidCacheEntryException.class)
            Object doCached(HandleCache cache, @SuppressWarnings("unused") long handle,
                            @Cached("handle") long cachedHandle,
                            @Cached("cache.len()") @SuppressWarnings("unused") int cachedLen,
                            @Cached("cache.getPtrToResolveHandle()") @SuppressWarnings("unused") TruffleObject ptrToResolveHandle,
                            @Cached("lookupPosition(cache, handle, cachedLen, ptrToResolveHandle)") int cachedPosition) throws InvalidCacheEntryException {
                if (cache.keys[cachedPosition] == cachedHandle) {
                    return cache.values[cachedPosition];
                }
                throw InvalidCacheEntryException.INSTANCE;
            }

            @Specialization(guards = {"cache.len() == cachedLen"}, replaces = "doCached")
            Object doFullLookup(HandleCache cache, long handle,
                            @Cached("cache.len()") int cachedLen,
                            @Cached("cache.getPtrToResolveHandle()") TruffleObject ptrToResolveHandle) {
                int pos = lookupPosition(cache, handle, cachedLen, ptrToResolveHandle);
                return cache.values[pos];
            }

            @ExplodeLoop
            protected int lookupPosition(HandleCache cache, long handle, int cachedLen, TruffleObject ptrToResolveHandle) {
                for (int i = 0; i < cachedLen; i++) {
                    if (cache.keys[i] == handle) {
                        return i;
                    }
                }

                try {
                    Object resolved = ForeignAccess.sendExecute(getExecuteNode(), ptrToResolveHandle, handle);

                    int insertPos = cache.pos;
                    cache.keys[insertPos] = handle;
                    cache.values[insertPos] = resolved;
                    cache.pos = (insertPos + 1) % cache.len();

                    return insertPos;
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