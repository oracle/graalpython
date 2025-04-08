.PHONY: all
all: hpy.universal

.PHONY: hpy.universal
hpy.universal:
	python3 setup.py build_clib -f build_ext -if

.PHONY: dist-info
dist-info:
	python3 setup.py dist_info

debug:
	HPY_DEBUG_BUILD=1 make all

autogen:
	python3 -m hpy.tools.autogen .

cppcheck-build-dir:
	mkdir -p $(or ${CPPCHECK_BUILD_DIR}, .cppcheck)

.PHONY: cppcheck
cppcheck: cppcheck-build-dir
	# azure pipelines doesn't show stderr, so we write the errors to a file and cat it later :(
	$(eval PYTHON_INC = $(shell python3 -q -c "from sysconfig import get_paths as gp; print(gp()['include'])"))
	$(eval PYTHON_PLATINC = $(shell python3 -q -c "from sysconfig import get_paths as gp; print(gp()['platinclude'])"))
	cppcheck --version
	cppcheck \
		-v \
		--error-exitcode=1 \
		--cppcheck-build-dir=$(or ${CPPCHECK_BUILD_DIR}, .cppcheck) \
		--enable=warning,performance,portability,information,missingInclude \
		--inline-suppr \
		--suppress=syntaxError \
		-I /usr/local/include/ \
		-I /usr/include/ \
		-I ${PYTHON_INC} \
		-I ${PYTHON_PLATINC} \
		-I . \
		-I hpy/devel/include/ \
		-I hpy/devel/include/hpy/ \
		-I hpy/devel/include/hpy/cpython/ \
		-I hpy/devel/include/hpy/universal/ \
		-I hpy/devel/include/hpy/runtime/ \
		-I hpy/universal/src/ \
		-I hpy/debug/src/ \
		-I hpy/debug/src/include \
		-I hpy/trace/src/ \
		-I hpy/trace/src/include \
		--force \
		-D NULL=0 \
		-D HPY_ABI_CPYTHON \
		-D __linux__=1 \
		-D __x86_64__=1 \
		-D __LP64__=1 \
		.

infer:
	python3 setup.py build_ext -if -U NDEBUG | compiledb
	# see commit cd8cd6e for why we need to ignore debug_ctx.c
	@infer --fail-on-issue --compilation-database compile_commands.json --report-blacklist-path-regex "hpy/debug/src/debug_ctx.c"

valgrind_args = --suppressions=hpy/tools/valgrind/python.supp --suppressions=hpy/tools/valgrind/hpy.supp --leak-check=full --show-leak-kinds=definite,indirect --log-file=/tmp/valgrind-output
python_args = -m pytest --valgrind --valgrind-log=/tmp/valgrind-output

.PHONY: valgrind
valgrind:
ifeq ($(HPY_TEST_PORTION),)
	PYTHONMALLOC=malloc valgrind $(valgrind_args) python3 $(python_args) test/
else
	PYTHONMALLOC=malloc valgrind $(valgrind_args) python3 $(python_args) --portion $(HPY_TEST_PORTION) test/
endif

porting-example-tests:
	cd docs/porting-example/steps && python3 setup00.py build_ext -i
	cd docs/porting-example/steps && python3 setup01.py build_ext -i
	cd docs/porting-example/steps && python3 setup02.py build_ext -i
	cd docs/porting-example/steps && python3 setup03.py --hpy-abi=universal build_ext -i
	python3 -m pytest docs/porting-example/steps/ ${TEST_ARGS}

docs-examples-tests:
	cd docs/examples/simple-example  && python3 setup.py --hpy-abi=universal install
	cd docs/examples/mixed-example   && python3 setup.py install
	cd docs/examples/snippets        && python3 setup.py --hpy-abi=universal install
	cd docs/examples/quickstart      && python3 setup.py --hpy-abi=universal install
	cd docs/examples/hpytype-example && python3 setup.py --hpy-abi=universal install
	python3 -m pytest docs/examples/tests.py ${TEST_ARGS}
