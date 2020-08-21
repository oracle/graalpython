# Interoperability

GraalVM supports several other programming languages, including JavaScript, R,
Ruby, and LLVM. GraalVM provides a Python API to interact with other languages
available on the GraalVM. In fact, GraalVM uses this API internally to
execute Python C extensions using the LLVM implementation in GraalVM.

You can import the `polyglot` module to interact with other languages.

```python
import polyglot
```

You can import a global value from the entire polyglot scope:
```python
imported_polyglot_global = polyglot.import_value("global_name")
```

This global should then work as expected; accessing attributes assumes it reads
from the `members` namespace; accessing items is supported both with strings and
numbers; calling methods on the result tries to do a straight invoke and falls
back to reading the member and trying to execute it.

You can evaluate some code in another language:
```python
polyglot.eval(string="1 + 1", language="ruby")
```

It also works with the path to a file:
```python
polyglot.eval(path="./my_ruby_file.rb", language="ruby")
```

If you pass a file, you can also try to rely on the file-based language detection:
```python
polyglot.eval(path="./my_ruby_file.rb")
```

To export something from Python to other Polyglot languages so they can import
it:
```python
foo = object()
polyglot.export_value(foo, name="python_foo")
```

The export function can be used as a decorator, in this case the function name
is used as the globally exported name:
```python
@polyglot.export_value
def python_method():
    return "Hello from Python!"
```

Here is an example of how to use JavaScript regular expression engine to
match Python strings. Save this code to the `polyglot_example.py` file:

```python
import polyglot

re = polyglot.eval(string="RegExp()", language="js")

pattern = re.compile(".*(?:we have (?:a )?matching strings?(?:[!\\?] )?)(.*)")

if pattern.exec("This string does not match"):
    raise SystemError("that shouldn't happen")

md = pattern.exec("Look, we have matching strings! This string was matched by Graal.js")
if not md:
    raise SystemError("this should have matched")

print("Here is what we found: '%s'" % md[1])
```

To run it, pass the `--jvm --polyglot` options to `graalpython` binary:
```shell
graalpython --jvm --polyglot polyglot_example.py
```

This example matches Python strings using the JavaScript regular expression object
and Python reads the captured group from the JavaScript result and prints: `Here
is what we found: 'This string was matched by Graal.js'`.

As a more complex example, we can read a file using R, process the data in
Python, and use R again to display the resulting data image, using both R and
Python libraries in conjunction. To run it, first install the
required R library:
```shell
R -e 'install.packages("https://www.rforge.net/src/contrib/jpeg_0.1-8.tar.gz", repos=NULL)'
```

This example also uses [image_magix.py](http://graalvm.org/docs/examples/image_magix.py) and works
on a JPEG image input (you can try with [this image](https://www.graalvm.org/resources/img/python_demo_picture.jpg)). These files have to be in the same folder the script below is located in and executed from.

```python
import polyglot
import sys
import time
sys.path.insert(0, ".")
from image_magix import Image

load_jpeg = polyglot.eval(string="""function(file.name) {
    library(jpeg)
    jimg <- readJPEG(file.name)
    jimg <- jimg*255
    jimg
}""", language="R")

raw_data = load_jpeg("python_demo_picture.jpg")

# the dimensions are R attributes; define function to access them
getDim = polyglot.eval(string="function(v, pos) dim(v)[[pos]]", language="R")

# Create object of Python class 'Image' with loaded JPEG data
image = Image(getDim(raw_data, 2), getDim(raw_data, 1), raw_data)

# Run Sobel filter
result = image.sobel()

draw = polyglot.eval(string="""function(processedImgObj) {
    require(grDevices)
    require(grid)
    mx <- matrix(processedImgObj$`@data`/255, nrow=processedImgObj$`@height`, ncol=processedImgObj$`@width`)
    grDevices:::awt()
    grid.raster(mx, height=unit(nrow(mx),"points"))
}""", language="R")

draw(result)
time.sleep(10)
```

## Java Interoperability

Finally, to interoperate with Java (only when running on the JVM), you can use
the `java` module:
```python
import java
BigInteger = java.type("java.math.BigInteger")
myBigInt = BigInteger(42)
myBigInt.shiftLeft(128)            # public Java methods can just be called
myBigInt["not"]()                  # Java method names that are keywords in
                                   # Python can be accessed using "[]"
byteArray = myBigInt.toByteArray()
print(list(byteArray))             # Java arrays can act like Python lists
```

For packages under the `java` package, you can also use the normal Python import
syntax:
```python
import java.util.ArrayList
from java.util import ArrayList

# these are the same class
java.util.ArrayList == ArrayList

al = ArrayList()
al.add(1)
al.add(12)
print(al) # prints [1, 12]
```

In addition to the `type` builtin method, the `java` module, exposes the following
methods as well:

Builtin                  | Specification
---                      | ---
`instanceof(obj, class)` | returns `True` if `obj` is an instance of `class` (`class` must be a foreign object class)
`is_function(obj)`       | returns `True` if `obj` is a Java host language function wrapped using Truffle interop
`is_object(obj)`         | returns `True` if `obj` if the argument is Java host language object wrapped using Truffle interop
`is_symbol(obj)`         | returns `True` if `obj` if the argument is a Java host symbol, representing the constructor and static members of a Java class, as obtained by `java.type`

```python
import java
ArrayList = java.type('java.util.ArrayList')
my_list = ArrayList()
print(java.is_symbol(ArrayList))    # prints True
print(java.is_symbol(my_list))      # prints False, my_list is not a Java host symbol
print(java.is_object(ArrayList))    # prints True, symbols are also host objects
print(java.is_function(my_list.add))# prints True, the add method of ArrayList
print(java.instanceof(my_list, ArrayList)) # prints True
```

See the [Polyglot Programming](https://www.graalvm.org/docs/reference-manual/polyglot-programming/) and the [Embed Languages](https://www.graalvm.org/reference-manual/embed-languages/) reference
for more information about interoperability with other programming languages.
