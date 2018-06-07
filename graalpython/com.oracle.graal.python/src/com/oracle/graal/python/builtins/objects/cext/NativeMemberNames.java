/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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
package com.oracle.graal.python.builtins.objects.cext;

public abstract class NativeMemberNames {
    public static final String OB_BASE = "ob_base";
    public static final String OB_REFCNT = "ob_refcnt";
    public static final String OB_TYPE = "ob_type";
    public static final String OB_SIZE = "ob_size";
    public static final String OB_SVAL = "ob_sval";
    public static final String TP_FLAGS = "tp_flags";
    public static final String TP_NAME = "tp_name";
    public static final String TP_BASE = "tp_base";
    public static final String TP_BASICSIZE = "tp_basicsize";
    public static final String TP_ALLOC = "tp_alloc";
    public static final String TP_AS_NUMBER = "tp_as_number";
    public static final String TP_HASH = "tp_hash";
    public static final String TP_RICHCOMPARE = "tp_richcompare";
    public static final String TP_SUBCLASSES = "tp_subclasses";
    public static final String TP_AS_BUFFER = "tp_as_buffer";
    public static final String TP_GETATTR = "tp_getattr";
    public static final String TP_SETATTR = "tp_setattr";
    public static final String TP_GETATTRO = "tp_getattro";
    public static final String TP_SETATTRO = "tp_setattro";
    public static final String _BASE = "_base";
    public static final String OB_ITEM = "ob_item";
    public static final String MA_USED = "ma_used";
    public static final String UNICODE_WSTR = "wstr";
    public static final String UNICODE_WSTR_LENGTH = "wstr_length";
    public static final String UNICODE_STATE = "state";
    public static final String UNICODE_STATE_INTERNED = "interned";
    public static final String UNICODE_STATE_KIND = "kind";
    public static final String UNICODE_STATE_COMPACT = "compact";
    public static final String UNICODE_STATE_ASCII = "ascii";
    public static final String UNICODE_STATE_READY = "ready";
    public static final String MD_DICT = "md_dict";
    public static final String BUF_DELEGATE = "buf_delegate";
    public static final String NB_ADD = "nb_add";
    public static final String NB_INDEX = "nb_index";
    public static final String NB_POW = "nb_power";

    public static boolean isValid(String key) {
        switch (key) {
            case OB_BASE:
            case OB_REFCNT:
            case OB_TYPE:
            case OB_SIZE:
            case OB_SVAL:
            case TP_FLAGS:
            case TP_NAME:
            case TP_BASE:
            case TP_BASICSIZE:
            case TP_ALLOC:
            case TP_AS_NUMBER:
            case TP_HASH:
            case TP_RICHCOMPARE:
            case TP_SUBCLASSES:
            case TP_AS_BUFFER:
            case TP_GETATTR:
            case TP_SETATTR:
            case TP_GETATTRO:
            case TP_SETATTRO:
            case _BASE:
            case OB_ITEM:
            case MA_USED:
            case UNICODE_WSTR:
            case UNICODE_WSTR_LENGTH:
            case UNICODE_STATE:
            case UNICODE_STATE_INTERNED:
            case UNICODE_STATE_KIND:
            case UNICODE_STATE_COMPACT:
            case UNICODE_STATE_ASCII:
            case UNICODE_STATE_READY:
            case MD_DICT:
            case NB_ADD:
            case NB_INDEX:
            case NB_POW:
                return true;
        }
        return false;
    }
}
