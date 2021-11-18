# PEG Parser for Python Sources

This is early prototype of the PEG parser for Python sources. The prototype
is not finished yet and it is under development.

## Structure of sources

* python - contains python sources needed for generating java parser
    * pegen - python peg parser used for parsing grammar and java parser generator
uses classes from this parser. It's a copy from CPython peg parser. Needs to be updated manually.
`TODO - change this`
    * pegjava - contain parser java_generator (written in Python) and the
grammar. The grammar is taken from Python grammar for generated C parser.
* src/main/java - sources needed for the parser itself. Also there is tokenizer written in java.
* src/test/java - tests for the generated parser and tokenizer
* src/test/resources/tokenizer - test files
    * testfiles - contains files with extension `.data`, where every line is a test case for tokenizer.
    * goldenFiles - golden files, that are generated from python `python generateGoldenFiles.py`. Java tokenizer test then
checks, whether it has the same results as the python tokenizer.
    * generateGoldenFiles.py - used for generating golden files from the test files

## Development

* Clone appropriate version (3.10 and above) of CPython somewhere and build it.
* Create venv and activate it
* Generate new version of the java parser: `python src/main/python/mainParserGen.py`.
* For development you can use NetBeans and then build and run the tests from the IDE
* Or you can build it and test from commandline in the top folder (where pom.xml is located)
    * build: `ant jar`
    * run tests: `ant test`

### Updating the grammar

We have modified the grammar actions and return types to match the Java
code. The git history shows the modifications were done to a CPython grammar
file. To update the grammar, check out the branch `grammar-import`. Get the
latest CPython grammar (in the CPython source `Grammar/python.gram`) and put it
over `src/main/python/pegjava/python.gram` (that's the only file in that
branch). Update also `src/main/python/pegen` from CPython. Then commit to the
python-grammar branch and push that branch. Now go back to master. And do `git
merge grammar-import`. Resolve any conflicts. Now regenerate the Java parser.

##### To see what we changed in the grammar

    git diff grammar-import -- src/main/python/pegjava/python.gram

##### To update the grammar

    git checkout grammar-import
    cp /PATH/TO/CPYTHON/SOURCE/Grammar/python.gram src/main/python/pegjava/python.gram
    git rm -rf src/main/python/pegen
    cp -R /PATH/TO/CPYTHON/SOURCE/Tools/peg_generator/pegen src/main/python/
    git add src/main/python/pegjava/python.gram src/main/python/pegen
    git commit -m "Update Python PEG grammar and generator to version INSERT-SHA1"
    git checkout master
    git merge grammar-import
