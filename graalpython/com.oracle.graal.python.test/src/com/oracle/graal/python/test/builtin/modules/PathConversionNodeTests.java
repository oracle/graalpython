/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.builtin.modules;

import static com.oracle.graal.python.test.GraalPythonEnvVars.IS_WINDOWS;
import static com.oracle.graal.python.test.PythonTests.ts;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.math.BigInteger;
import java.util.Collections;

import org.graalvm.polyglot.Context;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PosixFd;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PosixPath;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.graal.python.util.Function;
import com.oracle.truffle.api.strings.TruffleString;

@RunWith(Parameterized.class)
public class PathConversionNodeTests extends ConversionNodeTests {

    private static final TruffleString T_ABC = TruffleString.fromJavaStringUncached("abc", TS_ENCODING);

    @Parameter(0) public String backendName;
    private Function<PosixPath, String> pathToString;

    @Parameters(name = "{0}")
    public static String[] params() {
        return new String[]{"java", "native"};
    }

    @Before
    public void setUp() {
        org.junit.Assume.assumeTrue(backendName.equals("java") || !IS_WINDOWS);
        PythonTests.enterContext(Collections.singletonMap("python.PosixModuleBackend", backendName), new String[0]);
        pathToString = backendName.equals("java") ? p -> (String) p.value : p -> {
            Buffer b = (Buffer) p.value;
            return new String(b.data, 0, (int) b.length);
        };
    }

    @After
    public void tearDown() {
        PythonTests.closeContext();
    }

    @Test
    public void noneAllowed() {
        Assert.assertEquals(".", callAndExpectPath(true, false, PNone.NONE, null, false));
        Assert.assertEquals(".", callAndExpectPath(true, false, PNone.NO_VALUE, null, false));
    }

    @Test
    public void noneForbiddenWithFd() {
        expectPythonMessage("TypeError: fun: arg should be string, bytes, os.PathLike or integer, not NoneType");
        call(false, true, PNone.NONE);
    }

    @Test
    public void noValueForbiddenWithoutFd() {
        expectPythonMessage("TypeError: fun: arg should be string, bytes or os.PathLike, not NoneType");
        call(false, false, PNone.NO_VALUE);
    }

    @Test
    public void string() {
        Assert.assertEquals("abc", callAndExpectPath(false, false, T_ABC, false));
        Assert.assertEquals("abc", callAndExpectPath(false, false, factory().createString(T_ABC), false));
    }

    @Test
    public void stringWithZero() {
        expectPythonMessage("ValueError: fun: embedded null character in arg");
        call(false, false, ts("a\0c"));
    }

    @Test
    public void bytes() {
        Assert.assertEquals("abc", callAndExpectPath(false, false, factory().createBytes("abc".getBytes()), true));
        Assert.assertEquals("abc", callAndExpectPath(false, true, factory().createBytes("abc".getBytes()), true));
        Assert.assertEquals("abc", callAndExpectPath(true, false, factory().createBytes("abc".getBytes()), true));
        Assert.assertEquals("abc", callAndExpectPath(true, true, factory().createBytes("abc".getBytes()), true));
    }

    @Test
    public void bytesWithZero() {
        expectPythonMessage("ValueError: fun: embedded null character in arg");
        call(false, false, factory().createBytes("a\0c".getBytes()));
    }

    @Test
    public void buffer() {
        Assert.assertEquals("abc", callAndExpectPath(false, false, evalValue("import array\narray.array('B', b'abc')"), true));
        // TODO: can we assert somehow that a warning is actually produced?
    }

    @Test
    public void bufferWithZero() {
        expectPythonMessage("ValueError: fun: embedded null character in arg");
        call(false, false, evalValue("import array\narray.array('B', b'a\\0c')"));
    }

    @Test
    public void boolAllowed() {
        Assert.assertEquals(0, callAndExpectFd(false));
        Assert.assertEquals(1, callAndExpectFd(true));
    }

    @Test
    public void boolForbidden() {
        expectPythonMessage("TypeError: fun: arg should be string, bytes, os.PathLike or None, not bool");
        call(true, false, true);
    }

    @Test
    public void intAllowed() {
        Assert.assertEquals(42, callAndExpectFd(42));
    }

    @Test
    public void intForbiddenWithNullable() {
        expectPythonMessage("TypeError: fun: arg should be string, bytes, os.PathLike or None, not int");
        call(true, false, 42);
    }

    @Test
    public void intForbiddenWithoutNullable() {
        expectPythonMessage("TypeError: fun: arg should be string, bytes or os.PathLike, not int");
        call(false, false, 42);
    }

    @Test
    public void longFitsInt() {
        Assert.assertEquals(42, callAndExpectFd(42L));
    }

    @Test
    public void longTooBig() {
        expectPythonMessage("OverflowError: fd is greater than maximum");
        call(false, true, 1L << 40);
    }

    @Test
    public void longTooSmall() {
        expectPythonMessage("OverflowError: fd is less than minimum");
        call(false, true, -1L << 40);
    }

    @Test
    public void longForbidden() {
        expectPythonMessage("TypeError: fun: arg should be string, bytes, os.PathLike or None, not int");
        call(true, false, 42L);
    }

    @Test
    public void pintFitsInt() {
        Assert.assertEquals(42, callAndExpectFd(factory().createInt(BigInteger.valueOf(42))));
    }

    @Test
    public void pintTooBig() {
        expectPythonMessage("OverflowError: fd is greater than maximum");
        call(false, true, factory().createInt(BigInteger.ONE.shiftLeft(100)));
    }

    @Test
    public void pintTooSmall() {
        expectPythonMessage("OverflowError: fd is less than minimum");
        call(false, true, factory().createInt(BigInteger.ONE.shiftLeft(100).negate()));
    }

    @Test
    public void pintForbidden() {
        expectPythonMessage("TypeError: fun: arg should be string, bytes, os.PathLike or None, not int");
        call(true, false, factory().createInt(BigInteger.valueOf(42)));
    }

    @Test
    public void indexFitsInt() {
        Assert.assertEquals(42, callAndExpectFd(evalValue("class C:\n  def __index__(self):\n    return 42\nC()")));
    }

    @Test
    public void indexTooBig() {
        expectPythonMessage("OverflowError: fd is greater than maximum");
        call(false, true, evalValue("class C:\n  def __index__(self):\n    return 1 << 40\nC()"));
    }

    @Test
    public void indexTooSmall() {
        expectPythonMessage("OverflowError: fd is less than minimum");
        call(false, true, evalValue("class C:\n  def __index__(self):\n    return -1 << 100\nC()"));
    }

    @Test
    public void indexForbidden() {
        expectPythonMessage("TypeError: fun: arg should be string, bytes, os.PathLike or None, not C");
        call(true, false, evalValue("class C:\n  def __index__(self):\n    return 42\nC()"));
    }

    @Test
    public void indexInIntSubclass() {
        Assert.assertEquals(42, callAndExpectFd(evalValue("class C(int):\n  def __index__(self):\n    return 123\nC(42)")));
    }

    @Test
    public void fspathBytes() {
        Assert.assertEquals("abc", callAndExpectPathEx("p = b'abc'\nclass C:\n  def __fspath__(self):\n    return p\n(C(), p)", true));
    }

    @Test
    public void fspathString() {
        Assert.assertEquals("abc", callAndExpectPathEx("p = 'abc'\nclass C:\n  def __fspath__(self):\n    return p\n(C(), p)", false));
    }

    @Test
    public void fspathPString() {
        Assert.assertEquals("abc", callAndExpectPathEx("class S(str):\n  pass\np = S('abc')\nclass C:\n  def __fspath__(self):\n    return p\n(C(), p)", false));
    }

    @Test
    public void fspathNone() {
        expectPythonMessage("TypeError: expected C.__fspath__() to return str or bytes, not NoneType");
        call(true, true, evalValue("class C:\n  def __fspath__(self):\n    return None\nC()"));
    }

    @Test
    public void fspathInt() {
        expectPythonMessage("TypeError: expected C.__fspath__() to return str or bytes, not int");
        call(true, true, evalValue("class C:\n  def __fspath__(self):\n    return 42\nC()"));
    }

    @Test
    public void fspathFloat() {
        expectPythonMessage("TypeError: expected C.__fspath__() to return str or bytes, not float");
        call(true, true, evalValue("class C:\n  def __fspath__(self):\n    return 3.14\nC()"));
    }

    @Test
    public void fspathBufferLike() {
        expectPythonMessage("TypeError: expected C.__fspath__() to return str or bytes, not array");
        call(true, true, evalValue("class C:\n  def __fspath__(self):\n    import array\n    return array.array('B', b'abc')\nC()"));
    }

    @Test
    public void fspathBytesLike() {
        expectPythonMessage("TypeError: expected C.__fspath__() to return str or bytes, not bytearray");
        call(true, true, evalValue("class C:\n  def __fspath__(self):\n    return bytearray(b'abc')\nC()"));
    }

    @Test
    public void unsupportedType1() {
        expectPythonMessage("TypeError: fun: arg should be string, bytes, os.PathLike, integer or None, not float");
        call(true, true, 3.14);
    }

    @Test
    public void unsupportedType2() {
        expectPythonMessage("TypeError: fun: arg should be string, bytes, os.PathLike or None, not float");
        call(true, false, 3.14);
    }

    @Test
    public void unsupportedType3() {
        expectPythonMessage("TypeError: fun: arg should be string, bytes, os.PathLike or integer, not float");
        call(false, true, 3.14);
    }

    @Test
    public void unsupportedType4() {
        expectPythonMessage("TypeError: fun: arg should be string, bytes or os.PathLike, not float");
        call(false, false, 3.14);
    }

    private String callAndExpectPath(boolean nullable, boolean allowFd, Object arg, Object orig, boolean wasBufferLike) {
        Object result = call(nullable, allowFd, arg);
        Assert.assertThat(result, CoreMatchers.instanceOf(PosixPath.class));
        PosixPath path = (PosixPath) result;
        Assert.assertSame(orig, path.originalObject);
        Assert.assertEquals(wasBufferLike, path.wasBufferLike);
        return pathToString.apply(path);
    }

    private String callAndExpectPath(boolean nullable, boolean allowFd, Object arg, boolean wasBufferLike) {
        return callAndExpectPath(nullable, allowFd, arg, arg, wasBufferLike);
    }

    private String callAndExpectPathEx(String script, boolean wasBufferLike) {
        PTuple o = (PTuple) evalValue(script);
        Object arg = o.getSequenceStorage().getItemNormalized(0);
        Object orig = o.getSequenceStorage().getItemNormalized(1);
        return callAndExpectPath(true, true, arg, orig, wasBufferLike);
    }

    private static int callAndExpectFd(Object arg) {
        Object result = call(true, true, arg);
        Assert.assertThat(result, CoreMatchers.instanceOf(PosixFd.class));
        PosixFd fd = (PosixFd) result;
        Assert.assertSame(arg, fd.originalObject);
        return fd.fd;
    }

    protected static Object call(boolean nullable, boolean allowFd, Object arg) {
        return call(arg, PosixModuleBuiltinsFactory.PathConversionNodeGen.create("fun", "arg", nullable, allowFd));
    }

    private static PythonObjectFactory factory() {
        return PythonObjectFactory.getUncached();
    }

    private static Object evalValue(String source) {
        return PythonContext.get(null).getEnv().asGuestValue(Context.getCurrent().eval("python", source));
    }
}
