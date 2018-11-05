suite = {
    # --------------------------------------------------------------------------------------------------------------
    #
    #  METADATA
    #
    # --------------------------------------------------------------------------------------------------------------
    "mxversion": "5.190.1",
    "name": "graalpython",
    "versionConflictResolution": "latest",

    # --------------------------------------------------------------------------------------------------------------
    #
    #  DEPENDENCIES
    #
    # --------------------------------------------------------------------------------------------------------------
    "imports": {
        "suites": [
            {
                "name": "truffle",
                "versionFrom": "sulong",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
            {
                "name": "sulong",
                "version": "3a372fe20c04493e5cac31ed8f6e89f50ad959b1",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
            {
                "name": "regex",
                "version": "3a372fe20c04493e5cac31ed8f6e89f50ad959b1",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
        ],
    },

    # --------------------------------------------------------------------------------------------------------------
    #
    #  REPOS
    #
    # --------------------------------------------------------------------------------------------------------------
    "repositories": {
        "python-public-snapshots": {
            "url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots",
            "licenses": ["GPLv2-CPE", "UPL", "BSD-new", "MIT"]
        },
        "python-local-snapshots": {
            "url": "http://localhost",
            "licenses": ["GPLv2-CPE", "UPL", "BSD-new", "MIT"]
        },
    },

    "defaultLicense": "UPL",

    # --------------------------------------------------------------------------------------------------------------
    #
    #  LIBRARIES
    #
    # --------------------------------------------------------------------------------------------------------------
    "libraries": {
    },

    # --------------------------------------------------------------------------------------------------------------
    #
    #  PROJECTS
    #
    # --------------------------------------------------------------------------------------------------------------
    "externalProjects": {
        "lib.python": {
            "type": "python",
            "path": 'graalpython/lib-python',
            "source": [
                "3"
            ]
        },

        "lib.graalpython": {
            "type": "python",
            "path": 'graalpython/lib-graalpython',
            "source": []
        },

        "perf.benchmarks": {
            "type": "python",
            "path": 'graalpython/benchmarks',
            "source": [
                "src"
            ]
        },

        "util.scripts": {
            "type": "python",
            "path": 'scripts',
            "source": []
        },

        "com.oracle.graal.python.cext": {
            "type": "python",
            "path": "graalpython/com.oracle.graal.python.cext",
            "source": [
                "include",
                "src",
                "modules"
            ]
        },
    },


    "projects": {
        # GRAALPYTHON ANTLR
        "com.oracle.graal.python.parser.antlr": {
            "subDir": "graalpython",
            "buildEnv": {
                "ANTLR_JAR": "<path:truffle:ANTLR4_COMPLETE>",
                "PARSER_PATH": "<src_dir:com.oracle.graal.python>/com/oracle/graal/python/parser/antlr",
                "OUTPUT_PATH": "<src_dir:com.oracle.graal.python>/com/oracle/graal/python/parser/antlr",
                "PARSER_PKG": "com.oracle.graal.python.parser.antlr",
                "POSTPROCESSOR": "<suite:graalpython>/graalpython/com.oracle.graal.python.parser.antlr/postprocess.py",
            },
            "dependencies": [
                "truffle:ANTLR4_COMPLETE",
            ],
            "native": True,
            "vpath": True,
        },

        "com.oracle.graal.python.shell": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "javaCompliance": "1.8",
        },

        # GRAALPYTHON
        "com.oracle.graal.python": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
                "sdk:GRAAL_SDK",
                "truffle:ANTLR4",
                "regex:TREGEX",
            ],
            "buildDependencies": ["com.oracle.graal.python.parser.antlr"],
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "1.8",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "Truffle,Python",
        },

        # GRAALPYTHON TEST
        "com.oracle.graal.python.test": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.graal.python.shell",
                "com.oracle.graal.python",
                "truffle:TRUFFLE_TCK",
                "mx:JUNIT"
            ],
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "1.8",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "Truffle,Python",
        },

        "com.oracle.graal.python.tck": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:POLYGLOT_TCK",
                "mx:JUNIT"
            ],
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "1.8",
            "workingSets": "Truffle,Python",
        },


        "com.oracle.graal.python.cext": {
            "subDir": "graalpython",
            "native": True,
            "vpath": True,
            "results" : ["graalpython/lib-graalpython"],
            "output" : ".",
            "buildDependencies": [
                "sulong:SULONG_LIBS",
            ],
            "buildEnv": {
                "POLYGLOT_INC": "<path:SULONG_LIBS>",
            },
        },

        "python-lib": {
            "class": "ArchiveProject",
            "outputDir": "graalpython/lib-python/3",
            "prefix": "lib-python/3",
            "ignorePatterns": [
                "\/test\/",
                "\/tests\/",
                "\/idle_test\/",
            ],
            "license": ["PSF-License"],
        },

        "site-packages": {
            "subDir": "graalpython",
            "native": True,
            "vpath": False,
            "results": ["graalpython/lib-python/3/site-packages"],
            "output": ".",
            "license": ["MIT"],
            "defaultBuild" : False,
        },
    },

    "licenses": {
        "PSF-License": {
            "name": "Python Software Foundation License",
            "url": "https://docs.python.org/3/license.html",
        },
        "UPL" : {
            "name" : "Universal Permissive License, Version 1.0",
            "url" : "http://opensource.org/licenses/UPL",
        },
    },

    # --------------------------------------------------------------------------------------------------------------
    #
    #  DISTRIBUTIONS
    #
    # --------------------------------------------------------------------------------------------------------------
    "distributions": {
        "GRAALPYTHON-LAUNCHER": {
            "dependencies": [
                "com.oracle.graal.python.shell",
            ],
            "distDependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
        },

        "GRAALPYTHON-ENV": {
        },

        "GRAALPYTHON-ZIP": {
        },

        "GRAALPYTHON": {
            "dependencies": [
                "com.oracle.graal.python",
            ],
            "distDependencies": [
                "GRAALPYTHON-LAUNCHER",
                "truffle:TRUFFLE_API",
                "regex:TREGEX",
                "sdk:GRAAL_SDK",
                "truffle:ANTLR4",
            ],
            "sourcesPath": "graalpython.src.zip",
        },

        "GRAALPYTHON_PYTHON_LIB" : {
            "dependencies" : ["python-lib"],
            "description" : "Python 3 lib files",
        },

        "GRAALPYTHON_UNIT_TESTS": {
            "description": "unit tests",
            "dependencies": [
                "com.oracle.graal.python.test",
            ],
            "exclude": ["mx:JUNIT"],
            "distDependencies": [
                "GRAALPYTHON",
                "GRAALPYTHON-LAUNCHER",
                "truffle:TRUFFLE_TCK",
            ],
            "sourcesPath": "graalpython.tests.src.zip",
        },

        "GRAALPYTHON_TCK": {
            "description": "unit tests",
            "dependencies": [
                "com.oracle.graal.python.tck",
            ],
            "exclude": ["mx:JUNIT"],
            "distDependencies": [
                "sdk:POLYGLOT_TCK",
            ],
            "sourcesPath": "graalpython.tests.src.zip",
        },

        "GRAALPYTHON_GRAALVM_SUPPORT" : {
            "native" : True,
            "platformDependent" : True,
            "description" : "Graal.Python support distribution for the GraalVM",
            "layout" : {
                "./" : [
                    "dependency:com.oracle.graal.python.cext/graalpython/lib-graalpython",
                    "file:graalpython/com.oracle.graal.python.cext/include",
                    "extracted-dependency:graalpython:GRAALPYTHON_PYTHON_LIB",
                    "file:mx.graalpython/native-image.properties",
                ],
                "LICENSE_GRAALPYTHON" : "file:LICENSE",
                "3rd_party_licenses_graalpython.txt" : "file:3rd_party_licenses.txt",
            }
        },

        "GRAALPYTHON_GRAALVM_DOCS" : {
            "native" : True,
            "description" : "Graal.Python documentation files for the GraalVM",
            "layout" : {
                "README_GRAALPYTHON.md" : "file:README.md",
                "./" : "file:doc",
            },
        },
    },
}
