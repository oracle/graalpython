from os import listdir
from os.path import isfile, join, splitext, basename, dirname
from io import StringIO
from tokenize import generate_tokens
import token as Token

inputDir = join(dirname(__file__), "testFiles")
outputDir = join(dirname(__file__), "goldenFiles")

files = [f for f in listdir(inputDir) if isfile(join(inputDir, f))]

for file in files:
    if file.endswith(".data"):
        dataFile = open(join(inputDir, file), 'r')
        lines = dataFile.readlines()

        outputFile = open(join(outputDir, splitext(file)[0] + ".token"), 'w')


        for line in lines:
            line = line.strip()
            if len(line) > 0 and not line.startswith('//'):
                outputFile.write(line + '\n')
                tokens = generate_tokens(StringIO(line).readline)
                for token in tokens:
                    outputFile.write("Token type:%d (%d)" % (token.type, token.type))
                    if token.type == Token.OP:
                        outputFile.write(" exact_type:%d (%d)" % (token.exact_type, token.exact_type))
                    outputFile.write(" start:[%d, %d] end:[%d, %d]" % (token.start + token.end))
                    outputFile.write(" string:'%s'" % (token.string))
                    outputFile.write('\n')
                outputFile.write('\n')
