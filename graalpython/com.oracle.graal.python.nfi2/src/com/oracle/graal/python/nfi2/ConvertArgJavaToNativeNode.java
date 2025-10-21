/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nfi2;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

// TODO(NFI2) remove or replace with a simple function, we probably use interop only for widening conversions
abstract class ConvertArgJavaToNativeNode extends Node {

    abstract Object execute(Object originalArg);

    @GenerateUncached
    @GenerateInline(false)
    abstract static class ToVOIDNode extends ConvertArgJavaToNativeNode {

        @Specialization
        Object doConvert(@SuppressWarnings("unused") Object value) {
            return null;
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class ToENVNode extends ConvertArgJavaToNativeNode {

        @Specialization
        long doLong(long value) {
            return value;
        }

        @Specialization(limit = "3")
        long doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) {
            try {
                return interop.asLong(value);
            } catch (UnsupportedMessageException ex) {
                throw shouldNotReachHere(ex);
            }
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class ToINT8Node extends ConvertArgJavaToNativeNode {

        @Specialization
        byte doLong(byte value) {
            return value;
        }

        @Specialization(limit = "3")
        byte doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) {
            try {
                return interop.asByte(value);
            } catch (UnsupportedMessageException ex) {
                throw shouldNotReachHere(ex);
            }
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class ToINT16Node extends ConvertArgJavaToNativeNode {

        @Specialization
        short doLong(short value) {
            return value;
        }

        @Specialization(limit = "3")
        short doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) {
            try {
                return interop.asShort(value);
            } catch (UnsupportedMessageException ex) {
                throw shouldNotReachHere(ex);
            }
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class ToINT32Node extends ConvertArgJavaToNativeNode {

        @Specialization
        int doLong(int value) {
            return value;
        }

        @Specialization
        int doBool(boolean value) {
            // TODO(NFI2) some closure returns bool instead of SINT32
            return value ? 1 : 0;
        }

        @Specialization(limit = "3")
        int doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) {
            try {
                return interop.asInt(value);
            } catch (UnsupportedMessageException ex) {
                throw shouldNotReachHere(ex);
            }
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class ToINT64Node extends ConvertArgJavaToNativeNode {

        @Specialization
        long doLong(long value) {
            return value;
        }

        @Specialization(limit = "3")
        long doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) {
            try {
                return interop.asLong(value);
            } catch (UnsupportedMessageException ex) {
                throw shouldNotReachHere(ex);
            }
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class ToFLOATNode extends ConvertArgJavaToNativeNode {

        @Specialization
        float doLong(float value) {
            return value;
        }

        @Specialization(limit = "3")
        float doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) {
            try {
                return interop.asFloat(value);
            } catch (UnsupportedMessageException ex) {
                throw shouldNotReachHere(ex);
            }
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class ToDOUBLENode extends ConvertArgJavaToNativeNode {

        @Specialization
        double doLong(double value) {
            return value;
        }

        @Specialization(limit = "3")
        double doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) {
            try {
                return interop.asDouble(value);
            } catch (UnsupportedMessageException ex) {
                throw shouldNotReachHere(ex);
            }
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class ToPointerNode extends ConvertArgJavaToNativeNode {

        @Specialization
        long doLong(long value) {
            return value;
        }

        @Specialization
        long doLong(NativePointer value) {
            return value.nativePointer;
        }

        @Specialization(limit = "3", guards = "interop.isPointer(arg)", rewriteOn = UnsupportedMessageException.class)
        long putPointer(Object arg,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asPointer(arg);
        }

        @Specialization(limit = "3", guards = {"!interop.isPointer(arg)", "interop.isNull(arg)"})
        long putNull(@SuppressWarnings("unused") Object arg,
                        @SuppressWarnings("unused") @CachedLibrary("arg") InteropLibrary interop) {
            return 0L;
        }

        @Specialization(limit = "3", replaces = {"putPointer", "putNull"})
        static long putGeneric(Object arg,
                        @Bind Node node,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached InlinedBranchProfile exception) {
            try {
                if (!interop.isPointer(arg)) {
                    interop.toNative(arg);
                }
                if (interop.isPointer(arg)) {
                    return interop.asPointer(arg);
                }
            } catch (UnsupportedMessageException ex) {
                // fallthrough
            }
            exception.enter(node);
            if (interop.isNull(arg)) {
                return 0L;
            } else {
                try {
                    if (interop.isNumber(arg)) {
                        return interop.asLong(arg);
                    }
                } catch (UnsupportedMessageException ex2) {
                    // fallthrough
                }
            }
            throw shouldNotReachHere();
        }
    }

}
