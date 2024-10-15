# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import os
import sys
import time
from pathlib import Path
import argparse
import json

try:
    import py4cytoscape as p4c
except:
    raise SystemError("py4cytoscape is missing: pip install py4cytoscape")

try:
    p4c.cytoscape_ping()
except:
    raise SystemError("""
Cytoscape isn't running and/or installed.
If Cytoscape isn't installed, you can download it from:
https://cytoscape.org/download.html
""")


MAX_NODES_PER_GRAPH = 1000

gc_graph_path = Path(__file__).parent

def progress_bar(iteration, total, prefix='', suffix='', length=30, fill='█'):
    percent = ("{0:.1f}").format(100 * (iteration / float(total)))
    filled_length = int(length * iteration // total)
    bar = fill * filled_length + '-' * (length - filled_length)
    sys.stdout.write(f'\r{prefix} |{bar}| {percent}% {suffix}')
    sys.stdout.flush()

cytoscape_style = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<vizmap id="VizMap-2024_09_13-15_52" documentVersion="3.1">
    <visualStyle name="PythonGC_Style">
        <network>
            <visualProperty default="0.0" name="NETWORK_CENTER_X_LOCATION"/>
            <visualProperty default="1.0" name="NETWORK_SCALE_FACTOR"/>
            <visualProperty default="550.0" name="NETWORK_SIZE"/>
            <visualProperty default="true" name="NETWORK_EDGE_SELECTION"/>
            <visualProperty default="" name="NETWORK_TITLE"/>
            <visualProperty default="#FFFFFF" name="NETWORK_BACKGROUND_PAINT"/>
            <visualProperty default="false" name="NETWORK_ANNOTATION_SELECTION"/>
            <visualProperty default="400.0" name="NETWORK_HEIGHT"/>
            <visualProperty default="0.0" name="NETWORK_CENTER_Y_LOCATION"/>
            <visualProperty default="0.0" name="NETWORK_CENTER_Z_LOCATION"/>
            <visualProperty default="550.0" name="NETWORK_WIDTH"/>
            <visualProperty default="false" name="NETWORK_FORCE_HIGH_DETAIL"/>
            <visualProperty default="true" name="NETWORK_NODE_SELECTION"/>
            <visualProperty default="0.0" name="NETWORK_DEPTH"/>
            <visualProperty default="false" name="NETWORK_NODE_LABEL_SELECTION"/>
        </network>
        <node>
            <dependency value="true" name="nodeCustomGraphicsSizeSync"/>
            <dependency value="true" name="nodeSizeLocked"/>
            <visualProperty default="#FFFFFF" name="NODE_FILL_COLOR">
                <passthroughMapping attributeName="color" attributeType="string"/>
            </visualProperty>
            <visualProperty default="200.0" name="NODE_LABEL_WIDTH"/>
            <visualProperty default="true" name="NODE_VISIBLE"/>
            <visualProperty default="#333333" name="NODE_BORDER_PAINT"/>
            <visualProperty default="#B6B6B6" name="NODE_LABEL_BACKGROUND_COLOR"/>
            <visualProperty default="C,C,c,0.00,0.00" name="NODE_CUSTOMGRAPHICS_POSITION_5"/>
            <visualProperty default="0.0" name="NODE_X_LOCATION"/>
            <visualProperty default="50.0" name="NODE_CUSTOMGRAPHICS_SIZE_2"/>
            <visualProperty default="C,C,c,0.00,0.00" name="NODE_CUSTOMGRAPHICS_POSITION_8"/>
            <visualProperty default="DefaultVisualizableVisualProperty(id=NODE_CUSTOMPAINT_9, name=Node Custom Paint 9)" name="NODE_CUSTOMPAINT_9"/>
            <visualProperty default="SOLID" name="NODE_BORDER_STROKE"/>
            <visualProperty default="255" name="NODE_BORDER_TRANSPARENCY"/>
            <visualProperty default="true" name="NODE_NESTED_NETWORK_IMAGE_VISIBLE"/>
            <visualProperty default="DefaultVisualizableVisualProperty(id=NODE_CUSTOMPAINT_7, name=Node Custom Paint 7)" name="NODE_CUSTOMPAINT_7"/>
            <visualProperty default="org.cytoscape.cg.model.NullCustomGraphics,0,[ Remove Graphics ]," name="NODE_CUSTOMGRAPHICS_4"/>
            <visualProperty default="#252525" name="NODE_LABEL_COLOR"/>
            <visualProperty default="DefaultVisualizableVisualProperty(id=NODE_CUSTOMPAINT_5, name=Node Custom Paint 5)" name="NODE_CUSTOMPAINT_5"/>
            <visualProperty default="DefaultVisualizableVisualProperty(id=NODE_CUSTOMPAINT_4, name=Node Custom Paint 4)" name="NODE_CUSTOMPAINT_4"/>
            <visualProperty default="DefaultVisualizableVisualProperty(id=NODE_CUSTOMPAINT_6, name=Node Custom Paint 6)" name="NODE_CUSTOMPAINT_6"/>
            <visualProperty default="org.cytoscape.cg.model.NullCustomGraphics,0,[ Remove Graphics ]," name="NODE_CUSTOMGRAPHICS_3"/>
            <visualProperty default="org.cytoscape.cg.model.NullCustomGraphics,0,[ Remove Graphics ]," name="NODE_CUSTOMGRAPHICS_1"/>
            <visualProperty default="C,C,c,0.00,0.00" name="NODE_CUSTOMGRAPHICS_POSITION_9"/>
            <visualProperty default="50.0" name="NODE_CUSTOMGRAPHICS_SIZE_5"/>
            <visualProperty default="50.0" name="NODE_CUSTOMGRAPHICS_SIZE_6"/>
            <visualProperty default="255" name="NODE_TRANSPARENCY"/>
            <visualProperty default="DefaultVisualizableVisualProperty(id=NODE_CUSTOMPAINT_1, name=Node Custom Paint 1)" name="NODE_CUSTOMPAINT_1"/>
            <visualProperty default="255" name="NODE_LABEL_BACKGROUND_TRANSPARENCY"/>
            <visualProperty default="DefaultVisualizableVisualProperty(id=NODE_CUSTOMPAINT_3, name=Node Custom Paint 3)" name="NODE_CUSTOMPAINT_3"/>
            <visualProperty default="#787878" name="NODE_PAINT"/>
            <visualProperty default="org.cytoscape.cg.model.NullCustomGraphics,0,[ Remove Graphics ]," name="NODE_CUSTOMGRAPHICS_6"/>
            <visualProperty default="20.0" name="COMPOUND_NODE_PADDING"/>
            <visualProperty default="" name="NODE_LABEL">
                <passthroughMapping attributeName="shared name" attributeType="string"/>
            </visualProperty>
            <visualProperty default="DefaultVisualizableVisualProperty(id=NODE_CUSTOMPAINT_2, name=Node Custom Paint 2)" name="NODE_CUSTOMPAINT_2"/>
            <visualProperty default="50.0" name="NODE_HEIGHT"/>
            <visualProperty default="50.0" name="NODE_WIDTH"/>
            <visualProperty default="org.cytoscape.cg.model.NullCustomGraphics,0,[ Remove Graphics ]," name="NODE_CUSTOMGRAPHICS_2"/>
            <visualProperty default="N,S,c,0.00,0.00" name="NODE_LABEL_POSITION"/>
            <visualProperty default="50.0" name="NODE_SIZE"/>
            <visualProperty default="10" name="NODE_LABEL_FONT_SIZE">
                <discreteMapping attributeName="id" attributeType="string"/>
            </visualProperty>
            <visualProperty default="0.0" name="NODE_Z_LOCATION"/>
            <visualProperty default="#FF0066" name="NODE_SELECTED_PAINT"/>
            <visualProperty default="0.0" name="NODE_Y_LOCATION"/>
            <visualProperty default="0.0" name="NODE_LABEL_ROTATION"/>
            <visualProperty default="50.0" name="NODE_CUSTOMGRAPHICS_SIZE_4"/>
            <visualProperty default="C,C,c,0.00,0.00" name="NODE_CUSTOMGRAPHICS_POSITION_2"/>
            <visualProperty default="org.cytoscape.cg.model.NullCustomGraphics,0,[ Remove Graphics ]," name="NODE_CUSTOMGRAPHICS_5"/>
            <visualProperty default="org.cytoscape.cg.model.NullCustomGraphics,0,[ Remove Graphics ]," name="NODE_CUSTOMGRAPHICS_8"/>
            <visualProperty default="4.0" name="NODE_BORDER_WIDTH"/>
            <visualProperty default="ROUND_RECTANGLE" name="COMPOUND_NODE_SHAPE"/>
            <visualProperty default="C,C,c,0.00,0.00" name="NODE_CUSTOMGRAPHICS_POSITION_6"/>
            <visualProperty default="NONE" name="NODE_LABEL_BACKGROUND_SHAPE"/>
            <visualProperty default="C,C,c,0.00,0.00" name="NODE_CUSTOMGRAPHICS_POSITION_3"/>
            <visualProperty default="50.0" name="NODE_CUSTOMGRAPHICS_SIZE_9"/>
            <visualProperty default="false" name="NODE_SELECTED"/>
            <visualProperty default="" name="NODE_TOOLTIP"/>
            <visualProperty default="50.0" name="NODE_CUSTOMGRAPHICS_SIZE_3"/>
            <visualProperty default="255" name="NODE_LABEL_TRANSPARENCY"/>
            <visualProperty default="50.0" name="NODE_CUSTOMGRAPHICS_SIZE_8"/>
            <visualProperty default="org.cytoscape.cg.model.NullCustomGraphics,0,[ Remove Graphics ]," name="NODE_CUSTOMGRAPHICS_9"/>
            <visualProperty default="0.0" name="NODE_DEPTH"/>
            <visualProperty default="DefaultVisualizableVisualProperty(id=NODE_CUSTOMPAINT_8, name=Node Custom Paint 8)" name="NODE_CUSTOMPAINT_8"/>
            <visualProperty default="50.0" name="NODE_CUSTOMGRAPHICS_SIZE_7"/>
            <visualProperty default="C,C,c,0.00,0.00" name="NODE_CUSTOMGRAPHICS_POSITION_1"/>
            <visualProperty default="C,C,c,0.00,0.00" name="NODE_CUSTOMGRAPHICS_POSITION_7"/>
            <visualProperty default="Ubuntu Mono,plain,1" name="NODE_LABEL_FONT_FACE"/>
            <visualProperty default="C,C,c,0.00,0.00" name="NODE_CUSTOMGRAPHICS_POSITION_4"/>
            <visualProperty default="ELLIPSE" name="NODE_SHAPE">
                <passthroughMapping attributeName="shape" attributeType="string"/>
            </visualProperty>
            <visualProperty default="org.cytoscape.cg.model.NullCustomGraphics,0,[ Remove Graphics ]," name="NODE_CUSTOMGRAPHICS_7"/>
            <visualProperty default="50.0" name="NODE_CUSTOMGRAPHICS_SIZE_1"/>
        </node>
        <edge>
            <dependency value="false" name="arrowColorMatchesEdge"/>
            <visualProperty default="false" name="EDGE_SELECTED"/>
            <visualProperty default="AUTO_BEND" name="EDGE_STACKING"/>
            <visualProperty default="#404040" name="EDGE_UNSELECTED_PAINT"/>
            <visualProperty default="C,C,c,0.00,0.00" name="EDGE_LABEL_POSITION"/>
            <visualProperty default="#B6B6B6" name="EDGE_LABEL_BACKGROUND_COLOR"/>
            <visualProperty default="#333333" name="EDGE_TARGET_ARROW_UNSELECTED_PAINT"/>
            <visualProperty default="2.0" name="EDGE_WIDTH"/>
            <visualProperty default="#FFFF00" name="EDGE_SOURCE_ARROW_SELECTED_PAINT"/>
            <visualProperty default="#000000" name="EDGE_SOURCE_ARROW_UNSELECTED_PAINT"/>
            <visualProperty default="0.5" name="EDGE_STACKING_DENSITY"/>
            <visualProperty default="true" name="EDGE_CURVED"/>
            <visualProperty default="#FFFF00" name="EDGE_TARGET_ARROW_SELECTED_PAINT"/>
            <visualProperty default="true" name="EDGE_VISIBLE"/>
            <visualProperty default="12" name="EDGE_LABEL_FONT_SIZE"/>
            <visualProperty default="6.0" name="EDGE_TARGET_ARROW_SIZE"/>
            <visualProperty name="EDGE_BEND"/>
            <visualProperty default="0.0" name="EDGE_LABEL_ROTATION"/>
            <visualProperty default="6.0" name="EDGE_SOURCE_ARROW_SIZE"/>
            <visualProperty default="NONE" name="EDGE_LABEL_BACKGROUND_SHAPE"/>
            <visualProperty default="" name="EDGE_TOOLTIP"/>
            <visualProperty default="NONE" name="EDGE_SOURCE_ARROW_SHAPE"/>
            <visualProperty default="255" name="EDGE_LABEL_BACKGROUND_TRANSPARENCY"/>
            <visualProperty default="" name="EDGE_LABEL">
                <passthroughMapping attributeName="interaction" attributeType="string"/>
            </visualProperty>
            <visualProperty default="255" name="EDGE_LABEL_TRANSPARENCY"/>
            <visualProperty default="255" name="EDGE_TRANSPARENCY"/>
            <visualProperty default="#FF0000" name="EDGE_STROKE_SELECTED_PAINT"/>
            <visualProperty default="#808080" name="EDGE_PAINT"/>
            <visualProperty default="0.0" name="EDGE_Z_ORDER"/>
            <visualProperty default="#FF0000" name="EDGE_SELECTED_PAINT"/>
            <visualProperty default="SOLID" name="EDGE_LINE_TYPE"/>
            <visualProperty default="Dialog.plain,plain,10" name="EDGE_LABEL_FONT_FACE"/>
            <visualProperty default="#000000" name="EDGE_LABEL_COLOR"/>
            <visualProperty default="false" name="EDGE_LABEL_AUTOROTATE"/>
            <visualProperty default="200.0" name="EDGE_LABEL_WIDTH"/>
            <visualProperty default="#333333" name="EDGE_STROKE_UNSELECTED_PAINT"/>
            <visualProperty default="ARROW" name="EDGE_TARGET_ARROW_SHAPE"/>
        </edge>
    </visualStyle>
</vizmap>
"""

style_name = 'PythonGC_Style'
if style_name not in set(p4c.get_visual_style_names()):
    style_file = Path(gc_graph_path, style_name + '.xml')
    with open(style_file, 'w') as fp:
        fp.write(cytoscape_style)
    p4c.import_visual_styles(style_file)
    os.remove(style_file)



node_typename_exclude_set = {
'wrapper_descriptor',
'member_descriptor',
'method_descriptor',
'classmethod_descriptor',
'getset_descriptor',
'builtin_function_or_method',
'function',
'Quitter',
'_Printer',
'property',
'FileFinder',
'DebugActions',
'MatchFirst',
'collections.deque',
'slice',
'_Helper',
'_io.BufferedReader',
'IncrementalDecoder',
'Mark',
'generator',
'itertools.count',
'_LiteralSpecialForm',
'DistutilsLoader',
'method-wrapper',
'EmptyProvider',
'Interpolation',
'BasicInterpolation',
'uname_result',
'_Unknown',
'PercentStyle',
'_HAS_DEFAULT_FACTORY_CLASS',
'_KW_ONLY_TYPE',
'MemoizedZipManifests',
'_QByteMap',
'DictStack',
'JSONEncoder',
'_json.Scanner',
'dict_itemiterator',
'DistutilsMetaFinder',
'Repr',
'_auto_null',
'_WindowsFlavour',
'_PosixFlavour',
'Event',
'ListCompat',
'build_ext',
'install_lib',
'install',
'JSONDecoder',
'ConfigDiscovery',
'OrderedSet',
'build',
'_io.BufferedWriter',
'IncrementalEncoder',
'_Environ',
'TestRunner',
'list_iterator',
'operator.attrgetter',
'Condition',
'Charset',
'_localized_day',
'_localized_month',
'WeakValueDictionary',
'version_info',
'Undefined',
'PathMetadata',
'Context',
'NamedTupleMeta',
'_TypedDictMeta',
'Formatter',
'_StderrHandler',
'CodecInfo',
'_io.FileIO',
'collections.defaultdict',
'types.GenericAlias',
'abstractproperty',
'_MainThread',
'ExceptionTrap',
'InfinityType',
'NegativeInfinityType',
'_MISSING_TYPE',
'_FIELD_BASE',
'White',
'FollowedBy',
'DistInfoDistribution',
'_csv.Dialect',
'ThisTestCase',
'_Sentinel',
'Distribution',
'Log',
'Compat32',
'Manager',
'_thread.lock',
'PosixPath',
'ValueTerminal',
'Handlers',
'WeakSet',
'WorkingSet',
'RootLogger',
'FrameSummary',
'TextCalendar',
'_DeprecatedType',
'_struct.Struct',
'QuotedString',
'ResourceManager',
'NewType',
'SkipTo',
'EnumCheck',
'Sigmasks',
'UnixCCompiler',
'PycInvalidationMode',
'_io.TextIOWrapper',
'VendorImporter',
'PlistFormat',
'CharsNotIn',
'Extension',
'OneOrMore',
'NotAny',
'Forward',
'types.SimpleNamespace',
'Or',
'OpAssoc',
'PlaceHolder',
'Word',
'DistributionMetadata',
'Random',
'WeakKeyDictionary',
'_lazyclassproperty',
'FastPath',
'Lookup',
'FlagBoundary',
'cached_property',
'AddressInfo',
'_ParameterKind',
'SocketKind',
'Logger',
'_thread.RLock',
'LineStart',
'FoldedCase',
'_ABC',
'StringEnd',
'StringStart',
'functools.partial',
'LineEnd',
'ForwardRef',
'_AnyMeta',
'Empty',
'FreezableDefaultDict',
'_CallableType',
'Group',
'MsgFlag',
'IPv4Network',
'IPv4Address',
'RegexFlag',
'ExtensionFileLoader',
'Diagnostics',
'_NullToken',
'_TupleType',
'ZeroOrMore',
'_Precedence',
'_WordRegex',
'TestCase',
'TypeVar',
'IPv6Network',
'IPv6Address',
'_collections._tuplegetter',
'builtin_method',
'AddressFamily',
'Signals',
'_ProtocolMeta',
'method',
'_CallableGenericAlias',
'_SpecialGenericAlias',
'Suppress',
'staticmethod',
'frozenset',
'Opt',
'_SpecialForm',
'Combine',
'_SingleCharLiteral',
'Literal',
're.Pattern',
'classmethod',
'_NamedIntConstant',
'Regex',
'EnumType',
'_abc._abc_data',
'SourceFileLoader',
'_GenericAlias',
'_UnionGenericAlias',
'And',
'functools._lru_cache_wrapper',
'ModuleSpec',
'cell',
# 'module',
'ABCMeta',
'set',
'weakref.ReferenceType',
'Load',
'Store',
'Del',
'Add',
'Sub',
'Mult',
'MatMult',
'Div',
'Mod',
'Pow',
'LShift',
'RShift',
'BitOr',
'BitXor',
'BitAnd',
'FloorDiv',
'Invert',
'Not',
'UAdd',
'USub',
'Eq',
'NotEq',
'Lt',
'LtE',
'Gt',
'GtE',
'Is',
'IsNot',
'In',
'NotIn',
'type', ###
'torch._C._TensorMeta',
}

# json structure
"""
    "legend": {
        "n": "name",
        "gc": "gc address",
        "t": "type name",
        "ntv": "is native",
        "rc": "ref count",
        "gr": "gc refcount",
        "nr": "is unreachable",
        "ct": "is collecting"
    },
    "runs": [
        [
            "gc graphs"
        ],
        [
            "gc_collect_main",
            "",
            [
                "nodes",
                "young",
                {
                    "0x800055ae85aeb860": {
                        "n": "0x800055ae85aeb860",
                        "gc": "0x800055ae85aeb850",
                        "t": "dict",
                        "ntv": 0,
                        "rc": 11
                    },
                    }
                }
            ],
            [
                "edges",
                "young",
                [
                    [
                        "(nil)",
                        "(nil)"
                    ],
                    [
                        "0x55ae85da8bb0",
                        "0x800055ae85dbdb60"
                    ],
                ]
            ],
            [
                "update_refs",
                "base",
                {
                    "0x800055ae85aeb860": {
                        "gr": 11,
                        "ct": 1
                    }
                }
            ],
            [
                "subtract_refs",
                "base",
                {
                    "0x800055ae85aeb860": {
                        "gr": 11
                    }
                }
            ],
            [
                "move_unreachable",
                "base",
                {
                    "0x800055ae85aeb860": {
                        "nr": 0
                    }
                }
            ],
            [
                "move_unreachable",
                "weak_candidates",
                {
                    "0x800055ae85dbdb60": {
                        "nr": 1
                    },
                    }
                }
            ],
            [
                "move_unreachable",
                "weak_candidates",
                {
                    "0x800055ae85e163a0": {
                        "nr": 1
                    }
                }
            ]
        ],
        [
            "gc_collect_main",
            ....
"""

shapes = [
    'Ellipse', # 'native'
    'Hexagon', # 'managed'
]

colors = [
    '#000000', # 'Black'
    '#FF0000', # 'Red'
    '#008000', # 'Green'
    '#0000FF', # 'Blue'
    # '#800080', # 'Purple'
    # '#A52A2A', # 'Brown'
    # '#D3D3D3', # 'Gray'
    # '#FFFF00', # 'Yellow'
    '#FFFFFF', # 'White'
]

def get_color(number):
    max_code = len(colors)
    return colors[number] if number < max_code else colors[-1]

node_template = "\n{{ group: 'nodes', data: {{ id: '{id}', {props} }} }}"
node_template_dict = {
    "group": "nodes",
    "data": None
}
class Node:
    def __init__(self, id, p) -> None:
        self.id = id
        self.node_dict = node_template_dict.copy()
        data = {'id': id}
        props = ''
        for key in p:
            v = repr(p[key]) if isinstance(p[key], str) else p[key]
            props += "'{name}': {value},".format(name=Node.legend[key], value=v)
            data[Node.legend[key]] = v
        data['shared name'] = data['name']
        data.update(Node.extra_info.get(id, {}))

        if data.get('is native', None) is not None:
            is_native = data['is native']
            data['shape'] = shapes[is_native]
            refcount = data['ref count']
            refcount -= 0 if is_native == 1 else 10
            data['color'] = get_color(refcount)
        self.node_dict['data'] = data
        self.props = props
        self.p = p
        self.additional_props = None

    def to_dict(self):
        if not self.additional_props:
            return self.node_dict
        ret = node_template_dict.copy()
        ret["data"] = self.node_dict['data'].copy()
        for k in self.additional_props:
            ret["data"][Node.legend[k]] = self.additional_props[k]
        if ret["data"].get('gc refcount', None) is not None:
            refcount = ret["data"]['gc refcount']
            if refcount <= (ret["data"]['ref count'] * 2):
                is_native = ret["data"]['is native']
                refcount -= 0 if is_native == 1 else 10
                ret["data"]['color'] = get_color(refcount)
        return ret
        

    def __repr__(self) -> str:
        return node_template.format(id=self.id, props=self.props)
        

edge_template = "\n{{ group: 'edges', data: {{ id: '{src_id}-{dst_id}', source: '{src_id}', target: '{dst_id}' }} }}"
class Edge:
    def __init__(self, s, t) -> None:
        self.source = s
        self.target = t

    def to_dict(self):
        return {"data": { "source" : self.source, "target" : self.target} }

    def __repr__(self) -> str:
        return edge_template.format(src_id=self.source, dst_id=self.target)

def filter_nodes(nodes_container, edges_container, args):
    selected_nodes = set()
    reached_max = False

    for e in edges_container:
        if len(selected_nodes) > args.limit:
            reached_max = True
            break
        src, dst = e[0], e[1]
        if src in nodes_container and dst in nodes_container:
            src_typename = nodes_container[src]['t']
            if src_typename in node_typename_exclude_set:
                continue
            dst_typename = nodes_container[dst]['t']
            if dst_typename in node_typename_exclude_set:
                continue
            
            selected_nodes.add(src)
            selected_nodes.add(dst)

    return selected_nodes, reached_max

def filter_selected_type(nodes_container, edges_container, args):
    selected_nodes = set()
    reached_max = False

    if args.typename is None:
        return selected_nodes

    still_searching = True
    while still_searching:
        still_searching = False
        for e in edges_container:
            if len(selected_nodes) > args.limit:
                reached_max = True
                break
            src = e[0]
            dst = e[1]
            if src in nodes_container and dst in nodes_container:
                src_typename = nodes_container[src]['t']
                dst_typename = nodes_container[dst]['t']
                if src_typename in node_typename_exclude_set or dst_typename in node_typename_exclude_set:
                    continue
                is_candidate = (src_typename == args.typename) or (dst_typename == args.typename)
                is_candidate = is_candidate or (dst in selected_nodes and dst_typename not in node_typename_exclude_set)
                if is_candidate:
                    if src not in selected_nodes or dst not in selected_nodes:
                        still_searching = True
                        selected_nodes.add(src)
                        selected_nodes.add(dst)

    return selected_nodes, reached_max


def graphs_collection(gc_graph, args):
    typename_dict = {}

    def collect_stats(typename_dict, typename):
        typename_dict[typename] = typename_dict.get(typename, 0) + 1
    
    num_runs = len(gc_graph['runs'])
    stages = []
    runs_sections = []
    for run_i in range(1, num_runs):
        gc_collect_graph = gc_graph['runs'][run_i]

        stage_name = '[%d] gc collect' % run_i
        progress_bar_name = 'Graphs (' + args.typename + '):' if args.typename else 'Graphs:'
        progress_bar(run_i, num_runs, prefix=progress_bar_name, suffix=stage_name, length=80)

        # gc_collect_main = gc_collect_graph[0] # "gc_collect_main",
        # ignore = gc_collect_graph[1] # "",
        nodes_data = gc_collect_graph[2] # {..}
        edges_data = gc_collect_graph[3] # [..]

        nodes_generation = nodes_data[1]
        nodes_container = nodes_data[2] # dict
        
        edges_generation = edges_data[1]
        edges_container = edges_data[2] # list
        
        nodes_set = dict()
        edges_list = list()
        any_reached_max = False

        if args.typename is None:
            selected_nodes, reached_max = filter_nodes(nodes_container, edges_container, args)
        else:
            selected_nodes, reached_max = filter_selected_type(nodes_container, edges_container, args)
        
        any_reached_max = any_reached_max or reached_max

        for e in edges_container:
            src = e[0]
            dst = e[1]
            if src in selected_nodes and dst in selected_nodes:
                src_typename = nodes_container[src]['t']
                dst_typename = nodes_container[dst]['t']
                collect_stats(typename_dict, src_typename)
                collect_stats(typename_dict, dst_typename)
                edges_list.append(Edge(src, dst))
                if src not in nodes_set:
                    nodes_set[src] = Node(src, nodes_container[src])
                if dst not in nodes_set:
                    nodes_set[dst] = Node(dst, nodes_container[dst])
            else:
                # ignore it because src or dst aren't available
                pass
        run_subsections = []
        current_subsection_name = None
        for query_i in range(4, len(gc_collect_graph)):
            query_data = gc_collect_graph[query_i]
            subsection_name = query_data[0]
            subgroup_name = query_data[1]
            subgroup_nodes = query_data[2]

            if subsection_name != current_subsection_name:
                current_subsection_name = subsection_name
                subsection = [subsection_name, [], {}]
                run_subsections.append(subsection)
            else:
                subsection = run_subsections[-1]
            
            subsection[1].append(Node(subgroup_name, {'n': subgroup_name}))

            subgroup_nodes_dict = subsection[2]
            for n in subgroup_nodes:
                if n in nodes_set:
                    assert n not in subgroup_nodes_dict, n
                    subgroup_nodes_dict[n] = query_data[2][n]
                    subgroup_nodes_dict[n]['parent'] = subgroup_name

        
        stages.append([nodes_set, edges_list])
        runs_sections.append(run_subsections)
    print('') # after progress bar
    print('############# stats #############')
    for k, v in sorted(typename_dict.items(), key=lambda item: item[1]):
        print('%s: %d' % (k,v))
    if any_reached_max:
        print('** reached max number of nodes [MAX: %d] you can increase it using --limit NUMBER' % args.limit)
    return stages, runs_sections

def add_graph_to_cytoscape(stages, runs_sections, args):

    def create_network(nodes, edges, stage_name, subsection_name):
        data = {
            "elements" : {
                "nodes" : None,
                "edges" : None,
            }
        }
        nodes_list = [n.to_dict() for n in nodes.values()]
        edges_list = [e.to_dict() for e in edges]
        data["elements"]["nodes"] = nodes_list
        data["elements"]["edges"] = edges_list
        
        id = p4c.create_network_from_cytoscapejs(data, title=subsection_name, collection=stage_name)
        # p4c.rename_network(subsection_name, network=id)
        p4c.layout_network('cose')
        p4c.set_visual_style(style_name)
        return id

    collection_name = Path(args.filename).name
    num_stages = len(stages)
    range_direction = range(num_stages - 1, 0, -1) if args.reverse else range(num_stages)
    p_i = 0
    for stage_i in range_direction:
        p_i += 1
        nodes = stages[stage_i][0]
        if len(nodes) == 0:
            continue
        edges = stages[stage_i][1]
        stage_name = '[%d] gc collect' % stage_i
        progress_bar(p_i, num_stages, prefix='To Cytoscape:', suffix=stage_name, length=80)
        create_network(nodes, edges, collection_name, stage_name)
        if args.traverse_only:
            continue

        for section in runs_sections[stage_i]:
            section_name = section[0]
            groups = section[1]
            groups_dict = {}
            section_nodes = section[2]
            for n in section_nodes:
                additional_props = section_nodes[n]
                nodes[n].additional_props = additional_props
                group_name = additional_props.get('parent', None)
                if group_name:
                    g = groups_dict.get(group_name, [])
                    g.append(n)
                    groups_dict[group_name] = g
            create_network(nodes, edges, collection_name, section_name)
            for group_name in groups_dict:
                p4c.create_group(group_name, groups_dict[group_name], nodes_by_col='id')
    print('') # after progress bar



def fix_gc_json_file(filename):
    with open(filename, 'rb') as f:
        f.seek(-2, os.SEEK_END)
        while f.read(1) != b'\n':
            f.seek(-2, os.SEEK_CUR)
        last_line = f.readline().decode()

    if ']}' not in last_line:
        with open(filename, 'a') as f:
            f.write(']}')

def fix_extra_json_file(filename):
    with open(filename, 'rb') as f:
        f.seek(-2, os.SEEK_END)
        while f.read(1) != b'\n':
            f.seek(-2, os.SEEK_CUR)
        last_line = f.readline().decode()

    if not last_line.endswith('}'):
        with open(filename, 'a') as f:
            f.write('"0": {}}')

def main():
    parser = argparse.ArgumentParser(description ='Generate GC Graphs on Cytoscape.')
    parser.add_argument('filename', metavar='FILENAME',
                            help="Path of the json file to Generate GC Graphs")
    parser.add_argument("-r", "--reverse", action="store_true",
                            help="Reverse order of the Generated GC Graphs")
    parser.add_argument("-t", "--traverse-only", action="store_true",
                            help="Generate GC Graphs without internal routines")
    parser.add_argument("-n", "--typename", required=False,
                            help="Select only nodes with the provided type name [Exact] and their parent and childs")
    parser.add_argument("--extra-info", action="append", required=False,
                            help="Add extra info from additional json files")
    parser.add_argument("-l", "--limit", required=False, type=int, default=MAX_NODES_PER_GRAPH,
                            help="Maximum number of nodes per graph")
    args = parser.parse_args()

    fix_gc_json_file(args.filename)
    with open(args.filename) as fp:
        gc_graph = json.load(fp)

    extra_node_info = {}
    if args.extra_info:
        for f in args.extra_info:
            fix_extra_json_file(f)
            with open(f) as fp:
                extra_info = json.load(fp)
            extra_node_info.update(extra_info)

    gc_graph['legend']['parent'] = 'container'
    
    Node.legend = gc_graph['legend']
    Node.extra_info = extra_node_info

    stages, runs_sections = graphs_collection(gc_graph, args)
    add_graph_to_cytoscape(stages, runs_sections, args)

main()