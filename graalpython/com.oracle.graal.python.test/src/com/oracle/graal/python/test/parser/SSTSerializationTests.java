/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.parser;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.graal.python.parser.ScopeInfo;
import com.oracle.graal.python.runtime.PythonCodeSerializer;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class SSTSerializationTests extends ParserTestBase {

    @Test
    public void fileTest() throws Exception {
        checkFileSerialization("RuntimeFileTests/_collections_abc.py");
        checkFileSerialization("RuntimeFileTests/_descriptor.py");
        checkFileSerialization("RuntimeFileTests/_sitebuiltins.py");
        checkFileSerialization("RuntimeFileTests/builtins.py");
        checkFileSerialization("RuntimeFileTests/enumt.py");
        checkFileSerialization("RuntimeFileTests/functools.py");
        checkFileSerialization("RuntimeFileTests/heapq.py");
        checkFileSerialization("RuntimeFileTests/initCollectionsPart2.py");
        checkFileSerialization("RuntimeFileTests/keyword.py");
        checkFileSerialization("RuntimeFileTests/locale.py");
        checkFileSerialization("RuntimeFileTests/operator.py");
        checkFileSerialization("RuntimeFileTests/re.py");
        checkFileSerialization("RuntimeFileTests/reprlib.py");
        checkFileSerialization("RuntimeFileTests/site.py");
        checkFileSerialization("RuntimeFileTests/sre_compile.py");
        checkFileSerialization("RuntimeFileTests/sre_constants.py");
        checkFileSerialization("RuntimeFileTests/sre_parse.py");
        checkFileSerialization("RuntimeFileTests/sys.py");
        checkFileSerialization("RuntimeFileTests/traceback.py");
        checkFileSerialization("RuntimeFileTests/types.py");
        // TODO test of these two files are disabled because the comparing framedescriptors
        // fails due different order of slots. After solving this problem
        // we can enable it. The same is applied for allFilesInLib test
        // checkFileSerialization("RuntimeFileTests/collections__init__.py");
        // checkFileSerialization("RuntimeFileTests/initCollectionsPart1.py");
    }

    // Test
    public void allFilesInLib() throws Exception {
        // This test can take some time (around 30 s on machine with 8 cores). It goes throught all
        // the files in
        // cpython libraries files and test the serialization and deserialization
        String libDirPath = System.getProperty("org.graalvm.language.python.home");
        libDirPath += "/lib-python/3/";
        File libDir = new File(libDirPath);
        List<String> wrongFiles = Arrays.asList(
                        "test_parser.py",
                        "test_re.py",
                        "test_unicode_identifiers.py",
                        "badsyntax_3131.py",
                        "bad_coding2.py");
        checkFileSerialization(libDir, (File file) -> {
            if (file.isDirectory() && !"data".equals(file.getName())) {
                return true;
            } else {
                if (wrongFiles.contains(file.getName())) {
                    return false;
                }
                return file.getName().endsWith(".py");
            }
        }, true);
    }

    @Test
    public void assertTest() throws Exception {
        checkSerialization("assert True");
        checkSerialization(
                        "def avg(marks):\n" + "    assert len(marks) != 0,\"List is empty.\"");
        checkSerialization(
                        "def avg():\n" + "    assert len != 0, getMessage(len)");
        checkSerialization("assert not hascased");
    }

    @Test
    public void assignmentTest() throws Exception {
        checkSerialization("a = 1");
        checkSerialization("a = b = 1");
        checkSerialization("a = 0\n" + "b = a\n" + "c = a + a + b");
        checkSerialization("a = b = c = d = e");
        checkSerialization("a, b, c = 1, 2, 3");
        checkSerialization("def fn():\n  a = b = c = d = e");
        checkSerialization("def fn():\n  a, b, c = 1, 2, 3");
        checkSerialization("a.b = 1");
        checkSerialization("f().b = 1");
        checkSerialization("i, j, k = x = a");
        checkSerialization("a += b");
        checkSerialization("a -= b");
        checkSerialization("a *= b");
        checkSerialization("a /= b");
        checkSerialization("a //= b");
        checkSerialization("a %= b");
        checkSerialization("a &= b");
        checkSerialization("a |= b");
        checkSerialization("a ^= b");
        checkSerialization("a <<= b");
        checkSerialization("a >>= b");
        checkSerialization("a **= b");
        checkSerialization("def fn (): x += 3");
        checkSerialization(
                        "def _method(*args, **keywords):\n" + "    cls_or_self, *rest = args");
        checkSerialization("j: int");
        checkSerialization("def fn():\n" + "  index : int = 0\n");
        checkSerialization("j = 1\n" + "ahoj.__annotations__['j'] = float");
        checkSerialization("a: int = 1");
    }

    @Test
    public void awaitAndAsyncTest() throws Exception {
        checkSerialization("async def f():\n await smth()");
        checkSerialization("async def f():\n foo = await smth()");
        checkSerialization("async def f():\n foo, bar = await smth()");
        checkSerialization("async def f():\n (await smth())");
        checkSerialization("async def f():\n foo((await smth()))");
        checkSerialization("async def f():\n await foo(); return 42");
        checkSerialization("async def f():\n async with 1: pass");
        checkSerialization("async def f():\n async with a as b, c as d: pass");
        checkSerialization("async def f():\n async for i in (): pass");
        checkSerialization("async def f():\n async for i, b in (): pass");
    }

    @Test
    public void binaryOpTest() throws Exception {
        checkSerialization("1 + 10");
        checkSerialization("'ahoj' + 10");
        checkSerialization("3 ** 2");
        checkSerialization("3 ** 2 ** 2");
    }

    @Test
    public void callTest() throws Exception {
        checkSerialization("foo()");
        checkSerialization("foo(1)");
        checkSerialization("foo(arg = 1)");
        checkSerialization("foo(arg1 = 1, arg2 = 2)");
        checkSerialization("foo('ahoj', arg1 = 1, arg2 = 2)");
        checkSerialization("foo('ahoj', arg1 = 1, arg2 = 2)");
        checkSerialization("foo(*mylist)");
        checkSerialization("foo(*mylist1, *mylist2)");
        checkSerialization("foo(**mydict)");
        checkSerialization("foo(**mydict1, **mydict2)");
        checkSerialization("a.b.c.foo()");
        checkSerialization("def fn(): foo(arg = 1)");
        checkSerialization("def fn(arg = [1,2]): foo(arg = [1])");
        checkSerialization("def fn(): \"in\".format(name=\"Baf\")");
        checkSerialization("def fn(name): \"in\".format(name=name)");
    }

    @Test
    public void classDecoratorTest() throws Exception {
        checkSerialization("@class_decorator\n" +
                        "class foo():pass");
        checkSerialization("@decorator1\n" +
                        "@decorator1\n" +
                        "class foo():pass");
    }

    @Test
    public void classDefTest() throws Exception {
        checkSerialization("class foo():pass");
        checkSerialization("class foo(object):pass");
        checkSerialization("def fn():\n" + "  class DerivedClassName(modname.BaseClassName): pass");
        checkSerialization("class DerivedClassName(Base1, Base2, Base3): pass");
        checkSerialization(
                        "class OrderedDict(dict):\n" +
                                        "  def setup(dict_setitem = dict.__setitem__):\n" +
                                        "    dict_setitem()\n" +
                                        "    dict.clear()");
        checkSerialization(
                        "class Test():\n" +
                                        "    def fn1(format):\n" +
                                        "        return (format % args)\n" +
                                        "    def fn2(*args, **kwds):\n" +
                                        "        return self(*args, **kwds)\n");
        checkSerialization("class Enum(metaclass=EnumMeta): pass");
        checkSerialization(
                        "def test(arg):\n" +
                                        "  pass\n" +
                                        "class FalseRec:\n" +
                                        "  def test(self, arg):\n" +
                                        "    return test(arg+1)");
        checkSerialization(
                        "def make_named_tuple_class(name, fields):\n" +
                                        "    class named_tuple(tuple):\n" +
                                        "        __name__ = name\n" +
                                        "        n_sequence_fields = len(fields)\n" +
                                        "        fields = fields\n" +
                                        "        def __repr__(self):\n" +
                                        "            sb = name\n" +
                                        "            for f in fields:\n" +
                                        "                pass\n" +
                                        "    return named_tuple");
        checkSerialization(
                        "def fn():\n" +
                                        "    def get_nested_class():\n" +
                                        "        method_and_var = \"var\"\n" +
                                        "        class Test(object):\n" +
                                        "            def method_and_var(self):\n" +
                                        "                return \"method\"\n" +
                                        "            def test(self):\n" +
                                        "                return method_and_var\n" +
                                        "            def actual_global(self):\n" +
                                        "                return str(\"global\")\n" +
                                        "            def str(self):\n" +
                                        "                return str(self)\n" +
                                        "        return Test()\n" +
                                        "    t = get_nested_class()\n");
        checkSerialization("class Point:\n" +
                        "  x = 0\n" +
                        "  y = 0\n" +
                        "p = Point()\n" +
                        "a = p.x + p.y\n");
        checkSerialization(
                        "class Test():\n" +
                                        "    \"\"\"Class doc\"\"\"\n" +
                                        "    def method():\n" +
                                        "        \"\"\"Method doc\"\"\"");
        checkSerialization(
                        "class A:\n" +
                                        "    @classmethod\n" +
                                        "    def method(self, string:str):\n" +
                                        "        pass\n");
        checkSerialization(
                        "class A:\n" +
                                        "    class B:\n" +
                                        "        pass\n");
    }

    @Test
    public void classLocalMemberTest() throws Exception {
        checkSerialization(
                        "def fn():\n" +
                                        "    a_local_var = \"a local var\"\n" +
                                        "\n" +
                                        "    def f():\n" +
                                        "        class C(object):\n" +
                                        "            a_local_var = a_local_var\n" +
                                        "\n" +
                                        "        return C.a_local_var");
        checkSerialization(
                        "def fn():\n" +
                                        "    a_local_var_out = \"a local var\"\n" +
                                        "\n" +
                                        "    def f():\n" +
                                        "        class C(object):\n" +
                                        "            a_local_var = a_local_var_out\n" +
                                        "\n" +
                                        "        return C.a_local_var");
        checkSerialization(
                        "def fn():\n" +
                                        "    a_local_var = \"a local var\"\n" +
                                        "\n" +
                                        "    def f():\n" +
                                        "        class C(object):\n" +
                                        "            a_local_var = a_local_var\n" +
                                        "            def method01():\n" +
                                        "                return a_local_var\n" +
                                        "        return C.a_local_var");
    }

    @Test
    public void comparisionTest() throws Exception {
        checkSerialization("3 < 10");
        checkSerialization("1 < '10' > True");
        checkSerialization("1 < '10' > True != 1.0");
        checkSerialization("x < y() <= z");
        checkSerialization("x() < y() <= z()");
        checkSerialization("x() < y() < y() <= z()");
    }

    @Test
    public void compForTest() throws Exception {
        checkSerialization("(x*x for x in range(10))");
        checkSerialization("[x**y for x in range(20)]");
        checkSerialization("[x**y for x in range(20) if x*y % 3]");
        checkSerialization("foo(x+2 for x in range(10))");
        checkSerialization("{x**y for x in range(20)}");
        checkSerialization("{x:x*x for x in range(20)}");
        checkSerialization(
                        "dict1 = {'a': 1, 'b': 2, 'c': 3, 'd': 4, 'e': 5}\n" +
                                        "double_dict1 = {k:v*2 for (k,v) in dict1.items()}");
        checkSerialization(
                        "def fn():\n" +
                                        "  (x*x for x in range(10))");
        checkSerialization(
                        "def fn():\n" +
                                        "  c = 10\n" +
                                        "  (x + c for x in range(10))");
        checkSerialization(
                        "def fn(files, dirs):\n" +
                                        "  a = [join(dir, file) for dir in dirs for file in files]");
        checkSerialization("resutl = {i: tuple(j for j in t if i != j)\n" +
                        "                     for t in something for i in t}");
        checkSerialization(
                        "def mro(cls, abcs=None):\n" +
                                        "    for base in abcs:\n" +
                                        "        if not any(issubclass(b, base) for b in cls.__bases__):\n" +
                                        "            abstract_bases.append(base)\n" +
                                        "    other = [mro(base, abcs=abcs) for base in other]");
        checkSerialization(
                        "def fn(someset): return ', '.join(f'{name}' for name in someset)");
        checkSerialization("[b for b in [a for a in (1,2)]]");
        checkSerialization("[ b for a in d1 if d1 for b in d2]");
        checkSerialization("[i for i in range(3)]");
        checkSerialization("{i for i in range(3)}");
        checkSerialization("{i: i + 1 for i in range(3)}");
        checkSerialization("(i for i in range(3))");
        checkSerialization("[project_name for pkg in working_set\n" +
                        "    if name in [required.name for required in requires()]]\n");
    }

    @Test
    public void delTest() throws Exception {
        checkSerialization("del x");
        checkSerialization("del x, y, z");
        checkSerialization("del (x, y, z)");
    }

    @Test
    public void dictTest() throws Exception {
        checkSerialization("{}");
        checkSerialization("{a:b}");
        checkSerialization("{a:b,}");
        checkSerialization("{a:b, c:d}");
        checkSerialization("{a:b, c:d, }");
        checkSerialization("{**{}}");
        checkSerialization("{**{}, 3:4, **{5:6, 7:8}}");
        checkSerialization("{1:2, **{}, 3:4, **{5:6, 7:8}}");
        checkSerialization("{**{}, 3:4}");
        checkSerialization("{**{\"a\": \"hello\", \"b\": \"world\"}, **{3:4, 5:6}}");
        checkSerialization("{**{\"a\": \"hello\", \"b\": \"world\"}, 1:2,  **{3:4, 5:6}}");
    }

    @Test
    public void for01() throws Exception {
        checkSerialization("for i in 'ahoj':\n" + "  pass");
        checkSerialization("for i in range(210):\n" + "  print(i)");
        checkSerialization(
                        "for x in xrange(3):\n" +
                                        "  if x == 1:\n" +
                                        "    break\n");
        checkSerialization(
                        "def fn():\n" +
                                        "  for x in xrange(3):\n" +
                                        "    if x == 1:\n" +
                                        "      break\n");
        checkSerialization(
                        "for x in range(10):\n" +
                                        "    if x % 2 == 0:\n" +
                                        "        continue\n" +
                                        "    print(x)");
        checkSerialization(
                        "for x in range(10):\n" +
                                        "    if call01() == 0:\n" +
                                        "        continue\n" +
                                        "    print(x)");
        checkSerialization(
                        "for i in range(1, 10):\n" +
                                        "    if call01(i)==0:\n" +
                                        "        break\n" +
                                        "    print(i)\n" +
                                        "else:\n" +
                                        "    print(\"test\")");
        checkSerialization(
                        "for num in range(10,20):\n" +
                                        "   for i in range(2,num):\n" +
                                        "      if True:\n" +
                                        "         break\n" +
                                        "   else:\n" +
                                        "      pass");
        checkSerialization("for i, b in (): pass");
        checkSerialization(
                        "def fn():\n" +
                                        "  for a, b in ((1,2), (3,4)):\n" +
                                        "    print(a)");
        checkSerialization(
                        "def format(self):\n" +
                                        "    for frame in self:\n" +
                                        "        count += 1\n" +
                                        "        if count >= 3:\n" +
                                        "            continue\n" +
                                        "        if count == 4:\n" +
                                        "            for name, value in a:\n" +
                                        "                count = 1");
        checkSerialization(
                        "def merge(sequences):\n" +
                                        "    for s1 in sequences:\n" +
                                        "        for s2 in sequences:\n" +
                                        "            if candidate in s2[1:]:\n" +
                                        "                break\n" +
                                        "        else:\n" +
                                        "            break");
        checkSerialization(
                        "def formatyear():\n" +
                                        "        for (i, row) in something:\n" +
                                        "            pass\n" +
                                        "        return 10");
    }

    @Test
    public void functionDecoratorTest() throws Exception {
        checkSerialization("@some.path.to.decorator\n" + "def fn(): pass");
        checkSerialization(
                        "def outer():\n" +
                                        "  @decorator1\n" +
                                        "  def inner(): pass");
        checkSerialization(
                        "def outer():\n" +
                                        "  def decorator1(fn):\n" +
                                        "    pass\n" +
                                        "  @decorator1\n" +
                                        "  def inner(): pass");
    }

    @Test
    public void functionDefTest() throws Exception {
        checkSerialization("def fn(a, b=0, *arg, k1, k2=0): return a + b + k1 + k2 + sum(arg)");
        checkSerialization("def foo(): \n" + "  return 10\n");
        checkSerialization("def foo(a, b): \n" + "  return a + b\n");
        checkSerialization("def foo(par1 = 10): \n" + "  return par1");
        checkSerialization("def foo(par1, par2 = 22): \n" + "  return par1 * par2");
        checkSerialization("def foo(*args): \n" + "  pass");
        checkSerialization("def foo(*args): \n" + "  print(args)");
        checkSerialization(
                        "def bla():\n" +
                                        "  install(extra_opts=[1])\n" +
                                        "def install(extra_opts=[]):\n" +
                                        "  pass");
        checkSerialization(
                        "SOMETHING = NONE\n" +
                                        "def setup():\n" +
                                        "  global SOMETHING\n" +
                                        "  if True : SOMETHING = True\n" +
                                        "def install():\n" +
                                        "  if SOMETHING : pass");
        checkSerialization(
                        "def test():\n" +
                                        "  def inner (end):\n" +
                                        "    def inner_inner():\n" +
                                        "      print(\"inner_inner\", end=end)\n" +
                                        "    inner_inner()\n" +
                                        "  inner(\" baf\\n\")\n" +
                                        "test()");
        checkSerialization(
                        "def test():\n" +
                                        "  def inner (end):\n" +
                                        "    def inner_inner():\n" +
                                        "      print(\"inner_inner\", end=\" haha\\n\")\n" +
                                        "      print(end)\n" +
                                        "    inner_inner()\n" +
                                        "  inner(\" baf\\n\")\n" +
                                        "test()");
        checkSerialization(
                        "def __new__(_cls, hits, misses, maxsize, currsize):\n" +
                                        "  return _tuple_new(_cls, (hits, misses, maxsize, currsize))");
        checkSerialization("def __build_class__(func, name, *bases, metaclass=None, **kwargs): pass");
        checkSerialization("def __init__(self, max_size=0, *, ctx, pending_work_items): pass");
        checkSerialization("c = 2\n" +
                        "def foo(a, b): \n" +
                        "  return a + b + c\n" +
                        "foo(1,2)\n");
        checkSerialization("def test():\n" +
                        "  a = 1;\n" +
                        "  def fn1(): pass\n" +
                        "  def fn2(): pass\n" +
                        "  return locals()\n" +
                        "\n" +
                        "print(test())");
        checkSerialization("def substitute(self, mapping=_sentinel_dict, /, **kws): pass");
    }

    @Test
    public void funcDocTest() throws Exception {
        checkSerialization("def fn(): '1234' f'567'; pass");
        checkSerialization("def fn(): '1234' '567'; pass");
        checkSerialization("def fn(): f'1234'; pass");
        checkSerialization("def fn(): '123' f'456' '789'; pass");
    }

    @Test
    public void ifTest() throws Exception {
        checkSerialization("if False: \n" + "  10");
        checkSerialization("if False: \n" + "  10\n" + "else :\n" + "  a");
        checkSerialization("10 if False else 11");
        checkSerialization("predicate = func if func is not None else lambda a: a");
        checkSerialization("if not i: pass");
        checkSerialization("var = 100\n" + "if var == 200:\n" + "  print (2)\n" + "elif var == 150:\n" + "  print (1.5)\n" + "elif var == 100:\n" + "  print (1)");
    }

    @Test
    public void importTest() throws Exception {
        checkSerialization("import sys");
        checkSerialization("import sys as system");
        checkSerialization("import sys, math");
        checkSerialization("import sys as system, math");
        checkSerialization("import sys, math as my_math");
        checkSerialization("import encodings.aliases");
        checkSerialization("import encodings.aliases as a");
        checkSerialization("import encodings.aliases.something");
        checkSerialization("import encodings.aliases.something as a");
        checkSerialization("def fn():\n  import sys");
        checkSerialization("def fn():\n  import sys as system");
        checkSerialization("def fn():\n  import sys, math");
        checkSerialization("def fn():\n  import sys as system, math");
        checkSerialization("def fn():\n  import sys, math as my_math");
    }

    @Test
    public void importFromTest() throws Exception {
        checkSerialization("from sys.path import *");
        checkSerialization("from sys.path import dirname");
        checkSerialization("from sys.path import (dirname)");
        checkSerialization("from sys.path import (dirname,)");
        checkSerialization("from sys.path import dirname as my_dirname");
        checkSerialization("from sys.path import (dirname as my_dirname)");
        checkSerialization("from sys.path import (dirname as my_dirname,)");
        checkSerialization("from sys.path import dirname, basename");
        checkSerialization("from sys.path import (dirname, basename)");
        checkSerialization("from sys.path import (dirname, basename,)");
        checkSerialization("from sys.path import dirname as my_dirname, basename");
        checkSerialization("from sys.path import (dirname as my_dirname, basename)");
        checkSerialization("from sys.path import (dirname as my_dirname, basename,)");
        checkSerialization("from sys.path import dirname, basename as my_basename");
        checkSerialization("from sys.path import (dirname, basename as my_basename)");
        checkSerialization("from sys.path import (dirname, basename as my_basename,)");
        checkSerialization("from .bogus import x");
        checkSerialization("def fn():\n  from sys.path import dirname");
        checkSerialization("def fn():\n  from sys.path import dirname as my_dirname");
        checkSerialization("def fn():\n  from sys.path import (dirname as my_dirname, basename)");
    }

    @Test
    public void importRelativeTest() throws Exception {
        checkSerialization("from . import name");
        checkSerialization("from .. import name");
        checkSerialization("from ... import name");
        checkSerialization("from .... import name");
        checkSerialization("from .pkg import name");
        checkSerialization("from ..pkg import name");
        checkSerialization("from ...pkg import name");
        checkSerialization("from ....pkg import name");
    }

    @Test
    public void generatorTest() throws Exception {
        checkSerialization(
                        "def fn():\n" +
                                        "  yield 1\n" +
                                        "  yield 2");
        checkSerialization(
                        "def fn(self):\n" +
                                        "    caretspace = (c.isspace() for c in caretspace)\n" +
                                        "    yield caretspace");
        checkSerialization(
                        "def format_exception_only(self):\n" +
                                        "        if a():\n" +
                                        "            yield \"neco\"\n" +
                                        "            return\n" +
                                        "        if badline is not None:\n" +
                                        "            yield '\\n'\n" +
                                        "            if offset is not None:\n" +
                                        "                caretspace = (c.isspace() for c in caretspace)\n" +
                                        "                yield caretspace\n" +
                                        "        yield \"message\"");
        checkSerialization(
                        "def merge(h):\n" +
                                        "    while len(h) > 1:\n" +
                                        "        while True:\n" +
                                        "            value, order, next = s = h[0]\n" +
                                        "            yield value");
        checkSerialization(
                        "def merge(h):\n" +
                                        "    for order, it in b():\n" +
                                        "        value = next()\n" +
                                        "    while len(h) > 1:\n" +
                                        "        yield value");
        checkSerialization(
                        "def merge(h):\n" +
                                        "    while True:\n" +
                                        "        value = next()\n" +
                                        "    while h > 1:\n" +
                                        "        yield value");
        checkSerialization(
                        "def non_empty_lines(path):\n" +
                                        "    with open(path) as f:\n" +
                                        "        for line in f:\n" +
                                        "            line = line.strip()\n" +
                                        "            if line:\n" +
                                        "                yield line");
        checkSerialization(
                        "def b_func():\n" +
                                        "  exec_gen = False\n" +
                                        "  def _inner_func():\n" +
                                        "    def doit():\n" +
                                        "      nonlocal exec_gen\n" +
                                        "      exec_gen = True\n" +
                                        "      return [1]\n" +
                                        "    for A in doit():\n" +
                                        "      for C in Y:\n" +
                                        "        yield A\n" +
                                        "  gen = _inner_func()\n" +
                                        "  Y = [1, 2]\n" +
                                        "  list(gen)\n" +
                                        "  return gen");
        checkSerialization(
                        "def fn(a):\n" +
                                        "  yield a\n" +
                                        "  yield 2");
        checkSerialization(
                        "def fn(a, b):\n" +
                                        "  yield a\n" +
                                        "  yield b");
        checkSerialization(
                        "def fn(a, b=1):\n" +
                                        "  yield a\n" +
                                        "  yield b");
        checkSerialization(
                        "def fn(*arg):\n" +
                                        "  for p in arg:\n" +
                                        "    yield p");
        checkSerialization(
                        "def fn(**arg):\n" +
                                        "  for p in arg:\n" +
                                        "    yield p");
        checkSerialization(
                        "def fn():\n" +
                                        "  \"This is a doc\"\n" +
                                        "  yield \"neco\"");
        checkSerialization(
                        "def walk_stack(f):\n" +
                                        "    \"\"\"Documentation\"\"\"\n" +
                                        "    if f is None:\n" +
                                        "        f = a()\n" +
                                        "    while f is not None:\n" +
                                        "        yield f\n");
        checkSerialization(
                        "class OrderedDict(dict):\n" +
                                        "    def __reversed__(self):\n" +
                                        "        root = self.__root\n" +
                                        "        curr = root.prev\n" +
                                        "        while curr is not root:\n" +
                                        "            yield curr.key\n" +
                                        "            curr = curr.prev");
        checkSerialization(
                        "class Counter(dict):\n" +
                                        "    def _keep_positive(self):\n" +
                                        "        nonpositive = [elem for elem, count in self.items() if not count > 0]\n" +
                                        "        for elem in nonpositive:\n" +
                                        "            del self[elem]\n" +
                                        "        return self");
        checkSerialization(
                        "def gen(x): \n" +
                                        "  while x: \n" +
                                        "    if x == 10: \n" +
                                        "      break \n" +
                                        "    x = x - 1 \n" +
                                        "    yield x \n" +
                                        "  else: \n" +
                                        "    yield 100");
    }

    @Test
    public void getAttrTest() throws Exception {
        checkSerialization("a.b");
        checkSerialization("a().b");
        checkSerialization("a().b.c(x).d.f()");
    }

    @Test
    public void globalTest() throws Exception {
        checkSerialization("global x");
    }

    @Test
    public void lambdaTest() throws Exception {
        checkSerialization("def fn():\n" + "  lambda: 0");
        checkSerialization("def fn():\n" + "  lambda x: 0");
        checkSerialization("def fn():\n" + "  lambda *y: 0");
        checkSerialization("def fn():\n" + "  lambda *y, **z: 0");
        checkSerialization("def fn():\n" + "  lambda **z: 0");
        checkSerialization("def fn():\n" + "  lambda x, y: 0");
        checkSerialization("def fn():\n" + "  lambda foo=bar: 0");
        checkSerialization("def fn():\n" + "  lambda foo=bar, spaz=nifty+spit: 0");
        checkSerialization("def fn():\n" + "  lambda foo=bar, **z: 0");
        checkSerialization("def fn():\n" + "  lambda foo=bar, blaz=blat+2, **z: 0");
        checkSerialization("def fn():\n" + "  lambda foo=bar, blaz=blat+2, *y, **z: 0");
        checkSerialization("def fn():\n" + "  lambda x, *y, **z: 0");
    }

    @Test
    public void listTest() throws Exception {
        checkSerialization("[1,2,3,4]");
        checkSerialization("list = [1,2,3,4]");
        checkSerialization("[]");
        checkSerialization("l = []");
        checkSerialization("[*{2}, 3, *[4]]");
    }

    @Test
    public void logicOpTest() throws Exception {
        checkSerialization("a or b");
        checkSerialization("a and b");
        checkSerialization("not a");
    }

    @Test
    public void moduleDocTest() throws Exception {
        checkSerialization(
                        "# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.\n" + "# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n" +
                                        "\"\"\"MODULE A DOC\"\"\"\n" + "print(\"module A\")");
    }

    @Test
    public void nonlocalTest() throws Exception {
        checkSerialization("nonlocal x");
    }

    @Test
    public void numberBinTest() throws Exception {
        checkSerialization("0b101");
        checkSerialization("-0b101");
        checkSerialization("0b1111111111111111111111111111111111111111111111111111111111111111");
    }

    @Test
    public void numberComplexTest() throws Exception {
        checkSerialization("0+1j");
    }

    @Test
    public void numberIntTest() throws Exception {
        checkSerialization("1");
        checkSerialization("-1");
        checkSerialization("-0");
        checkSerialization("h == -1");
        checkSerialization("- 1");
        checkSerialization("-     1");
        checkSerialization("--2");
        checkSerialization("---2");
        checkSerialization("----2");
        checkSerialization("2147483647");
        checkSerialization("-2147483648");
        checkSerialization("-9223372036854775808");
        checkSerialization("-2147483649");
        checkSerialization("2147483648");
        checkSerialization("9223372036854775807");
        checkSerialization("9223372036854775808");
        checkSerialization("-9223372036854775809");
        checkSerialization("0x1");
        checkSerialization("0X1");
        checkSerialization("-0x1");
        checkSerialization("-0X1");
        checkSerialization("(0X1)");
        checkSerialization("-(0X1)");
    }

    @Test
    public void numberUnderscoreTest() throws Exception {
        checkSerialization("1_0");
        checkSerialization("1_0_6");
        checkSerialization("0b1_1");
        checkSerialization("0o1_7");
        checkSerialization("0x1_f");
        checkSerialization("0_0_0");
        checkSerialization("1_00_00.5");
        checkSerialization("1_00_00.5e5");
        checkSerialization("1_00_00e5_1");
        checkSerialization("1e1_0");
        checkSerialization(".1_4");
        checkSerialization(".1_4e1");
        checkSerialization("1_00_00j");
        checkSerialization("1_00_00.5j");
        checkSerialization("1_00_00e5_1j");
        checkSerialization(".1_4j");
        checkSerialization("(1_2.5+3_3j)");
        checkSerialization("(.5_6j)");
    }

    @Test
    public void positionalOnlyArgTest() throws Exception {
        checkSerialization("def name(p1, p2, /, p_or_kw, *, kw): pass");
        checkSerialization("def name(p1, p2=None, /, p_or_kw=None, *, kw): pass");
        checkSerialization("def name(p1, p2=None, /, *, kw): pass");
        checkSerialization("def name(p1, p2=None, /): pass");
        checkSerialization("def name(p1, p2, /, p_or_kw): pass");
        checkSerialization("def name(p1, p2, /): pass");
    }

    @Test
    public void raiseTest() throws Exception {
        checkSerialization("raise");
        checkSerialization("raise NameError");
        checkSerialization("raise NameError('Pavel')");
        checkSerialization("raise NameError('Pavel') from exc");
    }

    @Test
    public void setTest() throws Exception {
        checkSerialization("{2}");
        checkSerialization("{2,}");
        checkSerialization("{2, 3}");
        checkSerialization("{2, 3,}");
        checkSerialization("{*{2}, 3, *[4]}");
    }

    @Test
    public void simpleExpressionTest() throws Exception {
        checkSerialization("'ahoj'; 2");
        checkSerialization("'ahoj'; 2; 1.0");
        checkSerialization("None");
        checkSerialization("...");
        checkSerialization("a[1]");
        checkSerialization("");
        checkSerialization("\"\\uD800\"");
    }

    @Test
    public void slicingTest() throws Exception {
        checkSerialization("a[::]");
        checkSerialization("a[1::]");
        checkSerialization("a[:1:]");
        checkSerialization("a[::1]");
        checkSerialization("a()[b():c():d()]");
    }

    @Test
    public void starExprTest() throws Exception {
        checkSerialization("[*[1,2,3]]");
        checkSerialization("*a, = range(5)");
        checkSerialization("a, *b, c = range(5)");
        checkSerialization("first, *rest = seq");
        checkSerialization("[a, *b, c] = seq");
        checkSerialization("for a, *b in [(1, 2, 3), (4, 5, 6, 7)]:\n" +
                        "    print(b)");
    }

    @Test
    public void stringLiteralTest() throws Exception {
        checkSerialization("'ahoj'");
        checkSerialization("'''ahoj'''");
        checkSerialization("'''\n" + "ahoj\n" + "hello\n" + "good bye\n" + "'''");
        checkSerialization("__new__.__doc__ = f'Create new instance of {typename}({arg_list})'");
        checkSerialization("a = 'First 𝔘𝔫𝔦𝔠𝔬𝔡𝔢 string'\n" +
                        "b = 'Second 𝔘𝔫𝔦𝔠𝔬𝔡𝔢 string'\n" +
                        "c = 'Third 𝔘𝔫𝔦𝔠𝔬𝔡𝔢 string'\n");
    }

    @Test
    public void topLevelParserTest() throws Exception {
        checkSerialization(
                        "name = 'Pepa'\n" +
                                        "print(f\"hello {name}\")");
        checkSerialization(
                        "''.join(f'{name}' for name in ['Pepa', 'Pavel'])");
    }

    @Test
    public void tryTest() throws Exception {
        checkSerialization(
                        "try:\n" +
                                        "  pass\n" +
                                        "except ValueError:\n" +
                                        "  pass");
        checkSerialization(
                        "try:\n" +
                                        "  pass\n" +
                                        "except ValueError as va:\n" +
                                        "  pass");
        checkSerialization(
                        "def fn():\n" +
                                        "  try:\n" +
                                        "    pass\n" +
                                        "  except ValueError as va:\n" +
                                        "    pass");
        checkSerialization(
                        "try:\n" +
                                        "  pass\n" +
                                        "except (RuntimeError, TypeError, NameError):\n" +
                                        "  pass");
        checkSerialization(
                        "try:\n" +
                                        "  pass\n" +
                                        "except:\n" +
                                        "  pass");
        checkSerialization(
                        "for cls in (B, C, D):\n" +
                                        "    try:\n" +
                                        "        pass\n" +
                                        "    except D:\n" +
                                        "        pass\n" +
                                        "    except C:\n" +
                                        "        pass\n" +
                                        "    except B:\n" +
                                        "        pass");
        checkSerialization(
                        "try:\n" +
                                        "    pass\n" +
                                        "except OSError as err:\n" +
                                        "    pass\n" +
                                        "except ValueError:\n" +
                                        "    pass\n" +
                                        "except:\n" +
                                        "    raise");
        checkSerialization(
                        "try:\n" +
                                        "    pass\n" +
                                        "except OSError:\n" +
                                        "    pass\n" +
                                        "else:\n" +
                                        "    pass");
        checkSerialization(
                        "try:\n" +
                                        "   raise KeyboardInterrupt\n" +
                                        "finally:\n" +
                                        "   print('Goodbye, world!')");
        checkSerialization(
                        "def divide(x, y):\n" +
                                        "    try:\n" +
                                        "        result = x / y\n" +
                                        "    except ZeroDivisionError:\n" +
                                        "        print(\"division by zero!\")\n" +
                                        "    else:\n" +
                                        "        print(\"result is\", result)\n" +
                                        "    finally:\n" +
                                        "        print(\"executing finally clause\")");
        checkSerialization(
                        "def fn():\n" +
                                        "    try:\n" +
                                        "        pass\n" +
                                        "    except Exception as err:\n" +
                                        "        print(err)");
    }

    @Test
    public void tupleTest() throws Exception {
        checkSerialization("(1, 2, 3)");
        checkSerialization("(1, call01((1,2,)), 'ahoj')");
        checkSerialization("t = ()");
        checkSerialization("t = (2)");
        checkSerialization("t = (2,)");
        checkSerialization("t = ('strange,')");
        checkSerialization("1,2,3");
        checkSerialization("1,");
        checkSerialization("1, call1()");
        checkSerialization("t = 1, call1()");
        checkSerialization("a += 1,2,3");
        checkSerialization("a[1,3,4]");
        checkSerialization("b = (\n" +
                        "  (0x69, 0x131), # iı\n" +
                        ")");
    }

    @Test
    public void twoStringsTest() throws Exception {
        printFormatStringLiteralValues = true;
        checkSerialization("'123'  '456'");
        checkSerialization("f'123'  '456'");
        checkSerialization("'123'  f'456'");
        checkSerialization("f'123'  f'456'");
        checkSerialization("'123' '456' f'789' '0'");
        checkSerialization("'1' '2' '3' f'4' f'5' '6' '7' f'8' '9' '0'");
        printFormatStringLiteralValues = false;
    }

    @Test
    public void unaryTest() throws Exception {
        checkSerialization("+u");
        checkSerialization("-u");
        checkSerialization("~u");
    }

    @Test
    public void whileTest() throws Exception {
        checkSerialization("while True:\n" + "  pass");
        checkSerialization("while True:\n" + "  if False:\n" + "    continue\n" + "  pass");
        checkSerialization("while True:\n" + "  if False:\n" + "    break\n" + "  pass");
        checkSerialization("while True:\n" + "  if False:\n" + "    break\n" + "  if True:\n" + "    break\n" + "  pass");
        checkSerialization("while True:\n" + "  if False:\n" + "    break\n" + "  if True:\n" + "    continue\n" + "  pass");
        checkSerialization("while True:\n" +
                        "  if False:\n" +
                        "    break\n" +
                        "  pass\n" +
                        "else:\n" +
                        "  pass");
        checkSerialization("while True:\n" + "  if False:\n" + "    break\n" + "  if True:\n" + "    break\n" + "  pass\n" + "else:\n" + "  pass");
        checkSerialization("while True:\n" + "  if False:\n" + "    break\n" + "  if True:\n" + "    continue\n" + "  pass\n" + "else:\n" + "  print('done')");
        checkSerialization("while True:\n" + "  pass\n" + "else:\n" + "  pass");
        checkSerialization("while tb is not None: pass");
        checkSerialization(
                        "iters = 0\n" +
                                        "while iters < 40:\n" +
                                        "    while iters < 10:\n" +
                                        "        if False:\n" +
                                        "            break\n" +
                                        "        iters += 1\n" +
                                        "    else:\n" +
                                        "        iters += 1\n" +
                                        "        break");
    }

    @Test
    public void withTest() throws Exception {
        checkSerialization(
                        "with A() as a:\n" +
                                        "  pass");
        checkSerialization(
                        "with A() as a, B() as b:\n" +
                                        "  pass");
        checkSerialization(
                        "def fn():\n" +
                                        "  with A() as a:\n" +
                                        "    pass");
        checkSerialization(
                        "def fn():\n" +
                                        "  with A() as a, B() as b:\n" +
                                        "    pass");
        checkSerialization(
                        "def fn():\n" +
                                        "  with open('x'):\n" +
                                        "    pass");
        checkSerialization(
                        "def fn():\n" +
                                        "  with A() as a:\n" +
                                        "    with B() as b:\n" +
                                        "      pass");
    }

    @Test
    public void yeildTest() throws Exception {
        checkSerialization("def f(): yield 1");
        checkSerialization("def f(): yield");
        checkSerialization("def f(): x += yield");
        checkSerialization("def f(): x = yield 1");
        checkSerialization("def f(): x = y = yield 1");
        checkSerialization("def f(): x = yield");
        checkSerialization("def f(): x = y = yield");
        checkSerialization("def f(): 1 + (yield)*2");
        checkSerialization("def f(): (yield 1)*2");
        checkSerialization("def f(): return; yield 1");
        checkSerialization("def f(): yield 1; return");
        checkSerialization("def f(): yield from 1");
        checkSerialization("def f(): f((yield from 1))");
        checkSerialization("def f(): yield 1; return 1");
        checkSerialization(
                        "def f():\n" +
                                        "    for x in range(30):\n" +
                                        "        yield x\n");
        checkSerialization(
                        "def f():\n" +
                                        "    if (yield):\n" +
                                        "        yield x\n");
        checkSerialization(
                        "def fn():\n" +
                                        "    class MyIter:\n" +
                                        "        def __iter__(self):\n" +
                                        "            return self\n" +
                                        "        def __next__(self):\n" +
                                        "            raise StopIteration(42)\n" +
                                        "    def gen():\n" +
                                        "        nonlocal ret\n" +
                                        "        ret = yield from MyIter()\n" +
                                        "    ret = None\n" +
                                        "    list(gen())");
        checkSerialization("generator = type((lambda: (yield))())");
        checkSerialization(
                        "def gen():\n" +
                                        "  with fn():\n" +
                                        "    yield 12\n" +
                                        "    yield 13");
        checkSerialization(
                        "def gen(a):\n" +
                                        "  with a:\n" +
                                        "    bla(p1, p2, p3)\n" +
                                        "  with fn():\n" +
                                        "    yield 12\n" +
                                        "    yield 13");
        checkSerialization(
                        "def gen(a):\n" +
                                        "  with a:\n" +
                                        "    yield 12\n" +
                                        "    yield 13");
        checkSerialization(
                        "def gen():\n" +
                                        "  with A() as a, B() as b:\n" +
                                        "    yield a");
        checkSerialization(
                        "def gen():\n" +
                                        "  if b:\n" +
                                        "    yield 12\n" +
                                        "  else:\n" +
                                        "    yield 13");
        checkSerialization(
                        "def gen(c, b):\n" +
                                        "  if c:\n" +
                                        "    b=1\n" +
                                        "  if b:\n" +
                                        "    yield 12\n" +
                                        "  else:\n" +
                                        "    yield 13");
        checkSerialization(
                        "def gen(c, b):\n" +
                                        "  if c:\n" +
                                        "    b=1\n" +
                                        "  else:\n" +
                                        "    yield 9\n" +
                                        "  if b:\n" +
                                        "    yield 12\n" +
                                        "  else:\n" +
                                        "    yield 13");
        checkSerialization(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     x = x-1\n" +
                                        "     yield x");
        checkSerialization(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     pass\n" +
                                        "   while x:\n" +
                                        "     x = x-1\n" +
                                        "     yield x");
        checkSerialization(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     if x == 1:\n" +
                                        "       continue\n" +
                                        "     x = x-1\n" +
                                        "     yield x");
        checkSerialization(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     if x == 1:\n" +
                                        "       yield -1\n" +
                                        "     x = x-1\n" +
                                        "     yield x");
        checkSerialization(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     if x == 1:\n" +
                                        "       break\n" +
                                        "     x = x-1\n" +
                                        "     yield x");
        checkSerialization(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     if x == 1:\n" +
                                        "       break\n" +
                                        "     if x == 2:\n" +
                                        "       continue\n" +
                                        "     x = x-1\n" +
                                        "     yield x");
        checkSerialization(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     if x == 1:\n" +
                                        "       break\n" +
                                        "     x = x-1\n" +
                                        "     yield x\n" +
                                        "   else:\n" +
                                        "     yield 10");
        checkSerialization(
                        "def gen(x):\n" +
                                        "  try:\n" +
                                        "    pass\n" +
                                        "  except ValueError:\n" +
                                        "    yield 3");
        checkSerialization(
                        "def gen(x):\n" +
                                        "  try:\n" +
                                        "    yield 3\n" +
                                        "  except ValueError:\n" +
                                        "    pass");
        checkSerialization(
                        "def gen():\n" +
                                        "  try:\n" +
                                        "    pass\n" +
                                        "  except ValueError:\n" +
                                        "    pass\n" +
                                        "  try:\n" +
                                        "    yield 3\n" +
                                        "  except ValueError:\n" +
                                        "    pass");
    }

    public void checkFileSerialization(String path) throws Exception {
        File testDir = getTestFilesDir();
        File file = new File(testDir.getAbsolutePath() + "/" + path);
        TruffleFile src = context.getEnv().getInternalTruffleFile(file.getAbsolutePath());
        Source source = PythonLanguage.newSource(context, src, getFileName(file));
        checkSerialization(source);
    }

    public void checkSerialization(String code) throws Exception {
        Source source = Source.newBuilder(PythonLanguage.ID, code, "serializationTest").build();
        checkSerialization(source);
    }

    public void checkSerialization(Source source) throws Exception {
        PythonCodeSerializer serializer = context.getCore().getSerializer();

        // at first parse the source and obtain the parse result
        RootNode parserResult = (RootNode) parse(source, PythonParser.ParserMode.File);
        ScopeInfo parserScope = getLastGlobalScope();
        checkScopeSerialization(parserScope);
        Assert.assertNotNull("Parser result is null", parserResult);
        // serialize the source
        byte[] serializeResult = serializer.serialize(parserResult);
        Assert.assertNotNull("Serialized data are null", serializeResult);
        // and get the tree from serialized data
        RootNode deserialize = serializer.deserialize(source, serializeResult);

        Assert.assertNotNull("Deserialized result is null", parserResult);
        // compare the tree from parser with the tree from serializer
        String parserTree = printTreeToString(parserResult);
        String deserializedTree = printTreeToString(deserialize);
        assertDescriptionMatches(parserTree, deserializedTree, null);
    }

    public void checkScopeSerialization(ScopeInfo scope) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        ScopeInfo.write(dos, scope);
        dos.close();

        byte[] result = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(result);
        DataInputStream dis = new DataInputStream(bais);
        ScopeInfo deserializeScope = ScopeInfo.read(dis, null);

        StringBuilder original = new StringBuilder();
        printScope(scope, original, 0);
        StringBuilder deserialized = new StringBuilder();
        printScope(deserializeScope, deserialized, 0);
        assertDescriptionMatches(deserialized.toString(), original.toString(), null);
    }

    public void checkFileFromLib(String path) throws Exception {
        String libDirPath = System.getProperty("org.graalvm.language.python.home");
        libDirPath += "/lib-python/3/";
        File libDir = new File(libDirPath);
        File file = new File(libDir.getAbsolutePath() + "/" + path);
        TruffleFile src = context.getEnv().getInternalTruffleFile(file.getAbsolutePath());
        Source source = PythonLanguage.newSource(context, src, getFileName(file));
        checkSerialization(source);
    }

    private void checkFileSerialization(File file, FileFilter fileFilter, boolean printTestedFile) throws Exception {
        if (file.isFile()) {
            if (printTestedFile) {
                System.out.println("checking: " + file.getAbsolutePath());
            }
            TruffleFile src = context.getEnv().getInternalTruffleFile(file.getAbsolutePath());
            Source source = PythonLanguage.newSource(context, src, getFileName(file));
            checkSerialization(source);
        } else if (file.isDirectory() && fileFilter != null) {
            File[] children = file.listFiles(fileFilter);
            for (File child : children) {
                checkFileSerialization(child, fileFilter, printTestedFile);
            }
        }
    }

    private static void indent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }
    }

    private static void printSet(StringBuilder sb, Set<String> set) {
        if (set == null || set.isEmpty()) {
            sb.append("Empty");
        } else {
            sb.append("[");
            boolean first = true;
            for (String name : set) {
                if (first) {
                    sb.append(name);
                    first = false;
                } else {
                    sb.append(", ").append(name);
                }
            }
            sb.append("]");
        }
    }

    private static void printFrameSlots(StringBuilder sb, FrameSlot[] slots) {
        if (slots.length == 0) {
            sb.append("Empty");
        } else {
            sb.append("[");
            boolean first = true;
            for (FrameSlot slot : slots) {
                if (first) {
                    sb.append(slot.getIdentifier());
                    first = false;
                } else {
                    sb.append(", ").append(slot.getIdentifier());
                }
            }
        }
    }

    // here we can not use the ScopeInfo.debugPrint(), because we need exclude the temporary
    // variables.
    private static void printScope(ScopeInfo scope, StringBuilder sb, int indent) {
        indent(sb, indent);
        sb.append("Scope: ").append(scope.getScopeId()).append("\n");
        indent(sb, indent + 1);
        sb.append("Kind: ").append(scope.getScopeKind()).append("\n");
        Set<String> names = new HashSet<>();
        scope.getFrameDescriptor().getIdentifiers().forEach((id) -> {
            String name = (String) id;
            if (!name.startsWith(FrameSlotIDs.TEMP_LOCAL_PREFIX) && !name.startsWith(FrameSlotIDs.RETURN_SLOT_ID)) {
                names.add((String) id);
            }
        });
        indent(sb, indent + 1);
        sb.append("FrameDescriptor: ");
        printSet(sb, names);
        sb.append("\n");
        indent(sb, indent + 1);
        sb.append("CellVars: ");
        printFrameSlots(sb, scope.getCellVarSlots());
        sb.append("\n");
        indent(sb, indent + 1);
        sb.append("FreeVars: ");
        printFrameSlots(sb, scope.getFreeVarSlots());
        sb.append("\n");
        ScopeInfo child = scope.getFirstChildScope();
        while (child != null) {
            printScope(child, sb, indent + 1);
            child = child.getNextChildScope();
        }
    }

    // Test
    public void compareDeSerializationAndParsing() throws Exception {
        /*
         * This is for checking performace of the serialization and deserialization. On machine:
         * Processor Intel(R) Core(TM) i7-6820HQ CPU @ 2.70GHz Memory 32801MB Operating System
         * Ubuntu 18.04.2 LTS the result should be : Memory times: parsing: 6,674,919,690ns (100%)
         * serialization: 435,290,014ns(+6%) deserialization: 2,220,701,051ns (-67%)
         * 
         * Average times: parsing: 4,171,824ns (100%) serialization: 272,056ns (+6%)
         * deserialization: 1,387,938ns(-67%)
         * 
         * Times with file operations: parsing: 6,879,809,367ns (100%) serialization:
         * 583,667,690ns(+8%) deserialization: 2,270,131,909ns (-68%)
         * 
         * Average times: parsing: 4,299,880ns (100%) serialization: 364,792ns (+8%)
         * deserialization: 1,418,832ns(-68%)
         */

        String libDirPath = System.getProperty("org.graalvm.language.python.home");
        libDirPath += "/lib-python/3/";
        File libDir = new File(libDirPath);
        List<String> wrongFiles = Arrays.asList(
                        "test_parser.py",
                        "test_re.py",
                        "test_unicode_identifiers.py",
                        "badsyntax_3131.py",
                        "bad_coding2.py");
        FileFilter fileFilter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (file.isDirectory() && !"data".equals(file.getName()) /*
                                                                          * && !"test".equals(file.
                                                                          * getName())
                                                                          */) {
                    return true;
                } else {
                    if (wrongFiles.contains(file.getName())) {
                        return false;
                    }
                    return file.getName().endsWith(".py");
                }
            }
        };

        long[] data = new long[7];
        compareSerializationAndParsingTime(libDir, fileFilter, false, data);
        long avgMemoryParsing = data[0] / data[6];
        long avgMemorySer = data[1] / data[6];
        long avgMemorydeSer = data[2] / data[6];
        long avgFileParsing = data[3] / data[6];
        long avgFileSer = data[4] / data[6];
        long avgFiledeSer = data[5] / data[6];

        StringBuilder sb = new StringBuilder();
        sb.append("Tested files: ").append(String.format("%,d", data[6]));
        sb.append("\nMemory times:");
        sb.append("\n    parsing: ").append(String.format("%,d", data[0]));
        sb.append("ns (100%)\n    serialization: ").append(String.format("%,d", data[1]));
        sb.append("ns(+").append(data[1] * 100 / data[0]);
        sb.append("%)\n    deserialization: ").append(String.format("%,d", data[2]));
        sb.append("ns (").append(data[2] * 100 / data[0] - 100);
        sb.append("%)\n\n");
        sb.append("  Average times:");
        sb.append("\n    parsing: ").append(String.format("%,d", avgMemoryParsing));
        sb.append("ns (100%)\n    serialization: ").append(String.format("%,d", avgMemorySer));
        sb.append("ns (+").append(avgMemorySer * 100 / avgMemoryParsing);
        sb.append("%)\n    deserialization: ").append(String.format("%,d", avgMemorydeSer));
        sb.append("ns(").append(avgMemorydeSer * 100 / avgMemoryParsing - 100);
        sb.append("%)\n");
        sb.append("\nTimes with file operations:");
        sb.append("\n    parsing: ").append(String.format("%,d", data[3]));
        sb.append("ns (100%)\n    serialization: ").append(String.format("%,d", data[4]));
        sb.append("ns(+").append(data[4] * 100 / data[3]);
        sb.append("%)\n    deserialization: ").append(String.format("%,d", data[5]));
        sb.append("ns (").append(data[5] * 100 / data[3] - 100);
        sb.append("%)\n\n");
        sb.append("  Average times:");
        sb.append("\n    parsing: ").append(String.format("%,d", avgFileParsing));
        sb.append("ns (100%)\n    serialization: ").append(String.format("%,d", avgFileSer));
        sb.append("ns (+").append(avgFileSer * 100 / avgFileParsing);
        sb.append("%)\n    deserialization: ").append(String.format("%,d", avgFiledeSer));
        sb.append("ns(").append(avgFiledeSer * 100 / avgFileParsing - 100);
        sb.append("%)\n\n");
        System.out.println(sb.toString());
    }

    private void compareSerializationAndParsingTime(File file, FileFilter fileFilter, boolean printTestedFile, long[] data) throws Exception {
        long[] times;

        if (file.isFile()) {
            times = getTimes(file);
            data[0] += times[0];
            data[1] += times[1];
            data[2] += times[2];
            data[3] += times[3];
            data[4] += times[4];
            data[5] += times[5];
            data[6]++;
            if (printTestedFile) {
                StringBuilder sb = new StringBuilder();
                sb.append("Tested: ").append(file.getAbsolutePath());
                sb.append(" Memory times: parsing: ").append(String.format("%,d", times[0]));
                sb.append("ns (100%) serialization: ").append(String.format("%,d", times[1]));
                sb.append("ns (+").append(times[1] * 100 / times[0]);
                sb.append("%) deserialization: ").append(String.format("%,d", times[2]));
                sb.append("ns (").append(times[2] * 100 / times[0] - 100);
                sb.append("%) Times with file op.: parsing: ").append(String.format("%,d", times[3]));
                sb.append("ns (100%) serialization: ").append(String.format("%,d", times[4]));
                sb.append("ns (+").append(times[4] * 100 / times[3]);
                sb.append("%) deserialization: ").append(String.format("%,d", times[5]));
                sb.append("ns (").append(times[5] * 100 / times[3] - 100);
                sb.append("%)");
                System.out.println(sb.toString());
            }
        } else if (file.isDirectory() && fileFilter != null) {
            File[] children = file.listFiles(fileFilter);
            for (File child : children) {
                compareSerializationAndParsingTime(child, fileFilter, printTestedFile, data);
            }
        }
    }

    public long[] getTimes(File file) throws Exception {
        long[] result = new long[6];
        long startFile = System.nanoTime();
        TruffleFile src = context.getEnv().getInternalTruffleFile(file.getAbsolutePath());
        Source source = PythonLanguage.newSource(context, src, getFileName(file));
        // at first parse the source and obtain the parse result
        long startMemory = System.nanoTime();
        RootNode parserResult = (RootNode) parse(source, PythonParser.ParserMode.File);
        long end = System.nanoTime();
        result[0] = end - startMemory;
        result[3] = end - startFile;

        PythonCodeSerializer serializer = context.getCore().getSerializer();
        startFile = System.nanoTime();
        byte[] serializeResult = serializer.serialize(parserResult);
        end = System.nanoTime();
        result[1] = end - startFile;
        TruffleFile serFile = context.getEnv().getInternalTruffleFile(file.getAbsolutePath() + ".pyc");
        OutputStream out = serFile.newOutputStream();
        out.write(serializeResult);
        out.flush();
        out.close();
        end = System.nanoTime();
        result[4] = end - startFile;

        startFile = System.nanoTime();
        TruffleFile tFile = context.getEnv().getInternalTruffleFile(file.getAbsolutePath() + ".pyc");
        byte[] desbytes = tFile.readAllBytes();
        startMemory = System.nanoTime();
        serializer.deserialize(source, desbytes);
        end = System.nanoTime();
        result[2] = end - startMemory;
        result[5] = end - startFile;
        tFile.delete();
        return result;
    }
}
