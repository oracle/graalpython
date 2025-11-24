# pylint: disable=anomalous-backslash-in-string,line-too-long
suite = {
    # --------------------------------------------------------------------------------------------------------------
    #
    #  METADATA
    #
    # --------------------------------------------------------------------------------------------------------------
    "mxversion": "7.67.0",
    "name": "graalpython",
    "versionConflictResolution": "latest",

    "version": "25.1.0",
    "graalpython:pythonVersion": "3.12.8",
    "release": False,
    "groupId": "org.graalvm.python",
    "url": "http://www.graalvm.org/python",

    "developer": {
        "name": "GraalVM Development",
        "email": "graalvm-dev@oss.oracle.com",
        "organization": "Oracle Corporation",
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
                "versionFrom": "regex",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
            {
                "name": "sdk",
                "versionFrom": "truffle",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
            {
                "name": "tools",
                "version": "ac921cc439fc79d05d9d1cb55e436de3e855178b",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ],
            },
            {
                "name": "regex",
                "version": "ac921cc439fc79d05d9d1cb55e436de3e855178b",
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
            "licenses": ["GPLv2-CPE", "UPL", "BSD-new", "MIT", "PSF-License"]
        },
        "python-local-snapshots": {
            "url": "http://localhost",
            "licenses": ["GPLv2-CPE", "UPL", "BSD-new", "MIT", "PSF-License"]
        },
    },

    "defaultLicense": "UPL",

    "capture_suite_commit_info": True,

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
            "digest": "sha512:16920fd41f398696c563417049472c0d81abb2d293ecb45bbbe97c12651669833e34eac238e2e4a6f8761ea58fb39806425d2741e88e8c3097fe2b5457ebf488",
        },
        "XZ-5.6.2": {
            "urls": [
                "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/xz-5.6.2.tar.gz",
            ],
            "packedResource": True,
            "digest": "sha512:c32c32c95e3541b906e0284e66a953ace677e0ce6af2084e7b122600047bf7542c1b0fabb5909b19ff79fba6def530be674df1c675b22a47a8d57f3f0b736a82",
        },
        "BOUNCYCASTLE-PROVIDER": {
            "digest": "sha512:fb10c3c089921c8173ad285329f730e0e78de175d1b50b9bdd79c6a85a265af9b3331caa0c1ed57e5f47047319ce3b0f3bb5def0a3db9cccf2755cc95e145e52",
            "sourceDigest": "sha512:7b06374b75040a1dba9419e17be29a155f01b14961521adcb8e980397b6ac7e2de55958e74ad41ba94766c4e992935abbd94fb964dbf806445a63a7346c0ae2e",
            "maven": {
              "groupId": "org.bouncycastle",
              "artifactId": "bcprov-jdk18on",
              "version": "1.78.1",
            },
            "moduleName": "org.bouncycastle.provider",
        },
        "BOUNCYCASTLE-PKIX": {
            "digest": "sha512:d71a45844a7946b6a70315254e82a335d2df5e402b2d5a3b496fa69b355184338011b49c5f1c76026764a76f62f2bc140c25db2881bca91dde9677a25c6d587b",
            "sourceDigest": "sha512:8508e9b26c60cc2fd3219d8ab0d3928891ecc42926e7c862c0fbf9940a4bcffe35c4a76c3934b33ed4311817dbf3b0b50068482f7c5f550261a50cc97879923a",
            "maven": {
                "groupId": "org.bouncycastle",
                "artifactId": "bcpkix-jdk18on",
                "version": "1.78.1",
            },
            "moduleName": "org.bouncycastle.pkix",
        },
        "BOUNCYCASTLE-UTIL": {
            "digest": "sha512:6a338c50d662993c9f00bba23f98443c923b9a95ff61dc653906f51857f8afaecc57a536bfaf6848ac8e7e9ce0a21f84ec068815853261268f97e951526bc766",
            "sourceDigest": "sha512:852a1679a9c690f97c4ed175272b04ebedc89b9e4aa0322f32a799f619fd71602f89545fc02bb1093750ad7d796500fdd116203862ccecb3085af40aadcccea6",
            "maven": {
                "groupId": "org.bouncycastle",
                "artifactId": "bcutil-jdk18on",
                "version": "1.78.1",
            },
            "moduleName": "org.bouncycastle.util",
        },
        "BZIP2": {
            "urls": [
                "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/graalpython/bzip2-1.0.8.tar.gz",
            ],
            "packedResource": True,
            "digest": "sha512:083f5e675d73f3233c7930ebe20425a533feedeaaa9d8cc86831312a6581cefbe6ed0d08d2fa89be81082f2a5abdabca8b3c080bf97218a1bd59dc118a30b9f3",
        },
        "NETBEANS-LIB-PROFILER": {
            "moduleName": "org.netbeans.modules.org-netbeans-lib-profiler",
            "digest": "sha512:1de81a0340c0266b41ba774600346ac977910663016a0afa22859cf1eb9d9e507de4f66e3f51d5bd9575b1d1f083765ecb9b30c4d43adb201f68b83257e8a17d",
            "sourceDigest": "sha512:92c50b8832e3a9afc93f9eaacdfc79cdf2487a74a9f5cf93c54bed50e904ef70ac6504018558d7183f074132c37fe57b21bc1e662a71c74c4201b75cdc5f8947",
            "maven": {
              "groupId": "org.netbeans.modules",
              "artifactId": "org-netbeans-lib-profiler",
              "version": "RELEASE120-1",
            },
        },
        "GRAALPYTHON_PYFLATE_BENCHMARK_RESOURCE" : {
            # just any reasonably sized .tar.gz or .tar.bz2 for running the benchmark
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-944-linux-amd64.tar.gz"],
            "digest" : "sha512:72982ca01cce9dfa876687ec7b9627b81e241e6cddc8dedb976a5d06d058a067f83f5c063dc07d7ed19730ffb54af8343eae8ca0cc156353f7b18530eef73c50"
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

        "util.scripts": {
            "type": "python",
            "path": 'scripts',
            "source": [],
        },

        "docs.user": {
            "type": "python",
            "path": 'docs/user',
            "source": [],
        },

        "docs.contributor": {
            "type": "python",
            "path": 'docs/contributor',
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

        "python-liblzma": {
            "type": "python",
            "path": "graalpython/python-liblzma",
            "source": [
                "src",
            ],
        },

        "com.oracle.graal.python.frozen": {
            "type": "python",
            "path": "graalpython/com.oracle.graal.python.frozen",
        },

        "com.oracle.graal.python.pegparser.generator": {
            "type": "python",
            "path": "graalpython/com.oracle.graal.python.pegparser.generator",
            "source": [],
        },

        "graalpy-virtualenv-seeder": {
            "type": "python",
            "path": "graalpy_virtualenv_seeder",
            "source": [],
        }
    },

    "projects": {
        "com.oracle.graal.python.pegparser": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "javaCompliance": "17+",
            "dependencies": [
                "truffle:TRUFFLE_ICU4J",
            ],
            "buildDependencies": [
                "com.oracle.graal.python.pegparser.generator",
            ],
            "checkstyle": "com.oracle.graal.python",
        },

        "com.oracle.graal.python.pegparser.test": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "testProject": True,
            "javaCompliance": "17+",
            "dependencies": [
                "com.oracle.graal.python.pegparser",
                "mx:JUNIT",
            ],
        },

        "com.oracle.graal.python.pegparser.generator": {
            "subDir": "graalpython",
            "class": "CMakeNinjaProject",
            "ninja_targets": [
                "all",
            ],
            "results": [
                "Parser.java",
                "Python.asdl.stamp",
            ]
        },

        "com.oracle.graal.python.shell": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:POLYGLOT",
                "sdk:LAUNCHER_COMMON",
                "sdk:JLINE3",
                "sdk:MAVEN_DOWNLOADER",
            ],
            "requires": [
                "java.management",
                "java.xml",
            ],
            "jacoco": "include",
            "javaCompliance": "17+",
            "checkstyle": "com.oracle.graal.python",
        },

        "com.oracle.graal.python.annotations": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "jacoco": "include",
            "javaCompliance": "17+",
            "checkstyle": "com.oracle.graal.python",
        },

        # GRAALPYTHON-PROCESSOR
        "com.oracle.graal.python.processor": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.graal.python.annotations"
            ],
            "requires": [
                "java.compiler",
                "jdk.compiler",
            ],
            "jacoco": "exclude",
            "javaCompliance": "17+",
            "checkstyle": "com.oracle.graal.python",
        },

        # FROZEN MODULES
        "com.oracle.graal.python.frozen": {
            "subDir": "graalpython",
            "vpath": True,
            "type": "GraalpythonFrozenProject",
            "args": [
                "<path:com.oracle.graal.python.frozen>/freeze_modules.py",
                "--python-lib",
                "<suite:graalpython>/graalpython/lib-python/3",
                "--binary-dir",
                "<output_root:com.oracle.graal.python.frozen>/com/oracle/graal/python/builtins/objects/module/",
                "--sources-dir",
                "<path:com.oracle.graal.python>/src/com/oracle/graal/python/builtins/objects/module/",
            ],
            "platformDependent": False,
            "buildDependencies": [
                "com.oracle.graal.python",
                "GRAALPYTHON-LAUNCHER",
                "regex:TREGEX",
            ],
        },

        # HPy
        "hpy": {
            "subDir": "graalpython",
            "vpath": True,
            "type": "GraalpythonProject",
            "defaultBuild": False,
            "args": [
                "<path:hpy>/build.py",
                "--out",
                "<output_root:hpy>",
                "--cflags",
                "-I<output_root:com.oracle.graal.python>/jni_gen -I<output_root:graalpy-pyconfig>/<os>-<arch>/<multitarget_libc_selection>/ -I<path:com.oracle.graal.python.cext>/include",
            ],
            "platformDependent": True,
            "buildDependencies": [
                "com.oracle.graal.python",
                "GRAALPYTHON-LAUNCHER",
                "regex:TREGEX",
            ],
        },

        "com.oracle.graal.python.resources": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
            ],
            "jacoco": "include",
            "javaCompliance": "17+",
            "checkstyle": "com.oracle.graal.python",
            "annotationProcessors": [
                "truffle:TRUFFLE_DSL_PROCESSOR"
            ],
            "workingSets": "Truffle,Python",
            "spotbugsIgnoresGenerated": True,
        },

        # GRAALPYTHON
        "com.oracle.graal.python": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "jniHeaders": True,
            "dependencies": [
                "com.oracle.graal.python.annotations",
                "com.oracle.graal.python.pegparser",
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
                "tools:TRUFFLE_PROFILER",
                "sdk:POLYGLOT",
                "truffle:TRUFFLE_XZ",
                "truffle:TRUFFLE_ICU4J",
                "regex:TREGEX",
                "BOUNCYCASTLE-PROVIDER",
                "BOUNCYCASTLE-PKIX",
                "BOUNCYCASTLE-UTIL",
            ],
            "requires": [
                "java.logging",
                "java.management",
                "jdk.management",
                "jdk.unsupported",
                "jdk.security.auth",
            ],
            "jacoco": "include",
            "javaCompliance": "17+",
            "checkstyleVersion": "10.7.0",
            "annotationProcessors": [
                "GRAALPYTHON_PROCESSOR",
                "truffle:TRUFFLE_DSL_PROCESSOR"
            ],
            "forceJavac": True, # GRAALPYTHON_PROCESSOR is not compatible with ECJ
            "workingSets": "Truffle,Python",
            "spotbugsIgnoresGenerated": True,
        },

        # GRAALPYTHON_UNIT_TESTS
        "com.oracle.graal.python.test": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.graal.python.shell",
                "com.oracle.graal.python",
                "com.oracle.graal.python.test.integration",
                "truffle:TRUFFLE_TCK",
                "mx:JUNIT",
                "NETBEANS-LIB-PROFILER",
            ],
            "requires": [
                "java.management",
                "jdk.management",
                "jdk.unsupported",
                "java.logging",
            ],
            "jacoco": "exclude",
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "17+",
            "annotationProcessors": [
                "GRAALPYTHON_PROCESSOR",
                "truffle:TRUFFLE_DSL_PROCESSOR"
            ],
            "workingSets": "Truffle,Python",
            "testProject": True,
            "javaProperties": {
                # This is used to discover some files needed for the tests that
                # normally live in GraalPython source tree
                "test.graalpython.home": "<suite:graalpython>/graalpython"
            },
        },

        # GRAALPYTHON_INTEGRATION_UNIT_TESTS
        "com.oracle.graal.python.test.integration": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "mx:JUNIT",
                "sdk:GRAAL_SDK",
            ],
            "requires": [
                "java.management",
                "jdk.management",
                "jdk.unsupported",
                "java.logging",
            ],
            "jacoco": "exclude",
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "17+",
            "workingSets": "Truffle,Python",
            "testProject": True,
        },

        # GRAALPYTHON BENCH
        "com.oracle.graal.python.benchmarks": {
            "subDir": "graalpython",
            "sourceDirs": ["java"],
            "dependencies": [
                "com.oracle.graal.python",
                "sdk:POLYGLOT",
                "sdk:LAUNCHER_COMMON",
                "mx:JMH_1_21"
            ],
            "requires": [
                "java.logging",
            ],
            "jacoco": "exclude",
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "17+",
            "annotationProcessors": ["mx:JMH_1_21"],
            "workingSets": "Truffle,Python",
            "spotbugsIgnoresGenerated": True,
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
            "javaCompliance": "17+",
            "workingSets": "Truffle,Python",
            "jacoco": "exclude",
        },

        "python-venvlauncher": {
            "subDir": "graalpython",
            "native":  "executable",
            "deliverable": "venvlauncher",
            "os_arch": {
                "windows": {
                    "<others>": {
                        "defaultBuild": True,
                    },
                },
                "<others>": {
                    "<others>": {
                        "defaultBuild": False,
                    },
                },
            },
        },

        "python-macos-launcher": {
            "subDir": "graalpython",
            "native":  "executable",
            "deliverable": "macos-venvlauncher",
            "os_arch": {
                "darwin": {
                    "<others>": {
                        "defaultBuild": True,
                    },
                },
                "<others>": {
                    "<others>": {
                        "defaultBuild": False,
                    },
                },
            },
        },

        "python-libbz2": {
            "subDir": "graalpython",
            "class": "CMakeNinjaProject",
            "max_jobs": "4",
            "vpath": True,
            "ninja_targets": ["<lib:bz2support>"],
            "ninja_install_targets": ["install"],
            "results": [
                "bin/<lib:bz2support>",
            ],
            "cmakeConfig": {
                "CMAKE_BUILD_TYPE": "Release",
                "BZIP2_ROOT": "<path:BZIP2>",
                "BZIP2_VERSION_MAJOR": "1",
                "BZIP2_VERSION_MINOR": "0",
                "BZIP2_VERSION_PATCH": "8",
            },
            "os_arch": {
                "windows": {
                    "<others>": {
                        "defaultBuild": False,
                        "multitarget": {
                            "libc": ["default"],
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "defaultBuild": True,
                        "multitarget": [
                            {"libc": ["glibc", "default"]},
                            {"libc": ["musl"], "variant": ["swcfi"]},
                        ],
                    },
                },
            },
            "buildDependencies": [
                "BZIP2",
            ],
            "buildEnv": {
                "ARCH": "<arch>",
                "OS": "<os>",
            },
        },

        "python-liblzma": {
            "subDir": "graalpython",
            "class": "CMakeNinjaProject",
            "max_jobs": "8",
            "vpath": True,
            "ninja_targets": ["<lib:lzmasupport>"],
            "ninja_install_targets": ["install"],
            "results": [
                "bin/<lib:lzmasupport>",
            ],
            "cmakeConfig": {
                "XZ_SRC": "<path:XZ-5.6.2>",
                "XZ_VERSION_MAJOR": "5",
                "XZ_VERSION_MINOR": "6",
                "XZ_VERSION_PATCH": "2",
            },
            "os_arch": {
                "windows": {
                    "<others>": {
                        "defaultBuild": False,
                        "multitarget": {
                            "libc": ["default"],
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "defaultBuild": True,
                        "multitarget": [
                            {"libc": ["glibc", "default"]},
                            {"libc": ["musl"], "variant": ["swcfi"]},
                        ],
                    },
                },
            },
            "buildDependencies": [
                "XZ-5.6.2",
            ],
        },

        "graalpy-versions": {
            "subDir": "graalpython",
            "class": "CMakeNinjaProject",
            "max_jobs": "1",
            "ninja_targets": ["all"],
            "cmakeConfig": {
                "GRAALPY_VER": "<py_ver:binary><graal_ver:binary><release_level:binary><dev_tag:none>",
            },
            "results": [
                "graalpy_versions"
            ],
        },

        "graalpy-pyconfig": {
            "subDir": "graalpython",
            "class": "CMakeNinjaProject",
            "multitarget": [
                {"libc": ["glibc", "default"]},
                {"libc": ["musl"], "variant": ["swcfi"]},
            ],
            "max_jobs": "1",
            "ninja_targets": ["all"],
            "cmakeConfig": {
                "GRAALPY_VERSION": "<graal_ver:default>",
                "GRAALPY_VERSION_NUM": "<graal_ver:hex>",
            },
            "results": [
                "pyconfig.h",
            ],
        },

        "com.oracle.graal.python.cext": {
            "subDir": "graalpython",
            "class": "CMakeNinjaProject",
            "max_jobs": "8",
            "vpath": True,
            "use_jdk_headers": True, # not actually, just making mx happy
            "ninja_targets": ["all"],
            "ninja_install_targets": ["install"],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "multitarget": {
                            "libc": ["default"],
                        },
                        "cmakeConfig": {
                            "CAPI_INC_DIR": "<output_root:com.oracle.graal.python>/jni_gen",
                            "PYCONFIG_INCLUDE_DIR": "<output_root:graalpy-pyconfig>/<os>-<arch>/<multitarget_libc_selection>",
                            "TRUFFLE_NFI_H_INC": "<path:com.oracle.truffle.nfi.native>/include",
                            "GRAALPY_PARENT_DIR": "<suite_parent:graalpython>",
                            "GRAALPY_EXT": "<graalpy_ext>",
                            "LIBFFI_DIST": "<output_root:truffle:LIBFFI_DIST>/<os>-<arch>/<multitarget_libc_selection>/"
                        },
                        "results": [
                            "bin/<lib:python-native>",
                            "bin/python-native.lib",
                            "bin/modules/_sqlite3<graalpy_ext>",
                            "bin/modules/_cpython_sre<graalpy_ext>",
                            "bin/modules/_cpython_unicodedata<graalpy_ext>",
                            "bin/modules/_sha3<graalpy_ext>",
                            "bin/modules/_testcapi<graalpy_ext>",
                            "bin/modules/_testbuffer<graalpy_ext>",
                            "bin/modules/_testmultiphase<graalpy_ext>",
                            "bin/modules/_testsinglephase<graalpy_ext>",
                            "bin/modules/_ctypes<graalpy_ext>",
                            "bin/modules/_ctypes_test<graalpy_ext>",
                            "bin/modules/pyexpat<graalpy_ext>",
                        ],
                    },
                },
                "<others>": {
                    "<others>": {
                        "multitarget": [
                            {"libc": ["glibc", "default"]},
                            {"libc": ["musl"], "variant": ["swcfi"]},
                        ],
                        "cmakeConfig": {
                            "CAPI_INC_DIR": "<output_root:com.oracle.graal.python>/jni_gen",
                            "PYCONFIG_INCLUDE_DIR": "<output_root:graalpy-pyconfig>/<os>-<arch>/<multitarget_libc_selection>/",
                            "TRUFFLE_NFI_H_INC": "<path:com.oracle.truffle.nfi.native>/include",
                            "GRAALPY_PARENT_DIR": "<suite_parent:graalpython>",
                            "GRAALPY_EXT": "<graalpy_ext>",
                            "LIBFFI_DIST": "<output_root:truffle:LIBFFI_DIST>/<os>-<arch>/<multitarget_libc_selection>/"
                        },
                        "results": [
                            "bin/<lib:python-native>",
                            "bin/modules/_sqlite3<graalpy_ext>",
                            "bin/modules/_cpython_sre<graalpy_ext>",
                            "bin/modules/_cpython_unicodedata<graalpy_ext>",
                            "bin/modules/_sha3<graalpy_ext>",
                            "bin/modules/_testcapi<graalpy_ext>",
                            "bin/modules/_testbuffer<graalpy_ext>",
                            "bin/modules/_testmultiphase<graalpy_ext>",
                            "bin/modules/_testsinglephase<graalpy_ext>",
                            "bin/modules/_ctypes<graalpy_ext>",
                            "bin/modules/_ctypes_test<graalpy_ext>",
                            "bin/modules/pyexpat<graalpy_ext>",
                            "bin/modules/termios<graalpy_ext>",
                        ],
                    },
                },
            },
            "buildDependencies": [
                "graalpy-pyconfig",
                "com.oracle.graal.python",
                "truffle:LIBFFI_DIST",
            ],
        },

        "python-libzsupport": {
            "subDir": "graalpython",
            "native": "shared_lib",
            "deliverable": "zsupport",
            "buildDependencies": [],
            "cflags": [
                "-DNDEBUG", "-O3", "-Werror",
            ],
            "ldlibs": ["-lz"],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "defaultBuild": False,
                        "multitarget": {
                            "libc": ["default"],
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "defaultBuild": True,
                        "multitarget": [
                            {"libc": ["glibc", "default"]},
                            {"libc": ["musl"], "variant": ["swcfi"]},
                        ],
                    },
                },
            },
        },

        "python-libposix": {
            "subDir": "graalpython",
            "native": "shared_lib",
            "deliverable": "posix",
            "buildDependencies": [],
            "cflags": [
                "-DNDEBUG", "-O3", "-Wall", "-Werror",
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "defaultBuild": False,
                        "multitarget": {
                            "libc": ["default"],
                        },
                    },
                },
                "darwin": {
                    "<others>": {
                        "defaultBuild": True,
                        "multitarget": {
                            "libc": ["default"],
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "ldlibs": ["-lutil"],
                        "defaultBuild": True,
                        "multitarget": [
                            {"libc": ["glibc", "default"]},
                            {"libc": ["musl"], "variant": ["swcfi"]},
                        ],
                    },
                },
            },
        },

        "python-lib": {
            "class": "ArchiveProject",
            "outputDir": "graalpython/lib-python/3",
            "type": "dir",
            "prefix": "",
            "ignorePatterns": [
                ".pyc",
                "\\/__pycache__\\/",
                "\\/test\\/",
                "\\/tests\\/",
                "\\/idle_test\\/",
                "\\\\__pycache__\\\\",
                "\\\\test\\\\",
                "\\\\tests\\\\",
                "\\\\idle_test\\\\",
            ],
            "license": ["PSF-License"],
        },

        "python-test-support-lib": {
            "class": "ArchiveProject",
            "outputDir": "graalpython/lib-python/3/test/support",
            "prefix": "test/support",
            "ignorePatterns": [],
            "license": ["PSF-License"],
        },

        "graalpy_licenses": {
            "class": "StandaloneLicenses",
            "community_license_file": "LICENSE.txt",
            "community_3rd_party_license_file": "THIRD_PARTY_LICENSE.txt",
        },

        "graalpy_thin_launcher": {
            "class": "ThinLauncherProject",
            "mainClass": "com.oracle.graal.python.shell.GraalPythonMain",
            "jar_distributions": ["graalpython:GRAALPYTHON-LAUNCHER"],
            "relative_home_paths": {
                "python": "..",
            },
            "relative_jre_path": "../jvm",
            "relative_module_path": "../modules",
            "relative_extracted_lib_paths": {
                "truffle.attach.library": "../jvmlibs/<lib:truffleattach>",
                "truffle.nfi.library": "../jvmlibs/<lib:trufflenfi>",
            },
            "liblang_relpath": "../lib/<lib:pythonvm>",
            "default_vm_args": [
                "--vm.Xss16777216", # request 16M of stack
                '--vm.-enable-native-access=org.graalvm.shadowed.jline',
            ],
            "multitarget": [
                {"os": ["linux"], "libc": ["glibc", "default"], "compiler": ["llvm-toolchain", "host", "*"]},
                {"os": ["linux"], "libc": ["musl"], "variant": ["swcfi"]},
                {"os": ["windows", "darwin"], "libc": ["default"]},
            ],
        },

        "libpythonvm": {
            "class": "LanguageLibraryProject",
            "dependencies": [
                "GRAALPY_STANDALONE_DEPENDENCIES",
            ],
            "buildDependencies": [
                "GRAALPY_STANDALONE_COMMON",
            ],
            "build_args": [
                # From mx.graalpython/native-image.properties
                "--add-exports", "org.graalvm.nativeimage/org.graalvm.nativeimage.impl=ALL-UNNAMED",
                "-R:StackSize=16777216",
                "-H:+AddAllCharsets",
                "-H:IncludeLocales=no,be,ro,ru,es,se,in,ka,hu,hr,bg,is,mk,da,nn,cs,sq,fr,pl,fo,bs,kl,fa,sv,it,uk,af,tg,ps,de",
                # Configure launcher
                "-Dorg.graalvm.launcher.class=com.oracle.graal.python.shell.GraalPythonMain",
                # GraalPy standalone specific flags
                # uncomment to disable JLine FFM provider at native image build time
                #'-Dorg.graalvm.shadowed.org.jline.terminal.ffm.disable=true',
                 '--enable-native-access=org.graalvm.shadowed.jline',
                "-Dpolyglot.python.PosixModuleBackend=native",
                "-Dpolyglot.python.Sha3ModuleBackend=native",
                "-Dpolyglot.python.CompressionModulesBackend=native",
            ],
            "dynamicBuildArgs": "libpythonvm_build_args",
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
        # We need the versions twice (even though that's silly), because
        # otherwise they will not get included in both the resources jar and
        # the language jar.
        "GRAALPYTHON_VERSIONS_RES": {
            "type": "dir",
            "layout": {
                "./": "dependency:graalpy-versions/graalpy_versions",
            },
        },
        "GRAALPYTHON_VERSIONS_MAIN": {
            "type": "dir",
            "layout": {
                "./": "dependency:graalpy-versions/graalpy_versions",
            },
        },

        "GRAALPYTHON-LAUNCHER": {
            "moduleInfo": {
                "name": "org.graalvm.py.launcher",
                "exports": [
                    "com.oracle.graal.python.shell to org.graalvm.launcher",
                    # Exported to be accessible from readline callbacks
                    "com.oracle.graal.python.shell",
                ],
            },
            "useModulePath": True,
            "dependencies": [
                "com.oracle.graal.python.shell",
            ],
            "distDependencies": [
                "sdk:POLYGLOT",
                "sdk:LAUNCHER_COMMON",
                "sdk:JLINE3",
                "sdk:MAVEN_DOWNLOADER",
                "sdk:NATIVEIMAGE",
            ],
            "description": "GraalPy, a high-performance embeddable Python 3 runtime for Java. This artifact provides a command-line launcher for GraalPy.",
            "maven": {
                "groupId": "org.graalvm.python",
                "artifactId": "python-launcher",
                "tag": ["default", "public"],
            },
        },

        "GRAALPYTHON_NATIVE_LIBS": {
            "native": True,
            "platformDependent": True,
            "type": "dir",
            "os_arch": {
                "windows": {
                    "<others>": {
                        "layout": {
                            "<os>/<arch>/": [
                                "dependency:com.oracle.graal.python.cext/<os>-<arch>/<multitarget_libc_selection>/bin/*",
                                "dependency:python-libbz2/<os>-<arch>/<multitarget_libc_selection>/bin/*",
                            ]
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "layout": {
                            "<os>/<arch>/": [
                                "dependency:com.oracle.graal.python.cext/<os>-<arch>/<multitarget_libc_selection>/bin/*",
                                "dependency:python-libzsupport/<os>-<arch>/<multitarget_libc_selection>/*",
                                "dependency:python-libposix/<os>-<arch>/<multitarget_libc_selection>/*",
                                "dependency:python-libbz2/<os>-<arch>/<multitarget_libc_selection>/bin/*",
                                "dependency:python-liblzma/<os>-<arch>/<multitarget_libc_selection>/bin/*",
                            ]
                        },
                    },
                },
            },
            "platforms": [
                "linux-amd64",
                "linux-aarch64",
                "darwin-amd64",
                "darwin-aarch64",
                "windows-amd64",
            ],
            "description": "Contains the JNI native lib, the C API and support libs.",
            "maven": False,
        },

        "GRAALPYTHON_RESOURCES": {
            "platformDependent": False,
            "moduleInfo": {
                "name": "org.graalvm.py.resources",
            },
            "useModulePath": True,
            "dependencies": [
                "com.oracle.graal.python.resources",
                "GRAALPYTHON_VERSIONS_RES",
                "GRAALPYTHON_NI_RESOURCES",
                "GRAALPYTHON_LIBPYTHON_RESOURCES",
                "GRAALPYTHON_LIBGRAALPY_RESOURCES",
                "GRAALPYTHON_INCLUDE_RESOURCES",
                "GRAALPYTHON_NATIVE_RESOURCES",
            ],
            "distDependencies": [
                "truffle:TRUFFLE_API",
            ],
            "requires": [
                "java.base",
            ],
            "compress": True,
            "description": "GraalPy, a high-performance embeddable Python 3 runtime for Java. This artifact includes the GraalPy standard library. It is not recommended to depend on the artifact directly. Instead, use \'org.graalvm.polyglot:python\' or \'org.graalvm.polyglot:python-community\' to ensure all dependencies are pulled in correctly.",
            "maven": {
                "artifactId": "python-resources",
                "groupId": "org.graalvm.python",
                "tag": ["default", "public"],
            },
            "license": [
                "UPL",
                "MIT",
                "PSF-License",
            ],
        },

        "GRAALPYTHON": {
            "moduleInfo": {
                "name": "org.graalvm.py",
                "exports": [
                    "com.oracle.graal.python.* to org.graalvm.py.enterprise",
                ],
            },
            "useModulePath": True,
            "dependencies": [
                "GRAALPYTHON_VERSIONS_MAIN",
                "com.oracle.graal.python",
                "com.oracle.graal.python.frozen",
            ],
            "distDependencies": [
                "truffle:TRUFFLE_API",
                "tools:TRUFFLE_PROFILER",
                "regex:TREGEX",
                "sdk:POLYGLOT",
                "sdk:NATIVEIMAGE",
                "sdk:COLLECTIONS",
                "truffle:TRUFFLE_NFI",
                "truffle:TRUFFLE_NFI_LIBFFI", # runtime dependency
                "truffle:TRUFFLE_NFI_PANAMA", # runtime dependency
                "truffle:TRUFFLE_ICU4J",
                "truffle:TRUFFLE_XZ",
            ],
            "requires": [
                "java.base",
                "java.logging",
                "java.management",
                "jdk.management",
                "jdk.unsupported",
                "jdk.security.auth",
            ],
            "exclude": [
                "BOUNCYCASTLE-PROVIDER",
                "BOUNCYCASTLE-PKIX",
                "BOUNCYCASTLE-UTIL",
            ],
            "description": "GraalPy, a high-performance embeddable Python 3 runtime for Java. This artifact includes the core language runtime without standard libraries. It is not recommended to depend on the artifact directly. Instead, use \'org.graalvm.polyglot:python\' or \'org.graalvm.polyglot:python-community\' to ensure all dependencies are pulled in correctly.",
            "maven": {
                "artifactId": "python-language",
                "groupId": "org.graalvm.python",
                "tag": ["default", "public"],
            },
            "noMavenJavadoc": True,
            "license": [
                "UPL",
                "MIT",
                "PSF-License",
            ],
        },

        "PYTHON_POM": {
            "type": "pom",
            "runtimeDependencies": [
                "GRAALPYTHON",
                "GRAALPYTHON_RESOURCES",
                "truffle:TRUFFLE_RUNTIME",
            ],
            "description": "GraalPy, a high-performance embeddable Python 3 runtime for Java. This POM dependency includes GraalPy dependencies and Truffle Community Edition.",
            "maven": {
                "groupId": "org.graalvm.python",
                "artifactId": "python",
                "tag": ["default", "public"],
            },
            "license": [
                "UPL",
                "MIT",
                "PSF-License",
            ],
        },

        "GRAALPYTHON_PROCESSOR": {
            "dependencies": [
                "com.oracle.graal.python.processor",
            ],
            "description": "GraalPy Java annotations processor",
            "overlaps": ["GRAALPYTHON"], # sharing the annotations
            "maven": False,
        },

        "GRAALPYTHON_UNIT_TESTS": {
            "description": "GraalPy unit tests that can access its internals. These tests require open access to GraalPy and Truffle modules.",
            "dependencies": [
                "com.oracle.graal.python.test",
                "com.oracle.graal.python.pegparser.test",
            ],
            "exclude": ["mx:JUNIT"],
            "distDependencies": [
                "GRAALPYTHON",
                "GRAALPYTHON-LAUNCHER",
                "truffle:TRUFFLE_TCK",
                "GRAALPYTHON_INTEGRATION_UNIT_TESTS",
            ],
            "testDistribution": True,
            "maven": False,
            "unittestConfig": "python-internal",
        },

        "GRAALPYTHON_INTEGRATION_UNIT_TESTS": {
            "description": "Python integration tests. These tests access GraalPy only via the GraalVM SDK APIs",
            "dependencies": [
                "com.oracle.graal.python.test.integration",
            ],
            "exclude": ["mx:JUNIT"],
            "distDependencies": [
                "GRAALPYTHON",
                "GRAALPYTHON_RESOURCES",
                "sdk:GRAAL_SDK",
            ],
            "testDistribution": True,
            "maven": False,
        },

        "GRAALPYTHON_BENCH": {
            "description": "java python interop benchmarks",
            "dependencies": ["com.oracle.graal.python.benchmarks"],
            "exclude": ["mx:JMH_1_21"],
            "distDependencies": [
                "GRAALPYTHON",
                "GRAALPYTHON-LAUNCHER",
                # We run the benchmarks with Python home served from resources
                "GRAALPYTHON_RESOURCES",
                "sdk:POLYGLOT",
            ],
            "testDistribution": True,
            "maven": False,
        },

        "GRAALPYTHON_TCK": {
            "dependencies": [
                "com.oracle.graal.python.tck",
            ],
            "exclude": ["mx:JUNIT"],
            "distDependencies": [
                "sdk:POLYGLOT_TCK",
                "GRAALPYTHON",
                # We run the TCK with Python home served from resources
                "GRAALPYTHON_RESOURCES",
            ],
            "description" : "Truffle TCK provider for GraalPy.",
            "license": "UPL",
            "maven": {
                "groupId": "org.graalvm.python",
                "artifactId": "python-truffle-tck",
                "tag": ["default", "public"],
            },
            "noMavenJavadoc": True,
        },

        # Now come the different resource projects. These all end up bundled
        # together in a Jar, but they each have different extraction targets
        # depending on the platform.

        # The Python standard library. On Windows the Java code will extract
        # this to "/Lib", on Unix to "/lib/python<py_ver:major_minor>/"
        "GRAALPYTHON_LIBPYTHON_RESOURCES": {
            "native": False,
            "platformDependent": False,
            "hashEntry":  "META-INF/resources/libpython.sha256",
            "fileListEntry": "META-INF/resources/libpython.files",
            "type": "dir",
            "description": "GraalVM Python lib-python resources",
            "layout": {
                "./META-INF/resources/libpython/": [
                    "dependency:graalpython:python-lib/*",
                    "dependency:graalpython:python-test-support-lib/*",
                ],
            },
            "maven": False,
        },

        # The GraalPy core library. On Windows the Java code will extract this
        # to "/lib-graalpython", on Unix to "/lib/graalpy<graal_ver:major_minor>/"
        "GRAALPYTHON_LIBGRAALPY_RESOURCES": {
            "native": False,
            "platformDependent": False,
            "hashEntry":  "META-INF/resources/libgraalpy.sha256",
            "fileListEntry": "META-INF/resources/libgraalpy.files",
            "type": "dir",
            "description": "GraalVM Python lib-graalpython resources",
            "layout": {
                "./META-INF/resources/libgraalpy/": [
                    "file:graalpython/lib-graalpython/*",
                ],
            },
            "maven": False,
        },

        "GRAALPYTHON_NI_RESOURCES": {
            "native": False,
            "platformDependent": False,
            "hashEntry":  "META-INF/resources/ni.sha256",
            "fileListEntry": "META-INF/resources/ni.files",
            "type": "dir",
            "description": "GraalVM Python native image resources",
            "layout": {
                "./META-INF/resources/": [
                    "file:mx.graalpython/native-image.properties",
                ],
            },
            "maven": False,
        },

        # The Python headers. These go to "/include" on windows and
        # "/include/python<py_ver:major_minor>" on unix
        "GRAALPYTHON_INCLUDE_RESOURCES": {
            "native": False,
            "platformDependent": False,
            "hashEntry":  "META-INF/resources/include.sha256",
            "fileListEntry": "META-INF/resources/include.files",
            "type": "dir",
            "description": "GraalVM Python header resources",
            "layout": {
                "./META-INF/resources/include/": [
                    "file:graalpython/com.oracle.graal.python.cext/include/*",
                ],
            },
            "maven": False,
        },

        # The native libraries we ship and pyconfig.h. These are platform specific,
        # and even the names of libraries are platform specific. So we already put them
        # in the right folder structure here; pyconfig.h is put into the right
        # "include" directory, the structure must be kept in sync with how
        # GRAALPYTHON_INCLUDE_RESOURCES is deployed
        "GRAALPYTHON_NATIVE_RESOURCES": {
            "native": True,
            "platformDependent": True,
            "hashEntry":  "META-INF/resources/<os>/<arch>/native.sha256",
            "fileListEntry": "META-INF/resources/<os>/<arch>/native.files",
            "type": "dir",
            "description": "GraalVM Python platform dependent resources",
            "os_arch": {
                "windows": {
                    "<others>": {
                        "layout": {
                            "./META-INF/resources/<os>/<arch>/libs/python<py_ver:major_minor_nodot>.lib": "dependency:GRAALPYTHON_NATIVE_LIBS/<os>/<arch>/python-native.lib",
                            "./META-INF/resources/<os>/<arch>/lib-graalpython/": [
                                {
                                    "source_type": "dependency",
                                    "dependency": "GRAALPYTHON_NATIVE_LIBS",
                                    "path": "<os>/<arch>/*",
                                    "exclude": ["python-native.lib"],
                                },
                            ],
                            "./META-INF/resources/<os>/<arch>/Lib/venv/scripts/nt/graalpy.exe": "dependency:python-venvlauncher",
                            "./META-INF/resources/<os>/<arch>/Lib/venv/scripts/nt/python.exe": "dependency:python-venvlauncher",
                            "./META-INF/resources/<os>/<arch>/include/": "dependency:graalpy-pyconfig/<os>-<arch>/<multitarget_libc_selection>/pyconfig.h",
                        },
                    },
                },
                "darwin": {
                    "<others>": {
                        "layout": {
                            "./META-INF/resources/<os>/<arch>/lib/graalpy<graal_ver:major_minor>/": [
                                "dependency:GRAALPYTHON_NATIVE_LIBS/<os>/<arch>/*",
                            ],
                            "./META-INF/resources/<os>/<arch>/lib/python<py_ver:major_minor>/venv/scripts/macos/graalpy": "dependency:python-macos-launcher",
                            "./META-INF/resources/<os>/<arch>/include/python<py_ver:major_minor>/": "dependency:graalpy-pyconfig/<os>-<arch>/<multitarget_libc_selection>/pyconfig.h",
                        }
                    }
                },
                "<others>": {
                    "<others>": {
                        "layout": {
                            "./META-INF/resources/<os>/<arch>/lib/graalpy<graal_ver:major_minor>/": [
                                "dependency:GRAALPYTHON_NATIVE_LIBS/<os>/<arch>/*",
                            ],
                            "./META-INF/resources/<os>/<arch>/include/python<py_ver:major_minor>/": "dependency:graalpy-pyconfig/<os>-<arch>/<multitarget_libc_selection>/pyconfig.h",
                        },
                    },
                },
            },
            "platforms": [
                "linux-amd64",
                "linux-aarch64",
                "darwin-amd64",
                "darwin-aarch64",
                "windows-amd64",
            ],
            "maven": False,
        },

        # This puts the resources above into their platform specific folders at
        # build time. This is for the usage by mx and the SDK when building
        # standalones. The layouts should be exactly equivalent to what
        # PythonResource.java produces.
        "GRAALPYTHON_GRAALVM_SUPPORT": {
            "native": True,
            "platformDependent": True,
            "fileListPurpose": 'native-image-resources',
            "description": "GraalVM Python support distribution for the GraalVM",
            "platforms": [
                "linux-amd64",
                "linux-aarch64",
                "darwin-amd64",
                "darwin-aarch64",
                "windows-amd64",
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "layout": {
                            "./Lib/": [
                                "dependency:GRAALPYTHON_LIBPYTHON_RESOURCES/META-INF/resources/libpython/*",
                            ],
                            "./lib-graalpython/": [
                                "dependency:GRAALPYTHON_LIBGRAALPY_RESOURCES/META-INF/resources/libgraalpy/*",
                            ],
                            "./include/": [
                                "dependency:GRAALPYTHON_INCLUDE_RESOURCES/META-INF/resources/include/*",
                            ],
                            "./": [
                                "dependency:GRAALPYTHON_NI_RESOURCES/META-INF/resources/native-image.properties",
                                {
                                    "source_type": "dependency",
                                    "dependency": "GRAALPYTHON_NATIVE_RESOURCES",
                                    "path": "META-INF/resources/<os>/<arch>/*",
                                    "exclude": ["META-INF/resources/<os>/<arch>/native.sha256", "META-INF/resources/<os>/<arch>/native.files"],
                                },
                            ],
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "layout": {
                            "./lib/python<py_ver:major_minor>/": [
                                "dependency:GRAALPYTHON_LIBPYTHON_RESOURCES/META-INF/resources/libpython/*",
                            ],
                            "./lib/graalpy<graal_ver:major_minor>/": [
                                "dependency:GRAALPYTHON_LIBGRAALPY_RESOURCES/META-INF/resources/libgraalpy/*",
                            ],
                            "./include/python<py_ver:major_minor>/": [
                                "dependency:GRAALPYTHON_INCLUDE_RESOURCES/META-INF/resources/include/*",
                            ],
                            "./": [
                                "dependency:GRAALPYTHON_NI_RESOURCES/META-INF/resources/native-image.properties",
                                {
                                    "source_type": "dependency",
                                    "dependency": "GRAALPYTHON_NATIVE_RESOURCES",
                                    "path": "META-INF/resources/<os>/<arch>/*",
                                    "exclude": ["META-INF/resources/<os>/<arch>/native.sha256", "META-INF/resources/<os>/<arch>/native.files"],
                                },
                            ],
                        },
                    },
                },
            },
            "maven": False,
        },

        "GRAALPY_VIRTUALENV_SEEDER": {
            "native": True, # so it produces a tar, not a jar file
            "platformDependent": False,
            "description": "graalpy-virtualenv-seeder plugin sources usable to be installed into other interpreters",
            "layout": {
                "graalpy_virtualenv_seeder": "file:graalpy_virtualenv_seeder",
            },
            "maven": False,
        },

        "GRAALPYTHON_GRAALVM_DOCS": {
            "native": True,
            "description": "GraalVM Python documentation files for the GraalVM",
            "layout": {
                "README_GRAALPY.md": "file:README.md",
                "./": "file:docs",
            },
            "maven": False,
        },

        "GRAALPYTHON_GRAALVM_LICENSES": {
            "native": True,
            "platformDependent": True,
            "fileListPurpose": 'native-image-resources',
            "description": "GraalVM Python support distribution for the GraalVM license files",
            "layout": {
                "LICENSE_GRAALPY.txt": "file:LICENSE.txt",
                "THIRD_PARTY_LICENSE_GRAALPY.txt": "file:THIRD_PARTY_LICENSE.txt",
            },
            "maven": False,
        },

        "GRAALPY_STANDALONE_DEPENDENCIES": {
            "description": "GraalPy standalone dependencies",
            "class": "DynamicPOMDistribution",
            "distDependencies": [
                "graalpython:GRAALPYTHON-LAUNCHER",
                "graalpython:GRAALPYTHON",
                "graalpython:BOUNCYCASTLE-PROVIDER",
                "graalpython:BOUNCYCASTLE-PKIX",
                "graalpython:BOUNCYCASTLE-UTIL",
                "sdk:TOOLS_FOR_STANDALONE",
            ],
            "dynamicDistDependencies": "graalpy_standalone_deps",
            "maven": False,
        },

        "GRAALPY_STANDALONE_COMMON": {
            "description": "Common base layout for Native and JVM standalones",
            "type": "dir",
            "platformDependent": True,
            "platforms": "local",
            "layout": {
                "./": [
                    "extracted-dependency:GRAALPYTHON_GRAALVM_SUPPORT",
                    "extracted-dependency:GRAALPYTHON_GRAALVM_DOCS",
                    "extracted-dependency:GRAALPY_VIRTUALENV_SEEDER",
                    "dependency:graalpy_licenses/*",
                ],
                "bin/<exe:graalpy>": "dependency:graalpy_thin_launcher/<os>-<arch>/<multitarget_libc_selection>/<exe:graalpy_thin_launcher>",
                "bin/<exe:python>": "dependency:graalpy_thin_launcher/<os>-<arch>/<multitarget_libc_selection>/<exe:graalpy_thin_launcher>",
                "bin/<exe:python3>": "dependency:graalpy_thin_launcher/<os>-<arch>/<multitarget_libc_selection>/<exe:graalpy_thin_launcher>",
                "bin/<exe:graalpy-config>": "dependency:graalpy_thin_launcher/<os>-<arch>/<multitarget_libc_selection>/<exe:graalpy_thin_launcher>",
                "libexec/<exe:graalpy-polyglot-get>": "dependency:graalpy_thin_launcher/<os>-<arch>/<multitarget_libc_selection>/<exe:graalpy_thin_launcher>",
                "release": "dependency:sdk:STANDALONE_JAVA_HOME/release",
            },
        },

        "GRAALPY_NATIVE_STANDALONE": {
            "description": "GraalPy Native standalone",
            "type": "dir",
            "platformDependent": True,
            "platforms": "local",
            "layout": {
                "./": [
                    "dependency:GRAALPY_STANDALONE_COMMON/*",
                ],
                "lib/": "dependency:libpythonvm",
            },
        },

        "GRAALPY_JVM_STANDALONE": {
            "description": "GraalPy JVM standalone",
            "type": "dir",
            "platformDependent": True,
            "platforms": "local",
            "layout": {
                "./": [
                    "dependency:GRAALPY_STANDALONE_COMMON/*",
                ],
                "jvm/": {
                    "source_type": "dependency",
                    "dependency": "sdk:STANDALONE_JAVA_HOME",
                    "path": "*",
                    "exclude": [
                        # Native Image-related
                        "bin/native-image*",
                        "lib/static",
                        "lib/svm",
                        "lib/<lib:native-image-agent>",
                        "lib/<lib:native-image-diagnostics-agent>",
                        # Unnecessary and big
                        "lib/src.zip",
                        "jmods",
                    ],
                },
                "jvmlibs/": [
                    "extracted-dependency:truffle:TRUFFLE_ATTACH_GRAALVM_SUPPORT",
                    "extracted-dependency:truffle:TRUFFLE_NFI_NATIVE_GRAALVM_SUPPORT",
                ],
                "modules/": [
                    "classpath-dependencies:GRAALPY_STANDALONE_DEPENDENCIES",
                ],
            },
        },

        "GRAALPYTHON_POLYBENCH_BENCHMARKS": {
            "description": "Distribution for GraalPython polybench benchmarks",
            "layout": {
                "./interpreter/": [
                    "file:benchmarks/interpreter/*.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/arith-binop.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/arith-modulo-sized.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/attribute-access.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/attribute-access-polymorphic.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/attribute-access-super.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/attribute-bool.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/boolean-logic-sized.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/builtin-len.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/builtin-len-tuple-sized.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/call-classmethod-sized.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/call-method-polymorphic.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/bytecode-benchmark.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/for-range.py",
                    "file:graalpython/com.oracle.graal.python.benchmarks/python/micro/generate-functions-sized.py",
                ],
                "./warmup/": [
                    "file:benchmarks/warmup/*.py",
                    "dependency:GRAALPYTHON_PYFLATE_BENCHMARK_RESOURCE",
                ],
            },
        },

        "GRAALPY_NATIVE_STANDALONE_RELEASE_ARCHIVE": {
            "class": "DeliverableStandaloneArchive",
            "platformDependent": True,
            "standalone_dist": "GRAALPY_NATIVE_STANDALONE",
            "community_archive_name": "graalpy-community",
            "enterprise_archive_name": "graalpy",
            "language_id": "python",
        },

        "GRAALPY_JVM_STANDALONE_RELEASE_ARCHIVE": {
            "class": "DeliverableStandaloneArchive",
            "platformDependent": True,
            "standalone_dist": "GRAALPY_JVM_STANDALONE",
            "community_archive_name": "graalpy-community-jvm",
            "enterprise_archive_name": "graalpy-jvm",
            "language_id": "python",
        },
    },
}
