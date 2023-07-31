/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.util.PythonUtils;

public abstract class GraalHPyData {
    private static final int INDEX_DATA_PTR = 0;
    private static final int INDEX_CALL_FUN = 1;
    private static final int HPY_FIELD_OFFSET = 1;

    private static final Long DEFAULT_DATA_PTR_VALUE = 0L;
    private static final Object DEFAULT_CALL_FUNCTION_VALUE = null;

    public static void setHPyNativeSpace(PythonObject object, Object dataPtr) {
        Object[] hpyData = object.getHPyData();
        if (hpyData == null) {
            hpyData = new Object[]{dataPtr, DEFAULT_CALL_FUNCTION_VALUE};
            object.setHPyData(hpyData);
        } else {
            hpyData[INDEX_DATA_PTR] = dataPtr;
        }
    }

    public static Object getHPyNativeSpace(PythonObject object) {
        Object[] hpyData = object.getHPyData();
        return hpyData != null ? hpyData[INDEX_DATA_PTR] : DEFAULT_DATA_PTR_VALUE;
    }

    public static void setHPyCallFunction(PythonObject object, Object callFunctionPtr) {
        Object[] hpyData = object.getHPyData();
        if (hpyData == null) {
            hpyData = new Object[]{DEFAULT_DATA_PTR_VALUE, callFunctionPtr};
            object.setHPyData(hpyData);
        } else {
            hpyData[INDEX_CALL_FUN] = callFunctionPtr;
        }
    }

    public static Object getHPyCallFunction(PythonObject object) {
        Object[] hpyData = object.getHPyData();
        return hpyData != null ? hpyData[INDEX_CALL_FUN] : null;
    }

    public static Object getHPyField(PythonObject object, int location) {
        assert location > 0;
        Object[] hpyData = object.getHPyData();
        return hpyData != null ? hpyData[location + HPY_FIELD_OFFSET] : null;
    }

    public static int setHPyField(PythonObject object, Object referent, int location) {
        Object[] hpyFields = object.getHPyData();
        if (location != 0) {
            assert hpyFields != null;
            hpyFields[location + HPY_FIELD_OFFSET] = referent;
            return location;
        } else {
            int newLocation;
            if (hpyFields == null) {
                newLocation = 1;
                hpyFields = new Object[]{DEFAULT_DATA_PTR_VALUE, null, referent};
            } else {
                int newFieldIdx = hpyFields.length;
                hpyFields = PythonUtils.arrayCopyOf(hpyFields, newFieldIdx + 1);
                hpyFields[newFieldIdx] = referent;
                newLocation = newFieldIdx - HPY_FIELD_OFFSET;
            }
            object.setHPyData(hpyFields);
            return newLocation;
        }
    }
}
