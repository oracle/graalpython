# pylint: disable=anomalous-backslash-in-string
suite = {
    # --------------------------------------------------------------------------------------------------------------
    #
    #  METADATA
    #
    # --------------------------------------------------------------------------------------------------------------
    "mxversion": "5.225.2",
    "name": "graalpython",
    "versionConflictResolution": "latest",

    "version": "19.3.0",
    "release": False,
    "groupId": "org.graalvm.graalpython",
    "url": "http://www.graalvm.org/",

    "developer": {
        "name": "Truffle and Graal developers",
        "email": "graalvm-users@oss.oracle.com",
        "organization": "Graal",
        "organizationUrl": "http://www.graalvm.org/",
    },

    "scm": {
        "url": "https://github.com/graalvm/graalpython",
        "read": "https://github.com/graalvm/graalpython.git",
        "write": "git@github.com:graalvm/graalpython.git",
    },

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
                "version": "608db40a4c9e14093d6d8135bfbb3982d58147e0",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
            {
                "name": "regex",
                "version": "608db40a4c9e14093d6d8135bfbb3982d58147e0",
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
        "SETUPTOOLS": {
            "urls": [
                "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/setuptools-40.6.3.zip",
            ],
            "sha1": "7a5960b8062ddbf0c0e79f806e23785d55fec3c8",
        },
        "XZ-1.8": {
            "sha1": "c4f7d054303948eb6a4066194253886c8af07128",
            "maven": {
                "groupId": "org.tukaani",
                "artifactId": "xz",
                "version": "1.8",
            },
        },

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
            ],
        },

        "lib.graalpython": {
            "type": "python",
            "path": 'graalpython/lib-graalpython',
            "source": [],
        },

        "perf.benchmarks": {
            "type": "python",
            "path": 'graalpython/benchmarks',
            "source": [
                "src"
            ],
        },

        "util.scripts": {
            "type": "python",
            "path": 'scripts',
            "source": [],
        },

        "com.oracle.graal.python.cext": {
            "type": "python",
            "path": "graalpython/com.oracle.graal.python.cext",
            "source": [
                "include",
                "src",
                "modules"
            ],
        },

        "ci": {
            "type": "python",
            "path": "ci_common",
            "source": [],
        },
    },

    "projects": {
        # GRAALPYTHON ANTLR
        "com.oracle.graal.python.parser.antlr": {
            "subDir": "graalpython",
            "buildEnv": {
                "ANTLR_JAR": "<path:truffle:ANTLR4_COMPLETE>",
                "PARSER_PATH": "<src_dir:com.oracle.graal.python>/com/oracle/graal/python/parser/antlr",
                "PARSER_PKG": "com.oracle.graal.python.parser.antlr",
                "POSTPROCESSOR": "<suite:graalpython>/graalpython/com.oracle.graal.python.parser.antlr/postprocess.py",
            },
            "dependencies": [
                "truffle:ANTLR4_COMPLETE",
            ],
            "jacoco": "include",
            "native": True,
            "vpath": False,
        },

        "com.oracle.graal.python.shell": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "jacoco": "include",
            "javaCompliance": "8+",
            "checkstyle": "com.oracle.graal.python",
        },

        # GRAALPYTHON
        "com.oracle.graal.python": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
                "sdk:GRAAL_SDK",
                "truffle:ANTLR4",
                "sulong:SULONG_API",
                "XZ-1.8",
            ],
            "buildDependencies": ["com.oracle.graal.python.parser.antlr"],
            "jacoco": "include",
            "javaCompliance": "8+",
            "checkstyleVersion": "8.8",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "Truffle,Python",
            "spotbugsIgnoresGenerated": True,
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
            "jacoco": "exclude",
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "8+",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "Truffle,Python",
            "testProject": True,
        },

        "com.oracle.graal.python.tck": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:POLYGLOT_TCK",
                "mx:JUNIT"
            ],
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "8+",
            "workingSets": "Truffle,Python",
            "testProject": True,
        },

        "com.oracle.graal.python.cext": {
            "subDir": "graalpython",
            "native": True,
            "vpath": False,
            "results": ["graalpython/include"],
            "output": ".",
            "buildDependencies": [
                "sulong:SULONG_HOME",
                "sulong:SULONG_LEGACY",
                "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
            ],
            "buildEnv": {
                "TRUFFLE_H_INC": "<path:SULONG_LEGACY>/include",
                "ARCH": "<arch>",
                "OS": "<os>",
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
    },

    "licenses": {
        "PSF-License": {
            "name": "Python Software Foundation License",
            "url": "https://docs.python.org/3/license.html",
        },
        "UPL": {
            "name": "Universal Permissive License, Version 1.0",
            "url": "http://opensource.org/licenses/UPL",
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
                "PYTHON_USERBASE",
            ],
            "description": "GraalPython launcher",
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
                "sulong:SULONG",
            ],
            "sourcesPath": "graalpython.src.zip",
            "description": "GraalPython engine",
        },

        "GRAALPYTHON_PYTHON_LIB": {
            "dependencies": ["python-lib"],
            "description": "Python 3 lib files",
            "maven": False,
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
            "testDistribution": True,
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
            "testDistribution": True,
        },

        "GRAALPYTHON_GRAALVM_SUPPORT": {
            "native": True,
            "platformDependent": True,
            "description": "Graal.Python support distribution for the GraalVM",
            "layout": {
                "./": [
                    "dependency:com.oracle.graal.python.cext/graalpython/include",
                    "extracted-dependency:graalpython:GRAALPYTHON_PYTHON_LIB",
                    "file:mx.graalpython/native-image.properties",
                    "file:graalpython/lib-graalpython",
                ],
                "./lib-graalpython/capi/": [
                    "file:graalpython/com.oracle.graal.python.cext/setup.py",
                    "file:graalpython/com.oracle.graal.python.cext/src",
                    "file:graalpython/com.oracle.graal.python.cext/modules",
                ],
            },
            "maven": False,
        },

        "GRAALPYTHON_GRAALVM_DOCS": {
            "native": True,
            "description": "Graal.Python documentation files for the GraalVM",
            "layout": {
                "README_GRAALPYTHON.md": "file:README.md",
                "./": "file:doc",
            },
            "maven": False,
        },

        "GRAALPYTHON_GRAALVM_LICENSES": {
            "native": True,
            "platformDependent": True,
            "description": "Graal.Python support distribution for the GraalVM license files",
            "layout": {
                "LICENSE_GRAALPYTHON.txt": "file:LICENSE",
                "3rd_party_licenses_graalpython.txt": "file:3rd_party_licenses.txt",
            },
            "maven": False,
        },

        "PYTHON_USERBASE": {
            "native": True,
            "platformDependent": False,
            "description": "Blub",
            "layout": {
                "./README.md": "string:User base directory for development purpose.",
            },
            "maven": False,
        },
    },
}
