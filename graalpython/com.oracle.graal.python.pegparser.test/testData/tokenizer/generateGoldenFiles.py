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
                    end = token.end
                    start = token.start
                    text = token.string
                    if token.type == Token.COMMENT and token.string.startswith("# type: "):
                        # TODO this is hack. The python tokenizer doesn't recognize the TYPE_COMMENTs
                        outputFile.write("Token type:58 (TYPE_COMMENT)")
                        text = text.strip()[len("# type: "): len(text)]
                        start = list(start)
                        start[1] = start[1] + int(token.string.find(text))
                        start = tuple(start)
                        end = list(end)
                        end[1] = start[1] + len(text)
                        end = tuple(end)
                    else: 
                        if token.type == Token.NEWLINE:
                            # I'm not sure that this is the right fix. But at least for now it reflects changes in tokenizer. 
                            # On the other hadn this script is now limited only for one line tests
                            start = (2, -1)
                            end = (2, 0)
                        elif token.type == Token.ENDMARKER:
                            start = (3, 0)
                            end = (3, 0)
                        outputFile.write("Token type:%d (%s)" % (token.type, Token.tok_name[token.type]))
                        if token.type == Token.OP:
                            outputFile.write(" exact_type:%d (%s)" % (token.exact_type, Token.tok_name[token.exact_type]))
                    
                    outputFile.write(" start:[%d, %d] end:[%d, %d]" % (start + end))
                    outputFile.write(" string:'%s'" % (text))
                    outputFile.write('\n')
                outputFile.write('\n')
