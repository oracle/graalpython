/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Shape;

import sun.misc.Unsafe;

/**
 * POC-only reflective bridge to Truffle object internals.
 */
public final class DynamicObjectInternalAccessor {
    private static final Unsafe UNSAFE = PythonUtils.initUnsafe();
    private static final MethodHandles.Lookup TRUSTED_LOOKUP = getTrustedLookup();

    private static final MethodHandle SHAPE_GET_LOCATION = unreflectExact(Shape.class, "getLocation",
                    MethodType.methodType(Location.class, Shape.class, Object.class), Object.class);
    private static final MethodHandle LOCATION_GET_FINAL_ASSUMPTION_INTERNAL = unreflectExact(Location.class, "getFinalAssumptionInternal",
                    MethodType.methodType(Object.class, Location.class));
    private static final MethodHandle LOCATION_GET_INTERNAL = unreflectExact(Location.class, "getInternal",
                    MethodType.methodType(Object.class, Location.class, DynamicObject.class, Shape.class, boolean.class), DynamicObject.class, Shape.class, boolean.class);

    private DynamicObjectInternalAccessor() {
    }

    public static Location getLocationWithFinalAssumption(Shape shape, Object key) {
        try {
            Location location = (Location) SHAPE_GET_LOCATION.invokeExact(shape, key);
            if (location != null) {
                Object ignored = LOCATION_GET_FINAL_ASSUMPTION_INTERNAL.invokeExact(location);
                assert ignored != null;
            }
            return location;
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new RuntimeException("Failed to call Shape.getLocation/getFinalAssumptionInternal via MethodHandle", e);
        }
    }

    public static Object getLocationInternal(Location location, DynamicObject object, Shape shape, boolean guard) {
        try {
            return LOCATION_GET_INTERNAL.invokeExact(location, object, shape, guard);
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new RuntimeException("Failed to call Location.getInternal via MethodHandle", e);
        }
    }

    private static MethodHandle unreflectExact(Class<?> owner, String name, MethodType type, Class<?>... parameterTypes) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            return TRUSTED_LOOKUP.unreflect(method).asType(type);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize MethodHandle access to " + owner.getName() + "." + name, e);
        }
    }

    private static MethodHandles.Lookup getTrustedLookup() {
        try {
            Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            return (MethodHandles.Lookup) UNSAFE.getObject(UNSAFE.staticFieldBase(implLookup), UNSAFE.staticFieldOffset(implLookup));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access MethodHandles.Lookup.IMPL_LOOKUP", e);
        }
    }
}
