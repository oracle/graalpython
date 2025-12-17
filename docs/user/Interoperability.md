# Interoperability

GraalPy can interoperate with Java and other Graal languages that are implemented on the [Truffle framework](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/).
This means that you can use other languages' objects and functions directly from your Python scripts.
This interoperability works in both directions. Python can call other languages, and other languages can call Python code.

## Interacting with Java from Python scripts

Java is the host language of the JVM and runs the GraalPy interpreter itself. This means you can seamlessly access any Java class available in your classpath directly from Python.

### Basic Java access

Import the `java` module to access Java classes and methods:

```python
import java
BigInteger = java.type("java.math.BigInteger")
myBigInt = BigInteger.valueOf(42)
# Call Java methods directly
myBigInt.shiftLeft(128) # returns a <JavaObject[java.math.BigInteger] at ...>
# Java method names that are Python keywords must be accessed using `getattr`
getattr(myBigInt, "not")() # returns a <JavaObject[java.math.BigInteger] at ...>
byteArray = myBigInt.toByteArray()
# Java arrays can act like Python lists
assert len(byteArray) == 1 and byteArray[0] == 42
```

### Importing Java packages

You can import packages from the `java` namespace using conventional Python import syntax:

```python
import java.util.ArrayList
from java.util import ArrayList
assert java.util.ArrayList == ArrayList

al = ArrayList()
al.add(1)
al.add(12)
assert list(al) == [1, 12]
```

### Java module methods

In addition to the `type` built-in method, the `java` module exposes the following methods:

| Built-in                 | Specification
| ---                      | ---
| `instanceof(obj, class)` | Returns `True` if `obj` is an instance of `class` (`class` must be a foreign object class). |
| `is_function(obj)`       | Returns `True` if `obj` is a Java host language function wrapped using interop. |
| `is_object(obj)`         | Returns `True` if `obj` is a Java host language object wrapped using interop. |
| `is_symbol(obj)`         | Returns `True` if `obj` is a Java host symbol, representing the constructor and static members of a Java class, as obtained by `java.type`. |

Here's how to use these methods in practice:

```python
ArrayList = java.type('java.util.ArrayList')
my_list = ArrayList()
assert java.is_symbol(ArrayList)
assert not java.is_symbol(my_list)
assert java.is_object(ArrayList)
assert java.is_function(my_list.add)
assert java.instanceof(my_list, ArrayList)
```

See the [Polyglot Programming](https://github.com/oracle/graal/blob/master/docs/reference-manual/polyglot-programming.md) and [Embed Languages](https://github.com/oracle/graal/blob/master/docs/reference-manual/embedding/embed-languages.md) documentation for more information about interoperability with other programming languages.

## Interacting with foreign objects from Python scripts

When you use foreign objects in Python, GraalPy automatically makes them behave like their Python equivalents.

For example, a Java `ArrayList` acts like a Python `list`, and a Java `HashMap` acts like a Python `dict`:

```python
from java.util import ArrayList, HashMap
type(ArrayList()).mro() # => [<class 'polyglot.ForeignList'>, <class 'list'>, <class 'polyglot.ForeignObject'>, <class 'object'>]
type(HashMap()).mro() # => [<class 'polyglot.ForeignDict'>, <class 'dict'>, <class 'polyglot.ForeignObject'>, <class 'object'>]
```

This means you can use Python methods on foreign objects:

```python
from java.util import ArrayList, HashMap
# ArrayList behaves like a Python list so you can use Python methods
l = ArrayList()
l.append(1) # Python list method - l: [1]
l.extend([2, 3]) # Python list method - l: [1, 2, 3]
l.add(4) # Java ArrayList method still works - l: [1, 2, 3, 4]
l[1:3] # Python slicing works - returns [2, 3]
l.pop(1) # Python list method - returns 2, l: [1, 3, 4]
l.insert(1, 2) # Python list method - l: [1, 2, 3, 4]
l == [1, 2, 3, 4] # Python comparison works - True

# HashMap behaves like a Python dict so you can use Python methods
h = HashMap()
h[1] = 2 # Python dict syntax - h: {1: 2}
h.setdefault(3, 4) # Python dict method - h: {1: 2, 3: 4}
h |= {3: 6} # Python dict operator - h: {1: 2, 3: 6}
h == {1: 2, 3: 6} # Python comparison works - True
```

When a method is defined both in Python and on the foreign object, the Python's method takes precedence.

To call the foreign method explicitly, use `super(type_owning_the_python_method, foreign_object).method(*args)`:

```python
from java.util import ArrayList
l = ArrayList()
l.extend([5, 6, 7])
l.remove(7) # Calls Python list.remove()
assert l == [5, 6]

super(list, l).remove(0) # Calls Java's ArrayList.remove()
assert l == [6]
```

See the [Interop Types to Python](#interop-types-to-python) section for more interop traits and how they map to Python types.

## Interacting with other dynamic languages from Python scripts

The _polyglot_ API allows non-JVM specific interactions with other languages from Python scripts.
This includes all interactions with dynamic languages supported via the [Truffle framework](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/), including JavaScript and Ruby.

### Installing other dynamic languages

To use other languages, like JavaScript, you need to add their Maven dependencies to your project.

If you're using Maven with GraalPy, add the JavaScript dependency to your _pom.xml_ file:

```xml
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>js</artifactId>
    <version>25.0.1</version>
</dependency>
```

<aside markdown="1">
For Python developers, you need to install other languages using the `libexec/graalpy-polyglot-get` command from your GraalPy installation directory.

To install JavaScript, for example:

```bash
libexec/graalpy-polyglot-get js
```

</aside>

### Examples

Here are practical examples of using the `polyglot` API to work with JavaScript from Python:

1. Import the `polyglot` module to interact with other languages:
   ```python
   import polyglot
   ```

2. Evaluate inlined code in another language:
   ```python
   assert polyglot.eval(string="1 + 1", language="js") == 2
   ```

3. Evaluate code from a file:
   ```python
   with open("./my_js_file.js", "w") as f:
       f.write("Polyglot.export('JSMath', Math)")
   polyglot.eval(path="./my_js_file.js", language="js")
   ```

4. Import a global value from the polyglot scope:
   ```python
   Math = polyglot.import_value("JSMath")
   ```

    This global value should then work as expected:
    * Accessing attributes reads from the *polyglot members* namespace:
      ```python
      assert Math.E == 2.718281828459045
      ```

    * Calling a method on the result attempts to do a straight `invoke` and falls back to reading the member and trying to execute it.
      ```python
      assert Math.toString() == "[object Math]"
      ```

    * Accessing items is supported both with strings and numbers.
      ```python
      assert Math["PI"] == 3.141592653589793
      ```

5. Use the JavaScript regular expression engine to match Python strings:
   ```python
   js_re = polyglot.eval(string="RegExp()", language="js")

   pattern = js_re.compile(".*(?:we have (?:a )?matching strings?(?:[!\\?] )?)(.*)")

   if pattern.exec("This string does not match"): raise SystemError("that shouldn't happen")

   md = pattern.exec("Look, we have matching strings! This string was matched by Graal.js")

   assert "Graal.js" in md[1]
   ```

    This program matches Python strings using the JavaScript regular expression object. Python reads the captured group from the JavaScript result and checks for a substring in it.

## Exporting Python Objects to other languages

Use the `polyglot` module to expose Python objects to JVM languages and other Graal languages (languages implemented on the [Truffle framework](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/)).

This allows other languages to call your Python code directly.

1. You can export a Python object so other languages can access it:
   ```python
   import ssl
   polyglot.export_value(value=ssl, name="python_ssl")
   ```
   
   Then use it, for example, from JavaScript code:
   ```js
   Polyglot.import('python_ssl').get_server_certificate(["oracle.com", 443])
   ```

2. You can decorate a Python function to export it by name:
   ```python
   @polyglot.export_value
   def python_method():
       return "Hello from Python!"
   ```

   Then use it, for example, from Java code:
   ```java
   import org.graalvm.polyglot.*;

   class Main {
       public static void main(String[] args) {
           try (var context = Context.create()) {
               context.eval(Source.newBuilder("python", "file:///python_script.py").build());

               String result = context.
                   getPolyglotBindings().
                   getMember("python_method").
                   execute().
                   asString();
               assert result.equals("Hello from Python!");
           }
       }
    }
    ```

## Mapping types between Python and other languages

The interop protocol defines different types and traits that determine foreign objects behavior and restrictions when used in Python.

### Interop Types to Python

All foreign objects passed into Python have the Python type `polyglot.ForeignObject` or a subclass.

Types not listed in the table below have no special interpretation in Python.

| Interop Type   | Inherits from                     | Python Interpretation                                                                                               |
| :------------- | :-------------------------------- | :------------------------------------------------------------------------------------------------------------------ |
| `array`        | ForeignList, `list`               | An `array` behaves like a Python `list`.                                                                                                                                                                                                   |
| `boolean`      | ForeignBoolean, ForeignNumber     | `boolean` behaves like Python booleans, including the fact that in Python, all booleans are also integers (`1` and `0` for `true` and `false`, respectively).                                                                                      |
| `buffer`       | ForeignObject                     | Buffers work like Python buffer objects (such as those used with `memoryview`) to avoid copying data.                            |
| `exception`    | ForeignException, `BaseException` | An `exception` can be caught in a generic `except` clause.                                                                                                                                                                                 |
| `executable`   | ForeignExecutable                 | An `executable` object can be executed as a function, but never with keyword arguments.                                                                                                                                                    |
| `hash`         | ForeignDict, `dict`               | A `hash` behaves like a Python `dict`, with any "hashable" object as a key. "Hashable" follows Python semantics: generally every interop type with an identity is deemed "hashable".                                                       |
| `instantiable` | ForeignInstantiable               | An `instantiable` object can be called just like a Python type, but never with keyword arguments.                                                                                                                                          |
| `iterable`     | ForeignIterable                   | An `iterable` is treated in the same way as any Python object with an `__iter__` method. That is, it can be used in a loop and other places that accept Python iterables.                                                                  |
| `iterator`     | ForeignIterator, `iterator`       | An `iterator` is treated in the same way as any Python object with a `__next__` method.                                                                                                                                                    |
| `members`      | ForeignObject                     | Objects with `members` can be accessed using Python dot notation (`.`) or `getattr()`. |
| `MetaObject`   | ForeignAbstractClass              | Meta objects can be used in subtype and `isinstance` checks.                                                                                                                                                                               |
| `null`         | ForeignNone, `NoneType`           | `null` behaves like Python `None`. All interop null values (including JavaScript `undefined` and `null`) are treated as `None` in Python. |
| `number`       | ForeignNumber                     | `number` behaves like Python numbers (`int` and `float`). Foreign ranges are imported in some places such as typed arrays.                                     |
| `string`       | ForeignString, `str`              | Behaves in the same way as a Python string.                                                                                                                                                                                                |

Foreign numbers inherit from `polyglot.ForeignNumber` and not `int` or `float` because `InteropLibrary` has currently no way to differentiate integers and floats.

However:

* When foreign numbers are represented as Java primitives `byte`, `short`, `int`, `long`, they are considered Python `int` objects.
* When foreign numbers are represented as Java primitives `float`, `double`, they are considered Python `float` objects.
* When foreign booleans are represented as Java primitives `boolean`, they are considered Python `bool` objects.

### Python to Interop Types

The following table shows how Python objects are converted to interop types when passed to other languages:

| Interop Type   | Python Interpretation                                                                                                                                 |
| :------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `array`        | Any object with `__getitem__` and `__len__` methods, but not if it also has `keys`, `values`, and `items` methods (in the same way that `dict` does.) |
| `boolean`      | Only subtypes of Python `bool`. Note that in contrast to Python semantics, Python `bool` is *never* also an interop number.                           |
| `exception`    | Any Python `BaseException` subtype.                                                                                                                   |
| `executable`   | Any Python object with a `__call__` method.                                                                                                          |
| `hash`         | Only subtypes of `dict`.                                                                                                                              |
| `instantiable` | Any Python `type`.                                                                                                                                    |
| `iterable`     | Any Python object that has `__iter__` or `__getitem__` methods.                                                                                     |
| `iterator`     | Any Python object with a `__next__` method.                                                                                                           |
| `members`      | Any Python object. Note that the rules for readable/writable are a bit ad-hoc, since checking that is not part of the Python MOP.                     |
| `MetaObject`   | Any Python `type`.                                                                                                                                    |
| `null`         | Only `None`.                                                                                                                                          |
| `number`       | Only subtypes of `int` and `float`.                                                                                                                   |
| `string`       | Only subtypes of `str`.                                                                                                                               |

## The Interoperability Extension API

You can extend the interoperability protocol directly from Python through a simple API defined in the `polyglot` module.
This API lets you define interoperability behavior for custom or user-defined types that are not automatically supported.
This is particularly useful for external types which are not compatible by default with the interop protocol.
For example, `numpy` numeric types (for example, `numpy.int32`) which are not supported by default by the interop protocol need special handling to work properly with other languages.

The `polyglot` module provides these functions for customizing interop behavior:

| Function                          | Description                                                                                                                                                                   |
|:--------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `register_interop_behavior`       | Takes the receiver **type** as the first argument. The remaining keyword arguments correspond to the respective interop messages. Not all interop messages are supported. |
| `get_registered_interop_behavior` | Takes the receiver **type** as the first argument. Returns the list of extended interop messages for the given type.                                                      |
| `@interop_behavior`               | Class decorator that takes the receiver **type** as the only argument. The interop messages are extended via **static** methods defined in the decorated class (supplier).            |
| `register_interop_type`           | Takes a `foreign class` and `python class` as positional arguments and `allow_method_overwrites` as an optional argument (default: `False`). Every instance of the foreign class is then treated as an instance of the given python class. |
| `@interop_type`                   | Class decorator that takes the `foreign class` and optionally `allow_method_overwrites` as arguments. The instances of the foreign class will be treated as an instance of the annotated python class.                                      |

### Interop behavior usage example

You can use the `register_interop_behavior` API to add custom interop behavior to existing types:

For example, to make `numpy.int32` work properly with other languages:

```python
import polyglot
import numpy

polyglot.register_interop_behavior(numpy.int32,
    is_number=True,
    fitsInByte=lambda v: -128 <= v < 128,
    fitsInShort=lambda v: -0x8000 <= v < 0x8000,
    fitsInInt = True,
    fitsInLong = True,
    fitsInBigInteger = True,
    asByte = int,
    asShort = int,
    asInt = int,
    asLong = int,
    asBigInteger = int,
)
```

Alternatively, you can use the `@interop_behavior` decorator when you need to define multiple behaviors for a type.
With this decorator, you define interop behaviors using **static** methods in a decorated class.
The static method names must match the keyword argument names used by `register_interop_behavior`.

The following example uses the decorator approach for `numpy.float64`:

```python
from polyglot import interop_behavior
import numpy


@interop_behavior(numpy.float64)
class Float64InteropBehaviorSupplier:
    @staticmethod
    def is_number(_):
        return True

    @staticmethod
    def fitsInDouble(_):
        return True

    @staticmethod
    def asDouble(v):
        return float(v)
```

Both classes can then behave as expected when embedded:

```java
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Context;

class Main {
    public static void main(String[] args) {
        try (var context = Context.create()) {
            context.eval("python", Files.readString(Path.of("path/to/interop/behavior/script.py")));
            assert context.eval("python", "numpy.float64(12)").asDouble() == 12.0;
            assert context.eval("python", "numpy.int32(12)").asByte() == 12;
        }
    }
}
```

### Interop types usage example

The `register_interop_type` API allows the usage of python classes for foreign objects.
When you register a Python class for a foreign type, instances of that foreign object will no longer have the default `polyglot.ForeignObject` or `polyglot.Foreign* class`.
Instead, GraalPy creates a new generated class that inherits from both your Python class and `polyglot.ForeignObject`.
This lets you add Python methods to foreign objects, and map foreign functionality to Python's magic methods or more idiomatic Python patterns.

This is a simple Java class to customize:

```java
package org.example;

class MyJavaClass {
      private int x;
      private int y;
      
      public MyJavaClass(int x, int y) {
         this.x = x;
         this.y = y;
      }

      public int getX() {
         return x;
      }

      public int getY() {
         return y;
      }
   }
```

The following snippet sets up the Java environment and makes the object available to Python:

```java
import org.example.MyJavaClass;
        
class Main {

   public static void main(String[] args) {
      MyJavaClass myJavaObject = new MyJavaClass(42, 17);
      try (var context = Context.create()) {
         // myJavaObject will be globally available in example.py as my_java_object
         context.getBindings("python").putMember("my_java_object", myJavaObject);
         context.eval(Source.newBuilder("python", "example.py"));
      }
   }
}
```

This snippet states how to customize the Java object's behavior using Python classes:

```python
# example.py
import java
from polyglot import register_interop_type

print(my_java_object.getX()) # 42
print(type(my_java_object)) # <class 'polyglot.ForeignObject'>

class MyPythonClass:
   def get_tuple(self):
      return (self.getX(), self.getY())

foreign_class = java.type("org.example.MyJavaClass")

register_interop_type(foreign_class, MyPythonClass)

print(my_java_object.get_tuple()) # (42, 17)
print(type(my_java_object)) # <class 'polyglot.Java_org.example.MyJavaClass_generated'>
print(type(my_java_object).mro()) # [polyglot.Java_org.example.MyJavaClass_generated, MyPythonClass, polyglot.ForeignObject, object]

class MyPythonClassTwo:
   def get_tuple(self):
      return (self.getY(), self.getX())
   
   def __str__(self):
      return f"MyJavaInstance(x={self.getX()}, y={self.getY()})"

# If 'allow_method_overwrites=True' is not given, this would lead to an error due to the method conflict of 'get_tuple'  
register_interop_type(foreign_class, MyPythonClassTwo, allow_method_overwrites=True)

# A newly registered class will be before already registered classes in the mro.
# It allows overwriting methods from already registered classes with the flag 'allow_method_overwrites=True'
print(type(my_java_object).mro()) # [generated_class, MyPythonClassTwo, MyPythonClass, polyglot.ForeignObject, object]

print(my_java_object.get_tuple()) # (17, 42)
print(my_java_object) # MyJavaInstance(x=42, y=17)
```

For simpler cases, you can use the `@interop_type` decorator:

```python
import java
from polyglot import interop_type

foreign_class = java.type("org.example.MyJavaClass")

@interop_type(foreign_class)
class MyPythonClass:
   def get_tuple(self):
      return (self.getX(), self.getY())
```

### Supported messages

Most interop messages are supported by the interop behavior extension API. The naming convention for `register_interop_behavior` keyword arguments uses _snake_case_, so the interop `fitsInLong` message becomes `fits_in_long`. 
Each message can be extended with either a **pure Python function** (no default keyword arguments, free vars, or cell vars allowed) or a **boolean constant**.

The following table describes the supported interop messages:

| Message                    | Extension argument name       | Expected return type                                                                 |
| :------------------------- | :---------------------------- | :----------------------------------------------------------------------------------- |
| `isBoolean`                | `is_boolean`                  | `bool`                                                                               |
| `isDate`                   | `is_date`                     | `bool`                                                                               |
| `isDuration`               | `is_duration`                 | `bool`                                                                               |
| `isExecutable`             | `is_executable`               | `bool`                                                                               |
| `isIterator`               | `is_iterator`                 | `bool`                                                                               |
| `isNumber`                 | `is_number`                   | `bool`                                                                               |
| `isString`                 | `is_string`                   | `bool`                                                                               |
| `isTime`                   | `is_time`                     | `bool`                                                                               |
| `isTimeZone`               | `is_time_zone`                | `bool`                                                                               |
| `fitsInBigInteger`         | `fits_in_big_integer`         | `bool`                                                                               |
| `fitsInByte`               | `fits_in_byte`                | `bool`                                                                               |
| `fitsInDouble`             | `fits_in_double`              | `bool`                                                                               |
| `fitsInFloat`              | `fits_in_float`               | `bool`                                                                               |
| `fitsInInt`                | `fits_in_int`                 | `bool`                                                                               |
| `fitsInLong`               | `fits_in_long`                | `bool`                                                                               |
| `fitsInShort`              | `fits_in_short`               | `bool`                                                                               |
| `asBigInteger`             | `as_big_integer`              | `int`                                                                                |
| `asBoolean`                | `as_boolean`                  | `bool`                                                                               |
| `asByte`                   | `as_byte`                     | `int`                                                                                |
| `asDate`                   | `as_date`                     | tuple: (`year`: int, `month`: int, `day`: int)                                       |
| `asDouble`                 | `as_double`                   | `float`                                                                              |
| `asDuration`               | `as_duration`                 | tuple: (`seconds`: int, `nano_adjustment`: int)                                      |
| `asFloat`                  | `as_float`                    | `float`                                                                              |
| `asInt`                    | `as_int`                      | `int`                                                                                |
| `asLong`                   | `as_long`                     | `int`                                                                                |
| `asShort`                  | `as_short`                    | `int`                                                                                |
| `asString`                 | `as_string`                   | `str`                                                                                |
| `asTime`                   | `as_time`                     | tuple: (`hour`: int, `minute`: int, `second`: int, `microsecond`: int)               |
| `asTimeZone`               | `as_time_zone`                | `str` (timezone name) or `int` (UTC delta in seconds)                               |
| `execute`                  | `execute`                     | `object`                                                                             |
| `readArrayElement`         | `read_array_element`          | `object`                                                                             |
| `getArraySize`             | `get_array_size`              | `int`                                                                                |
| `hasArrayElements`         | `has_array_elements`          | `bool`                                                                               |
| `isArrayElementReadable`   | `is_array_element_readable`   | `bool`                                                                               |
| `isArrayElementModifiable` | `is_array_element_modifiable` | `bool`                                                                               |
| `isArrayElementInsertable` | `is_array_element_insertable` | `bool`                                                                               |
| `isArrayElementRemovable`  | `is_array_element_removable`  | `bool`                                                                               |
| `removeArrayElement`       | `remove_array_element`        | `None`                                                                               |
| `writeArrayElement`        | `write_array_element`         | `None`                                                                               |
| `hasIterator`              | `has_iterator`                | `bool`                                                                               |
| `hasIteratorNextElement`   | `has_iterator_next_element`   | `bool`                                                                               |
| `getIterator`              | `get_iterator`                | Python iterator                                                                      |
| `getIteratorNextElement`   | `get_iterator_next_element`   | `object`                                                                             |
| `hasHashEntries`           | `has_hash_entries`            | `bool`                                                                               |
| `getHashEntriesIterator`   | `get_hash_entries_iterator`   | Python iterator                                                                      |
| `getHashKeysIterator`      | `get_hash_keys_iterator`      | Python iterator                                                                      |
| `getHashSize`              | `get_hash_size`               | `int`                                                                                |
| `getHashValuesIterator`    | `get_hash_values_iterator`    | Python iterator                                                                      |
| `isHashEntryReadable`      | `is_hash_entry_readable`      | `bool`                                                                               |
| `isHashEntryModifiable`    | `is_hash_entry_modifiable`    | `bool`                                                                               |
| `isHashEntryInsertable`    | `is_hash_entry_insertable`    | `bool`                                                                               |
| `isHashEntryRemovable`     | `is_hash_entry_removable`     | `bool`                                                                               |
| `readHashValue`            | `read_hash_value`             | `object`                                                                             |
| `writeHashEntry`           | `write_hash_entry`            | `None`                                                                               |
| `removeHashEntry`          | `remove_hash_entry`           | `None`                                                                               | 
