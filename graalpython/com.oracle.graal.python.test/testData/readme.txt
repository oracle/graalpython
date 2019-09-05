The folders here contains golden files and test file that are used for testing 
parser.

There are two kinds of files. `.scope` which contains print of the scope tree, 
which is generated during first phase of parsing and `.tast` files, which 
contains truffle AST. For one test can be generated one `.scope` file and one 
`.tast` file

Folder `goldenFiles` contains golden files for tests, where the parsed source 
for the test is a string. The structure is that the folder is class name, where 
the test is and the name of file is to the of the test. So for example:

public class AssignmentTests extends ParserTestBase {

    @Test
    public void assignment01() throws Exception {
        checkTreeResult("a = 1");
    }

}

creates file assignment01.tast (if not exists yet) in folder `AssignmentTests` 
in `goldenFiles` folder.

The `testFiles` folder has the same structure, but also can contains tested 
`.py` file. The name of the test is the name of file with `.py` extension 
that is tested.

For example:

public class RuntimeFileTests extends ParserTestBase {

    @Test
    public void _collections_abc() throws Exception {
        checkScopeAndTree();
    }

}

This test is looking for file `testFiles/RuntimeFileTests/_collections_abc.py` 
and after first run are created `testFiles/RuntimeFileTests/_collections_abc.scope` 
and `testFiles/RuntimeFileTests/_collections_abc.tast` golden files.

As is mentioned the first run of these test generate the golden file for testing 
source. The golden file is generated with the old parser and compares the result 
from new parser. If the old parser generate wrong tree, then it should be 
manually corrected or reused the output from new parser. 


If there is a change in a tree, that causes failing many tests, then it can be 
done by deleting all `.tast` files, then run parser tests and check, whether 
the diffs are correct.

These commands should do it:

--------------------------------------------------

$ cd graalpython/com.oracle.graal.python.test/testData

$ find . -name "*.tast" -delete

$ mx unittest BasicTests FunctionDefFromCPTests FuncDefTests ExpressionsFromCPTests LambdaInFunctionTests AssignmentTests ImportTests ListAndSlicingTests DictAndSetTests AwaitAndAsyncTests ClassDefTests GeneratorAndCompForTests YieldStatementTests RuntimeFileTests

--------------------------------------------------

After this all `.tast` files are regenerated. Currently after the regeneration 
there are two test failing `DictAndSetTests.dict09` and `DictAndSetTests.dict10`. 
It's because the old parser generated the tree in wrong way. These are small tests 
and should be enough just the revert the modifications and provide needed changes 
manually if there are necessary.

The same aproach can be used for `.scope` files.


