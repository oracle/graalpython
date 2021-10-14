# PEG Parser for Python Sources

This is early prototype of the PEG parser for Python sources. The prototype
is not finished yet and it is under development. 

## Structure of sources

* python - contains python source needed for generating java parser
** pegen - python peg parser used for parsing grammar and java parser generator 
uses classes from this parser. It's a copy from CPython. Needs to be updated manually.
`TODO - change this` 
** pegjava - contain parser java_generator (written in Python) and the 
grammar. The grammar is taken from Python grammar for parser generated in C.
* src - sources needed for the parser itself. Also there is tokenizer written in java.
* test - tests for the generated parser and tokenizer
* testData - test files for the tokenizer
** testfiles - contains files with extension `.data`, where every line is a test case.
** goldenFiles - there are golden files, that are generated from python. Java tokenizer test then 
checks, whether it has the same results as the python tokenizer. 
** generateGoldenFiles.py - used for generating golden files from the test files

## Development

* Clone appropriate version (3.10 and above) of CPython somewhere and build it.
* Create venv and activate it
* Go to the folder python and here you can generate new version of the java parser
`python mainParserGen.py`. 


