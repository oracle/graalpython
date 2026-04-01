# Migrate from Jython to GraalPy

Jython has been the standard way to run Python code on the JVM and integrate with Java libraries.
However, Jython's latest stable releases only support Python 2.x, which reached end-of-life (EOL) in 2020.

GraalPy provides a modern alternative that supports Python 3.x on the JVM with excellent Java interoperability.
While GraalPy offers similar capabilities to Jython, there are important differences in how Java integration works.

This guide shows you how to migrate from Jython to GraalPy.

### Prerequisites

- To move plain Jython scripts from Jython to GraalPy, use a GraalPy JVM-based runtime. (For more information, see available [GraalPy Distributions](Python-Runtime.md))
- First migrate code from Python 2 to Python 3 following [the official guide from the Python community](https://docs.python.org/3/howto/pyporting.html)

## GraalPy Java Interoperability Overview

GraalPy provides excellent [Java interoperability](Interoperability.md), allowing you to use Java libraries from Python with minimal friction.
It offers specialized features for Java integration that go beyond what's available for other languages on the GraalVM platform.

**Important:** GraalPy doesn't support all Jython features out of the box.
Some features are available but disabled by default for performance reasons.
You can enable them during migration with `--python.EmulateJython`, but updating your code to use GraalPy's native approach is recommended for better performance.

## Java Package Imports

### Packages That Work by Default

Certain features of Jython's Java integration are enabled by default on GraalPy.
Here's what works the same:

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

### Packages Outside the Java Namespace

To import classes from packages outside the `java` namespace, use the `--python.EmulateJython` option during migration.

> **Note:** When embedding GraalPy in a modularized application, you may have to add exports for the required modules according to JSR 376.

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

Instead, import classes directly:

```python
import javax.swing.Window as Window
```

## Java Object Usage

### Working with Java Objects

Constructing and working with Java objects and classes is achieved with conventional Python syntax.
The methods of a Java object can also be retrieved and referenced as first class objects (bound to their instance), in the same way as Python methods:

```python
>>> from java.util import Random
>>> rg = Random(99)
>>> rg.nextInt()
1491444859
>>> boundNextInt = rg.nextInt
>>> boundNextInt()
1672896916
```

### Type Conversion

GraalPy automatically converts between Python and Java types to make method calls and data passing seamless.
When you call Java methods, GraalPy matches your Python arguments to the available Java parameter types using a best-effort approach.

GraalPy's type matching is more flexible than Jython's as it can convert any Python object with `__int__` or `__float__` methods to the corresponding Java types.
This means you can use NumPy arrays as Java `int[]` or Pandas DataFrames as Java `double[][]` when the data types are compatible.

Here are the supported type conversions:

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

## Java Arrays

### Special Jython Module: jarray

GraalPy implements the `jarray` module (to create primitive Java arrays) for compatibility.
This module is always available, since we have not found its presence to have a negative impact:

```python
>>> import jarray
>>> jarray.array([1,2,3], 'i')
```

Note that its usage is equivalent to constructing the array type using the `java.type` function and then populating the array:

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

## Java Exceptions

You can catch Java exceptions as you would expect:

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

## Java Collections

### Collection Interface Features

Java arrays and collections that implement the `java.util.Collection` interface can be accessed using the `[]` syntax.
An empty collection is considered `false` in boolean conversions.
The length of a collection is exposed by the `len` built-in function:

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

### Iterating Over Collections

Java iterables that implement the `java.lang.Iterable` interface can be iterated over using a `for` loop or the `iter` built-in function and are accepted by all built-ins that expect an iterable:

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

You can also iterate over Java iterators directly:

```python
>>> from java.util import ArrayList
>>> l = ArrayList()
>>> l.add("foo")
True
>>> i = l.iterator()  # Calls the Java iterator methods
>>> next(i)
'foo'
```

### Working with Maps

Mapped collections that implement the `java.util.Map` interface can be accessed using the `[]` notation.
An empty map is considered `false` in boolean conversions.
Iteration of a map yields its keys, consistent with `dict`:

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

## Inheritance from Java Classes

### Understanding Java Class Inheritance

Inheriting from a Java class (or implementing a Java interface) is supported with some syntactical and significant behavioral differences from Jython.
To create a class that inherits from a Java class (or implements a Java interface), use the conventional Python `class` statement.
Declared methods override (implement) superclass (interface) methods when their names match.

**Important:** There is actually delegation happening here - when inheriting from Java, two classes are created, one in Java and one in Python.
These reference each other and any methods that are declared in Python that override or implement a Java method on the superclass are declared on the Java side as delegating to Python.
The created object does not behave like a Python object but instead in the same way as a foreign Java object.
The reason for this is that when you create an instance of your new class, you get a reference to the *Java* object.

### Inheritance behavior up to GraalPy version 25.1

When inheriting from a Java class, you can pass the keyword `new_style=False`.
This is the default up to and including GraalPy version 25.1.

Key points for legacy inheritance:

- The generated class is a Java object and no forwarding to the Python-side happens by default
- To call Python methods that do *not* override or implement methods that already existed on the superclass, you need to use the special `this` attribute
- Once you are in a Python method, your `self` refers to the Python object, and to get back from a Python method to Java, use the special attribute `__super__`
- If you need to call a static method from an instance on the Java side, use `getClass().static` to get to the meta-object holding the static members
- The `__init__` method on the Python object is actually called *before* the connection to the Java side is established, so you cannot currently override construction of the Java object

For example:

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

For more information about how the generated Java subclass behaves, see the [Truffle documentation](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#createHostAdapter(java.lang.Object%5B%5D)).

### Inheritance behavior from GraalPy 25.1

When inheriting from a Java class, you can pass the keyword `new_style=True`. This is the default after GraalPy version 25.1.
The generated class is a Java object, but attribute lookup is dispatched to the Python object as needed.

Features of modern inheritance:

- Multiple levels of inheritance are supported
- `super()` calls work both in the constructor override via `__new__` as well as in Java method overrides
- The `self` in a method refers to the Java object, but any access that does not refer to a field or method on the Java class is transparently dispatched to the Python side
- Static methods can be called both from the instance as well as the class

For example:

```python
from java.util.logging import Level

class PythonLevel(Level, new_style=True):
    def __new__(cls, name="default name", level=2):
        """Provide a default constructor that modifies
        the construction of the Java instance"""
        return super().__new__(cls, name, level)

    def __init__(self, *args, **kwarg):
        """After the instance is created, initialize the
        misc_value field"""
        self.misc_value = 42

    def getName(self):
        """This overrides the Java method on
        java.util.logging.Level"""
        return super().getName() + " from Python with super()"

    def pythonName(self):
        """This adds a method that is only visible from Python,
        but self and super calls work as expected"""
        return f"PythonName for Level {self.intValue()} named {super().getName()}"

    def callStaticFromPython(self, name):
        """Java static methods can be called from the
        instance as well as the class"""
        return self.parse(name)

pl = PythonLevel()
assert issubclass(PythonLevel, Level)
assert PythonLevel.parse("INFO").getName() == "INFO"
```

## Embedding Python into Java

If you were embedding Jython in Java applications, there were two main approaches that need different migration paths:

- **`PythonInterpreter` approach:** Use the `PythonInterpreter` object that Jython provides.

   Existing code using Jython in this manner depends directly on the Jython package (for example, in the Maven configuration), because the Java code has references to Jython internal classes. These classes do not exist in GraalVM, and no equivalent classes are exposed.

   Switch to the [GraalVM SDK](https://central.sonatype.com/artifact/org.graalvm.sdk/graal-sdk). Using this SDK, no APIs particular to Python are exposed, everything is achieved via the GraalVM API, with maximum configurability of the Python runtime. Refer to the [Embedding Getting Started](Embedding-Getting-Started.md) documentation for preparing a setup.

- **JSR 223 `ScriptEngine` approach:** Embed Jython in Java via [JSR 223](https://www.jcp.org/en/jsr/detail?id=223) by using the classes of the `javax.script` package, and, in particular, via the `ScriptEngine` class.

   This approach is not recommended, because the `ScriptEngine` APIs are not a clean fit for the options and capabilities of GraalPy.

   However, to migrate existing code, an example ScriptEngine implementation is provided that you can inline into your project. Refer to [the Embedding Languages reference manual](https://www.graalvm.org/latest/reference-manual/embed-languages/#compatibility-with-jsr-223-scriptengine) for details.
