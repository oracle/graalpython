---
layout: docs-experimental
toc_group: python
link_title: Jython Compatibility
permalink: /reference-manual/python/Modern-Python-on-JVM/
redirect_from:
  - /reference-manual/python/Jython/
---

# Modern Python for the JVM

For Python version 2 (now EOL), Jython is the _de facto_ means of interfacing Python and Java.
Most existing Jython code that uses Java integration will be based on a stable Jython release&mdash;however, these are only available in Python 2.x versions.
GraalPy, in contrast, is compatible with Python 3.x and does not provide full compatibility with earlier 2.x versions of Jython.

To migrate code from Python 2 to Python 3, follow [the official guide from the Python community](https://docs.python.org/3/howto/pyporting.html).
Once your Jython code is compatible with Python 3, follow this guide to iron out other differences between GraalPy and Jython.

GraalPy's first-class support for [Java interoperability](Interoperability.md) makes using Java libraries from Python as easy as possible, with special affordances for Java code beyond the generic interoperability support for other Graal languages (languages implemented on the [Truffle framework](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/)).

Not all features of Jython are supported on GraalPy.
Some are supported, but disabled by default due to their negative impact on runtime performance.
During migration, you can enable these features using a command line option: `--python.EmulateJython`.
We recommend to move away from these features, however, to achieve optimal performance.

## Migrating Jython Scripts

> To move plain Jython scripts from Jython to GraalPy, use a GraalPy JVM-based runtime.
> (For more information, see available [GraalPy Distributions](Python-Runtime.md)).

### Importing a Java Package

There are certain features of Jython's Java integration that are enabled by default on GraalPy.
Here is an example:

```python
>>> import java.awt as awt
>>> win = awt.Frame()
>>> win.setSize(200, 200)
>>> win.setTitle("Hello from Python!")
>>> win.getSize().toString()
'java.awt.Dimension[width=200,height=200]'
>>> win.show()
```

This example produces the same result when run on both Jython and GraalPy.
However, when the example is run on GraalPy, only packages that are in the `java` namespace can be imported directly.
To import classes from packages outside the `java` namespace, use the `--python.EmulateJython` option.

> Note: When embedding GraalPy in a modularized application, you may have to
> add exports for the required modules according to JSR 376.

Additionally, it is not possible to import Java packages as Python modules in all circumstances.
For example, this will work:

```python
import java.lang as lang
```

But, this will not work:

```python
import javax.swing as swing
from javax.swing import *
```

Instead, import one of the classes directly:

```python
import javax.swing.Window as Window
```

### Basic Object Usage

Constructing and working with Java objects and classes is achieved with conventional
Python syntax.
The methods of a Java object can also be retrieved and referenced as first class objects (bound to their instance), in the same way as Python methods. For example:

```python
>>> from java.util import Random
>>> rg = Random(99)
>>> rg.nextInt()
1491444859
>>> boundNextInt = rg.nextInt
>>> boundNextInt()
1672896916
```

### Java-to-Python Types: Automatic Conversion

Method overloads are resolved by matching the Python arguments in a best-effort manner to the available parameter types.
This approach is also taken when converting data.
The goal here is to make using Java from Python as smooth as possible.
The matching approach taken by GraalPy is similar to Jython, but GraalPy uses a more dynamic approach to matching&mdash;Python types emulating `int` or `float` are also converted to the appropriate Java types.
This enables you, for example, to use a Pandas frame as `double[][]` or NumPy array elements as `int[]` when the elements fit into those Java primitive types.

| Java type                      | Python type                                                                             |
|:-------------------------------|:----------------------------------------------------------------------------------------|
| `null`                         | `None`                                                                                  |
| `boolean`                      | `bool`                                                                                  |
| `byte`, `short`, `int`, `long` | `int`, any object that has an `__int__` method                                          |
| `float`                        | `float`, any object that has a `__float__` method                                       |
| `char`                         | `str` of length 1                                                                       |
| `java.lang.String`             | `str`                                                                                   |
| `byte[]`                       | `bytes`, `bytearray`, wrapped Java `array`, Python list with only the appropriate types |
| Java arrays                    | Wrapped Java `array` or Python `list` with only the appropriate types                   |
| Java objects                   | Wrapped Java object of the appropriate type                                             |
| `java.lang.Object`             | Any object                                                                              |

### Special Jython Module: `jarray`

GraalPy implements the `jarray` module (to create primitive Java arrays) for compatibility.
For example:

```python
>>> import jarray
>>> jarray.array([1,2,3], 'i')
```

Note that its usage is equivalent to constructing the array type using the `java.type` function and then populating the array, as follows:

```python
>>> import java
>>> java.type("int[]")(10)
```

The code that creates a Java array can also use Python types.
However, implicitly, this may produce a copy of the array data, which can be deceptive when using a Java array as an output parameter:

```python
>>> i = java.io.ByteArrayInputStream(b"foobar")
>>> buf = [0, 0, 0]
>>> i.read(buf) # buf is automatically converted to a byte[] array
3
>>> buf
[0, 0, 0] # the converted byte[] array is lost
>>> jbuf = java.type("byte[]")(3)
>>> i.read(jbuf)
3
>>> jbuf
[98, 97, 122]
```

### Exceptions from Java

To catch Java exceptions, use the `--python.EmulateJython` option.

> Note: Catching a Java exception incurs a performance penalty.

For example:

```python
>>> import java
>>> v = java.util.Vector()
>>> try:
...    x = v.elementAt(7)
... except java.lang.ArrayIndexOutOfBoundsException as e:
...    print(e.getMessage())
...
7 >= 0
```

### Java Collections

* Java arrays and collections that implement the `java.util.Collection` interface can be accessed using the `[]` syntax. 
An empty collection is considered `false` in boolean conversions. 
The length of a collection is exposed by the `len` built-in function.
For example:

    ```python
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
    ```

* Java iterables that implement the `java.lang.Iterable` interface can be iterated over using a `for` loop or the `iter` built-in function and are accepted by all built-ins that expect an iterable. 
For example:

    ```python
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
    ```

* An iterator can be iterated as well. For example:

    ```python
    >>> from java.util import ArrayList
    >>> l = ArrayList()
    >>> l.add("foo")
    True
    >>> i = l.iterator()  # Calls the Java iterator methods
    >>> next(i)
    'foo'
    ```

* Mapped collections that implement the `java.util.Map` interface can be accessed using the `[]` notation.
An empty map is considered `false` in boolean conversions. Iteration of a map yields its keys, consistent with `dict`.
For example:

    ```python
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
    ```

### Inheritance from Java

Inheriting from a Java class (or implementing a Java interface) is supported with some syntactical differences from Jython. 
To create a class that inherits from a Java class (or implements a Java interface), use the conventional Python `class` statement: declared methods
override (implement) superclass (interface) methods when their names match. 
To call the a superclass method, use the special attribute `self.__super__`. 
The created object does not behave like a Python object but instead in the same way as a foreign Java object.
Its Python-level members can be accessed using its `this` attribute. For example:

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

# The python attributes/methods of the object are accessed through 'this' attribute
for record in handler.this.logged:
    print(f'Python captured message "{record.getMessage()}" at level {record.getLevel().getName()}')
```

## Embedding Python into Java

The other way to use Jython was to embed it into a Java application. There were two options for such an embedding.

1. Use the `PythonInterpreter` object that Jython provides.
Existing code using Jython in this manner depends directly on the Jython package (for example, in the Maven configuration), because the Java code has references to Jython internal classes.
These classes do not exist in GraalVM, and no equivalent classes are exposed.
To migrate from this usage, switch to the [GraalVM SDK](https://mvnrepository.com/artifact/org.graalvm.sdk/graal-sdk).
Using this SDK, no APIs particular to Python are exposed, everything is achieved via the GraalVM API, with maximum configurability of the Python runtime.
Refer to the [Getting Started](README.md) documentation for preparing a setup.

2. Embed Jython in Java via [JSR 223](https://www.jcp.org/en/jsr/detail?id=223) by using the classes of the the `javax.script` package, and, in particular, via the `ScriptEngine` class.
We do not recommend this approach, because the `ScriptEngine` APIs are not a clean fit for the options and capabilities of GraalPy.
However, to migrate existing code, we provide an example ScriptEngine implementation that you can inline into your project.
Refer to [the reference manual for embedding](https://www.graalvm.org/latest/reference-manual/embed-languages/#compatibility-with-jsr-223-scriptengine) for details.
