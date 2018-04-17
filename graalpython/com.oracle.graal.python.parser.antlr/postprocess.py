import sys
import re


COPYRIGHT_HEADER = """\
/*
 * Copyright (c) 2017-2018, Oracle and/or its affiliates.
 * Copyright (c) 2014 by Bart Kiers
 *
 * The MIT License (MIT)
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
// Checkstyle: stop
//@formatter:off
{0}\
"""

PTRN_SUPPRESS_WARNINGS = re.compile(r"@SuppressWarnings.*")
PTRN_GENBY = re.compile(r"Generated from (?P<path>.*)/(?P<grammar>.*.g4)")


def replace_suppress_warnings(line):
	return PTRN_SUPPRESS_WARNINGS.sub('@SuppressWarnings("all")', line)


def replace_rulectx(line):
	return line.replace("(RuleContext)_localctx", "_localctx")


def replace_genby(line):
	return PTRN_GENBY.sub("Generated from \g<grammar>", line)


TRANSFORMS = [
	replace_suppress_warnings,
	replace_rulectx,
	replace_genby,
]


def postprocess(file):
	lines = []
	for line in file:
		for transform in TRANSFORMS:
			if hasattr(transform, '__call__'):
				line = transform(line)
		lines.append(line)
	return ''.join(lines)


if __name__ == '__main__':
	fpath = sys.argv[1]
	print("postprocessing {}".format(fpath))
	with open(fpath, 'r') as FILE:
		content = COPYRIGHT_HEADER.format(postprocess(FILE))
	with open(fpath, 'w+') as FILE:
		FILE.write(content)