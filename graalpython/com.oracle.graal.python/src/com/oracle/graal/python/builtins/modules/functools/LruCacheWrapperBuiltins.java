/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.functools;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_FUNCTOOLS;
import static com.oracle.graal.python.nodes.ErrorMessages.MAXSIZE_SHOULD_BE_INTEGER_OR_NONE;
import static com.oracle.graal.python.nodes.ErrorMessages.THE_FIRST_ARGUMENT_MUST_BE_CALLABLE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLEAR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___COPY__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DEEPCOPY__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.functools.LruCacheObject.WrapperType;
import com.oracle.graal.python.builtins.modules.functools.LruCacheWrapperBuiltinsClinicProviders.LruCacheNewNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.DescrGetBuiltinNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyUnicodeCheckExactNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PLruCacheWrapper)
public final class LruCacheWrapperBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = LruCacheWrapperBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return LruCacheWrapperBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 5, takesVarArgs = true, takesVarKeywordArgs = true, //
                    constructsClass = PythonBuiltinClassType.PLruCacheWrapper, //
                    parameterNames = {"$cls", "user_function", "maxsize", "typed", "cache_info_type"}, //
                    doc = "Create a cached callable that wraps another function.\n" + //
                                    "\n" + //
                                    "user_function:      the function being cached\n" + //
                                    "\n" + //
                                    "maxsize:  0         for no caching\n" + //
                                    "          None      for unlimited cache size\n" + //
                                    "          n         for a bounded cache\n" + //
                                    "\n" + //
                                    "typed:    False     cache f(3) and f(3.0) as identical calls\n" + //
                                    "          True      cache f(3) and f(3.0) as distinct calls\n" + //
                                    "\n" + //
                                    "cache_info_type:    namedtuple class with the fields:\n" + //
                                    "                       hits misses currsize maxsize\n")
    @ArgumentClinic(name = "typed", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    protected abstract static class LruCacheNewNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return LruCacheNewNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object lruCacheNew(VirtualFrame frame, Object type,
                        Object func, Object maxsize_O, int typed, Object cache_info_type,
                        @Bind("this") Node inliningTarget,
                        @Cached PyCallableCheckNode callableCheck,
                        @Cached PyIndexCheckNode indexCheck,
                        @Cached PyNumberAsSizeNode numberAsSize,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {

            if (!callableCheck.execute(inliningTarget, func)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, THE_FIRST_ARGUMENT_MUST_BE_CALLABLE);
            }

            /* select the caching function, and make/inc maxsize_O */
            int maxsize;
            WrapperType wrapper;
            if (maxsize_O == PNone.NONE) {
                wrapper = WrapperType.INFINITE;
                /* use this only to initialize LruCacheObject attribute maxsize */
                maxsize = -1;
            } else if (indexCheck.execute(inliningTarget, maxsize_O)) {
                maxsize = numberAsSize.executeExact(frame, inliningTarget, maxsize_O, OverflowError);
                if (maxsize < 0) {
                    maxsize = 0;
                }
                if (maxsize == 0) {
                    wrapper = WrapperType.UNCACHED;
                } else {
                    wrapper = WrapperType.BOUNDED;
                }
            } else {
                throw raiseNode.get(inliningTarget).raise(TypeError, MAXSIZE_SHOULD_BE_INTEGER_OR_NONE);
            }

            LruCacheObject obj = factory.createLruCacheObject(type);

            obj.root.prev = obj.root;
            obj.root.next = obj.root;
            obj.wrapper = wrapper;
            obj.typed = typed;
            // obj.cache = new ObjectHashMap();

            obj.func = func;
            obj.misses = obj.hits = 0;
            obj.maxsize = maxsize;

            obj.kwdMark = PythonContext.get(inliningTarget).lookupBuiltinModule(T_FUNCTOOLS).getModuleState();

            obj.cacheInfoType = cache_info_type;
            // obj.dict = null;
            // obj.weakreflist = null;
            return obj;
        }
    }

    @Builtin(name = "cache_info", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CacheInfoNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object info(VirtualFrame frame, LruCacheObject self,
                        @Cached CallVarargsMethodNode callNode) {
            if (self.maxsize == -1) {
                return callNode.execute(frame, self.cacheInfoType,
                                new Object[]{self.hits, self.misses, PNone.NONE, self.cache.size()},
                                PKeyword.EMPTY_KEYWORDS);
            }
            return callNode.execute(frame, self.cacheInfoType,
                            new Object[]{self.hits, self.misses, self.maxsize, self.cache.size()},
                            PKeyword.EMPTY_KEYWORDS);
        }
    }

    @Builtin(name = "cache_clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CacheClearNode extends PythonUnaryBuiltinNode {

        // lru_cache_clear_list
        static void lruCacheClearList(LruListElemObject link) {
            while (link != null) {
                link = link.next;
            }
        }

        @Specialization
        Object clear(LruCacheObject self) {
            LruListElemObject list = ClearNode.lruCacheUnlinkList(self);
            self.hits = self.misses = 0;
            self.cache.clear();
            lruCacheClearList(list);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class LruDictNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(mapping)")
        static Object getDict(LruCacheObject self, @SuppressWarnings("unused") PNone mapping,
                        @Bind("this") Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(inliningTarget, self);
        }

        @Specialization
        static Object setDict(LruCacheObject self, PDict mapping,
                        @Bind("this") Node inliningTarget,
                        @Cached SetDictNode setDict) {
            setDict.execute(inliningTarget, self, mapping);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isDict(mapping)"})
        static Object setDict(@SuppressWarnings("unused") LruCacheObject self, Object mapping,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, mapping);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PartialReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(VirtualFrame frame, LruCacheObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getQualname) {
            return getQualname.execute(frame, inliningTarget, self, T___QUALNAME__);
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class PartialCallNode extends PythonVarargsBuiltinNode {

        // uncached_lru_cache_wrapper
        @Specialization(guards = "self.isUncached()")
        static Object uncachedLruCacheWrapper(VirtualFrame frame, LruCacheObject self, Object[] args, PKeyword[] kwds,
                        @Shared @Cached CallVarargsMethodNode callNode) {
            self.misses++;
            return callNode.execute(frame, self.func, args, kwds);
        }

        // lru_cache_make_key
        static Object lruCacheMakeKey(Object kwdMark, Object[] args, PKeyword[] kwds, int typed,
                        Node inliningTarget,
                        GetClassNode getClassNode,
                        PyUnicodeCheckExactNode unicodeCheckExact,
                        PyLongCheckExactNode longCheckExact,
                        PythonObjectFactory factory) {
            int kwdsSize = kwds.length;
            /* short path, key will match args anyway, which is a tuple */
            if (typed == 0 && kwdsSize == 0) {
                if (args.length == 1) {
                    Object key = args[0];
                    if (unicodeCheckExact.execute(inliningTarget, key) || longCheckExact.execute(inliningTarget, key)) {
                        /*
                         * For common scalar keys, save space by dropping the enclosing args tuple
                         */
                        return key;
                    }
                }

                return factory.createTuple(args);
            }
            int argsLen = args.length;
            int keySize = args.length;
            if (kwdsSize != 0) {
                keySize += kwdsSize * 2 + 1;
            }
            if (typed != 0) {
                keySize += argsLen + kwdsSize;
            }

            Object[] keyArray = new Object[keySize];
            int keyPos = 0;
            for (Object item : args) {
                keyArray[keyPos++] = item;
            }
            if (kwdsSize != 0) {
                keyArray[keyPos++] = kwdMark;
                for (PKeyword kv : kwds) {
                    keyArray[keyPos++] = kv.getName();
                    keyArray[keyPos++] = kv.getValue();
                }
                assert (keyPos == argsLen + kwdsSize * 2 + 1);
            }
            if (typed != 0) {
                for (Object arg : args) {
                    keyArray[keyPos++] = getClassNode.execute(inliningTarget, arg);
                }
                if (kwdsSize != 0) {
                    for (PKeyword kv : kwds) {
                        keyArray[keyPos++] = getClassNode.execute(inliningTarget, kv.getValue());
                    }
                }
            }
            assert (keyPos == keySize);
            return factory.createTuple(keyArray);
        }

        // infinite_lru_cache_wrapper
        static Object infiniteLruCacheWrapper(VirtualFrame frame, LruCacheObject self, Object[] args, PKeyword[] kwds,
                        Node inliningTarget,
                        Object key,
                        long hash,
                        Object cachedItem,
                        ObjectHashMap.PutNode setItem,
                        CallVarargsMethodNode callNode) {
            Object result = cachedItem;
            if (result != null) {
                self.hits++;
                return result;
            }
            self.misses++;
            result = callNode.execute(frame, self.func, args, kwds);
            setItem.put(frame, inliningTarget, self.cache, key, hash, result);
            return result;
        }

        /*-
        static void lru_cache_prepend_link(LruCacheObject self, LruListElemObject link) {
            LruListElemObject root = self;
            LruListElemObject first = root.next;
            first.prev = root.next = link;
            link.prev = root;
            link.next = first;
        }
        */

        // lru_cache_append_link
        static void lruCacheAppendLink(LruCacheObject self, LruListElemObject link) {
            LruListElemObject root = self.root;
            LruListElemObject last = root.prev;
            last.next = root.prev = link;
            link.prev = last;
            link.next = root;
        }

        // lru_cache_extract_link
        static void lruCacheExtractLink(LruListElemObject link) {
            LruListElemObject prev = link.prev;
            LruListElemObject next = link.next;
            prev.next = link.next;
            next.prev = link.prev;
        }

        /*
         * General note on reentrancy:
         *
         * There are four dictionary calls in the bounded_lru_cache_wrapper(): 1) The initial check
         * for a cache match. 2) The post user-function check for a cache match. 3) The deletion of
         * the oldest entry. 4) The addition of the newest entry.
         *
         * In all four calls, we have a known hash which lets use avoid a call to __hash__(). That
         * leaves only __eq__ as a possible source of a reentrant call.
         *
         * The __eq__ method call is always made for a cache hit (dict access #1). Accordingly, we
         * have make sure not modify the cache state prior to this call.
         *
         * The __eq__ method call is never made for the deletion (dict access #3) because it is an
         * identity match.
         *
         * For the other two accesses (#2 and #4), calls to __eq__ only occur when some other entry
         * happens to have an exactly matching hash (all 64-bits). Though rare, this can happen, so
         * we have to make sure to either call it at the top of its code path before any cache state
         * modifications (dict access #2) or be prepared to restore invariants at the end of the
         * code path (dict access #4).
         *
         * Another possible source of reentrancy is a decref which can trigger arbitrary code
         * execution. To make the code easier to reason about, the decrefs are deferred to the end
         * of the each possible code path so that we know the cache is a consistent state.
         */

        // bounded_lru_cache_wrapper
        static Object boundedLruCacheWrapper(VirtualFrame frame, Node inliningTarget, LruCacheObject self, Object[] args, PKeyword[] kwds,
                        Object key,
                        long hash,
                        Object cachedItem,
                        ObjectHashMap.GetNode getItem,
                        ObjectHashMap.PutNode setItem,
                        ObjectHashMap.RemoveNode popItem,
                        CallVarargsMethodNode callNode) {
            if (cachedItem != null) {
                assert cachedItem instanceof LruListElemObject : "cachedItem should be an LruListElemObject";
                LruListElemObject link = (LruListElemObject) cachedItem;
                lruCacheExtractLink(link);
                lruCacheAppendLink(self, link);
                self.hits++;
                return link.result;
            }
            self.misses++;
            Object result = callNode.execute(frame, self.func, args, kwds);
            Object testresult = getItem.execute(frame, inliningTarget, self.cache, key, hash);
            if (testresult != null) {
                /*
                 * Getting here means that this same key was added to the cache during the
                 * PyObject_Call(). Since the link update is already done, we need only return the
                 * computed result.
                 */
                return result;
            }
            /*
             * This is the normal case. The new key wasn't found before user function call and it is
             * still not there. So we proceed normally and update the cache with the new result.
             */

            assert (self.maxsize > 0);
            if (self.cache.size() < self.maxsize || self.root.next == self.root) {
                /* Cache is not full, so put the result in a new link */
                LruListElemObject link = new LruListElemObject();

                link.hash = hash;
                link.key = key;
                link.result = result;
                /*
                 * What is really needed here is a SetItem variant with a "no clobber" option. If
                 * the __eq__ call triggers a reentrant call that adds this same key, then this
                 * setitem call will update the cache dict with this new link, leaving the old link
                 * as an orphan (i.e. not having a cache dict entry that refers to it).
                 */
                setItem.put(frame, inliningTarget, self.cache, key, hash, link);
                lruCacheAppendLink(self, link);
                return result;
            }
            /*
             * Since the cache is full, we need to evict an old key and add a new key. Rather than
             * free the old link and allocate a new one, we reuse the link for the new key and
             * result and move it to front of the cache to mark it as recently used.
             *
             * We try to assure all code paths (including errors) leave all of the links in place.
             * Either the link is successfully updated and moved or it is restored to its old
             * position. However if an unrecoverable error is found, it doesn't make sense to
             * reinsert the link, so we leave it out and the cache will no longer register as full.
             */

            /* Extract the oldest item. */
            // assert (self.next != self);
            LruListElemObject link = self.root.next;
            lruCacheExtractLink(link);
            /*
             * Remove it from the cache. The cache dict holds one reference to the link. We created
             * one other reference when the link was created. The linked list only has borrowed
             * references.
             */
            Object popresult = popItem.execute(frame, inliningTarget, self.cache, link.key, link.hash);
            popresult = popresult != null ? popresult : PNone.NONE;
            if (popresult == PNone.NONE) {
                /*
                 * Getting here means that the user function call or another thread has already
                 * removed the old key from the dictionary. This link is now an orphan. Since we
                 * don't want to leave the cache in an inconsistent state, we don't restore the
                 * link.
                 */
                return result;
            }
            // mq: in case this is needed, this should be executed within the catch statement
            // if (popresult == null) {
            // /* An error arose while trying to remove the oldest key (the one
            // being evicted) from the cache. We restore the link to its
            // original position as the oldest link. Then we allow the
            // error propagate upward; treating it the same as an error
            // arising in the user function. */
            // lru_cache_prepend_link(self, link);
            // }

            link.hash = hash;
            link.key = key;
            link.result = result;
            /*
             * Note: The link is being added to the cache dict without the prev and next fields set
             * to valid values. We have to wait for successful insertion in the cache dict before
             * adding the link to the linked list. Otherwise, the potentially reentrant __eq__ call
             * could cause the then orphan link to be visited.
             */
            setItem.put(frame, inliningTarget, self.cache, key, hash, link);
            lruCacheAppendLink(self, link);
            return result;
        }

        @Specialization(guards = "!self.isUncached()")
        static Object cachedLruCacheWrapper(VirtualFrame frame, LruCacheObject self, Object[] args, PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached CallVarargsMethodNode callNode,
                        @Cached PyObjectHashNode hashNode,
                        @Cached ObjectHashMap.GetNode getItem,
                        @Cached ObjectHashMap.PutNode setItem,
                        @Cached GetClassNode getClassNode,
                        @Cached PyUnicodeCheckExactNode unicodeCheckExact,
                        @Cached PyLongCheckExactNode longCheckExact,
                        @Cached ObjectHashMap.RemoveNode popItem,
                        @Cached InlinedConditionProfile profile,
                        @Cached PythonObjectFactory factory) {
            Object key = lruCacheMakeKey(self.kwdMark, args, kwds, self.typed,
                            inliningTarget, getClassNode, unicodeCheckExact, longCheckExact, factory);
            long hash = hashNode.execute(frame, inliningTarget, key);
            Object cached = getItem.execute(frame, inliningTarget, self.cache, key, hash);
            if (profile.profile(inliningTarget, self.isInfinite())) {
                return infiniteLruCacheWrapper(frame, self, args, kwds, inliningTarget, key, hash, cached, setItem, callNode);
            }
            return boundedLruCacheWrapper(frame, inliningTarget, self, args, kwds, key, hash, cached, getItem, setItem, popItem, callNode);
        }
    }

    @Builtin(name = J___CLEAR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        // lru_cache_unlink_list
        static LruListElemObject lruCacheUnlinkList(LruCacheObject self) {
            LruListElemObject root = self.root;
            LruListElemObject link = root.next;
            if (link == root) {
                return null;
            }
            root.prev.next = null;
            root.next = root.prev = root;
            return link;
        }

        @Specialization
        Object clear(LruCacheObject self) {
            LruListElemObject list = lruCacheUnlinkList(self);
            self.cache.clear();
            self.func = null;
            self.kwdMark = null;
            // self.lru_list_elem_type = null;
            self.cacheInfoType = null;
            // self.dict = null;
            CacheClearNode.lruCacheClearList(list);
            return PNone.NONE;
        }
    }

    @Slot(SlotKind.tp_descr_get)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class GetNode extends DescrGetBuiltinNode {

        @Specialization
        static Object getmethod(LruCacheObject self, Object obj, @SuppressWarnings("unused") Object type,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile objIsNoneProfile,
                        @Cached PythonObjectFactory factory) {
            if (objIsNoneProfile.profile(inliningTarget, obj instanceof PNone)) {
                return self;
            }
            return factory.createMethod(obj, self);
        }
    }

    @Builtin(name = J___COPY__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object copy(LruCacheObject self) {
            return self;
        }
    }

    @Builtin(name = J___DEEPCOPY__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DeepCopyNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object deepcopy(LruCacheObject self, @SuppressWarnings("unused") Object ignored) {
            return self;
        }
    }
}
