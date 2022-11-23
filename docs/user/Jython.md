---
layout: docs-experimental
toc_group: python
link_title: Jython Compatibility
permalink: /reference-manual/python/Jython/
---
# Jython Migration Guide

Most Jython code that uses Java integration will be based on a
stable Jython release, and these only come in Python 2.x versions.
GraalVM's Python runtime, in contrast, is only targeting Python 3.x.
GraalVM does not provide a full compatibility with these earlier 2.x versions of Jython.
Thus, a significant migration step will have to be taken to migrate all your code to Python 3.

For Jython-specific features, follow this document to learn about migration to GraalVM's Python runtime.

Note that some features of Jython have a negative impact on runtime performance, and are disabled by default.
To make migration easier, you can enable some features with a command line flag on GraalVM: `--python.EmulateJython`.

## Importing Java Packages

There are certain features of Jython's Java integration that are enabled by default on GraalVM's Python runtime.
Here is an example:

    >>> import java.awt as awt
    >>> win = awt.Frame()
    >>> win.setSize(200, 200)
    >>> win.setTitle("Hello from Python!")
    >>> win.getSize().toString()
    'java.awt.Dimension[width=200,height=200]'
    >>> win.show()

This example works exactly the same on both Jython and Python on GraalVM.
However, on GraalVM only packages in the `java` namespace can be directly imported.
Importing classes from packages outside the `java` namespace also requires the `--python.EmulateJython` option to be active.

Additionally, importing Java packages as Python modules is only supported under very specific circumstances.
For example, this will work:
```python
import java.lang as lang
```

This will not work:
```python
import javax.swing as swing
from javax.swing import *
```

Instead, you will have to import one of the classes you are interested in directly:
```python
import javax.swing.Window as Window
```

## Basic Object Usage

Constructing and working with Java objects and classes is done with natural
Python syntax. The methods of Java objects can also be retrieved and passed
around as first class objects (bound to their instance), the same as Python
methods:

    >>> from java.util import Random
    >>> rg = Random(99)
    >>> rg.nextInt()
    1491444859
    >>> boundNextInt = rg.nextInt
    >>> boundNextInt()
    1672896916

## Java-to-Python Types: Automatic Conversion

Method overloads are resolved by matching the Python arguments in a best-effort manner to the available parameter types.
This also happens during when data conversion.
The goal here is to make using Java from Python as smooth as possible.
The matching allowed here is similar to Jython, but GraalVM's Python runtime uses a more dynamic approach to matching &mdash; Python types emulating `int` or `float` are also converted to the appropriate Java types.
This allows, for example, to use Pandas frames as `double[][]` or NumPy array elements as `int[]` when the elements fit into those Java primitive types.

| Java type              | Python type                                                                       |
|:-----------------------|:----------------------------------------------------------------------------------|
| null                   | None                                                                              |
| boolean                | bool                                                                              |
| byte, short, int, long | int, any object that has an `__int__` method                                      |
| float                  | float, any object that has a `__float__` method                                   |
| char                   | str of length 1                                                                   |
| java.lang.String       | str                                                                               |
| byte[]                 | bytes, bytearray, wrapped Java array, Python list with only the appropriate types |
| Java arrays            | Wrapped Java array or Python list with only the appropriate types                 |
| Java objects           | Wrapped Java object of the appropriate type                                       |
| java.lang.Object       | Any object                                                                        |

## Special Jython Modules

The `jarray` module which is used to create primitive Java arrays is supported for compatibility.

    >>> import jarray
    >>> jarray.array([1,2,3], 'i')

Note that its usage is equivalent of constructing the array types using the `java.type` function and filling the array.

    >>> import java
    >>> java.type("int[]")(10)

The code that only needs to pass a Java array can also use Python types.
However, implicitly, this may entail a copy of the array data, which can be deceiving when using Java arrays as output parameters:

    >>> i = java.io.ByteArrayInputStream(b"foobar")
    >>> buf = [0, 0, 0]
    >>> i.read(buf) # buf is automatically converted to a byte[] array
    3
    >>> buf
    [0, 0, 0] # the converted byte[] array got lost
    >>> jbuf = java.type("byte[]")(3)
    >>> i.read(jbuf)
    3
    >>> jbuf
    [98, 97, 122]

Other modules than `jarray` are not supported.

## Exceptions from Java

Catching all kinds of Java exceptions comes with a performance penalty and is only enabled with the `--python.EmulateJython` option.

    >>> import java
    >>> v = java.util.Vector()
    >>> try:
    ...    x = v.elementAt(7)
    ... except java.lang.ArrayIndexOutOfBoundsException as e:
    ...    print(e.getMessage())
    ...
    7 >= 0

## Java Collections
Java arrays and collections implementing `java.util.Collection` can be accessed using the `[]` syntax. Empty collections
are considered false in boolean conversions. Their length is exposed by `len` builtin function.

    >>> from java.util import ArrayList
    >>> l = ArrayList()
    >>> l.add("foo")
    True
    >>> l.add("baz")
    True
    >>> l[0]
    'foo'
    >>> l[1] = "bar"
    >>> del l[1]
    >>> len(l)
    1
    >>> bool(l)
    True
    >>> del l[0]
    >>> bool(l)
    False

Java iterables implementing `java.lang.Iterable` can be iterated using `for` loop or `iter` builtin function
and are accepted by all builtins that expect iterables.

    >>> [x for x in l]
    ['foo', 'bar']
    >>> i = iter(l)
    >>> next(i)
    'foo'
    >>> next(i)
    'bar'
    >>> next(i)
    Traceback (most recent call last):
    File "<stdin>", line 1, in <module>
    StopIteration
    >>> set(l)
    {'foo', 'bar'}

Iterators can be iterated as well.

    >>> from java.util import ArrayList
    >>> l = ArrayList()
    >>> l.add("foo")
    True
    >>> i = l.iterator()  # Calls the Java iterator methods
    >>> next(i)
    'foo'

Map collections implementing `java.util.Map` can be accessed using `[]` notation.
Empty maps are considered false in boolean conversions. Iteration of maps yields the keys, consistent with `dict`.

    >>> from java.util import HashMap
    >>> m = HashMap()
    >>> m['foo'] = 5
    >>> m['foo']
    5
    >>> m['bar']
    Traceback (most recent call last):
    File "<stdin>", line 1, in <module>
    KeyError: bar
    >>> [k for k in m]
    ['foo']
    >>> bool(m)
    True
    >>> del m['foo']
    >>> bool(m)
    False

## Inheritance from Java

Inheriting from a Java class or implementing an interface is supported with some syntactical differences from Jython. A
class inheriting from a Java class can be created using an ordinary `class` statement where declared methods will
override/implement the superclass methods when they match in name. Super calls are performed using a special
attribute `self.__super__`. The created object won't behave like a Python object but like a foreign Java object. Its
Python-level members can be accessed using its `this` attribute. Example:

```python
import atexit
from java.util.logging import Logger, Handler


class MyHandler(Handler):
    def __init__(self):
        self.logged = []

    def publish(self, record):
        self.logged.append(record)


logger = Logger.getLogger("mylog")
logger.setUseParentHandlers(False)
handler = MyHandler()
logger.addHandler(handler)
# Make sure the handler is not used after the Python context has been closed
atexit.register(lambda: logger.removeHandler(handler))

logger.info("Hi")
logger.warning("Bye")

# The python attributes/methods of the object have to be accessed through 'this' attribute
for record in handler.this.logged:
    print(f'Python captured message "{record.getMessage()}" at level {record.getLevel().getName()}')
```

## Embedding Python into Java

The other way to use Jython is to embed it into Java applications.

There are two options for embedding Jython in a Java application.
One it to use the `PythonInterpreter` object that Jython provides.
Existing code using Jython n this manner depends directly on the Jython package (for example, in the Maven configuration), because the Java code has references to Jython internal classes.
These classes do not exist in GraalVM, and no equivalent classes are exposed.
To migrate from this usage, switch to the [GraalVM SDK](https://mvnrepository.com/artifact/org.graalvm.sdk/graal-sdk).
Using this SDK, no APIs particular to Python are exposed, everything is done through the GraalVM API, with maximum configurability of the Python runtime.

The other option to embed Jython in Java is via [JSR 223](https://www.jcp.org/en/jsr/detail?id=223) by using the classes of the the `javax.script` package, and, in particular, via the `ScriptEngine` class.
We do not recommend this approach, since the `ScriptEngine` APIs are not a clean fit for the options and capabilities of GraalVM Python.
However, to migrate existing code, the NetBeans project provides packages on Maven Central to help here.
Remove Jython and add the following dependencies instead (using a Maven `pom.xml` as an example):

```xml
<dependency>
  <groupId>org.netbeans.api</groupId>
  <artifactId>org-netbeans-libs-graalsdk</artifactId>
  <version>RELEASE150</version> <!-- or any later release -->
</dependency>
<dependency>
  <groupId>org.netbeans.api</groupId>
  <artifactId>org-netbeans-api-scripting</artifactId>
  <version>RELEASE150</version> <!-- or any later release -->
</dependency>
```

Afterwards, basic usage of GraalVM's Python can be achieved by replacing

```java
ScriptEngine python = new ScriptEngineManager().getEngineByName("python");
`` 

with

```java
import org.netbeans.api.scripting.Scripting;
// ...
ScriptEngineManager manager = Scripting.newBuilder().allowAllAccess(true).build();
ScriptEngine python = manager.getEngineByName("GraalVM:python");
```

It is important to note that either of those options will only work if your application is executed on GraalVM with the Python language installed.
For more details, refer to the [Embed Languages](https://github.com/oracle/graal/blob/master/docs/reference-manual/embedding/embed-languages.md) guide.
