#!/usr/bin/python3
# Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
import datetime
import json
import os
import re
import ssl
import subprocess
import sys

import jinja2

REPO = os.path.normpath(os.path.join(os.path.dirname(__file__), '..'))

CURRENT_YEAR = datetime.datetime.now().year
LICENSE = f"""/*
 * Copyright (c) 2021, {CURRENT_YEAR}, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */"""


def get_all_ciphers():
    cipher_string = 'ALL:COMPLEMENTOFALL'
    standard_names = {}
    cmd = ['openssl', 'ciphers', '-stdname', cipher_string]
    for line in subprocess.run(cmd, check=True, capture_output=True, text=True).stdout.splitlines():
        stdname, _, name, *_ = line.split()
        standard_names[name] = stdname
    context = ssl.create_default_context()
    context.set_ciphers(cipher_string)
    all_ciphers = context.get_ciphers()
    for cipher in all_ciphers:
        cipher['java_name'] = standard_names[cipher['name']]
    return all_ciphers


def get_all_cipher_strings():
    with open('/usr/include/openssl/ssl.h') as hfile:
        cipher_strings = re.findall(r'^#\s*define SSL_TXT_\S*\s+ "([^"]+)"', hfile.read(), re.MULTILINE)
        # This one doesn't have a macro, add it manually
        cipher_strings.append("TLSv1.0")
        cipher_strings.sort()
        return cipher_strings


def get_cipher_strings_mapping(all_ciphers):
    cipher_string_mapping = []
    for cipher_string in get_all_cipher_strings():
        # The ciphersuites argument disables implicit adding of TLSv1.3 ciphersuites to everything
        cmd = ['openssl', 'ciphers', '-stdname', '-ciphersuites', '', cipher_string]
        ciphers = []
        for line in subprocess.run(cmd, capture_output=True, text=True).stdout.splitlines():
            cipher = line.split()[0]
            if any(c['java_name'] == cipher for c in all_ciphers):
                ciphers.append(cipher)
        cipher_string_mapping.append((cipher_string, ciphers))
    return cipher_string_mapping


def generate_test_data(outpath):
    cipher_strings = [
        # These are examples from the openssl-ciphers manpage
        'ALL:eNULL',
        'ALL:!ADH:@STRENGTH',
        'ALL:!aNULL',
        # '3DES:+RSA', - TODO doesn't match anything on the JDK, investigate
        # 'RC4:!COMPLEMENTOFDEFAULT', - skipped because it produces empty list
        'RSA:!COMPLEMENTOFALL',
        # We don't implement @SECLEVEL yet
        # 'ALL:@SECLEVEL=2',
        # Cpython's default
        "DEFAULT:!aNULL:!eNULL:!MD5:!3DES:!DES:!RC4:!IDEA:!SEED:!aDSS:!SRP:!PSK",
    ]

    data = {}
    context = ssl.SSLContext()
    for cipher_string in cipher_strings:
        try:
            context.set_ciphers(cipher_string)
            data[cipher_string] = context.get_ciphers()
        except ssl.SSLError:
            print(f"Skipping {cipher_string}", file=sys.stderr)
    with open(outpath, 'w') as f:
        json.dump(data, f, indent=2)


CIPHER_KEYS = [
    ('id', 'int', 'opensslId'),
    ('name', 'String', 'opensslName'),
    ('protocol', 'String', 'protocol'),
    ('description', 'String', 'description'),
    ('strength_bits', 'int', 'strengthBits'),
    ('alg_bits', 'int', 'algorithmBits'),
    ('aead', 'boolean', 'aead'),
    ('symmetric', 'String', 'symmetric'),
    ('digest', 'String', 'digest'),
    ('kea', 'String', 'kea'),
    ('auth', 'String', 'auth'),
]


def value_list(cipher):
    values = []
    for python_name, _, _ in CIPHER_KEYS:
        value = cipher[python_name]
        if value is None:
            values.append('null')
        elif value is True:
            values.append('true')
        elif value is False:
            values.append('false')
        elif isinstance(value, str):
            values.append(f'"{value}"')
        else:
            values.append(str(value))
    return ', '.join(values)


def getter(name):
    return f'get{name[0].upper()}{name[1:]}'


env = jinja2.Environment()
env.filters['value_list'] = value_list
env.filters['getter'] = getter

SSL_CIPHER_TEMPLATE = """\
{{ license }}
package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

/**
{{ autogenerated }}

This class represents OpenSSL metadata of all SSL/TLS cipher suites.
**/
public enum SSLCipher {
    {% for cipher in ciphers -%}
    {{ cipher['java_name'] }}({{ cipher | value_list }}){% if loop.last %};{% else %},{% endif %}
    {% endfor %}

    {% for _, type, name in vars -%}
    private final {{ type if type != 'String' else 'TruffleString' }} {{ name }};
    {% endfor %}

    {% for python_name, _, _ in vars -%}
    private static final TruffleString T_{{ python_name | upper }} = tsLiteral("{{ python_name }}");
    {% endfor %}

    SSLCipher({% for _, type, name in vars %}{{type}} {{name}}{% if not loop.last %}, {% endif %}{% endfor %}) {
        {% for _, type, name in vars -%}
        this.{{name}} = {%if type == "String" %}toTruffleStringUncached({{name}}){% else %}{{name}}{% endif %};
        {% endfor %}
    }

    public PKeyword[] asKeywords() {
        return new PKeyword[]{{ '{' }}{% for python_name, type, java_name in vars -%}
            new PKeyword(T_{{ python_name | upper }}, {{ java_name | getter }}(){% if type == 'String' %} != null ? {{ java_name | getter }}() : PNone.NONE{% endif %}){% if not loop.last %}, {% endif %}
        {% endfor %}};
    }

    {% for _, type, name in vars -%}
    public {{ type if type != 'String' else 'TruffleString' }} {{ name | getter }}() {
        return {{ name }};
    }
    {% endfor %}
}

"""

SSL_CIPHER_STRING_MAPPING_TEMPLATE = """\
{{ license }}
package com.oracle.graal.python.builtins.objects.ssl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
{{ autogenerated }}

Class containing mappings of OpenSSL cipher strings to lists of ciphers.
**/
public abstract class SSLCipherStringMapping {
    private static final Map<String, List<SSLCipher>> mapping = new HashMap<>();

    public static List<SSLCipher> get(String cipherString) {
        return mapping.get(cipherString);
    }

    static {
        {% for cipher_string, suites in mapping -%}
        {% if suites -%}
        mapping.put("{{ cipher_string }}", Arrays.asList({% for suite in suites -%}SSLCipher.{{suite}}{% if not loop.last %}, {% endif %}{% endfor %}));
        {% endif -%}
        {% endfor -%}
    }
}

"""

openssl_version = subprocess.run(['openssl', 'version'], capture_output=True, text=True, check=True).stdout
autogenerated = f"""\
This file has been automatically generated using scripts/generate_ssl_data.py. Do not edit!
Generated using: {openssl_version}
""".rstrip()
common = {
    'license': LICENSE,
    'autogenerated': autogenerated,
}
SSL_CODE_DIR = os.path.join(REPO,
                            'graalpython/com.oracle.graal.python/src/com/oracle/graal/python/builtins/objects/ssl/')
SSL_CIPHER_PATH = os.path.join(SSL_CODE_DIR, 'SSLCipher.java')
SSL_CIPHER_STRING_MAPPING_PATH = os.path.join(SSL_CODE_DIR, 'SSLCipherStringMapping.java')
all_ciphers = get_all_ciphers()
env.from_string(SSL_CIPHER_TEMPLATE) \
    .stream(ciphers=all_ciphers, vars=CIPHER_KEYS, **common) \
    .dump(SSL_CIPHER_PATH)
env.from_string(SSL_CIPHER_STRING_MAPPING_TEMPLATE) \
    .stream(mapping=get_cipher_strings_mapping(all_ciphers), **common) \
    .dump(SSL_CIPHER_STRING_MAPPING_PATH)
generate_test_data(
    os.path.join(REPO, 'graalpython/com.oracle.graal.python.test/src/tests/ssldata/expected_ciphers.json'))
subprocess.run(['mx', 'eclipseformat', '--primary', '--filelist', '-'], text=True, stdout=subprocess.DEVNULL,
               input='\n'.join([SSL_CIPHER_PATH, SSL_CIPHER_STRING_MAPPING_PATH]))
