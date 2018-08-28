**Thank you for thinking about contributing something to this Python
implementation on Graal!**

## First things first

You will need to sign the [Oracle Contributor
Agreement](http://www.graalvm.org/community/contributors/) for us to be able to
merge your work.

Please also take some time to review our [code of
conduct](http://www.graalvm.org/community/conduct/) for contributors.

## How to contribute?

If you want to make a contribution, it's best to open an issue first about the
area you would like to offer your help with. This implementation is in an early
state and it will make more sense to work on some areas than others, so before
you sink work into a pull request that we won't be able to accept, please talk
to us :)

To get started with development on Python, first make sure you can build at
least the _truffle_ project in the [Graal](https://github.com/oracle/graal)
repository with our build tool [mx](https://github.com/graalvm/mx), as well as
[Sulong](https://github.com/graalvm/sulong).

Once you can build those projects, clone this repository and run

    $ mx build
    $ mx python -c "print(42)"

If this prints "42", then everything went fine and you just built and ran
Python. Note that you don't need GraalVM for this, but then you'll only run
interpreted, which won't give very good performance (but that might be fine for
development).

When you make your changes, you can test them with `mx python`. Additionally,
there are various "gates" that we use on our CI system to check any code that
goes in. You can run all of these gates using `mx python-gate` or just some by
using `mx python-gate --tags [TAG]`. Three interesting tags to run that cover
most things are:

- `python-unittest` - Run the unittests written in Python, including those for the C extension API
- `python-graalvm` - Build a minimal GraalVM bundle with Python only and a native launcher
- `python-license` - Check that all files have the correct copyright headers applied to them
