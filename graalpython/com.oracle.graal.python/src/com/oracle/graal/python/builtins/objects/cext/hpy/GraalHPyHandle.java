/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
// skip GIL
package com.oracle.graal.python.builtins.objects.cext.hpy;

import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.GetHPyHandleForSingleton;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFactory.GetHPyHandleForSingletonNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.llvm.GraalHPyLLVMContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
public final class GraalHPyHandle implements TruffleObject {
    private static final int UNINITIALIZED = Integer.MIN_VALUE;

    public static final Object NULL_HANDLE_DELEGATE = PNone.NO_VALUE;
    public static final GraalHPyHandle NULL_HANDLE = new GraalHPyHandle();
    public static final String J_I = "_i";
    public static final TruffleString T_I = tsLiteral(J_I);

    private final Object delegate;
    /**
     * The ID of the handle if it was allocated in the handle table.
     * <p>
     * The value also encodes the state:<br/>
     * (1) If the value is {@link #UNINITIALIZED}, then the handle was never allocated in the handle
     * table.<br/>
     * (2) If the value is zero or positive then this is the index for the handle table. If the<br/>
     * (3) If the value is negative but not {@link #UNINITIALIZED} then the handle was already
     * closed (only used in HPy debug mode)<br/>
     * </p>
     */
    private int id;

    private GraalHPyHandle() {
        this(NULL_HANDLE_DELEGATE, 0);
    }

    private GraalHPyHandle(Object delegate) {
        this(delegate, UNINITIALIZED);
    }

    private GraalHPyHandle(Object delegate, int id) {
        assert delegate != null : "HPy handles to Java null are not allowed";
        assert delegate != NULL_HANDLE_DELEGATE || id == 0 : "must not not create more than on HPy_NULL";
        this.delegate = assertNoJavaString(delegate);
        this.id = id;
    }

    public static GraalHPyHandle createSingleton(Object delegate, int handle) {
        assert handle <= GraalHPyBoxing.SINGLETON_HANDLE_MAX;
        return new GraalHPyHandle(delegate, handle);
    }

    public static GraalHPyHandle create(Object delegate) {
        return new GraalHPyHandle(delegate);
    }

    public static GraalHPyHandle createField(Object delegate, int idx) {
        return new GraalHPyHandle(delegate, idx);
    }

    public static GraalHPyHandle createGlobal(Object delegate, int idx) {
        return new GraalHPyHandle(delegate, idx);
    }

    /**
     * This is basically like {@code toNative} but also returns the ID.
     */
    public int getIdUncached(GraalHPyContext context) {
        return getId(context, ConditionProfile.getUncached(), GetHPyHandleForSingletonNodeGen.getUncached());
    }

    public int getId(GraalHPyContext context, ConditionProfile hasIdProfile, GetHPyHandleForSingleton getSingletonNode) {
        int result = id;
        if (!isPointer(hasIdProfile)) {
            assert !GraalHPyBoxing.isBoxablePrimitive(delegate) : "allocating handle for value that could be boxed";
            result = getSingletonNode.execute(this.delegate);
            if (result == -1) {
                // profiled by the node
                result = context.getHPyHandleForNonSingleton(this.delegate);
            }
            id = result;
        }
        assert isValidId(this.delegate, result);
        return result;
    }

    public boolean isValidId(Object obj, int newId) {
        if (delegate == PNone.NO_VALUE) {
            // special case of HPy_NULL internally represented as NO_VALUE
            return newId == 0;
        }
        int singletonId = GraalHPyContext.getHPyHandleForSingleton(obj);
        return singletonId == -1 || singletonId == newId;
    }

    @ExportMessage
    boolean isPointer(
                    @Exclusive @Cached(inline = false) ConditionProfile isNativeProfile) {
        return isNativeProfile.profile(id >= 0 || delegate instanceof Integer || delegate instanceof Double);
    }

    @ExportMessage
    long asPointer() throws UnsupportedMessageException {
        // note: we don't use a profile here since 'asPointer' is usually used right after
        // 'isPointer'
        if (!isPointer(ConditionProfile.getUncached())) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedMessageException.create();
        }
        if (id != UNINITIALIZED) {
            return GraalHPyBoxing.boxHandle(id);
        } else if (delegate instanceof Integer) {
            return GraalHPyBoxing.boxInt((Integer) delegate);
        } else if (delegate instanceof Double) {
            return GraalHPyBoxing.boxDouble((Double) delegate);
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    /**
     * Allocates the handle in the global handle table of the provided HPy context. If this is used
     * in compiled code, this {@code GraalHPyHandle} object will definitively be allocated.
     */
    @ExportMessage
    void toNative(@Exclusive @Cached(inline = false) ConditionProfile isNativeProfile,
                    @Cached GetHPyHandleForSingleton getSingletonNode,
                    @CachedLibrary("this") InteropLibrary lib) {
        getId(PythonContext.get(lib).getHPyContext(), isNativeProfile, getSingletonNode);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasNativeType(@Bind("$node") Node node) {
        return PythonContext.get(node).getHPyContext().getBackend() instanceof GraalHPyLLVMContext;
    }

    @ExportMessage
    Object getNativeType(@Bind("$node") Node node) {
        if (PythonContext.get(node).getHPyContext().getBackend() instanceof GraalHPyLLVMContext backend) {
            return backend.getHPyNativeType();
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    public Object getDelegate() {
        return delegate;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new PythonAbstractObject.Keys(new TruffleString[]{T_I});
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMemberReadable(String key,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared("eq") @Cached TruffleString.EqualNode eqNode) {
        TruffleString tmember = fromJavaStringNode.execute(key, TS_ENCODING);
        return eqNode.execute(T_I, tmember, TS_ENCODING);
    }

    @ExportMessage
    Object readMember(String key,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared("eq") @Cached TruffleString.EqualNode eqNode) throws UnknownIdentifierException {
        TruffleString tmember = fromJavaStringNode.execute(key, TS_ENCODING);
        if (eqNode.execute(T_I, tmember, TS_ENCODING)) {
            return this;
        }
        throw UnknownIdentifierException.create(key);
    }

    @ExportMessage
    boolean isNull() {
        return id == 0;
    }

    static boolean isAllocated(int id) {
        return id != UNINITIALIZED && id != 0;
    }

    boolean isAllocated() {
        return GraalHPyHandle.isAllocated(id);
    }

    boolean isValid() {
        return id > 0;
    }

    void closeAndInvalidate(GraalHPyContext hpyContext) {
        assert id != UNINITIALIZED;
        if (hpyContext.releaseHPyHandleForObject(id)) {
            id = -id;
        }
    }

    int getGlobalId() {
        assert id > 0 : "any HPyGlobal handle already has an id";
        return id;
    }

    int getFieldId() {
        assert id >= 0 : "any HPyField handle already has an id";
        return id;
    }

    public GraalHPyHandle copy() {
        return new GraalHPyHandle(delegate);
    }

    static boolean wasAllocated(int id) {
        return id != UNINITIALIZED;
    }
}
