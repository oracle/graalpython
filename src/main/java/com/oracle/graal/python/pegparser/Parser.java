/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 */

// Checkstyle: stop
// JaCoCo Exclude
//@formatter:off
// Generated from pegjava/python.gram by pegen
package com.oracle.graal.python.pegparser;

import com.oracle.graal.python.pegparser.sst.*;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"all", "cast"})
public final class Parser extends AbstractParser {

    // parser fields
    

    private static final Object[][][] reservedKeywords = new Object[][][]{
        null,
        null,
        {
            {"if", 513},
            {"as", 519},
            {"in", 522},
            {"or", 529},
            {"is", 532},
        },
        {
            {"del", 505},
            {"def", 512},
            {"for", 516},
            {"try", 517},
            {"and", 530},
            {"not", 531},
        },
        {
            {"from", 502},
            {"pass", 504},
            {"with", 515},
            {"elif", 520},
            {"else", 521},
            {"None", 525},
            {"True", 526},
        },
        {
            {"raise", 503},
            {"yield", 506},
            {"break", 508},
            {"class", 514},
            {"while", 518},
            {"False", 527},
        },
        {
            {"return", 500},
            {"import", 501},
            {"assert", 507},
            {"global", 510},
            {"except", 523},
            {"lambda", 528},
        },
        {
            {"finally", 524},
        },
        {
            {"continue", 509},
            {"nonlocal", 511},
        },
    };
    @Override
    protected Object[][][] getReservedKeywords() { return reservedKeywords; }
    private static final String[] softKeywords = new String[]{
        "_",
        "case",
        "match",
    };
    @Override
    protected String[] getSoftKeywords() { return softKeywords; }
    private static final int FILE_ID = 1000;
    private static final int INTERACTIVE_ID = 1001;
    private static final int EVAL_ID = 1002;
    private static final int FUNC_TYPE_ID = 1003;
    private static final int FSTRING_ID = 1004;
    private static final int TYPE_EXPRESSIONS_ID = 1005;
    private static final int STATEMENTS_ID = 1006;
    private static final int STATEMENT_ID = 1007;
    private static final int STATEMENT_NEWLINE_ID = 1008;
    private static final int SIMPLE_STMTS_ID = 1009;
    private static final int SIMPLE_STMT_ID = 1010;
    private static final int COMPOUND_STMT_ID = 1011;
    private static final int ASSIGNMENT_ID = 1012;
    private static final int AUGASSIGN_ID = 1013;
    private static final int GLOBAL_STMT_ID = 1014;
    private static final int NONLOCAL_STMT_ID = 1015;
    private static final int YIELD_STMT_ID = 1016;
    private static final int ASSERT_STMT_ID = 1017;
    private static final int DEL_STMT_ID = 1018;
    private static final int IMPORT_STMT_ID = 1019;
    private static final int IMPORT_NAME_ID = 1020;
    private static final int IMPORT_FROM_ID = 1021;
    private static final int IMPORT_FROM_TARGETS_ID = 1022;
    private static final int IMPORT_FROM_AS_NAMES_ID = 1023;
    private static final int IMPORT_FROM_AS_NAME_ID = 1024;
    private static final int DOTTED_AS_NAMES_ID = 1025;
    private static final int DOTTED_AS_NAME_ID = 1026;
    private static final int DOTTED_NAME_ID = 1027;  // Left-recursive
    private static final int IF_STMT_ID = 1028;
    private static final int ELIF_STMT_ID = 1029;
    private static final int ELSE_BLOCK_ID = 1030;
    private static final int WHILE_STMT_ID = 1031;
    private static final int FOR_STMT_ID = 1032;
    private static final int WITH_STMT_ID = 1033;
    private static final int WITH_ITEM_ID = 1034;
    private static final int TRY_STMT_ID = 1035;
    private static final int EXCEPT_BLOCK_ID = 1036;
    private static final int FINALLY_BLOCK_ID = 1037;
    private static final int MATCH_STMT_ID = 1038;
    private static final int SUBJECT_EXPR_ID = 1039;
    private static final int CASE_BLOCK_ID = 1040;
    private static final int GUARD_ID = 1041;
    private static final int PATTERNS_ID = 1042;
    private static final int PATTERN_ID = 1043;
    private static final int AS_PATTERN_ID = 1044;
    private static final int OR_PATTERN_ID = 1045;
    private static final int CLOSED_PATTERN_ID = 1046;
    private static final int LITERAL_PATTERN_ID = 1047;
    private static final int SIGNED_NUMBER_ID = 1048;
    private static final int CAPTURE_PATTERN_ID = 1049;
    private static final int WILDCARD_PATTERN_ID = 1050;
    private static final int VALUE_PATTERN_ID = 1051;
    private static final int ATTR_ID = 1052;  // Left-recursive
    private static final int NAME_OR_ATTR_ID = 1053;  // Left-recursive
    private static final int GROUP_PATTERN_ID = 1054;
    private static final int SEQUENCE_PATTERN_ID = 1055;
    private static final int OPEN_SEQUENCE_PATTERN_ID = 1056;
    private static final int MAYBE_SEQUENCE_PATTERN_ID = 1057;
    private static final int MAYBE_STAR_PATTERN_ID = 1058;
    private static final int STAR_PATTERN_ID = 1059;
    private static final int MAPPING_PATTERN_ID = 1060;
    private static final int ITEMS_PATTERN_ID = 1061;
    private static final int KEY_VALUE_PATTERN_ID = 1062;
    private static final int DOUBLE_STAR_PATTERN_ID = 1063;
    private static final int CLASS_PATTERN_ID = 1064;
    private static final int POSITIONAL_PATTERNS_ID = 1065;
    private static final int KEYWORD_PATTERNS_ID = 1066;
    private static final int KEYWORD_PATTERN_ID = 1067;
    private static final int RETURN_STMT_ID = 1068;
    private static final int RAISE_STMT_ID = 1069;
    private static final int FUNCTION_DEF_ID = 1070;
    private static final int FUNCTION_DEF_RAW_ID = 1071;
    private static final int FUNC_TYPE_COMMENT_ID = 1072;
    private static final int PARAMS_ID = 1073;
    private static final int PARAMETERS_ID = 1074;
    private static final int SLASH_NO_DEFAULT_ID = 1075;
    private static final int SLASH_WITH_DEFAULT_ID = 1076;
    private static final int STAR_ETC_ID = 1077;
    private static final int KWDS_ID = 1078;
    private static final int PARAM_NO_DEFAULT_ID = 1079;
    private static final int PARAM_WITH_DEFAULT_ID = 1080;
    private static final int PARAM_MAYBE_DEFAULT_ID = 1081;
    private static final int PARAM_ID = 1082;
    private static final int ANNOTATION_ID = 1083;
    private static final int DEFAULT_PARAM_ID = 1084;
    private static final int DECORATORS_ID = 1085;
    private static final int CLASS_DEF_ID = 1086;
    private static final int CLASS_DEF_RAW_ID = 1087;
    private static final int BLOCK_ID = 1088;
    private static final int STAR_EXPRESSIONS_ID = 1089;
    private static final int STAR_EXPRESSION_ID = 1090;
    private static final int STAR_NAMED_EXPRESSIONS_ID = 1091;
    private static final int STAR_NAMED_EXPRESSION_ID = 1092;
    private static final int NAMED_EXPRESSION_ID = 1093;
    private static final int DIRECT_NAMED_EXPRESSION_ID = 1094;
    private static final int ANNOTATED_RHS_ID = 1095;
    private static final int EXPRESSIONS_ID = 1096;
    private static final int EXPRESSION_ID = 1097;
    private static final int LAMBDEF_ID = 1098;
    private static final int LAMBDA_PARAMS_ID = 1099;
    private static final int LAMBDA_PARAMETERS_ID = 1100;
    private static final int LAMBDA_SLASH_NO_DEFAULT_ID = 1101;
    private static final int LAMBDA_SLASH_WITH_DEFAULT_ID = 1102;
    private static final int LAMBDA_STAR_ETC_ID = 1103;
    private static final int LAMBDA_KWDS_ID = 1104;
    private static final int LAMBDA_PARAM_NO_DEFAULT_ID = 1105;
    private static final int LAMBDA_PARAM_WITH_DEFAULT_ID = 1106;
    private static final int LAMBDA_PARAM_MAYBE_DEFAULT_ID = 1107;
    private static final int LAMBDA_PARAM_ID = 1108;
    private static final int DISJUNCTION_ID = 1109;
    private static final int CONJUNCTION_ID = 1110;
    private static final int INVERSION_ID = 1111;
    private static final int COMPARISON_ID = 1112;
    private static final int COMPARE_OP_BITWISE_OR_PAIR_ID = 1113;
    private static final int EQ_BITWISE_OR_ID = 1114;
    private static final int NOTEQ_BITWISE_OR_ID = 1115;
    private static final int LTE_BITWISE_OR_ID = 1116;
    private static final int LT_BITWISE_OR_ID = 1117;
    private static final int GTE_BITWISE_OR_ID = 1118;
    private static final int GT_BITWISE_OR_ID = 1119;
    private static final int NOTIN_BITWISE_OR_ID = 1120;
    private static final int IN_BITWISE_OR_ID = 1121;
    private static final int ISNOT_BITWISE_OR_ID = 1122;
    private static final int IS_BITWISE_OR_ID = 1123;
    private static final int BITWISE_OR_ID = 1124;  // Left-recursive
    private static final int BITWISE_XOR_ID = 1125;  // Left-recursive
    private static final int BITWISE_AND_ID = 1126;  // Left-recursive
    private static final int SHIFT_EXPR_ID = 1127;  // Left-recursive
    private static final int SUM_ID = 1128;  // Left-recursive
    private static final int TERM_ID = 1129;  // Left-recursive
    private static final int FACTOR_ID = 1130;
    private static final int POWER_ID = 1131;
    private static final int AWAIT_PRIMARY_ID = 1132;
    private static final int PRIMARY_ID = 1133;  // Left-recursive
    private static final int SLICES_ID = 1134;
    private static final int SLICE_ID = 1135;
    private static final int ATOM_ID = 1136;
    private static final int STRINGS_ID = 1137;
    private static final int LIST_ID = 1138;
    private static final int LISTCOMP_ID = 1139;
    private static final int TUPLE_ID = 1140;
    private static final int GROUP_ID = 1141;
    private static final int GENEXP_ID = 1142;
    private static final int SET_ID = 1143;
    private static final int SETCOMP_ID = 1144;
    private static final int DICT_ID = 1145;
    private static final int DICTCOMP_ID = 1146;
    private static final int DOUBLE_STARRED_KVPAIRS_ID = 1147;
    private static final int DOUBLE_STARRED_KVPAIR_ID = 1148;
    private static final int KVPAIR_ID = 1149;
    private static final int FOR_IF_CLAUSES_ID = 1150;
    private static final int FOR_IF_CLAUSE_ID = 1151;
    private static final int YIELD_EXPR_ID = 1152;
    private static final int ARGUMENTS_ID = 1153;
    private static final int ARGS_ID = 1154;
    private static final int KWARGS_ID = 1155;
    private static final int STARRED_EXPRESSION_ID = 1156;
    private static final int KWARG_OR_STARRED_ID = 1157;
    private static final int KWARG_OR_DOUBLE_STARRED_ID = 1158;
    private static final int STAR_TARGETS_ID = 1159;
    private static final int STAR_TARGETS_LIST_SEQ_ID = 1160;
    private static final int STAR_TARGETS_TUPLE_SEQ_ID = 1161;
    private static final int STAR_TARGET_ID = 1162;
    private static final int TARGET_WITH_STAR_ATOM_ID = 1163;
    private static final int STAR_ATOM_ID = 1164;
    private static final int SINGLE_TARGET_ID = 1165;
    private static final int SINGLE_SUBSCRIPT_ATTRIBUTE_TARGET_ID = 1166;
    private static final int DEL_TARGETS_ID = 1167;
    private static final int DEL_TARGET_ID = 1168;
    private static final int DEL_T_ATOM_ID = 1169;
    private static final int TARGETS_ID = 1170;
    private static final int TARGET_ID = 1171;
    private static final int T_PRIMARY_ID = 1172;  // Left-recursive
    private static final int T_LOOKAHEAD_ID = 1173;
    private static final int T_ATOM_ID = 1174;
    private static final int INVALID_ARGUMENTS_ID = 1175;
    private static final int INVALID_KWARG_ID = 1176;
    private static final int INVALID_EXPRESSION_ID = 1177;
    private static final int INVALID_NAMED_EXPRESSION_ID = 1178;
    private static final int INVALID_ASSIGNMENT_ID = 1179;
    private static final int INVALID_ANN_ASSIGN_TARGET_ID = 1180;
    private static final int INVALID_DEL_STMT_ID = 1181;
    private static final int INVALID_BLOCK_ID = 1182;
    private static final int INVALID_PRIMARY_ID = 1183;  // Left-recursive
    private static final int INVALID_COMPREHENSION_ID = 1184;
    private static final int INVALID_DICT_COMPREHENSION_ID = 1185;
    private static final int INVALID_PARAMETERS_ID = 1186;
    private static final int INVALID_PARAMETERS_HELPER_ID = 1187;
    private static final int INVALID_LAMBDA_PARAMETERS_ID = 1188;
    private static final int INVALID_LAMBDA_PARAMETERS_HELPER_ID = 1189;
    private static final int INVALID_STAR_ETC_ID = 1190;
    private static final int INVALID_LAMBDA_STAR_ETC_ID = 1191;
    private static final int INVALID_DOUBLE_TYPE_COMMENTS_ID = 1192;
    private static final int INVALID_WITH_ITEM_ID = 1193;
    private static final int INVALID_FOR_TARGET_ID = 1194;
    private static final int INVALID_GROUP_ID = 1195;
    private static final int INVALID_IMPORT_FROM_TARGETS_ID = 1196;
    private static final int INVALID_WITH_STMT_ID = 1197;
    private static final int INVALID_EXCEPT_BLOCK_ID = 1198;
    private static final int INVALID_MATCH_STMT_ID = 1199;
    private static final int INVALID_CASE_BLOCK_ID = 1200;
    private static final int INVALID_IF_STMT_ID = 1201;
    private static final int INVALID_ELIF_STMT_ID = 1202;
    private static final int INVALID_WHILE_STMT_ID = 1203;
    private static final int INVALID_DOUBLE_STARRED_KVPAIRS_ID = 1204;
    private static final int INVALID_KVPAIR_ID = 1205;
    private static final int _TMP_1_ID = 1206;
    private static final int _LOOP0_2_ID = 1207;
    private static final int _TMP_3_ID = 1208;
    private static final int _LOOP0_4_ID = 1209;
    private static final int _LOOP0_6_ID = 1210;
    private static final int _GATHER_5_ID = 1211;
    private static final int _LOOP0_8_ID = 1212;
    private static final int _GATHER_7_ID = 1213;
    private static final int _LOOP0_10_ID = 1214;
    private static final int _GATHER_9_ID = 1215;
    private static final int _LOOP0_12_ID = 1216;
    private static final int _GATHER_11_ID = 1217;
    private static final int _LOOP1_13_ID = 1218;
    private static final int _LOOP0_15_ID = 1219;
    private static final int _GATHER_14_ID = 1220;
    private static final int _TMP_16_ID = 1221;
    private static final int _TMP_17_ID = 1222;
    private static final int _TMP_18_ID = 1223;
    private static final int _TMP_19_ID = 1224;
    private static final int _TMP_20_ID = 1225;
    private static final int _TMP_21_ID = 1226;
    private static final int _TMP_22_ID = 1227;
    private static final int _TMP_23_ID = 1228;
    private static final int _TMP_24_ID = 1229;
    private static final int _LOOP1_25_ID = 1230;
    private static final int _TMP_26_ID = 1231;
    private static final int _TMP_27_ID = 1232;
    private static final int _TMP_28_ID = 1233;
    private static final int _LOOP0_30_ID = 1234;
    private static final int _GATHER_29_ID = 1235;
    private static final int _LOOP0_32_ID = 1236;
    private static final int _GATHER_31_ID = 1237;
    private static final int _TMP_33_ID = 1238;
    private static final int _TMP_34_ID = 1239;
    private static final int _LOOP0_35_ID = 1240;
    private static final int _LOOP1_36_ID = 1241;
    private static final int _TMP_37_ID = 1242;
    private static final int _LOOP0_39_ID = 1243;
    private static final int _GATHER_38_ID = 1244;
    private static final int _TMP_40_ID = 1245;
    private static final int _LOOP0_42_ID = 1246;
    private static final int _GATHER_41_ID = 1247;
    private static final int _TMP_43_ID = 1248;
    private static final int _TMP_44_ID = 1249;
    private static final int _TMP_45_ID = 1250;
    private static final int _TMP_46_ID = 1251;
    private static final int _TMP_47_ID = 1252;
    private static final int _TMP_48_ID = 1253;
    private static final int _TMP_49_ID = 1254;
    private static final int _TMP_50_ID = 1255;
    private static final int _LOOP0_52_ID = 1256;
    private static final int _GATHER_51_ID = 1257;
    private static final int _LOOP0_54_ID = 1258;
    private static final int _GATHER_53_ID = 1259;
    private static final int _TMP_55_ID = 1260;
    private static final int _LOOP0_57_ID = 1261;
    private static final int _GATHER_56_ID = 1262;
    private static final int _LOOP0_59_ID = 1263;
    private static final int _GATHER_58_ID = 1264;
    private static final int _TMP_60_ID = 1265;
    private static final int _TMP_61_ID = 1266;
    private static final int _LOOP1_62_ID = 1267;
    private static final int _TMP_63_ID = 1268;
    private static final int _TMP_64_ID = 1269;
    private static final int _TMP_65_ID = 1270;
    private static final int _LOOP1_66_ID = 1271;
    private static final int _LOOP0_68_ID = 1272;
    private static final int _GATHER_67_ID = 1273;
    private static final int _TMP_69_ID = 1274;
    private static final int _TMP_70_ID = 1275;
    private static final int _TMP_71_ID = 1276;
    private static final int _LOOP0_73_ID = 1277;
    private static final int _GATHER_72_ID = 1278;
    private static final int _TMP_74_ID = 1279;
    private static final int _LOOP0_76_ID = 1280;
    private static final int _GATHER_75_ID = 1281;
    private static final int _TMP_77_ID = 1282;
    private static final int _LOOP0_79_ID = 1283;
    private static final int _GATHER_78_ID = 1284;
    private static final int _LOOP0_81_ID = 1285;
    private static final int _GATHER_80_ID = 1286;
    private static final int _TMP_82_ID = 1287;
    private static final int _TMP_83_ID = 1288;
    private static final int _TMP_84_ID = 1289;
    private static final int _TMP_85_ID = 1290;
    private static final int _TMP_86_ID = 1291;
    private static final int _TMP_87_ID = 1292;
    private static final int _TMP_88_ID = 1293;
    private static final int _TMP_89_ID = 1294;
    private static final int _TMP_90_ID = 1295;
    private static final int _LOOP0_91_ID = 1296;
    private static final int _LOOP0_92_ID = 1297;
    private static final int _TMP_93_ID = 1298;
    private static final int _LOOP0_94_ID = 1299;
    private static final int _TMP_95_ID = 1300;
    private static final int _LOOP1_96_ID = 1301;
    private static final int _LOOP0_97_ID = 1302;
    private static final int _TMP_98_ID = 1303;
    private static final int _LOOP1_99_ID = 1304;
    private static final int _TMP_100_ID = 1305;
    private static final int _LOOP1_101_ID = 1306;
    private static final int _LOOP1_102_ID = 1307;
    private static final int _LOOP0_103_ID = 1308;
    private static final int _LOOP1_104_ID = 1309;
    private static final int _LOOP0_105_ID = 1310;
    private static final int _LOOP1_106_ID = 1311;
    private static final int _LOOP0_107_ID = 1312;
    private static final int _TMP_108_ID = 1313;
    private static final int _LOOP1_109_ID = 1314;
    private static final int _TMP_110_ID = 1315;
    private static final int _LOOP1_111_ID = 1316;
    private static final int _TMP_112_ID = 1317;
    private static final int _LOOP1_113_ID = 1318;
    private static final int _TMP_114_ID = 1319;
    private static final int _LOOP0_116_ID = 1320;
    private static final int _GATHER_115_ID = 1321;
    private static final int _TMP_117_ID = 1322;
    private static final int _LOOP1_118_ID = 1323;
    private static final int _TMP_119_ID = 1324;
    private static final int _TMP_120_ID = 1325;
    private static final int _LOOP0_121_ID = 1326;
    private static final int _LOOP0_122_ID = 1327;
    private static final int _TMP_123_ID = 1328;
    private static final int _LOOP0_124_ID = 1329;
    private static final int _TMP_125_ID = 1330;
    private static final int _LOOP1_126_ID = 1331;
    private static final int _LOOP0_127_ID = 1332;
    private static final int _TMP_128_ID = 1333;
    private static final int _LOOP1_129_ID = 1334;
    private static final int _TMP_130_ID = 1335;
    private static final int _LOOP1_131_ID = 1336;
    private static final int _LOOP1_132_ID = 1337;
    private static final int _LOOP0_133_ID = 1338;
    private static final int _LOOP1_134_ID = 1339;
    private static final int _LOOP0_135_ID = 1340;
    private static final int _LOOP1_136_ID = 1341;
    private static final int _LOOP0_137_ID = 1342;
    private static final int _TMP_138_ID = 1343;
    private static final int _LOOP1_139_ID = 1344;
    private static final int _TMP_140_ID = 1345;
    private static final int _LOOP1_141_ID = 1346;
    private static final int _LOOP1_142_ID = 1347;
    private static final int _LOOP1_143_ID = 1348;
    private static final int _TMP_144_ID = 1349;
    private static final int _TMP_145_ID = 1350;
    private static final int _LOOP0_147_ID = 1351;
    private static final int _GATHER_146_ID = 1352;
    private static final int _TMP_148_ID = 1353;
    private static final int _TMP_149_ID = 1354;
    private static final int _TMP_150_ID = 1355;
    private static final int _TMP_151_ID = 1356;
    private static final int _TMP_152_ID = 1357;
    private static final int _TMP_153_ID = 1358;
    private static final int _TMP_154_ID = 1359;
    private static final int _LOOP1_155_ID = 1360;
    private static final int _TMP_156_ID = 1361;
    private static final int _TMP_157_ID = 1362;
    private static final int _TMP_158_ID = 1363;
    private static final int _TMP_159_ID = 1364;
    private static final int _LOOP0_161_ID = 1365;
    private static final int _GATHER_160_ID = 1366;
    private static final int _TMP_162_ID = 1367;
    private static final int _LOOP1_163_ID = 1368;
    private static final int _LOOP0_164_ID = 1369;
    private static final int _LOOP0_165_ID = 1370;
    private static final int _TMP_166_ID = 1371;
    private static final int _TMP_167_ID = 1372;
    private static final int _LOOP0_169_ID = 1373;
    private static final int _GATHER_168_ID = 1374;
    private static final int _TMP_170_ID = 1375;
    private static final int _LOOP0_172_ID = 1376;
    private static final int _GATHER_171_ID = 1377;
    private static final int _LOOP0_174_ID = 1378;
    private static final int _GATHER_173_ID = 1379;
    private static final int _LOOP0_176_ID = 1380;
    private static final int _GATHER_175_ID = 1381;
    private static final int _LOOP0_178_ID = 1382;
    private static final int _GATHER_177_ID = 1383;
    private static final int _LOOP0_179_ID = 1384;
    private static final int _TMP_180_ID = 1385;
    private static final int _LOOP0_182_ID = 1386;
    private static final int _GATHER_181_ID = 1387;
    private static final int _TMP_183_ID = 1388;
    private static final int _LOOP1_184_ID = 1389;
    private static final int _TMP_185_ID = 1390;
    private static final int _TMP_186_ID = 1391;
    private static final int _TMP_187_ID = 1392;
    private static final int _TMP_188_ID = 1393;
    private static final int _LOOP0_190_ID = 1394;
    private static final int _GATHER_189_ID = 1395;
    private static final int _TMP_191_ID = 1396;
    private static final int _TMP_192_ID = 1397;
    private static final int _TMP_193_ID = 1398;
    private static final int _LOOP0_195_ID = 1399;
    private static final int _GATHER_194_ID = 1400;
    private static final int _TMP_196_ID = 1401;
    private static final int _TMP_197_ID = 1402;
    private static final int _TMP_198_ID = 1403;
    private static final int _TMP_199_ID = 1404;
    private static final int _TMP_200_ID = 1405;
    private static final int _TMP_201_ID = 1406;
    private static final int _TMP_202_ID = 1407;
    private static final int _TMP_203_ID = 1408;
    private static final int _TMP_204_ID = 1409;
    private static final int _LOOP0_205_ID = 1410;
    private static final int _LOOP0_206_ID = 1411;
    private static final int _LOOP0_207_ID = 1412;
    private static final int _TMP_208_ID = 1413;
    private static final int _TMP_209_ID = 1414;
    private static final int _TMP_210_ID = 1415;
    private static final int _TMP_211_ID = 1416;
    private static final int _LOOP0_212_ID = 1417;
    private static final int _LOOP1_213_ID = 1418;
    private static final int _LOOP0_214_ID = 1419;
    private static final int _LOOP1_215_ID = 1420;
    private static final int _TMP_216_ID = 1421;
    private static final int _TMP_217_ID = 1422;
    private static final int _TMP_218_ID = 1423;
    private static final int _TMP_219_ID = 1424;
    private static final int _LOOP0_221_ID = 1425;
    private static final int _GATHER_220_ID = 1426;
    private static final int _TMP_222_ID = 1427;
    private static final int _LOOP0_224_ID = 1428;
    private static final int _GATHER_223_ID = 1429;
    private static final int _TMP_225_ID = 1430;
    private static final int _TMP_226_ID = 1431;
    private static final int _LOOP0_228_ID = 1432;
    private static final int _GATHER_227_ID = 1433;
    private static final int _TMP_229_ID = 1434;
    private static final int _TMP_230_ID = 1435;
    private static final int _TMP_231_ID = 1436;
    private static final int _TMP_232_ID = 1437;
    private static final int _TMP_233_ID = 1438;
    private static final int _TMP_234_ID = 1439;
    private static final int _TMP_235_ID = 1440;
    private static final int _TMP_236_ID = 1441;
    private static final int _TMP_237_ID = 1442;
    private static final int _TMP_238_ID = 1443;
    private static final int _TMP_239_ID = 1444;
    private static final int _TMP_240_ID = 1445;
    private static final int _TMP_241_ID = 1446;
    private static final int _TMP_242_ID = 1447;
    private static final int _TMP_243_ID = 1448;
    private static final int _TMP_244_ID = 1449;
    private static final int _TMP_245_ID = 1450;
    private static final int _TMP_246_ID = 1451;
    private static final int _TMP_247_ID = 1452;
    private static final int _TMP_248_ID = 1453;
    private static final int _TMP_249_ID = 1454;
    private static final int _TMP_250_ID = 1455;
    private static final int _TMP_251_ID = 1456;
    private static final int _TMP_252_ID = 1457;
    private static final int _TMP_253_ID = 1458;
    private static final int _TMP_254_ID = 1459;

    public Parser(ParserTokenizer tokenizer, NodeFactory factory, FExprParser fexprParser, ParserErrorCallback errorCb) {
        super(tokenizer, factory, fexprParser, errorCb);
    }

    // file: statements? $
    public ModTy file_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, FILE_ID)) {
            _res = (ModTy)cache.getResult(_mark, FILE_ID);
            return (ModTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // statements? $
            StmtTy[] a;
            Token endmarker_var;
            if (
                ((a = _tmp_1_rule()) != null || true)  // statements?
                &&
                (endmarker_var = expect(Token.Kind.ENDMARKER)) != null  // token='ENDMARKER'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createModule(a,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, FILE_ID, _res);
                return (ModTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, FILE_ID, _res);
        return (ModTy)_res;
    }

    // interactive: statement_newline
    public ModTy interactive_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INTERACTIVE_ID)) {
            _res = (ModTy)cache.getResult(_mark, INTERACTIVE_ID);
            return (ModTy)_res;
        }
        { // statement_newline
            StmtTy[] a;
            if (
                (a = statement_newline_rule()) != null  // statement_newline
            )
            {
                // TODO: node.action: _PyAST_Interactive ( a , p -> arena )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Interactive ( a , p -> arena ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INTERACTIVE_ID, _res);
                return (ModTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INTERACTIVE_ID, _res);
        return (ModTy)_res;
    }

    // eval: expressions NEWLINE* $
    public ModTy eval_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, EVAL_ID)) {
            _res = (ModTy)cache.getResult(_mark, EVAL_ID);
            return (ModTy)_res;
        }
        { // expressions NEWLINE* $
            Token[] _loop0_2_var;
            ExprTy a;
            Token endmarker_var;
            if (
                (a = expressions_rule()) != null  // expressions
                &&
                (_loop0_2_var = _loop0_2_rule()) != null  // NEWLINE*
                &&
                (endmarker_var = expect(Token.Kind.ENDMARKER)) != null  // token='ENDMARKER'
            )
            {
                _res = a;
                cache.putResult(_mark, EVAL_ID, _res);
                return (ModTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, EVAL_ID, _res);
        return (ModTy)_res;
    }

    // func_type: '(' type_expressions? ')' '->' expression NEWLINE* $
    public ModTy func_type_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, FUNC_TYPE_ID)) {
            _res = (ModTy)cache.getResult(_mark, FUNC_TYPE_ID);
            return (ModTy)_res;
        }
        { // '(' type_expressions? ')' '->' expression NEWLINE* $
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token[] _loop0_4_var;
            ExprTy[] a;
            ExprTy b;
            Token endmarker_var;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                ((a = _tmp_3_rule()) != null || true)  // type_expressions?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
                &&
                (_literal_2 = expect(51)) != null  // token='->'
                &&
                (b = expression_rule()) != null  // expression
                &&
                (_loop0_4_var = _loop0_4_rule()) != null  // NEWLINE*
                &&
                (endmarker_var = expect(Token.Kind.ENDMARKER)) != null  // token='ENDMARKER'
            )
            {
                // TODO: node.action: _PyAST_FunctionType ( a , b , p -> arena )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_FunctionType ( a , b , p -> arena ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, FUNC_TYPE_ID, _res);
                return (ModTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, FUNC_TYPE_ID, _res);
        return (ModTy)_res;
    }

    // fstring: star_expressions
    public ExprTy fstring_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, FSTRING_ID)) {
            _res = (ExprTy)cache.getResult(_mark, FSTRING_ID);
            return (ExprTy)_res;
        }
        { // star_expressions
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                cache.putResult(_mark, FSTRING_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, FSTRING_ID, _res);
        return (ExprTy)_res;
    }

    // type_expressions:
    //     | ','.expression+ ',' '*' expression ',' '**' expression
    //     | ','.expression+ ',' '*' expression
    //     | ','.expression+ ',' '**' expression
    //     | '*' expression ',' '**' expression
    //     | '*' expression
    //     | '**' expression
    //     | ','.expression+
    public ExprTy[] type_expressions_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, TYPE_EXPRESSIONS_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, TYPE_EXPRESSIONS_ID);
            return (ExprTy[])_res;
        }
        { // ','.expression+ ',' '*' expression ',' '**' expression
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token _literal_3;
            ExprTy[] a;
            ExprTy b;
            ExprTy c;
            if (
                (a = _gather_5_rule()) != null  // ','.expression+
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                (_literal_1 = expect(16)) != null  // token='*'
                &&
                (b = expression_rule()) != null  // expression
                &&
                (_literal_2 = expect(12)) != null  // token=','
                &&
                (_literal_3 = expect(35)) != null  // token='**'
                &&
                (c = expression_rule()) != null  // expression
            )
            {
                _res = this.appendToEnd(this.appendToEnd(a,b),c);
                cache.putResult(_mark, TYPE_EXPRESSIONS_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // ','.expression+ ',' '*' expression
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            ExprTy b;
            if (
                (a = _gather_7_rule()) != null  // ','.expression+
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                (_literal_1 = expect(16)) != null  // token='*'
                &&
                (b = expression_rule()) != null  // expression
            )
            {
                _res = this.appendToEnd(a,b);
                cache.putResult(_mark, TYPE_EXPRESSIONS_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // ','.expression+ ',' '**' expression
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            ExprTy b;
            if (
                (a = _gather_9_rule()) != null  // ','.expression+
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                (_literal_1 = expect(35)) != null  // token='**'
                &&
                (b = expression_rule()) != null  // expression
            )
            {
                _res = this.appendToEnd(a,b);
                cache.putResult(_mark, TYPE_EXPRESSIONS_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // '*' expression ',' '**' expression
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            ExprTy a;
            ExprTy b;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = expression_rule()) != null  // expression
                &&
                (_literal_1 = expect(12)) != null  // token=','
                &&
                (_literal_2 = expect(35)) != null  // token='**'
                &&
                (b = expression_rule()) != null  // expression
            )
            {
                _res = this.appendToEnd(this.singletonSequence(a),b);
                cache.putResult(_mark, TYPE_EXPRESSIONS_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // '*' expression
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = expression_rule()) != null  // expression
            )
            {
                _res = this.singletonSequence(a);
                cache.putResult(_mark, TYPE_EXPRESSIONS_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // '**' expression
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (a = expression_rule()) != null  // expression
            )
            {
                _res = this.singletonSequence(a);
                cache.putResult(_mark, TYPE_EXPRESSIONS_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // ','.expression+
            ExprTy[] a;
            if (
                (a = (ExprTy[])_gather_11_rule()) != null  // ','.expression+
            )
            {
                _res = a;
                cache.putResult(_mark, TYPE_EXPRESSIONS_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, TYPE_EXPRESSIONS_ID, _res);
        return (ExprTy[])_res;
    }

    // statements: statement+
    public StmtTy[] statements_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STATEMENTS_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, STATEMENTS_ID);
            return (StmtTy[])_res;
        }
        { // statement+
            StmtTy[] a;
            if (
                (a = _loop1_13_rule()) != null  // statement+
            )
            {
                _res = a;
                cache.putResult(_mark, STATEMENTS_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STATEMENTS_ID, _res);
        return (StmtTy[])_res;
    }

    // statement: compound_stmt | simple_stmts
    public StmtTy[] statement_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STATEMENT_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, STATEMENT_ID);
            return (StmtTy[])_res;
        }
        { // compound_stmt
            StmtTy a;
            if (
                (a = compound_stmt_rule()) != null  // compound_stmt
            )
            {
                _res = new StmtTy [ ] {a};
                cache.putResult(_mark, STATEMENT_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // simple_stmts
            StmtTy[] a;
            if (
                (a = (StmtTy[])simple_stmts_rule()) != null  // simple_stmts
            )
            {
                _res = a;
                cache.putResult(_mark, STATEMENT_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STATEMENT_ID, _res);
        return (StmtTy[])_res;
    }

    // statement_newline: compound_stmt NEWLINE | simple_stmts | NEWLINE | $
    public StmtTy[] statement_newline_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STATEMENT_NEWLINE_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, STATEMENT_NEWLINE_ID);
            return (StmtTy[])_res;
        }
        Token startToken = getAndInitializeToken();
        { // compound_stmt NEWLINE
            StmtTy a;
            Token newline_var;
            if (
                (a = compound_stmt_rule()) != null  // compound_stmt
                &&
                (newline_var = expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.singletonSequence(a);
                cache.putResult(_mark, STATEMENT_NEWLINE_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // simple_stmts
            StmtTy[] simple_stmts_var;
            if (
                (simple_stmts_var = simple_stmts_rule()) != null  // simple_stmts
            )
            {
                _res = simple_stmts_var;
                cache.putResult(_mark, STATEMENT_NEWLINE_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // NEWLINE
            Token newline_var;
            if (
                (newline_var = expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = this.singletonSequence(factory.createPass(startToken.startOffset,endToken.endOffset));
                cache.putResult(_mark, STATEMENT_NEWLINE_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // $
            Token endmarker_var;
            if (
                (endmarker_var = expect(Token.Kind.ENDMARKER)) != null  // token='ENDMARKER'
            )
            {
                // TODO: node.action: _PyPegen_interactive_exit ( p )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_interactive_exit ( p ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, STATEMENT_NEWLINE_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STATEMENT_NEWLINE_ID, _res);
        return (StmtTy[])_res;
    }

    // simple_stmts: simple_stmt !';' NEWLINE | ';'.simple_stmt+ ';'? NEWLINE
    public StmtTy[] simple_stmts_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SIMPLE_STMTS_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, SIMPLE_STMTS_ID);
            return (StmtTy[])_res;
        }
        { // simple_stmt !';' NEWLINE
            StmtTy a;
            Token newline_var;
            if (
                (a = simple_stmt_rule()) != null  // simple_stmt
                &&
                genLookahead_expect(false, 13)  // token=';'
                &&
                (newline_var = expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.singletonSequence(a);;
                cache.putResult(_mark, SIMPLE_STMTS_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // ';'.simple_stmt+ ';'? NEWLINE
            Token _opt_var;
            StmtTy[] a;
            Token newline_var;
            if (
                (a = (StmtTy[])_gather_14_rule()) != null  // ';'.simple_stmt+
                &&
                ((_opt_var = _tmp_16_rule()) != null || true)  // ';'?
                &&
                (newline_var = expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = a;
                cache.putResult(_mark, SIMPLE_STMTS_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SIMPLE_STMTS_ID, _res);
        return (StmtTy[])_res;
    }

    // simple_stmt:
    //     | assignment
    //     | star_expressions
    //     | &'return' return_stmt
    //     | &('import' | 'from') import_stmt
    //     | &'raise' raise_stmt
    //     | 'pass'
    //     | &'del' del_stmt
    //     | &'yield' yield_stmt
    //     | &'assert' assert_stmt
    //     | 'break'
    //     | 'continue'
    //     | &'global' global_stmt
    //     | &'nonlocal' nonlocal_stmt
    public StmtTy simple_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SIMPLE_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, SIMPLE_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // assignment
            StmtTy assignment_var;
            if (
                (assignment_var = assignment_rule()) != null  // assignment
            )
            {
                _res = assignment_var;
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            ExprTy e;
            if (
                (e = star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = factory.createExpression(e);
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'return' return_stmt
            StmtTy return_stmt_var;
            if (
                genLookahead_expect(true, 500)  // token='return'
                &&
                (return_stmt_var = return_stmt_rule()) != null  // return_stmt
            )
            {
                _res = return_stmt_var;
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &('import' | 'from') import_stmt
            StmtTy import_stmt_var;
            if (
                genLookahead__tmp_17_rule(true)
                &&
                (import_stmt_var = import_stmt_rule()) != null  // import_stmt
            )
            {
                _res = import_stmt_var;
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'raise' raise_stmt
            StmtTy raise_stmt_var;
            if (
                genLookahead_expect(true, 503)  // token='raise'
                &&
                (raise_stmt_var = raise_stmt_rule()) != null  // raise_stmt
            )
            {
                _res = raise_stmt_var;
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'pass'
            Token _keyword;
            if (
                (_keyword = expect(504)) != null  // token='pass'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createPass(startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'del' del_stmt
            StmtTy del_stmt_var;
            if (
                genLookahead_expect(true, 505)  // token='del'
                &&
                (del_stmt_var = del_stmt_rule()) != null  // del_stmt
            )
            {
                _res = del_stmt_var;
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'yield' yield_stmt
            StmtTy yield_stmt_var;
            if (
                genLookahead_expect(true, 506)  // token='yield'
                &&
                (yield_stmt_var = yield_stmt_rule()) != null  // yield_stmt
            )
            {
                _res = yield_stmt_var;
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'assert' assert_stmt
            StmtTy assert_stmt_var;
            if (
                genLookahead_expect(true, 507)  // token='assert'
                &&
                (assert_stmt_var = assert_stmt_rule()) != null  // assert_stmt
            )
            {
                _res = assert_stmt_var;
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'break'
            Token _keyword;
            if (
                (_keyword = expect(508)) != null  // token='break'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBreak(startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'continue'
            Token _keyword;
            if (
                (_keyword = expect(509)) != null  // token='continue'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createContinue(startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'global' global_stmt
            StmtTy global_stmt_var;
            if (
                genLookahead_expect(true, 510)  // token='global'
                &&
                (global_stmt_var = global_stmt_rule()) != null  // global_stmt
            )
            {
                _res = global_stmt_var;
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'nonlocal' nonlocal_stmt
            StmtTy nonlocal_stmt_var;
            if (
                genLookahead_expect(true, 511)  // token='nonlocal'
                &&
                (nonlocal_stmt_var = nonlocal_stmt_rule()) != null  // nonlocal_stmt
            )
            {
                _res = nonlocal_stmt_var;
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SIMPLE_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // compound_stmt:
    //     | &('def' | '@' | ASYNC) function_def
    //     | &'if' if_stmt
    //     | &('class' | '@') class_def
    //     | &('with' | ASYNC) with_stmt
    //     | &('for' | ASYNC) for_stmt
    //     | &'try' try_stmt
    //     | &'while' while_stmt
    //     | match_stmt
    public StmtTy compound_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, COMPOUND_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, COMPOUND_STMT_ID);
            return (StmtTy)_res;
        }
        { // &('def' | '@' | ASYNC) function_def
            StmtTy function_def_var;
            if (
                genLookahead__tmp_18_rule(true)
                &&
                (function_def_var = function_def_rule()) != null  // function_def
            )
            {
                _res = function_def_var;
                cache.putResult(_mark, COMPOUND_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'if' if_stmt
            StmtTy if_stmt_var;
            if (
                genLookahead_expect(true, 513)  // token='if'
                &&
                (if_stmt_var = if_stmt_rule()) != null  // if_stmt
            )
            {
                _res = if_stmt_var;
                cache.putResult(_mark, COMPOUND_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &('class' | '@') class_def
            StmtTy class_def_var;
            if (
                genLookahead__tmp_19_rule(true)
                &&
                (class_def_var = class_def_rule()) != null  // class_def
            )
            {
                _res = class_def_var;
                cache.putResult(_mark, COMPOUND_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &('with' | ASYNC) with_stmt
            StmtTy with_stmt_var;
            if (
                genLookahead__tmp_20_rule(true)
                &&
                (with_stmt_var = with_stmt_rule()) != null  // with_stmt
            )
            {
                _res = with_stmt_var;
                cache.putResult(_mark, COMPOUND_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &('for' | ASYNC) for_stmt
            StmtTy for_stmt_var;
            if (
                genLookahead__tmp_21_rule(true)
                &&
                (for_stmt_var = for_stmt_rule()) != null  // for_stmt
            )
            {
                _res = for_stmt_var;
                cache.putResult(_mark, COMPOUND_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'try' try_stmt
            StmtTy try_stmt_var;
            if (
                genLookahead_expect(true, 517)  // token='try'
                &&
                (try_stmt_var = try_stmt_rule()) != null  // try_stmt
            )
            {
                _res = try_stmt_var;
                cache.putResult(_mark, COMPOUND_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'while' while_stmt
            StmtTy while_stmt_var;
            if (
                genLookahead_expect(true, 518)  // token='while'
                &&
                (while_stmt_var = while_stmt_rule()) != null  // while_stmt
            )
            {
                _res = while_stmt_var;
                cache.putResult(_mark, COMPOUND_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // match_stmt
            StmtTy match_stmt_var;
            if (
                (match_stmt_var = match_stmt_rule()) != null  // match_stmt
            )
            {
                _res = match_stmt_var;
                cache.putResult(_mark, COMPOUND_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, COMPOUND_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // assignment:
    //     | NAME ':' expression ['=' annotated_rhs]
    //     | ('(' single_target ')' | single_subscript_attribute_target) ':' expression ['=' annotated_rhs]
    //     | ((star_targets '='))+ (yield_expr | star_expressions) !'=' TYPE_COMMENT?
    //     | single_target augassign ~ (yield_expr | star_expressions)
    //     | invalid_assignment
    public StmtTy assignment_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ASSIGNMENT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, ASSIGNMENT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // NAME ':' expression ['=' annotated_rhs]
            Token _literal;
            ExprTy a;
            ExprTy b;
            ExprTy c;
            if (
                (a = name_token()) != null  // NAME
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = expression_rule()) != null  // expression
                &&
                ((c = _tmp_22_rule()) != null || true)  // ['=' annotated_rhs]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAnnAssignment(setExprContext(a,ExprContext.Store),b,(ExprTy)c,true,startToken.startOffset,endToken.endOffset);;
                cache.putResult(_mark, ASSIGNMENT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // ('(' single_target ')' | single_subscript_attribute_target) ':' expression ['=' annotated_rhs]
            Token _literal;
            ExprTy a;
            ExprTy b;
            ExprTy c;
            if (
                (a = _tmp_23_rule()) != null  // '(' single_target ')' | single_subscript_attribute_target
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = expression_rule()) != null  // expression
                &&
                ((c = _tmp_24_rule()) != null || true)  // ['=' annotated_rhs]
            )
            {
                // TODO: node.action: CHECK_VERSION ( stmt_ty , 6 , "Variable annotations syntax is" , _PyAST_AnnAssign ( a , b , c , 0 , EXTRA ) )
                debugMessageln("[33;5;7m!!! TODO: Convert CHECK_VERSION ( stmt_ty , 6 , 'Variable annotations syntax is' , _PyAST_AnnAssign ( a , b , c , 0 , EXTRA ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, ASSIGNMENT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // ((star_targets '='))+ (yield_expr | star_expressions) !'=' TYPE_COMMENT?
            ExprTy[] a;
            ExprTy b;
            Token tc;
            if (
                (a = (ExprTy[])_loop1_25_rule()) != null  // ((star_targets '='))+
                &&
                (b = _tmp_26_rule()) != null  // yield_expr | star_expressions
                &&
                genLookahead_expect(false, 22)  // token='='
                &&
                ((tc = _tmp_27_rule()) != null || true)  // TYPE_COMMENT?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAssignment(a,(ExprTy)b,newTypeComment((Token)tc),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, ASSIGNMENT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // single_target augassign ~ (yield_expr | star_expressions)
            int _cut_var = 0;
            ExprTy a;
            ExprTy.BinOp.Operator b;
            ExprTy c;
            if (
                (a = single_target_rule()) != null  // single_target
                &&
                (b = augassign_rule()) != null  // augassign
                &&
                (_cut_var = 1) != 0
                &&
                (c = _tmp_28_rule()) != null  // yield_expr | star_expressions
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAugAssignment(a,b,(ExprTy)c,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, ASSIGNMENT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        if (callInvalidRules) { // invalid_assignment
            Object invalid_assignment_var;
            if (
                (invalid_assignment_var = invalid_assignment_rule()) != null  // invalid_assignment
            )
            {
                _res = invalid_assignment_var;
                cache.putResult(_mark, ASSIGNMENT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, ASSIGNMENT_ID, _res);
        return (StmtTy)_res;
    }

    // augassign:
    //     | '+='
    //     | '-='
    //     | '*='
    //     | '@='
    //     | '/='
    //     | '%='
    //     | '&='
    //     | '|='
    //     | '^='
    //     | '<<='
    //     | '>>='
    //     | '**='
    //     | '//='
    public ExprTy.BinOp.Operator augassign_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, AUGASSIGN_ID)) {
            _res = (ExprTy.BinOp.Operator)cache.getResult(_mark, AUGASSIGN_ID);
            return (ExprTy.BinOp.Operator)_res;
        }
        { // '+='
            Token _literal;
            if (
                (_literal = expect(36)) != null  // token='+='
            )
            {
                _res = ExprTy.BinOp.Operator.ADD;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        { // '-='
            Token _literal;
            if (
                (_literal = expect(37)) != null  // token='-='
            )
            {
                _res = ExprTy.BinOp.Operator.SUB;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        { // '*='
            Token _literal;
            if (
                (_literal = expect(38)) != null  // token='*='
            )
            {
                _res = ExprTy.BinOp.Operator.MULT;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        { // '@='
            Token _literal;
            if (
                (_literal = expect(50)) != null  // token='@='
            )
            {
                _res = ExprTy.BinOp.Operator.MATMULT;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        { // '/='
            Token _literal;
            if (
                (_literal = expect(39)) != null  // token='/='
            )
            {
                _res = ExprTy.BinOp.Operator.DIV;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        { // '%='
            Token _literal;
            if (
                (_literal = expect(40)) != null  // token='%='
            )
            {
                _res = ExprTy.BinOp.Operator.MOD;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        { // '&='
            Token _literal;
            if (
                (_literal = expect(41)) != null  // token='&='
            )
            {
                _res = ExprTy.BinOp.Operator.BITAND;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        { // '|='
            Token _literal;
            if (
                (_literal = expect(42)) != null  // token='|='
            )
            {
                _res = ExprTy.BinOp.Operator.BITOR;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        { // '^='
            Token _literal;
            if (
                (_literal = expect(43)) != null  // token='^='
            )
            {
                _res = ExprTy.BinOp.Operator.BITXOR;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        { // '<<='
            Token _literal;
            if (
                (_literal = expect(44)) != null  // token='<<='
            )
            {
                _res = ExprTy.BinOp.Operator.LSHIFT;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        { // '>>='
            Token _literal;
            if (
                (_literal = expect(45)) != null  // token='>>='
            )
            {
                _res = ExprTy.BinOp.Operator.RSHIFT;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        { // '**='
            Token _literal;
            if (
                (_literal = expect(46)) != null  // token='**='
            )
            {
                _res = ExprTy.BinOp.Operator.POW;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        { // '//='
            Token _literal;
            if (
                (_literal = expect(48)) != null  // token='//='
            )
            {
                _res = ExprTy.BinOp.Operator.FLOORDIV;
                cache.putResult(_mark, AUGASSIGN_ID, _res);
                return (ExprTy.BinOp.Operator)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, AUGASSIGN_ID, _res);
        return (ExprTy.BinOp.Operator)_res;
    }

    // global_stmt: 'global' ','.NAME+
    public StmtTy global_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, GLOBAL_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, GLOBAL_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'global' ','.NAME+
            Token _keyword;
            ExprTy[] a;
            if (
                (_keyword = expect(510)) != null  // token='global'
                &&
                (a = (ExprTy[])_gather_29_rule()) != null  // ','.NAME+
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createGlobal(extractNames(a),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, GLOBAL_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, GLOBAL_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // nonlocal_stmt: 'nonlocal' ','.NAME+
    public StmtTy nonlocal_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, NONLOCAL_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, NONLOCAL_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'nonlocal' ','.NAME+
            Token _keyword;
            ExprTy[] a;
            if (
                (_keyword = expect(511)) != null  // token='nonlocal'
                &&
                (a = (ExprTy[])_gather_31_rule()) != null  // ','.NAME+
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createNonLocal(extractNames(a),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, NONLOCAL_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, NONLOCAL_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // yield_stmt: yield_expr
    public StmtTy yield_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, YIELD_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, YIELD_STMT_ID);
            return (StmtTy)_res;
        }
        { // yield_expr
            ExprTy y;
            if (
                (y = yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = factory.createExpression(y);
                cache.putResult(_mark, YIELD_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, YIELD_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // assert_stmt: 'assert' expression [',' expression]
    public StmtTy assert_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ASSERT_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, ASSERT_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'assert' expression [',' expression]
            Token _keyword;
            ExprTy a;
            ExprTy b;
            if (
                (_keyword = expect(507)) != null  // token='assert'
                &&
                (a = expression_rule()) != null  // expression
                &&
                ((b = _tmp_33_rule()) != null || true)  // [',' expression]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAssert(a,b,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, ASSERT_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, ASSERT_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // del_stmt: 'del' del_targets &(';' | NEWLINE) | invalid_del_stmt
    public StmtTy del_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DEL_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, DEL_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'del' del_targets &(';' | NEWLINE)
            Token _keyword;
            ExprTy[] a;
            if (
                (_keyword = expect(505)) != null  // token='del'
                &&
                (a = del_targets_rule()) != null  // del_targets
                &&
                genLookahead__tmp_34_rule(true)
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createDelete(a,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, DEL_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_del_stmt
            ExprTy invalid_del_stmt_var;
            if (
                (invalid_del_stmt_var = invalid_del_stmt_rule()) != null  // invalid_del_stmt
            )
            {
                _res = invalid_del_stmt_var;
                cache.putResult(_mark, DEL_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DEL_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // import_stmt: import_name | import_from
    public StmtTy import_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, IMPORT_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, IMPORT_STMT_ID);
            return (StmtTy)_res;
        }
        { // import_name
            StmtTy import_name_var;
            if (
                (import_name_var = import_name_rule()) != null  // import_name
            )
            {
                _res = import_name_var;
                cache.putResult(_mark, IMPORT_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // import_from
            StmtTy import_from_var;
            if (
                (import_from_var = import_from_rule()) != null  // import_from
            )
            {
                _res = import_from_var;
                cache.putResult(_mark, IMPORT_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, IMPORT_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // import_name: 'import' dotted_as_names
    public StmtTy import_name_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, IMPORT_NAME_ID)) {
            _res = (StmtTy)cache.getResult(_mark, IMPORT_NAME_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'import' dotted_as_names
            Token _keyword;
            AliasTy[] a;
            if (
                (_keyword = expect(501)) != null  // token='import'
                &&
                (a = dotted_as_names_rule()) != null  // dotted_as_names
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createImport(a,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, IMPORT_NAME_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, IMPORT_NAME_ID, _res);
        return (StmtTy)_res;
    }

    // import_from:
    //     | 'from' (('.' | '...'))* dotted_name 'import' import_from_targets
    //     | 'from' (('.' | '...'))+ 'import' import_from_targets
    public StmtTy import_from_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, IMPORT_FROM_ID)) {
            _res = (StmtTy)cache.getResult(_mark, IMPORT_FROM_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'from' (('.' | '...'))* dotted_name 'import' import_from_targets
            Token _keyword;
            Token _keyword_1;
            Token[] a;
            ExprTy b;
            AliasTy[] c;
            if (
                (_keyword = expect(502)) != null  // token='from'
                &&
                (a = _loop0_35_rule()) != null  // (('.' | '...'))*
                &&
                (b = dotted_name_rule()) != null  // dotted_name
                &&
                (_keyword_1 = expect(501)) != null  // token='import'
                &&
                (c = import_from_targets_rule()) != null  // import_from_targets
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createImportFrom(((ExprTy.Name)b).id,c,countDots(a),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, IMPORT_FROM_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'from' (('.' | '...'))+ 'import' import_from_targets
            Token _keyword;
            Token _keyword_1;
            Token[] a;
            AliasTy[] b;
            if (
                (_keyword = expect(502)) != null  // token='from'
                &&
                (a = _loop1_36_rule()) != null  // (('.' | '...'))+
                &&
                (_keyword_1 = expect(501)) != null  // token='import'
                &&
                (b = import_from_targets_rule()) != null  // import_from_targets
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createImportFrom(null,b,countDots(a),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, IMPORT_FROM_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, IMPORT_FROM_ID, _res);
        return (StmtTy)_res;
    }

    // import_from_targets:
    //     | '(' import_from_as_names ','? ')'
    //     | import_from_as_names !','
    //     | '*'
    //     | invalid_import_from_targets
    public AliasTy[] import_from_targets_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, IMPORT_FROM_TARGETS_ID)) {
            _res = (AliasTy[])cache.getResult(_mark, IMPORT_FROM_TARGETS_ID);
            return (AliasTy[])_res;
        }
        Token startToken = getAndInitializeToken();
        { // '(' import_from_as_names ','? ')'
            Token _literal;
            Token _literal_1;
            Token _opt_var;
            AliasTy[] a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (a = import_from_as_names_rule()) != null  // import_from_as_names
                &&
                ((_opt_var = _tmp_37_rule()) != null || true)  // ','?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                _res = a;
                cache.putResult(_mark, IMPORT_FROM_TARGETS_ID, _res);
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        { // import_from_as_names !','
            AliasTy[] import_from_as_names_var;
            if (
                (import_from_as_names_var = import_from_as_names_rule()) != null  // import_from_as_names
                &&
                genLookahead_expect(false, 12)  // token=','
            )
            {
                _res = import_from_as_names_var;
                cache.putResult(_mark, IMPORT_FROM_TARGETS_ID, _res);
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        { // '*'
            Token _literal;
            if (
                (_literal = expect(16)) != null  // token='*'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = this.singletonSequence(factory.createAlias("*",null,startToken.startOffset,endToken.endOffset));
                cache.putResult(_mark, IMPORT_FROM_TARGETS_ID, _res);
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_import_from_targets
            AliasTy[] invalid_import_from_targets_var;
            if (
                (invalid_import_from_targets_var = invalid_import_from_targets_rule()) != null  // invalid_import_from_targets
            )
            {
                _res = invalid_import_from_targets_var;
                cache.putResult(_mark, IMPORT_FROM_TARGETS_ID, _res);
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, IMPORT_FROM_TARGETS_ID, _res);
        return (AliasTy[])_res;
    }

    // import_from_as_names: ','.import_from_as_name+
    public AliasTy[] import_from_as_names_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, IMPORT_FROM_AS_NAMES_ID)) {
            _res = (AliasTy[])cache.getResult(_mark, IMPORT_FROM_AS_NAMES_ID);
            return (AliasTy[])_res;
        }
        { // ','.import_from_as_name+
            AliasTy[] a;
            if (
                (a = (AliasTy[])_gather_38_rule()) != null  // ','.import_from_as_name+
            )
            {
                _res = a;
                cache.putResult(_mark, IMPORT_FROM_AS_NAMES_ID, _res);
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, IMPORT_FROM_AS_NAMES_ID, _res);
        return (AliasTy[])_res;
    }

    // import_from_as_name: NAME ['as' NAME]
    public AliasTy import_from_as_name_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, IMPORT_FROM_AS_NAME_ID)) {
            _res = (AliasTy)cache.getResult(_mark, IMPORT_FROM_AS_NAME_ID);
            return (AliasTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // NAME ['as' NAME]
            ExprTy a;
            ExprTy b;
            if (
                (a = name_token()) != null  // NAME
                &&
                ((b = _tmp_40_rule()) != null || true)  // ['as' NAME]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAlias(((ExprTy.Name)a).id,b == null ? null :((ExprTy.Name)b).id,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, IMPORT_FROM_AS_NAME_ID, _res);
                return (AliasTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, IMPORT_FROM_AS_NAME_ID, _res);
        return (AliasTy)_res;
    }

    // dotted_as_names: ','.dotted_as_name+
    public AliasTy[] dotted_as_names_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DOTTED_AS_NAMES_ID)) {
            _res = (AliasTy[])cache.getResult(_mark, DOTTED_AS_NAMES_ID);
            return (AliasTy[])_res;
        }
        { // ','.dotted_as_name+
            AliasTy[] a;
            if (
                (a = (AliasTy[])_gather_41_rule()) != null  // ','.dotted_as_name+
            )
            {
                _res = a;
                cache.putResult(_mark, DOTTED_AS_NAMES_ID, _res);
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DOTTED_AS_NAMES_ID, _res);
        return (AliasTy[])_res;
    }

    // dotted_as_name: dotted_name ['as' NAME]
    public AliasTy dotted_as_name_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DOTTED_AS_NAME_ID)) {
            _res = (AliasTy)cache.getResult(_mark, DOTTED_AS_NAME_ID);
            return (AliasTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // dotted_name ['as' NAME]
            ExprTy a;
            ExprTy b;
            if (
                (a = dotted_name_rule()) != null  // dotted_name
                &&
                ((b = _tmp_43_rule()) != null || true)  // ['as' NAME]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAlias(((ExprTy.Name)a).id,b == null ? null :((ExprTy.Name)b).id,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, DOTTED_AS_NAME_ID, _res);
                return (AliasTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DOTTED_AS_NAME_ID, _res);
        return (AliasTy)_res;
    }

    // Left-recursive
    // dotted_name: dotted_name '.' NAME | NAME
    public ExprTy dotted_name_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DOTTED_NAME_ID)) {
            _res = cache.getResult(_mark, DOTTED_NAME_ID);
            return (ExprTy)_res;
        }
        int _resmark = mark();
        while (true) {
            cache.putResult(_mark, DOTTED_NAME_ID, _res);
            reset(_mark);
            Object _raw = dotted_name_raw();
            if (_raw == null || mark() <= _resmark)
                break;
            _resmark = mark();
            _res = _raw;
        }
        reset(_resmark);
        return (ExprTy)_res;
    }
    private ExprTy dotted_name_raw()
    {
        int _mark = mark();
        Object _res = null;
        { // dotted_name '.' NAME
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = dotted_name_rule()) != null  // dotted_name
                &&
                (_literal = expect(23)) != null  // token='.'
                &&
                (b = name_token()) != null  // NAME
            )
            {
                _res = this.joinNamesWithDot(a,b);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // NAME
            ExprTy name_var;
            if (
                (name_var = name_token()) != null  // NAME
            )
            {
                _res = name_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // if_stmt:
    //     | 'if' named_expression ':' block elif_stmt
    //     | 'if' named_expression ':' block else_block?
    //     | invalid_if_stmt
    public StmtTy if_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, IF_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, IF_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'if' named_expression ':' block elif_stmt
            Token _keyword;
            Token _literal;
            ExprTy a;
            StmtTy[] b;
            StmtTy c;
            if (
                (_keyword = expect(513)) != null  // token='if'
                &&
                (a = named_expression_rule()) != null  // named_expression
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                (c = elif_stmt_rule()) != null  // elif_stmt
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createIf(a,b,this.singletonSequence(c),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, IF_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'if' named_expression ':' block else_block?
            Token _keyword;
            Token _literal;
            ExprTy a;
            StmtTy[] b;
            StmtTy[] c;
            if (
                (_keyword = expect(513)) != null  // token='if'
                &&
                (a = named_expression_rule()) != null  // named_expression
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                ((c = _tmp_44_rule()) != null || true)  // else_block?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createIf(a,b,c,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, IF_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_if_stmt
            ExprTy invalid_if_stmt_var;
            if (
                (invalid_if_stmt_var = invalid_if_stmt_rule()) != null  // invalid_if_stmt
            )
            {
                _res = invalid_if_stmt_var;
                cache.putResult(_mark, IF_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, IF_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // elif_stmt:
    //     | 'elif' named_expression ':' block elif_stmt
    //     | 'elif' named_expression ':' block else_block?
    //     | invalid_elif_stmt
    public StmtTy elif_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ELIF_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, ELIF_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'elif' named_expression ':' block elif_stmt
            Token _keyword;
            Token _literal;
            ExprTy a;
            StmtTy[] b;
            StmtTy c;
            if (
                (_keyword = expect(520)) != null  // token='elif'
                &&
                (a = named_expression_rule()) != null  // named_expression
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                (c = elif_stmt_rule()) != null  // elif_stmt
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createIf(a,b,this.singletonSequence(c),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, ELIF_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'elif' named_expression ':' block else_block?
            Token _keyword;
            Token _literal;
            ExprTy a;
            StmtTy[] b;
            StmtTy[] c;
            if (
                (_keyword = expect(520)) != null  // token='elif'
                &&
                (a = named_expression_rule()) != null  // named_expression
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                ((c = _tmp_45_rule()) != null || true)  // else_block?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createIf(a,b,c,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, ELIF_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_elif_stmt
            ExprTy invalid_elif_stmt_var;
            if (
                (invalid_elif_stmt_var = invalid_elif_stmt_rule()) != null  // invalid_elif_stmt
            )
            {
                _res = invalid_elif_stmt_var;
                cache.putResult(_mark, ELIF_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, ELIF_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // else_block: 'else' &&':' block
    public StmtTy[] else_block_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ELSE_BLOCK_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, ELSE_BLOCK_ID);
            return (StmtTy[])_res;
        }
        { // 'else' &&':' block
            Token _keyword;
            Token _literal;
            StmtTy[] b;
            if (
                (_keyword = expect(521)) != null  // token='else'
                &&
                (_literal = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                (b = block_rule()) != null  // block
            )
            {
                _res = b;
                cache.putResult(_mark, ELSE_BLOCK_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, ELSE_BLOCK_ID, _res);
        return (StmtTy[])_res;
    }

    // while_stmt: 'while' named_expression ':' block else_block? | invalid_while_stmt
    public StmtTy while_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, WHILE_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, WHILE_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'while' named_expression ':' block else_block?
            Token _keyword;
            Token _literal;
            ExprTy a;
            StmtTy[] b;
            StmtTy[] c;
            if (
                (_keyword = expect(518)) != null  // token='while'
                &&
                (a = named_expression_rule()) != null  // named_expression
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                ((c = _tmp_46_rule()) != null || true)  // else_block?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createWhile(a,b,c,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, WHILE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_while_stmt
            ExprTy invalid_while_stmt_var;
            if (
                (invalid_while_stmt_var = invalid_while_stmt_rule()) != null  // invalid_while_stmt
            )
            {
                _res = invalid_while_stmt_var;
                cache.putResult(_mark, WHILE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, WHILE_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // for_stmt:
    //     | 'for' star_targets 'in' ~ star_expressions &&':' TYPE_COMMENT? block else_block?
    //     | ASYNC 'for' star_targets 'in' ~ star_expressions &&':' TYPE_COMMENT? block else_block?
    //     | invalid_for_target
    public StmtTy for_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, FOR_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, FOR_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'for' star_targets 'in' ~ star_expressions &&':' TYPE_COMMENT? block else_block?
            int _cut_var = 0;
            Token _keyword;
            Token _keyword_1;
            Token _literal;
            StmtTy[] b;
            StmtTy[] el;
            ExprTy ex;
            ExprTy t;
            Token tc;
            if (
                (_keyword = expect(516)) != null  // token='for'
                &&
                (t = star_targets_rule()) != null  // star_targets
                &&
                (_keyword_1 = expect(522)) != null  // token='in'
                &&
                (_cut_var = 1) != 0
                &&
                (ex = star_expressions_rule()) != null  // star_expressions
                &&
                (_literal = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                ((tc = _tmp_47_rule()) != null || true)  // TYPE_COMMENT?
                &&
                (b = block_rule()) != null  // block
                &&
                ((el = _tmp_48_rule()) != null || true)  // else_block?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createFor(t,ex,b,el,newTypeComment(tc),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, FOR_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        { // ASYNC 'for' star_targets 'in' ~ star_expressions &&':' TYPE_COMMENT? block else_block?
            int _cut_var = 0;
            Token _keyword;
            Token _keyword_1;
            Token _literal;
            Token async_var;
            StmtTy[] b;
            StmtTy[] el;
            ExprTy ex;
            ExprTy t;
            Token tc;
            if (
                (async_var = expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
                &&
                (_keyword = expect(516)) != null  // token='for'
                &&
                (t = star_targets_rule()) != null  // star_targets
                &&
                (_keyword_1 = expect(522)) != null  // token='in'
                &&
                (_cut_var = 1) != 0
                &&
                (ex = star_expressions_rule()) != null  // star_expressions
                &&
                (_literal = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                ((tc = _tmp_49_rule()) != null || true)  // TYPE_COMMENT?
                &&
                (b = block_rule()) != null  // block
                &&
                ((el = _tmp_50_rule()) != null || true)  // else_block?
            )
            {
                // TODO: node.action: CHECK_VERSION ( stmt_ty , 5 , "Async for loops are" , _PyAST_AsyncFor ( t , ex , b , el , NEW_TYPE_COMMENT ( p , tc ) , EXTRA ) )
                debugMessageln("[33;5;7m!!! TODO: Convert CHECK_VERSION ( stmt_ty , 5 , 'Async for loops are' , _PyAST_AsyncFor ( t , ex , b , el , NEW_TYPE_COMMENT ( p , tc ) , EXTRA ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, FOR_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        if (callInvalidRules) { // invalid_for_target
            ExprTy invalid_for_target_var;
            if (
                (invalid_for_target_var = invalid_for_target_rule()) != null  // invalid_for_target
            )
            {
                _res = invalid_for_target_var;
                cache.putResult(_mark, FOR_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, FOR_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // with_stmt:
    //     | 'with' '(' ','.with_item+ ','? ')' ':' block
    //     | 'with' ','.with_item+ ':' TYPE_COMMENT? block
    //     | ASYNC 'with' '(' ','.with_item+ ','? ')' ':' block
    //     | ASYNC 'with' ','.with_item+ ':' TYPE_COMMENT? block
    //     | invalid_with_stmt
    public StmtTy with_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, WITH_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, WITH_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'with' '(' ','.with_item+ ','? ')' ':' block
            Token _keyword;
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token _opt_var;
            StmtTy.With.Item[] a;
            StmtTy[] b;
            if (
                (_keyword = expect(515)) != null  // token='with'
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                (a = (StmtTy.With.Item[])_gather_51_rule()) != null  // ','.with_item+
                &&
                ((_opt_var = expect(12)) != null || true)  // ','?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
                &&
                (_literal_2 = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
            )
            {
                _res = factory.createWith(a,b,null,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, WITH_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'with' ','.with_item+ ':' TYPE_COMMENT? block
            Token _keyword;
            Token _literal;
            StmtTy.With.Item[] a;
            StmtTy[] b;
            Token tc;
            if (
                (_keyword = expect(515)) != null  // token='with'
                &&
                (a = (StmtTy.With.Item[])_gather_53_rule()) != null  // ','.with_item+
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                ((tc = _tmp_55_rule()) != null || true)  // TYPE_COMMENT?
                &&
                (b = block_rule()) != null  // block
            )
            {
                _res = factory.createWith(a,b,newTypeComment((Token)tc),startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, WITH_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // ASYNC 'with' '(' ','.with_item+ ','? ')' ':' block
            Token _keyword;
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token _opt_var;
            StmtTy.With.Item[] a;
            Token async_var;
            StmtTy[] b;
            if (
                (async_var = expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
                &&
                (_keyword = expect(515)) != null  // token='with'
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                (a = (StmtTy.With.Item[])_gather_56_rule()) != null  // ','.with_item+
                &&
                ((_opt_var = expect(12)) != null || true)  // ','?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
                &&
                (_literal_2 = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
            )
            {
                // TODO: node.action: CHECK_VERSION ( stmt_ty , 5 , "Async with statements are" , _PyAST_AsyncWith ( a , b , NULL , EXTRA ) )
                debugMessageln("[33;5;7m!!! TODO: Convert CHECK_VERSION ( stmt_ty , 5 , 'Async with statements are' , _PyAST_AsyncWith ( a , b , NULL , EXTRA ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, WITH_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // ASYNC 'with' ','.with_item+ ':' TYPE_COMMENT? block
            Token _keyword;
            Token _literal;
            StmtTy.With.Item[] a;
            Token async_var;
            StmtTy[] b;
            Token tc;
            if (
                (async_var = expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
                &&
                (_keyword = expect(515)) != null  // token='with'
                &&
                (a = (StmtTy.With.Item[])_gather_58_rule()) != null  // ','.with_item+
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                ((tc = _tmp_60_rule()) != null || true)  // TYPE_COMMENT?
                &&
                (b = block_rule()) != null  // block
            )
            {
                // TODO: node.action: CHECK_VERSION ( stmt_ty , 5 , "Async with statements are" , _PyAST_AsyncWith ( a , b , NEW_TYPE_COMMENT ( p , tc ) , EXTRA ) )
                debugMessageln("[33;5;7m!!! TODO: Convert CHECK_VERSION ( stmt_ty , 5 , 'Async with statements are' , _PyAST_AsyncWith ( a , b , NEW_TYPE_COMMENT ( p , tc ) , EXTRA ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, WITH_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_with_stmt
            ExprTy[] invalid_with_stmt_var;
            if (
                (invalid_with_stmt_var = invalid_with_stmt_rule()) != null  // invalid_with_stmt
            )
            {
                _res = invalid_with_stmt_var;
                cache.putResult(_mark, WITH_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, WITH_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // with_item:
    //     | expression 'as' star_target &(',' | ')' | ':')
    //     | invalid_with_item
    //     | expression
    public StmtTy.With.Item with_item_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, WITH_ITEM_ID)) {
            _res = (StmtTy.With.Item)cache.getResult(_mark, WITH_ITEM_ID);
            return (StmtTy.With.Item)_res;
        }
        Token startToken = getAndInitializeToken();
        { // expression 'as' star_target &(',' | ')' | ':')
            Token _keyword;
            ExprTy e;
            ExprTy t;
            if (
                (e = expression_rule()) != null  // expression
                &&
                (_keyword = expect(519)) != null  // token='as'
                &&
                (t = star_target_rule()) != null  // star_target
                &&
                genLookahead__tmp_61_rule(true)
            )
            {
                _res = factory.createWithItem(e,t,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, WITH_ITEM_ID, _res);
                return (StmtTy.With.Item)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_with_item
            ExprTy invalid_with_item_var;
            if (
                (invalid_with_item_var = invalid_with_item_rule()) != null  // invalid_with_item
            )
            {
                _res = invalid_with_item_var;
                cache.putResult(_mark, WITH_ITEM_ID, _res);
                return (StmtTy.With.Item)_res;
            }
            reset(_mark);
        }
        { // expression
            ExprTy e;
            if (
                (e = expression_rule()) != null  // expression
            )
            {
                _res = factory.createWithItem(e,null,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, WITH_ITEM_ID, _res);
                return (StmtTy.With.Item)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, WITH_ITEM_ID, _res);
        return (StmtTy.With.Item)_res;
    }

    // try_stmt:
    //     | 'try' &&':' block finally_block
    //     | 'try' &&':' block except_block+ else_block? finally_block?
    public StmtTy try_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, TRY_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, TRY_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'try' &&':' block finally_block
            Token _keyword;
            Token _literal;
            StmtTy[] b;
            StmtTy[] f;
            if (
                (_keyword = expect(517)) != null  // token='try'
                &&
                (_literal = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                (f = finally_block_rule()) != null  // finally_block
            )
            {
                _res = factory.createTry(b,null,null,f,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, TRY_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'try' &&':' block except_block+ else_block? finally_block?
            Token _keyword;
            Token _literal;
            StmtTy[] b;
            StmtTy[] el;
            StmtTy.Try.ExceptHandler[] ex;
            StmtTy[] f;
            if (
                (_keyword = expect(517)) != null  // token='try'
                &&
                (_literal = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                (ex = (StmtTy.Try.ExceptHandler[])_loop1_62_rule()) != null  // except_block+
                &&
                ((el = _tmp_63_rule()) != null || true)  // else_block?
                &&
                ((f = _tmp_64_rule()) != null || true)  // finally_block?
            )
            {
                _res = factory.createTry(b,ex,el,f,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, TRY_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, TRY_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // except_block:
    //     | 'except' expression ['as' NAME] ':' block
    //     | 'except' ':' block
    //     | invalid_except_block
    public StmtTy.Try.ExceptHandler except_block_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, EXCEPT_BLOCK_ID)) {
            _res = (StmtTy.Try.ExceptHandler)cache.getResult(_mark, EXCEPT_BLOCK_ID);
            return (StmtTy.Try.ExceptHandler)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'except' expression ['as' NAME] ':' block
            Token _keyword;
            Token _literal;
            StmtTy[] b;
            ExprTy e;
            ExprTy t;
            if (
                (_keyword = expect(523)) != null  // token='except'
                &&
                (e = expression_rule()) != null  // expression
                &&
                ((t = _tmp_65_rule()) != null || true)  // ['as' NAME]
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
            )
            {
                _res = factory.createExceptHandler(e,t != null ?((ExprTy.Name)t).id : null,b,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, EXCEPT_BLOCK_ID, _res);
                return (StmtTy.Try.ExceptHandler)_res;
            }
            reset(_mark);
        }
        { // 'except' ':' block
            Token _keyword;
            Token _literal;
            StmtTy[] b;
            if (
                (_keyword = expect(523)) != null  // token='except'
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
            )
            {
                _res = factory.createExceptHandler(null,null,b,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, EXCEPT_BLOCK_ID, _res);
                return (StmtTy.Try.ExceptHandler)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_except_block
            Object invalid_except_block_var;
            if (
                (invalid_except_block_var = invalid_except_block_rule()) != null  // invalid_except_block
            )
            {
                _res = invalid_except_block_var;
                cache.putResult(_mark, EXCEPT_BLOCK_ID, _res);
                return (StmtTy.Try.ExceptHandler)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, EXCEPT_BLOCK_ID, _res);
        return (StmtTy.Try.ExceptHandler)_res;
    }

    // finally_block: 'finally' ':' block
    public StmtTy[] finally_block_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, FINALLY_BLOCK_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, FINALLY_BLOCK_ID);
            return (StmtTy[])_res;
        }
        { // 'finally' ':' block
            Token _keyword;
            Token _literal;
            StmtTy[] a;
            if (
                (_keyword = expect(524)) != null  // token='finally'
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (a = block_rule()) != null  // block
            )
            {
                _res = a;
                cache.putResult(_mark, FINALLY_BLOCK_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, FINALLY_BLOCK_ID, _res);
        return (StmtTy[])_res;
    }

    // match_stmt:
    //     | "match" subject_expr ':' NEWLINE INDENT case_block+ DEDENT
    //     | invalid_match_stmt
    public StmtTy match_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, MATCH_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, MATCH_STMT_ID);
            return (StmtTy)_res;
        }
        { // "match" subject_expr ':' NEWLINE INDENT case_block+ DEDENT
            ExprTy _keyword;
            Token _literal;
            StmtTy.Match.Case[] cases;
            Token dedent_var;
            Token indent_var;
            Token newline_var;
            ExprTy subject;
            if (
                (_keyword = expect_SOFT_KEYWORD("match")) != null  // soft_keyword='"match"'
                &&
                (subject = subject_expr_rule()) != null  // subject_expr
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (newline_var = expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                (indent_var = expect(Token.Kind.INDENT)) != null  // token='INDENT'
                &&
                (cases = (StmtTy.Match.Case[])_loop1_66_rule()) != null  // case_block+
                &&
                (dedent_var = expect(Token.Kind.DEDENT)) != null  // token='DEDENT'
            )
            {
                // TODO: node.action: CHECK_VERSION ( stmt_ty , 10 , "Pattern matching is" , _PyAST_Match ( subject , cases , EXTRA ) )
                debugMessageln("[33;5;7m!!! TODO: Convert CHECK_VERSION ( stmt_ty , 10 , 'Pattern matching is' , _PyAST_Match ( subject , cases , EXTRA ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, MATCH_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_match_stmt
            ExprTy invalid_match_stmt_var;
            if (
                (invalid_match_stmt_var = invalid_match_stmt_rule()) != null  // invalid_match_stmt
            )
            {
                _res = invalid_match_stmt_var;
                cache.putResult(_mark, MATCH_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, MATCH_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // subject_expr: star_named_expression ',' star_named_expressions? | named_expression
    public ExprTy subject_expr_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SUBJECT_EXPR_ID)) {
            _res = (ExprTy)cache.getResult(_mark, SUBJECT_EXPR_ID);
            return (ExprTy)_res;
        }
        { // star_named_expression ',' star_named_expressions?
            Token _literal;
            ExprTy value;
            ExprTy[] values;
            if (
                (value = star_named_expression_rule()) != null  // star_named_expression
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                ((values = star_named_expressions_rule()) != null || true)  // star_named_expressions?
            )
            {
                // TODO: node.action: _PyAST_Tuple ( CHECK ( asdl_expr_seq * , this . insertInFront ( value , values ) ) , Load , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Tuple ( CHECK ( asdl_expr_seq * , this . insertInFront ( value , values ) ) , Load , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, SUBJECT_EXPR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // named_expression
            ExprTy named_expression_var;
            if (
                (named_expression_var = named_expression_rule()) != null  // named_expression
            )
            {
                _res = named_expression_var;
                cache.putResult(_mark, SUBJECT_EXPR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SUBJECT_EXPR_ID, _res);
        return (ExprTy)_res;
    }

    // case_block: "case" patterns guard? ':' block | invalid_case_block
    public StmtTy.Match.Case case_block_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, CASE_BLOCK_ID)) {
            _res = (StmtTy.Match.Case)cache.getResult(_mark, CASE_BLOCK_ID);
            return (StmtTy.Match.Case)_res;
        }
        { // "case" patterns guard? ':' block
            ExprTy _keyword;
            Token _literal;
            StmtTy[] body;
            ExprTy guard;
            ExprTy pattern;
            if (
                (_keyword = expect_SOFT_KEYWORD("case")) != null  // soft_keyword='"case"'
                &&
                (pattern = patterns_rule()) != null  // patterns
                &&
                ((guard = guard_rule()) != null || true)  // guard?
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (body = block_rule()) != null  // block
            )
            {
                // TODO: node.action: _PyAST_match_case ( pattern , guard , body , p -> arena )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_match_case ( pattern , guard , body , p -> arena ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, CASE_BLOCK_ID, _res);
                return (StmtTy.Match.Case)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_case_block
            ExprTy invalid_case_block_var;
            if (
                (invalid_case_block_var = invalid_case_block_rule()) != null  // invalid_case_block
            )
            {
                _res = invalid_case_block_var;
                cache.putResult(_mark, CASE_BLOCK_ID, _res);
                return (StmtTy.Match.Case)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, CASE_BLOCK_ID, _res);
        return (StmtTy.Match.Case)_res;
    }

    // guard: 'if' named_expression
    public ExprTy guard_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, GUARD_ID)) {
            _res = (ExprTy)cache.getResult(_mark, GUARD_ID);
            return (ExprTy)_res;
        }
        { // 'if' named_expression
            Token _keyword;
            ExprTy guard;
            if (
                (_keyword = expect(513)) != null  // token='if'
                &&
                (guard = named_expression_rule()) != null  // named_expression
            )
            {
                // TODO: node.action: guard
                debugMessageln("[33;5;7m!!! TODO: Convert guard to Java !!![0m");
                _res = null;
                cache.putResult(_mark, GUARD_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, GUARD_ID, _res);
        return (ExprTy)_res;
    }

    // patterns: open_sequence_pattern | pattern
    public ExprTy patterns_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, PATTERNS_ID)) {
            _res = (ExprTy)cache.getResult(_mark, PATTERNS_ID);
            return (ExprTy)_res;
        }
        { // open_sequence_pattern
            ExprTy[] values;
            if (
                (values = (ExprTy[])open_sequence_pattern_rule()) != null  // open_sequence_pattern
            )
            {
                // TODO: node.action: _PyAST_Tuple ( values , Load , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Tuple ( values , Load , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, PATTERNS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // pattern
            ExprTy pattern_var;
            if (
                (pattern_var = pattern_rule()) != null  // pattern
            )
            {
                _res = pattern_var;
                cache.putResult(_mark, PATTERNS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, PATTERNS_ID, _res);
        return (ExprTy)_res;
    }

    // pattern: as_pattern | or_pattern
    public ExprTy pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, PATTERN_ID);
            return (ExprTy)_res;
        }
        { // as_pattern
            ExprTy as_pattern_var;
            if (
                (as_pattern_var = as_pattern_rule()) != null  // as_pattern
            )
            {
                _res = as_pattern_var;
                cache.putResult(_mark, PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // or_pattern
            ExprTy or_pattern_var;
            if (
                (or_pattern_var = or_pattern_rule()) != null  // or_pattern
            )
            {
                _res = or_pattern_var;
                cache.putResult(_mark, PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // as_pattern: or_pattern 'as' capture_pattern
    public ExprTy as_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, AS_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, AS_PATTERN_ID);
            return (ExprTy)_res;
        }
        { // or_pattern 'as' capture_pattern
            Token _keyword;
            ExprTy pattern;
            ExprTy target;
            if (
                (pattern = or_pattern_rule()) != null  // or_pattern
                &&
                (_keyword = expect(519)) != null  // token='as'
                &&
                (target = capture_pattern_rule()) != null  // capture_pattern
            )
            {
                // TODO: node.action: _PyAST_MatchAs ( pattern , target -> v . Name . id , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_MatchAs ( pattern , target -> v . Name . id , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, AS_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, AS_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // or_pattern: '|'.closed_pattern+
    public ExprTy or_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, OR_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, OR_PATTERN_ID);
            return (ExprTy)_res;
        }
        { // '|'.closed_pattern+
            ExprTy[] patterns;
            if (
                (patterns = (ExprTy[])_gather_67_rule()) != null  // '|'.closed_pattern+
            )
            {
                // TODO: node.action: asdl_seq_LEN ( patterns ) == 1 ? asdl_seq_GET ( patterns , 0 ) : _PyAST_MatchOr ( patterns , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert asdl_seq_LEN ( patterns ) == 1 ? asdl_seq_GET ( patterns , 0 ) : _PyAST_MatchOr ( patterns , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, OR_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, OR_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // closed_pattern:
    //     | literal_pattern
    //     | capture_pattern
    //     | wildcard_pattern
    //     | value_pattern
    //     | group_pattern
    //     | sequence_pattern
    //     | mapping_pattern
    //     | class_pattern
    public ExprTy closed_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, CLOSED_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, CLOSED_PATTERN_ID);
            return (ExprTy)_res;
        }
        { // literal_pattern
            ExprTy literal_pattern_var;
            if (
                (literal_pattern_var = literal_pattern_rule()) != null  // literal_pattern
            )
            {
                _res = literal_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // capture_pattern
            ExprTy capture_pattern_var;
            if (
                (capture_pattern_var = capture_pattern_rule()) != null  // capture_pattern
            )
            {
                _res = capture_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // wildcard_pattern
            ExprTy wildcard_pattern_var;
            if (
                (wildcard_pattern_var = wildcard_pattern_rule()) != null  // wildcard_pattern
            )
            {
                _res = wildcard_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // value_pattern
            ExprTy value_pattern_var;
            if (
                (value_pattern_var = value_pattern_rule()) != null  // value_pattern
            )
            {
                _res = value_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // group_pattern
            ExprTy group_pattern_var;
            if (
                (group_pattern_var = group_pattern_rule()) != null  // group_pattern
            )
            {
                _res = group_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // sequence_pattern
            ExprTy sequence_pattern_var;
            if (
                (sequence_pattern_var = sequence_pattern_rule()) != null  // sequence_pattern
            )
            {
                _res = sequence_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // mapping_pattern
            ExprTy mapping_pattern_var;
            if (
                (mapping_pattern_var = mapping_pattern_rule()) != null  // mapping_pattern
            )
            {
                _res = mapping_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // class_pattern
            ExprTy class_pattern_var;
            if (
                (class_pattern_var = class_pattern_rule()) != null  // class_pattern
            )
            {
                _res = class_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // literal_pattern:
    //     | signed_number !('+' | '-')
    //     | signed_number '+' NUMBER
    //     | signed_number '-' NUMBER
    //     | strings
    //     | 'None'
    //     | 'True'
    //     | 'False'
    public ExprTy literal_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LITERAL_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, LITERAL_PATTERN_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // signed_number !('+' | '-')
            ExprTy signed_number_var;
            if (
                (signed_number_var = signed_number_rule()) != null  // signed_number
                &&
                genLookahead__tmp_69_rule(false)
            )
            {
                _res = signed_number_var;
                cache.putResult(_mark, LITERAL_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // signed_number '+' NUMBER
            Token _literal;
            ExprTy imag;
            ExprTy real;
            if (
                (real = signed_number_rule()) != null  // signed_number
                &&
                (_literal = expect(14)) != null  // token='+'
                &&
                (imag = number_token()) != null  // NUMBER
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.ADD,real,imag,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, LITERAL_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // signed_number '-' NUMBER
            Token _literal;
            ExprTy imag;
            ExprTy real;
            if (
                (real = signed_number_rule()) != null  // signed_number
                &&
                (_literal = expect(15)) != null  // token='-'
                &&
                (imag = number_token()) != null  // NUMBER
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.SUB,real,imag,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, LITERAL_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // strings
            ExprTy strings_var;
            if (
                (strings_var = strings_rule()) != null  // strings
            )
            {
                _res = strings_var;
                cache.putResult(_mark, LITERAL_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'None'
            Token _keyword;
            if (
                (_keyword = expect(525)) != null  // token='None'
            )
            {
                _res = factory.createNone(startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, LITERAL_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'True'
            Token _keyword;
            if (
                (_keyword = expect(526)) != null  // token='True'
            )
            {
                _res = factory.createBooleanLiteral(true,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, LITERAL_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'False'
            Token _keyword;
            if (
                (_keyword = expect(527)) != null  // token='False'
            )
            {
                _res = factory.createBooleanLiteral(false,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, LITERAL_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LITERAL_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // signed_number: NUMBER | '-' NUMBER
    public ExprTy signed_number_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SIGNED_NUMBER_ID)) {
            _res = (ExprTy)cache.getResult(_mark, SIGNED_NUMBER_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // NUMBER
            ExprTy number_var;
            if (
                (number_var = number_token()) != null  // NUMBER
            )
            {
                _res = number_var;
                cache.putResult(_mark, SIGNED_NUMBER_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '-' NUMBER
            Token _literal;
            ExprTy number;
            if (
                (_literal = expect(15)) != null  // token='-'
                &&
                (number = number_token()) != null  // NUMBER
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createUnaryOp(ExprTy.UnaryOp.Operator.SUB,number,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, SIGNED_NUMBER_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SIGNED_NUMBER_ID, _res);
        return (ExprTy)_res;
    }

    // capture_pattern: !"_" NAME !('.' | '(' | '=')
    public ExprTy capture_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, CAPTURE_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, CAPTURE_PATTERN_ID);
            return (ExprTy)_res;
        }
        { // !"_" NAME !('.' | '(' | '=')
            ExprTy name;
            if (
                genLookahead_expect_SOFT_KEYWORD(false, "_")
                &&
                (name = name_token()) != null  // NAME
                &&
                genLookahead__tmp_70_rule(false)
            )
            {
                // TODO: node.action: _PyPegen_set_expr_context ( p , name , Store )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_set_expr_context ( p , name , Store ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, CAPTURE_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, CAPTURE_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // wildcard_pattern: "_"
    public ExprTy wildcard_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, WILDCARD_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, WILDCARD_PATTERN_ID);
            return (ExprTy)_res;
        }
        { // "_"
            ExprTy _keyword;
            if (
                (_keyword = expect_SOFT_KEYWORD("_")) != null  // soft_keyword='"_"'
            )
            {
                // TODO: node.action: _PyAST_Name ( CHECK ( PyObject * , _PyPegen_new_identifier ( p , "_" ) ) , Store , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Name ( CHECK ( PyObject * , _PyPegen_new_identifier ( p , '_' ) ) , Store , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, WILDCARD_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, WILDCARD_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // value_pattern: attr !('.' | '(' | '=')
    public ExprTy value_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, VALUE_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, VALUE_PATTERN_ID);
            return (ExprTy)_res;
        }
        { // attr !('.' | '(' | '=')
            ExprTy attr;
            if (
                (attr = attr_rule()) != null  // attr
                &&
                genLookahead__tmp_71_rule(false)
            )
            {
                // TODO: node.action: attr
                debugMessageln("[33;5;7m!!! TODO: Convert attr to Java !!![0m");
                _res = null;
                cache.putResult(_mark, VALUE_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, VALUE_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // Left-recursive
    // attr: name_or_attr '.' NAME
    public ExprTy attr_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ATTR_ID)) {
            _res = cache.getResult(_mark, ATTR_ID);
            return (ExprTy)_res;
        }
        int _resmark = mark();
        while (true) {
            cache.putResult(_mark, ATTR_ID, _res);
            reset(_mark);
            Object _raw = attr_raw();
            if (_raw == null || mark() <= _resmark)
                break;
            _resmark = mark();
            _res = _raw;
        }
        reset(_resmark);
        return (ExprTy)_res;
    }
    private ExprTy attr_raw()
    {
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // name_or_attr '.' NAME
            Token _literal;
            ExprTy attr;
            ExprTy value;
            if (
                (value = name_or_attr_rule()) != null  // name_or_attr
                &&
                (_literal = expect(23)) != null  // token='.'
                &&
                (attr = name_token()) != null  // NAME
            )
            {
                _res = factory.createGetAttribute(value,((ExprTy.Name)attr).id,startToken.startOffset,startToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // Left-recursive
    // name_or_attr: attr | NAME
    public ExprTy name_or_attr_rule()
    {
        int _mark = mark();
        Object _res = null;
        { // attr
            ExprTy attr_var;
            if (
                (attr_var = attr_rule()) != null  // attr
            )
            {
                _res = attr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // NAME
            ExprTy name_var;
            if (
                (name_var = name_token()) != null  // NAME
            )
            {
                _res = name_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // group_pattern: '(' pattern ')'
    public ExprTy group_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, GROUP_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, GROUP_PATTERN_ID);
            return (ExprTy)_res;
        }
        { // '(' pattern ')'
            Token _literal;
            Token _literal_1;
            ExprTy pattern;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (pattern = pattern_rule()) != null  // pattern
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                // TODO: node.action: pattern
                debugMessageln("[33;5;7m!!! TODO: Convert pattern to Java !!![0m");
                _res = null;
                cache.putResult(_mark, GROUP_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, GROUP_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // sequence_pattern: '[' maybe_sequence_pattern? ']' | '(' open_sequence_pattern? ')'
    public ExprTy sequence_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SEQUENCE_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, SEQUENCE_PATTERN_ID);
            return (ExprTy)_res;
        }
        { // '[' maybe_sequence_pattern? ']'
            Token _literal;
            Token _literal_1;
            ExprTy[] values;
            if (
                (_literal = expect(9)) != null  // token='['
                &&
                ((values = maybe_sequence_pattern_rule()) != null || true)  // maybe_sequence_pattern?
                &&
                (_literal_1 = expect(10)) != null  // token=']'
            )
            {
                // TODO: node.action: _PyAST_List ( values , Load , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_List ( values , Load , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, SEQUENCE_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' open_sequence_pattern? ')'
            Token _literal;
            Token _literal_1;
            ExprTy[] values;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                ((values = open_sequence_pattern_rule()) != null || true)  // open_sequence_pattern?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                // TODO: node.action: _PyAST_Tuple ( values , Load , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Tuple ( values , Load , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, SEQUENCE_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SEQUENCE_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // open_sequence_pattern: maybe_star_pattern ',' maybe_sequence_pattern?
    public ExprTy[] open_sequence_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, OPEN_SEQUENCE_PATTERN_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, OPEN_SEQUENCE_PATTERN_ID);
            return (ExprTy[])_res;
        }
        { // maybe_star_pattern ',' maybe_sequence_pattern?
            Token _literal;
            ExprTy value;
            ExprTy[] values;
            if (
                (value = (ExprTy)maybe_star_pattern_rule()) != null  // maybe_star_pattern
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                ((values = (ExprTy[])maybe_sequence_pattern_rule()) != null || true)  // maybe_sequence_pattern?
            )
            {
                _res = this.insertInFront(value,(SSTNode [ ])values);
                cache.putResult(_mark, OPEN_SEQUENCE_PATTERN_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, OPEN_SEQUENCE_PATTERN_ID, _res);
        return (ExprTy[])_res;
    }

    // maybe_sequence_pattern: ','.maybe_star_pattern+ ','?
    public ExprTy[] maybe_sequence_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, MAYBE_SEQUENCE_PATTERN_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, MAYBE_SEQUENCE_PATTERN_ID);
            return (ExprTy[])_res;
        }
        { // ','.maybe_star_pattern+ ','?
            Token _opt_var;
            ExprTy[] values;
            if (
                (values = (ExprTy[])_gather_72_rule()) != null  // ','.maybe_star_pattern+
                &&
                ((_opt_var = (Token)expect(12)) != null || true)  // ','?
            )
            {
                // TODO: node.action: values
                debugMessageln("[33;5;7m!!! TODO: Convert values to Java !!![0m");
                _res = null;
                cache.putResult(_mark, MAYBE_SEQUENCE_PATTERN_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, MAYBE_SEQUENCE_PATTERN_ID, _res);
        return (ExprTy[])_res;
    }

    // maybe_star_pattern: star_pattern | pattern
    public ExprTy maybe_star_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, MAYBE_STAR_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, MAYBE_STAR_PATTERN_ID);
            return (ExprTy)_res;
        }
        { // star_pattern
            ExprTy star_pattern_var;
            if (
                (star_pattern_var = star_pattern_rule()) != null  // star_pattern
            )
            {
                _res = star_pattern_var;
                cache.putResult(_mark, MAYBE_STAR_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // pattern
            ExprTy pattern_var;
            if (
                (pattern_var = pattern_rule()) != null  // pattern
            )
            {
                _res = pattern_var;
                cache.putResult(_mark, MAYBE_STAR_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, MAYBE_STAR_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // star_pattern: '*' (capture_pattern | wildcard_pattern)
    public ExprTy star_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, STAR_PATTERN_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '*' (capture_pattern | wildcard_pattern)
            Token _literal;
            ExprTy value;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (value = _tmp_74_rule()) != null  // capture_pattern | wildcard_pattern
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createStarred(value,ExprContext.Store,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, STAR_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STAR_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // mapping_pattern: '{' items_pattern? '}'
    public ExprTy mapping_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, MAPPING_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, MAPPING_PATTERN_ID);
            return (ExprTy)_res;
        }
        { // '{' items_pattern? '}'
            Token _literal;
            Token _literal_1;
            KeyValuePair[] items;
            if (
                (_literal = expect(25)) != null  // token='{'
                &&
                ((items = items_pattern_rule()) != null || true)  // items_pattern?
                &&
                (_literal_1 = expect(26)) != null  // token='}'
            )
            {
                // TODO: node.action: _PyAST_Dict ( CHECK ( asdl_expr_seq * , _PyPegen_get_keys ( p , items ) ) , CHECK ( asdl_expr_seq * , _PyPegen_get_values ( p , items ) ) , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Dict ( CHECK ( asdl_expr_seq * , _PyPegen_get_keys ( p , items ) ) , CHECK ( asdl_expr_seq * , _PyPegen_get_values ( p , items ) ) , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, MAPPING_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, MAPPING_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // items_pattern: ','.key_value_pattern+ ','?
    public KeyValuePair[] items_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ITEMS_PATTERN_ID)) {
            _res = (KeyValuePair[])cache.getResult(_mark, ITEMS_PATTERN_ID);
            return (KeyValuePair[])_res;
        }
        { // ','.key_value_pattern+ ','?
            Token _opt_var;
            KeyValuePair[] items;
            if (
                (items = (KeyValuePair[])_gather_75_rule()) != null  // ','.key_value_pattern+
                &&
                ((_opt_var = (Token)expect(12)) != null || true)  // ','?
            )
            {
                // TODO: node.action: items
                debugMessageln("[33;5;7m!!! TODO: Convert items to Java !!![0m");
                _res = null;
                cache.putResult(_mark, ITEMS_PATTERN_ID, _res);
                return (KeyValuePair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, ITEMS_PATTERN_ID, _res);
        return (KeyValuePair[])_res;
    }

    // key_value_pattern: (literal_pattern | value_pattern) ':' pattern | double_star_pattern
    public KeyValuePair key_value_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, KEY_VALUE_PATTERN_ID)) {
            _res = (KeyValuePair)cache.getResult(_mark, KEY_VALUE_PATTERN_ID);
            return (KeyValuePair)_res;
        }
        { // (literal_pattern | value_pattern) ':' pattern
            Token _literal;
            ExprTy key;
            ExprTy value;
            if (
                (key = _tmp_77_rule()) != null  // literal_pattern | value_pattern
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (value = pattern_rule()) != null  // pattern
            )
            {
                _res = new KeyValuePair(key,value);
                cache.putResult(_mark, KEY_VALUE_PATTERN_ID, _res);
                return (KeyValuePair)_res;
            }
            reset(_mark);
        }
        { // double_star_pattern
            KeyValuePair double_star_pattern_var;
            if (
                (double_star_pattern_var = double_star_pattern_rule()) != null  // double_star_pattern
            )
            {
                _res = double_star_pattern_var;
                cache.putResult(_mark, KEY_VALUE_PATTERN_ID, _res);
                return (KeyValuePair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, KEY_VALUE_PATTERN_ID, _res);
        return (KeyValuePair)_res;
    }

    // double_star_pattern: '**' capture_pattern
    public KeyValuePair double_star_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DOUBLE_STAR_PATTERN_ID)) {
            _res = (KeyValuePair)cache.getResult(_mark, DOUBLE_STAR_PATTERN_ID);
            return (KeyValuePair)_res;
        }
        { // '**' capture_pattern
            Token _literal;
            ExprTy value;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (value = capture_pattern_rule()) != null  // capture_pattern
            )
            {
                // TODO: node.action: KeyValuePair ( null , value )
                debugMessageln("[33;5;7m!!! TODO: Convert KeyValuePair ( null , value ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, DOUBLE_STAR_PATTERN_ID, _res);
                return (KeyValuePair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DOUBLE_STAR_PATTERN_ID, _res);
        return (KeyValuePair)_res;
    }

    // class_pattern:
    //     | name_or_attr '(' ')'
    //     | name_or_attr '(' positional_patterns ','? ')'
    //     | name_or_attr '(' keyword_patterns ','? ')'
    //     | name_or_attr '(' positional_patterns ',' keyword_patterns ','? ')'
    public ExprTy class_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, CLASS_PATTERN_ID)) {
            _res = (ExprTy)cache.getResult(_mark, CLASS_PATTERN_ID);
            return (ExprTy)_res;
        }
        { // name_or_attr '(' ')'
            Token _literal;
            Token _literal_1;
            ExprTy func;
            if (
                (func = name_or_attr_rule()) != null  // name_or_attr
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                // TODO: node.action: _PyAST_Call ( func , NULL , NULL , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Call ( func , NULL , NULL , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, CLASS_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // name_or_attr '(' positional_patterns ','? ')'
            Token _literal;
            Token _literal_1;
            Token _opt_var;
            ExprTy[] args;
            ExprTy func;
            if (
                (func = name_or_attr_rule()) != null  // name_or_attr
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                (args = positional_patterns_rule()) != null  // positional_patterns
                &&
                ((_opt_var = expect(12)) != null || true)  // ','?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                // TODO: node.action: _PyAST_Call ( func , args , NULL , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Call ( func , args , NULL , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, CLASS_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // name_or_attr '(' keyword_patterns ','? ')'
            Token _literal;
            Token _literal_1;
            Token _opt_var;
            ExprTy func;
            KeywordTy[] keywords;
            if (
                (func = name_or_attr_rule()) != null  // name_or_attr
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                (keywords = keyword_patterns_rule()) != null  // keyword_patterns
                &&
                ((_opt_var = expect(12)) != null || true)  // ','?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                // TODO: node.action: _PyAST_Call ( func , NULL , keywords , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Call ( func , NULL , keywords , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, CLASS_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // name_or_attr '(' positional_patterns ',' keyword_patterns ','? ')'
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token _opt_var;
            ExprTy[] args;
            ExprTy func;
            KeywordTy[] keywords;
            if (
                (func = name_or_attr_rule()) != null  // name_or_attr
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                (args = positional_patterns_rule()) != null  // positional_patterns
                &&
                (_literal_1 = expect(12)) != null  // token=','
                &&
                (keywords = keyword_patterns_rule()) != null  // keyword_patterns
                &&
                ((_opt_var = expect(12)) != null || true)  // ','?
                &&
                (_literal_2 = expect(8)) != null  // token=')'
            )
            {
                // TODO: node.action: _PyAST_Call ( func , args , keywords , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Call ( func , args , keywords , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, CLASS_PATTERN_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, CLASS_PATTERN_ID, _res);
        return (ExprTy)_res;
    }

    // positional_patterns: ','.pattern+
    public ExprTy[] positional_patterns_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, POSITIONAL_PATTERNS_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, POSITIONAL_PATTERNS_ID);
            return (ExprTy[])_res;
        }
        { // ','.pattern+
            ExprTy[] args;
            if (
                (args = (ExprTy[])_gather_78_rule()) != null  // ','.pattern+
            )
            {
                // TODO: node.action: args
                debugMessageln("[33;5;7m!!! TODO: Convert args to Java !!![0m");
                _res = null;
                cache.putResult(_mark, POSITIONAL_PATTERNS_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, POSITIONAL_PATTERNS_ID, _res);
        return (ExprTy[])_res;
    }

    // keyword_patterns: ','.keyword_pattern+
    public KeywordTy[] keyword_patterns_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, KEYWORD_PATTERNS_ID)) {
            _res = (KeywordTy[])cache.getResult(_mark, KEYWORD_PATTERNS_ID);
            return (KeywordTy[])_res;
        }
        { // ','.keyword_pattern+
            KeywordTy[] keywords;
            if (
                (keywords = (KeywordTy[])_gather_80_rule()) != null  // ','.keyword_pattern+
            )
            {
                // TODO: node.action: keywords
                debugMessageln("[33;5;7m!!! TODO: Convert keywords to Java !!![0m");
                _res = null;
                cache.putResult(_mark, KEYWORD_PATTERNS_ID, _res);
                return (KeywordTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, KEYWORD_PATTERNS_ID, _res);
        return (KeywordTy[])_res;
    }

    // keyword_pattern: NAME '=' pattern
    public KeywordTy keyword_pattern_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, KEYWORD_PATTERN_ID)) {
            _res = (KeywordTy)cache.getResult(_mark, KEYWORD_PATTERN_ID);
            return (KeywordTy)_res;
        }
        { // NAME '=' pattern
            Token _literal;
            ExprTy arg;
            ExprTy value;
            if (
                (arg = name_token()) != null  // NAME
                &&
                (_literal = expect(22)) != null  // token='='
                &&
                (value = pattern_rule()) != null  // pattern
            )
            {
                // TODO: node.action: _PyAST_keyword ( arg -> v . Name . id , value , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_keyword ( arg -> v . Name . id , value , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, KEYWORD_PATTERN_ID, _res);
                return (KeywordTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, KEYWORD_PATTERN_ID, _res);
        return (KeywordTy)_res;
    }

    // return_stmt: 'return' star_expressions?
    public StmtTy return_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, RETURN_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, RETURN_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'return' star_expressions?
            Token _keyword;
            ExprTy a;
            if (
                (_keyword = expect(500)) != null  // token='return'
                &&
                ((a = _tmp_82_rule()) != null || true)  // star_expressions?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createReturn(a,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, RETURN_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, RETURN_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // raise_stmt: 'raise' expression ['from' expression] | 'raise'
    public StmtTy raise_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, RAISE_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, RAISE_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'raise' expression ['from' expression]
            Token _keyword;
            ExprTy a;
            ExprTy b;
            if (
                (_keyword = expect(503)) != null  // token='raise'
                &&
                (a = expression_rule()) != null  // expression
                &&
                ((b = _tmp_83_rule()) != null || true)  // ['from' expression]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createRaise(a,b,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, RAISE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'raise'
            Token _keyword;
            if (
                (_keyword = expect(503)) != null  // token='raise'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createRaise(null,null,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, RAISE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, RAISE_STMT_ID, _res);
        return (StmtTy)_res;
    }

    // function_def: decorators function_def_raw | function_def_raw
    public StmtTy function_def_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, FUNCTION_DEF_ID)) {
            _res = (StmtTy)cache.getResult(_mark, FUNCTION_DEF_ID);
            return (StmtTy)_res;
        }
        { // decorators function_def_raw
            ExprTy[] d;
            StmtTy f;
            if (
                (d = decorators_rule()) != null  // decorators
                &&
                (f = function_def_raw_rule()) != null  // function_def_raw
            )
            {
                _res = factory.createFunctionDef(f,d);
                cache.putResult(_mark, FUNCTION_DEF_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // function_def_raw
            StmtTy function_def_raw_var;
            if (
                (function_def_raw_var = function_def_raw_rule()) != null  // function_def_raw
            )
            {
                _res = function_def_raw_var;
                cache.putResult(_mark, FUNCTION_DEF_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, FUNCTION_DEF_ID, _res);
        return (StmtTy)_res;
    }

    // function_def_raw:
    //     | 'def' NAME '(' params? ')' ['->' expression] &&':' func_type_comment? block
    //     | ASYNC 'def' NAME '(' params? ')' ['->' expression] &&':' func_type_comment? block
    public StmtTy function_def_raw_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, FUNCTION_DEF_RAW_ID)) {
            _res = (StmtTy)cache.getResult(_mark, FUNCTION_DEF_RAW_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'def' NAME '(' params? ')' ['->' expression] &&':' func_type_comment? block
            Token _keyword;
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            ExprTy a;
            StmtTy[] b;
            ExprTy n;
            ArgumentsTy params;
            Token tc;
            if (
                (_keyword = expect(512)) != null  // token='def'
                &&
                (n = name_token()) != null  // NAME
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                ((params = _tmp_84_rule()) != null || true)  // params?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
                &&
                ((a = _tmp_85_rule()) != null || true)  // ['->' expression]
                &&
                (_literal_2 = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                ((tc = _tmp_86_rule()) != null || true)  // func_type_comment?
                &&
                (b = block_rule()) != null  // block
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createFunctionDef(((ExprTy.Name)n).id,params,b,(ExprTy)a,newTypeComment((Token)tc),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, FUNCTION_DEF_RAW_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // ASYNC 'def' NAME '(' params? ')' ['->' expression] &&':' func_type_comment? block
            Token _keyword;
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            ExprTy a;
            Token async_var;
            StmtTy[] b;
            ExprTy n;
            ArgumentsTy params;
            Token tc;
            if (
                (async_var = expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
                &&
                (_keyword = expect(512)) != null  // token='def'
                &&
                (n = name_token()) != null  // NAME
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                ((params = _tmp_87_rule()) != null || true)  // params?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
                &&
                ((a = _tmp_88_rule()) != null || true)  // ['->' expression]
                &&
                (_literal_2 = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                ((tc = _tmp_89_rule()) != null || true)  // func_type_comment?
                &&
                (b = block_rule()) != null  // block
            )
            {
                // TODO: node.action: CHECK_VERSION ( stmt_ty , 5 , "Async functions are" , _PyAST_AsyncFunctionDef ( n -> v . Name . id , ( params ) ? params : CHECK ( arguments_ty , _PyPegen_empty_arguments ( p ) ) , b , NULL , a , NEW_TYPE_COMMENT ( p , tc ) , EXTRA ) )
                debugMessageln("[33;5;7m!!! TODO: Convert CHECK_VERSION ( stmt_ty , 5 , 'Async functions are' , _PyAST_AsyncFunctionDef ( n -> v . Name . id , ( params ) ? params : CHECK ( arguments_ty , _PyPegen_empty_arguments ( p ) ) , b , NULL , a , NEW_TYPE_COMMENT ( p , tc ) , EXTRA ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, FUNCTION_DEF_RAW_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, FUNCTION_DEF_RAW_ID, _res);
        return (StmtTy)_res;
    }

    // func_type_comment:
    //     | NEWLINE TYPE_COMMENT &(NEWLINE INDENT)
    //     | invalid_double_type_comments
    //     | TYPE_COMMENT
    public Token func_type_comment_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, FUNC_TYPE_COMMENT_ID)) {
            _res = (Token)cache.getResult(_mark, FUNC_TYPE_COMMENT_ID);
            return (Token)_res;
        }
        { // NEWLINE TYPE_COMMENT &(NEWLINE INDENT)
            Token newline_var;
            Token t;
            if (
                (newline_var = expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                (t = expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
                &&
                genLookahead__tmp_90_rule(true)
            )
            {
                _res = t;
                cache.putResult(_mark, FUNC_TYPE_COMMENT_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_double_type_comments
            Token invalid_double_type_comments_var;
            if (
                (invalid_double_type_comments_var = invalid_double_type_comments_rule()) != null  // invalid_double_type_comments
            )
            {
                _res = invalid_double_type_comments_var;
                cache.putResult(_mark, FUNC_TYPE_COMMENT_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // TYPE_COMMENT
            Token type_comment_var;
            if (
                (type_comment_var = expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                _res = type_comment_var;
                cache.putResult(_mark, FUNC_TYPE_COMMENT_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, FUNC_TYPE_COMMENT_ID, _res);
        return (Token)_res;
    }

    // params: invalid_parameters | parameters
    public ArgumentsTy params_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, PARAMS_ID)) {
            _res = (ArgumentsTy)cache.getResult(_mark, PARAMS_ID);
            return (ArgumentsTy)_res;
        }
        if (callInvalidRules) { // invalid_parameters
            Object invalid_parameters_var;
            if (
                (invalid_parameters_var = invalid_parameters_rule()) != null  // invalid_parameters
            )
            {
                _res = invalid_parameters_var;
                cache.putResult(_mark, PARAMS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // parameters
            ArgumentsTy parameters_var;
            if (
                (parameters_var = parameters_rule()) != null  // parameters
            )
            {
                _res = parameters_var;
                cache.putResult(_mark, PARAMS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, PARAMS_ID, _res);
        return (ArgumentsTy)_res;
    }

    // parameters:
    //     | slash_no_default param_no_default* param_with_default* star_etc?
    //     | slash_with_default param_with_default* star_etc?
    //     | param_no_default+ param_with_default* star_etc?
    //     | param_with_default+ star_etc?
    //     | star_etc
    public ArgumentsTy parameters_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, PARAMETERS_ID)) {
            _res = (ArgumentsTy)cache.getResult(_mark, PARAMETERS_ID);
            return (ArgumentsTy)_res;
        }
        { // slash_no_default param_no_default* param_with_default* star_etc?
            ArgTy[] a;
            ArgTy[] b;
            NameDefaultPair[] c;
            StarEtc d;
            if (
                (a = slash_no_default_rule()) != null  // slash_no_default
                &&
                (b = (ArgTy[])_loop0_91_rule()) != null  // param_no_default*
                &&
                (c = _loop0_92_rule()) != null  // param_with_default*
                &&
                ((d = _tmp_93_rule()) != null || true)  // star_etc?
            )
            {
                _res = factory.createArguments(a,null,b,c,d);
                cache.putResult(_mark, PARAMETERS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // slash_with_default param_with_default* star_etc?
            SlashWithDefault a;
            NameDefaultPair[] b;
            StarEtc c;
            if (
                (a = slash_with_default_rule()) != null  // slash_with_default
                &&
                (b = _loop0_94_rule()) != null  // param_with_default*
                &&
                ((c = _tmp_95_rule()) != null || true)  // star_etc?
            )
            {
                _res = factory.createArguments(null,a,null,b,c);
                cache.putResult(_mark, PARAMETERS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // param_no_default+ param_with_default* star_etc?
            ArgTy[] a;
            NameDefaultPair[] b;
            StarEtc c;
            if (
                (a = (ArgTy[])_loop1_96_rule()) != null  // param_no_default+
                &&
                (b = _loop0_97_rule()) != null  // param_with_default*
                &&
                ((c = _tmp_98_rule()) != null || true)  // star_etc?
            )
            {
                _res = factory.createArguments(null,null,a,b,c);
                cache.putResult(_mark, PARAMETERS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // param_with_default+ star_etc?
            NameDefaultPair[] a;
            StarEtc b;
            if (
                (a = _loop1_99_rule()) != null  // param_with_default+
                &&
                ((b = _tmp_100_rule()) != null || true)  // star_etc?
            )
            {
                _res = factory.createArguments(null,null,null,a,b);
                cache.putResult(_mark, PARAMETERS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // star_etc
            StarEtc a;
            if (
                (a = star_etc_rule()) != null  // star_etc
            )
            {
                _res = factory.createArguments(null,null,null,null,a);
                cache.putResult(_mark, PARAMETERS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, PARAMETERS_ID, _res);
        return (ArgumentsTy)_res;
    }

    // slash_no_default: param_no_default+ '/' ',' | param_no_default+ '/' &')'
    public ArgTy[] slash_no_default_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SLASH_NO_DEFAULT_ID)) {
            _res = (ArgTy[])cache.getResult(_mark, SLASH_NO_DEFAULT_ID);
            return (ArgTy[])_res;
        }
        { // param_no_default+ '/' ','
            Token _literal;
            Token _literal_1;
            ArgTy[] a;
            if (
                (a = (ArgTy[])_loop1_101_rule()) != null  // param_no_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                (_literal_1 = expect(12)) != null  // token=','
            )
            {
                _res = a;
                cache.putResult(_mark, SLASH_NO_DEFAULT_ID, _res);
                return (ArgTy[])_res;
            }
            reset(_mark);
        }
        { // param_no_default+ '/' &')'
            Token _literal;
            ArgTy[] a;
            if (
                (a = (ArgTy[])_loop1_102_rule()) != null  // param_no_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                genLookahead_expect(true, 8)  // token=')'
            )
            {
                _res = a;
                cache.putResult(_mark, SLASH_NO_DEFAULT_ID, _res);
                return (ArgTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SLASH_NO_DEFAULT_ID, _res);
        return (ArgTy[])_res;
    }

    // slash_with_default:
    //     | param_no_default* param_with_default+ '/' ','
    //     | param_no_default* param_with_default+ '/' &')'
    public SlashWithDefault slash_with_default_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SLASH_WITH_DEFAULT_ID)) {
            _res = (SlashWithDefault)cache.getResult(_mark, SLASH_WITH_DEFAULT_ID);
            return (SlashWithDefault)_res;
        }
        { // param_no_default* param_with_default+ '/' ','
            Token _literal;
            Token _literal_1;
            ArgTy[] a;
            NameDefaultPair[] b;
            if (
                (a = _loop0_103_rule()) != null  // param_no_default*
                &&
                (b = _loop1_104_rule()) != null  // param_with_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                (_literal_1 = expect(12)) != null  // token=','
            )
            {
                _res = new SlashWithDefault(a,b);
                cache.putResult(_mark, SLASH_WITH_DEFAULT_ID, _res);
                return (SlashWithDefault)_res;
            }
            reset(_mark);
        }
        { // param_no_default* param_with_default+ '/' &')'
            Token _literal;
            ArgTy[] a;
            NameDefaultPair[] b;
            if (
                (a = _loop0_105_rule()) != null  // param_no_default*
                &&
                (b = _loop1_106_rule()) != null  // param_with_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                genLookahead_expect(true, 8)  // token=')'
            )
            {
                _res = new SlashWithDefault(a,b);
                cache.putResult(_mark, SLASH_WITH_DEFAULT_ID, _res);
                return (SlashWithDefault)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SLASH_WITH_DEFAULT_ID, _res);
        return (SlashWithDefault)_res;
    }

    // star_etc:
    //     | '*' param_no_default param_maybe_default* kwds?
    //     | '*' ',' param_maybe_default+ kwds?
    //     | kwds
    //     | invalid_star_etc
    public StarEtc star_etc_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_ETC_ID)) {
            _res = (StarEtc)cache.getResult(_mark, STAR_ETC_ID);
            return (StarEtc)_res;
        }
        { // '*' param_no_default param_maybe_default* kwds?
            Token _literal;
            ArgTy a;
            NameDefaultPair[] b;
            ArgTy c;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = param_no_default_rule()) != null  // param_no_default
                &&
                (b = _loop0_107_rule()) != null  // param_maybe_default*
                &&
                ((c = _tmp_108_rule()) != null || true)  // kwds?
            )
            {
                _res = new StarEtc(a,b,c);
                cache.putResult(_mark, STAR_ETC_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        { // '*' ',' param_maybe_default+ kwds?
            Token _literal;
            Token _literal_1;
            NameDefaultPair[] b;
            ArgTy c;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (_literal_1 = expect(12)) != null  // token=','
                &&
                (b = _loop1_109_rule()) != null  // param_maybe_default+
                &&
                ((c = _tmp_110_rule()) != null || true)  // kwds?
            )
            {
                _res = new StarEtc(null,b,c);
                cache.putResult(_mark, STAR_ETC_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        { // kwds
            ArgTy a;
            if (
                (a = kwds_rule()) != null  // kwds
            )
            {
                _res = new StarEtc(null,null,a);
                cache.putResult(_mark, STAR_ETC_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_star_etc
            Token invalid_star_etc_var;
            if (
                (invalid_star_etc_var = invalid_star_etc_rule()) != null  // invalid_star_etc
            )
            {
                _res = invalid_star_etc_var;
                cache.putResult(_mark, STAR_ETC_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STAR_ETC_ID, _res);
        return (StarEtc)_res;
    }

    // kwds: '**' param_no_default
    public ArgTy kwds_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, KWDS_ID)) {
            _res = (ArgTy)cache.getResult(_mark, KWDS_ID);
            return (ArgTy)_res;
        }
        { // '**' param_no_default
            Token _literal;
            ArgTy a;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (a = param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = a;
                cache.putResult(_mark, KWDS_ID, _res);
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, KWDS_ID, _res);
        return (ArgTy)_res;
    }

    // param_no_default: param ',' TYPE_COMMENT? | param TYPE_COMMENT? &')'
    public ArgTy param_no_default_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, PARAM_NO_DEFAULT_ID)) {
            _res = (ArgTy)cache.getResult(_mark, PARAM_NO_DEFAULT_ID);
            return (ArgTy)_res;
        }
        { // param ',' TYPE_COMMENT?
            Token _literal;
            ArgTy a;
            Token tc;
            if (
                (a = param_rule()) != null  // param
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                ((tc = expect(Token.Kind.TYPE_COMMENT)) != null || true)  // TYPE_COMMENT?
            )
            {
                _res = factory.createArgument(a.arg,a.annotation,newTypeComment(tc),a.getStartOffset(),a.getEndOffset());
                cache.putResult(_mark, PARAM_NO_DEFAULT_ID, _res);
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        { // param TYPE_COMMENT? &')'
            ArgTy a;
            Token tc;
            if (
                (a = param_rule()) != null  // param
                &&
                ((tc = expect(Token.Kind.TYPE_COMMENT)) != null || true)  // TYPE_COMMENT?
                &&
                genLookahead_expect(true, 8)  // token=')'
            )
            {
                _res = factory.createArgument(a.arg,a.annotation,newTypeComment(tc),a.getStartOffset(),a.getEndOffset());
                cache.putResult(_mark, PARAM_NO_DEFAULT_ID, _res);
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, PARAM_NO_DEFAULT_ID, _res);
        return (ArgTy)_res;
    }

    // param_with_default:
    //     | param default_param ',' TYPE_COMMENT?
    //     | param default_param TYPE_COMMENT? &')'
    public NameDefaultPair param_with_default_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, PARAM_WITH_DEFAULT_ID)) {
            _res = (NameDefaultPair)cache.getResult(_mark, PARAM_WITH_DEFAULT_ID);
            return (NameDefaultPair)_res;
        }
        { // param default_param ',' TYPE_COMMENT?
            Token _literal;
            ArgTy a;
            ExprTy c;
            Token tc;
            if (
                (a = param_rule()) != null  // param
                &&
                (c = default_param_rule()) != null  // default_param
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                ((tc = expect(Token.Kind.TYPE_COMMENT)) != null || true)  // TYPE_COMMENT?
            )
            {
                _res = new NameDefaultPair(factory.createArgument(a.arg,a.annotation,newTypeComment(tc),a.getStartOffset(),a.getEndOffset()),c);
                cache.putResult(_mark, PARAM_WITH_DEFAULT_ID, _res);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        { // param default_param TYPE_COMMENT? &')'
            ArgTy a;
            ExprTy c;
            Token tc;
            if (
                (a = param_rule()) != null  // param
                &&
                (c = default_param_rule()) != null  // default_param
                &&
                ((tc = expect(Token.Kind.TYPE_COMMENT)) != null || true)  // TYPE_COMMENT?
                &&
                genLookahead_expect(true, 8)  // token=')'
            )
            {
                _res = new NameDefaultPair(factory.createArgument(a.arg,a.annotation,newTypeComment(tc),a.getStartOffset(),a.getEndOffset()),c);
                cache.putResult(_mark, PARAM_WITH_DEFAULT_ID, _res);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, PARAM_WITH_DEFAULT_ID, _res);
        return (NameDefaultPair)_res;
    }

    // param_maybe_default:
    //     | param default_param? ',' TYPE_COMMENT?
    //     | param default_param? TYPE_COMMENT? &')'
    public NameDefaultPair param_maybe_default_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, PARAM_MAYBE_DEFAULT_ID)) {
            _res = (NameDefaultPair)cache.getResult(_mark, PARAM_MAYBE_DEFAULT_ID);
            return (NameDefaultPair)_res;
        }
        { // param default_param? ',' TYPE_COMMENT?
            Token _literal;
            ArgTy a;
            ExprTy c;
            Token tc;
            if (
                (a = param_rule()) != null  // param
                &&
                ((c = default_param_rule()) != null || true)  // default_param?
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                ((tc = expect(Token.Kind.TYPE_COMMENT)) != null || true)  // TYPE_COMMENT?
            )
            {
                _res = new NameDefaultPair(factory.createArgument(a.arg,a.annotation,newTypeComment(tc),a.getStartOffset(),a.getEndOffset()),c);
                cache.putResult(_mark, PARAM_MAYBE_DEFAULT_ID, _res);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        { // param default_param? TYPE_COMMENT? &')'
            ArgTy a;
            ExprTy c;
            Token tc;
            if (
                (a = param_rule()) != null  // param
                &&
                ((c = default_param_rule()) != null || true)  // default_param?
                &&
                ((tc = expect(Token.Kind.TYPE_COMMENT)) != null || true)  // TYPE_COMMENT?
                &&
                genLookahead_expect(true, 8)  // token=')'
            )
            {
                _res = new NameDefaultPair(factory.createArgument(a.arg,a.annotation,newTypeComment(tc),a.getStartOffset(),a.getEndOffset()),c);
                cache.putResult(_mark, PARAM_MAYBE_DEFAULT_ID, _res);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, PARAM_MAYBE_DEFAULT_ID, _res);
        return (NameDefaultPair)_res;
    }

    // param: NAME annotation?
    public ArgTy param_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, PARAM_ID)) {
            _res = (ArgTy)cache.getResult(_mark, PARAM_ID);
            return (ArgTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // NAME annotation?
            ExprTy a;
            ExprTy b;
            if (
                (a = name_token()) != null  // NAME
                &&
                ((b = annotation_rule()) != null || true)  // annotation?
            )
            {
                _res = factory.createArgument(((ExprTy.Name)a).id,b,null,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, PARAM_ID, _res);
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, PARAM_ID, _res);
        return (ArgTy)_res;
    }

    // annotation: ':' expression
    public ExprTy annotation_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ANNOTATION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, ANNOTATION_ID);
            return (ExprTy)_res;
        }
        { // ':' expression
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(11)) != null  // token=':'
                &&
                (a = expression_rule()) != null  // expression
            )
            {
                _res = a;
                cache.putResult(_mark, ANNOTATION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, ANNOTATION_ID, _res);
        return (ExprTy)_res;
    }

    // default_param: '=' expression
    public ExprTy default_param_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DEFAULT_PARAM_ID)) {
            _res = (ExprTy)cache.getResult(_mark, DEFAULT_PARAM_ID);
            return (ExprTy)_res;
        }
        { // '=' expression
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(22)) != null  // token='='
                &&
                (a = expression_rule()) != null  // expression
            )
            {
                _res = a;
                cache.putResult(_mark, DEFAULT_PARAM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DEFAULT_PARAM_ID, _res);
        return (ExprTy)_res;
    }

    // decorators: (('@' named_expression NEWLINE))+
    public ExprTy[] decorators_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DECORATORS_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, DECORATORS_ID);
            return (ExprTy[])_res;
        }
        { // (('@' named_expression NEWLINE))+
            ExprTy[] a;
            if (
                (a = (ExprTy[])_loop1_111_rule()) != null  // (('@' named_expression NEWLINE))+
            )
            {
                _res = a;
                cache.putResult(_mark, DECORATORS_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DECORATORS_ID, _res);
        return (ExprTy[])_res;
    }

    // class_def: decorators class_def_raw | class_def_raw
    public StmtTy class_def_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, CLASS_DEF_ID)) {
            _res = (StmtTy)cache.getResult(_mark, CLASS_DEF_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // decorators class_def_raw
            ExprTy[] a;
            StmtTy b;
            if (
                (a = decorators_rule()) != null  // decorators
                &&
                (b = class_def_raw_rule()) != null  // class_def_raw
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createClassDef(b,a,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, CLASS_DEF_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // class_def_raw
            StmtTy class_def_raw_var;
            if (
                (class_def_raw_var = class_def_raw_rule()) != null  // class_def_raw
            )
            {
                _res = class_def_raw_var;
                cache.putResult(_mark, CLASS_DEF_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, CLASS_DEF_ID, _res);
        return (StmtTy)_res;
    }

    // class_def_raw: 'class' NAME ['(' arguments? ')'] &&':' block
    public StmtTy class_def_raw_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, CLASS_DEF_RAW_ID)) {
            _res = (StmtTy)cache.getResult(_mark, CLASS_DEF_RAW_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'class' NAME ['(' arguments? ')'] &&':' block
            Token _keyword;
            Token _literal;
            ExprTy a;
            ExprTy b;
            StmtTy[] c;
            if (
                (_keyword = expect(514)) != null  // token='class'
                &&
                (a = name_token()) != null  // NAME
                &&
                ((b = _tmp_112_rule()) != null || true)  // ['(' arguments? ')']
                &&
                (_literal = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                (c = block_rule()) != null  // block
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createClassDef(a,b,c,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, CLASS_DEF_RAW_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, CLASS_DEF_RAW_ID, _res);
        return (StmtTy)_res;
    }

    // block: NEWLINE INDENT statements DEDENT | simple_stmts | invalid_block
    public StmtTy[] block_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, BLOCK_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, BLOCK_ID);
            return (StmtTy[])_res;
        }
        { // NEWLINE INDENT statements DEDENT
            StmtTy[] a;
            Token dedent_var;
            Token indent_var;
            Token newline_var;
            if (
                (newline_var = expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                (indent_var = expect(Token.Kind.INDENT)) != null  // token='INDENT'
                &&
                (a = statements_rule()) != null  // statements
                &&
                (dedent_var = expect(Token.Kind.DEDENT)) != null  // token='DEDENT'
            )
            {
                _res = a;
                cache.putResult(_mark, BLOCK_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // simple_stmts
            StmtTy[] simple_stmts_var;
            if (
                (simple_stmts_var = simple_stmts_rule()) != null  // simple_stmts
            )
            {
                _res = simple_stmts_var;
                cache.putResult(_mark, BLOCK_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_block
            Token invalid_block_var;
            if (
                (invalid_block_var = invalid_block_rule()) != null  // invalid_block
            )
            {
                _res = invalid_block_var;
                cache.putResult(_mark, BLOCK_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, BLOCK_ID, _res);
        return (StmtTy[])_res;
    }

    // star_expressions:
    //     | star_expression ((',' star_expression))+ ','?
    //     | star_expression ','
    //     | star_expression
    public ExprTy star_expressions_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_EXPRESSIONS_ID)) {
            _res = (ExprTy)cache.getResult(_mark, STAR_EXPRESSIONS_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // star_expression ((',' star_expression))+ ','?
            Token _opt_var;
            ExprTy a;
            ExprTy[] b;
            if (
                (a = star_expression_rule()) != null  // star_expression
                &&
                (b = _loop1_113_rule()) != null  // ((',' star_expression))+
                &&
                ((_opt_var = _tmp_114_rule()) != null || true)  // ','?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(this.insertInFront(a,b),ExprContext.Load,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, STAR_EXPRESSIONS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expression ','
            Token _literal;
            ExprTy a;
            if (
                (a = star_expression_rule()) != null  // star_expression
                &&
                (_literal = expect(12)) != null  // token=','
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(this.singletonSequence(a),ExprContext.Load,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, STAR_EXPRESSIONS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expression
            ExprTy star_expression_var;
            if (
                (star_expression_var = star_expression_rule()) != null  // star_expression
            )
            {
                _res = star_expression_var;
                cache.putResult(_mark, STAR_EXPRESSIONS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STAR_EXPRESSIONS_ID, _res);
        return (ExprTy)_res;
    }

    // star_expression: '*' bitwise_or | expression
    public ExprTy star_expression_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_EXPRESSION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, STAR_EXPRESSION_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '*' bitwise_or
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createStarred(a,ExprContext.Load,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, STAR_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression
            ExprTy expression_var;
            if (
                (expression_var = expression_rule()) != null  // expression
            )
            {
                _res = expression_var;
                cache.putResult(_mark, STAR_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STAR_EXPRESSION_ID, _res);
        return (ExprTy)_res;
    }

    // star_named_expressions: ','.star_named_expression+ ','?
    public ExprTy[] star_named_expressions_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_NAMED_EXPRESSIONS_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, STAR_NAMED_EXPRESSIONS_ID);
            return (ExprTy[])_res;
        }
        { // ','.star_named_expression+ ','?
            Token _opt_var;
            ExprTy[] a;
            if (
                (a = (ExprTy[])_gather_115_rule()) != null  // ','.star_named_expression+
                &&
                ((_opt_var = _tmp_117_rule()) != null || true)  // ','?
            )
            {
                _res = a;
                cache.putResult(_mark, STAR_NAMED_EXPRESSIONS_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STAR_NAMED_EXPRESSIONS_ID, _res);
        return (ExprTy[])_res;
    }

    // star_named_expression: '*' bitwise_or | named_expression
    public ExprTy star_named_expression_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_NAMED_EXPRESSION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, STAR_NAMED_EXPRESSION_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '*' bitwise_or
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createStarred(a,ExprContext.Load,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, STAR_NAMED_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // named_expression
            ExprTy named_expression_var;
            if (
                (named_expression_var = named_expression_rule()) != null  // named_expression
            )
            {
                _res = named_expression_var;
                cache.putResult(_mark, STAR_NAMED_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STAR_NAMED_EXPRESSION_ID, _res);
        return (ExprTy)_res;
    }

    // named_expression: NAME ':=' ~ expression | invalid_named_expression | expression !':='
    public ExprTy named_expression_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, NAMED_EXPRESSION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, NAMED_EXPRESSION_ID);
            return (ExprTy)_res;
        }
        { // NAME ':=' ~ expression
            int _cut_var = 0;
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = name_token()) != null  // NAME
                &&
                (_literal = expect(53)) != null  // token=':='
                &&
                (_cut_var = 1) != 0
                &&
                (b = expression_rule()) != null  // expression
            )
            {
                // TODO: node.action: _PyAST_NamedExpr ( CHECK ( expr_ty , _PyPegen_set_expr_context ( p , a , Store ) ) , b , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_NamedExpr ( CHECK ( expr_ty , _PyPegen_set_expr_context ( p , a , Store ) ) , b , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, NAMED_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        if (callInvalidRules) { // invalid_named_expression
            Object invalid_named_expression_var;
            if (
                (invalid_named_expression_var = invalid_named_expression_rule()) != null  // invalid_named_expression
            )
            {
                _res = invalid_named_expression_var;
                cache.putResult(_mark, NAMED_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression !':='
            ExprTy expression_var;
            if (
                (expression_var = expression_rule()) != null  // expression
                &&
                genLookahead_expect(false, 53)  // token=':='
            )
            {
                _res = expression_var;
                cache.putResult(_mark, NAMED_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, NAMED_EXPRESSION_ID, _res);
        return (ExprTy)_res;
    }

    // direct_named_expression: NAME ':=' ~ expression | expression !':='
    public ExprTy direct_named_expression_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DIRECT_NAMED_EXPRESSION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, DIRECT_NAMED_EXPRESSION_ID);
            return (ExprTy)_res;
        }
        { // NAME ':=' ~ expression
            int _cut_var = 0;
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = name_token()) != null  // NAME
                &&
                (_literal = expect(53)) != null  // token=':='
                &&
                (_cut_var = 1) != 0
                &&
                (b = expression_rule()) != null  // expression
            )
            {
                // TODO: node.action: _PyAST_NamedExpr ( CHECK ( expr_ty , _PyPegen_set_expr_context ( p , a , Store ) ) , b , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_NamedExpr ( CHECK ( expr_ty , _PyPegen_set_expr_context ( p , a , Store ) ) , b , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, DIRECT_NAMED_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        { // expression !':='
            ExprTy expression_var;
            if (
                (expression_var = expression_rule()) != null  // expression
                &&
                genLookahead_expect(false, 53)  // token=':='
            )
            {
                _res = expression_var;
                cache.putResult(_mark, DIRECT_NAMED_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DIRECT_NAMED_EXPRESSION_ID, _res);
        return (ExprTy)_res;
    }

    // annotated_rhs: yield_expr | star_expressions
    public ExprTy annotated_rhs_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ANNOTATED_RHS_ID)) {
            _res = (ExprTy)cache.getResult(_mark, ANNOTATED_RHS_ID);
            return (ExprTy)_res;
        }
        { // yield_expr
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                cache.putResult(_mark, ANNOTATED_RHS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                cache.putResult(_mark, ANNOTATED_RHS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, ANNOTATED_RHS_ID, _res);
        return (ExprTy)_res;
    }

    // expressions: expression ((',' expression))+ ','? | expression ',' | expression
    public ExprTy expressions_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, EXPRESSIONS_ID)) {
            _res = (ExprTy)cache.getResult(_mark, EXPRESSIONS_ID);
            return (ExprTy)_res;
        }
        { // expression ((',' expression))+ ','?
            Token _opt_var;
            ExprTy a;
            ExprTy[] b;
            if (
                (a = expression_rule()) != null  // expression
                &&
                (b = _loop1_118_rule()) != null  // ((',' expression))+
                &&
                ((_opt_var = _tmp_119_rule()) != null || true)  // ','?
            )
            {
                // TODO: node.action: _PyAST_Tuple ( CHECK ( asdl_expr_seq * , this . insertInFront ( a , b ) ) , Load , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Tuple ( CHECK ( asdl_expr_seq * , this . insertInFront ( a , b ) ) , Load , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, EXPRESSIONS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression ','
            Token _literal;
            ExprTy a;
            if (
                (a = expression_rule()) != null  // expression
                &&
                (_literal = expect(12)) != null  // token=','
            )
            {
                // TODO: node.action: _PyAST_Tuple ( CHECK ( asdl_expr_seq * , this . singletonSequence ( a ) ) , Load , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Tuple ( CHECK ( asdl_expr_seq * , this . singletonSequence ( a ) ) , Load , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, EXPRESSIONS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression
            ExprTy expression_var;
            if (
                (expression_var = expression_rule()) != null  // expression
            )
            {
                _res = expression_var;
                cache.putResult(_mark, EXPRESSIONS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, EXPRESSIONS_ID, _res);
        return (ExprTy)_res;
    }

    // expression:
    //     | invalid_expression
    //     | disjunction 'if' disjunction 'else' expression
    //     | disjunction
    //     | lambdef
    public ExprTy expression_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, EXPRESSION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, EXPRESSION_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_expression
            ExprTy invalid_expression_var;
            if (
                (invalid_expression_var = invalid_expression_rule()) != null  // invalid_expression
            )
            {
                _res = invalid_expression_var;
                cache.putResult(_mark, EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // disjunction 'if' disjunction 'else' expression
            Token _keyword;
            Token _keyword_1;
            ExprTy a;
            ExprTy b;
            ExprTy c;
            if (
                (a = disjunction_rule()) != null  // disjunction
                &&
                (_keyword = expect(513)) != null  // token='if'
                &&
                (b = disjunction_rule()) != null  // disjunction
                &&
                (_keyword_1 = expect(521)) != null  // token='else'
                &&
                (c = expression_rule()) != null  // expression
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createIfExpression(b,a,c,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // disjunction
            ExprTy disjunction_var;
            if (
                (disjunction_var = disjunction_rule()) != null  // disjunction
            )
            {
                _res = disjunction_var;
                cache.putResult(_mark, EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // lambdef
            ExprTy lambdef_var;
            if (
                (lambdef_var = lambdef_rule()) != null  // lambdef
            )
            {
                _res = lambdef_var;
                cache.putResult(_mark, EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, EXPRESSION_ID, _res);
        return (ExprTy)_res;
    }

    // lambdef: 'lambda' lambda_params? ':' expression
    public ExprTy lambdef_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LAMBDEF_ID)) {
            _res = (ExprTy)cache.getResult(_mark, LAMBDEF_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'lambda' lambda_params? ':' expression
            Token _keyword;
            Token _literal;
            ArgumentsTy a;
            ExprTy b;
            if (
                (_keyword = expect(528)) != null  // token='lambda'
                &&
                ((a = _tmp_120_rule()) != null || true)  // lambda_params?
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = expression_rule()) != null  // expression
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createLambda(a,b,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, LAMBDEF_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LAMBDEF_ID, _res);
        return (ExprTy)_res;
    }

    // lambda_params: invalid_lambda_parameters | lambda_parameters
    public ArgumentsTy lambda_params_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LAMBDA_PARAMS_ID)) {
            _res = (ArgumentsTy)cache.getResult(_mark, LAMBDA_PARAMS_ID);
            return (ArgumentsTy)_res;
        }
        if (callInvalidRules) { // invalid_lambda_parameters
            Object invalid_lambda_parameters_var;
            if (
                (invalid_lambda_parameters_var = invalid_lambda_parameters_rule()) != null  // invalid_lambda_parameters
            )
            {
                _res = invalid_lambda_parameters_var;
                cache.putResult(_mark, LAMBDA_PARAMS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // lambda_parameters
            ArgumentsTy lambda_parameters_var;
            if (
                (lambda_parameters_var = lambda_parameters_rule()) != null  // lambda_parameters
            )
            {
                _res = lambda_parameters_var;
                cache.putResult(_mark, LAMBDA_PARAMS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LAMBDA_PARAMS_ID, _res);
        return (ArgumentsTy)_res;
    }

    // lambda_parameters:
    //     | lambda_slash_no_default lambda_param_no_default* lambda_param_with_default* lambda_star_etc?
    //     | lambda_slash_with_default lambda_param_with_default* lambda_star_etc?
    //     | lambda_param_no_default+ lambda_param_with_default* lambda_star_etc?
    //     | lambda_param_with_default+ lambda_star_etc?
    //     | lambda_star_etc
    public ArgumentsTy lambda_parameters_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LAMBDA_PARAMETERS_ID)) {
            _res = (ArgumentsTy)cache.getResult(_mark, LAMBDA_PARAMETERS_ID);
            return (ArgumentsTy)_res;
        }
        { // lambda_slash_no_default lambda_param_no_default* lambda_param_with_default* lambda_star_etc?
            ArgTy[] a;
            ArgTy[] b;
            NameDefaultPair[] c;
            StarEtc d;
            if (
                (a = lambda_slash_no_default_rule()) != null  // lambda_slash_no_default
                &&
                (b = (ArgTy[])_loop0_121_rule()) != null  // lambda_param_no_default*
                &&
                (c = _loop0_122_rule()) != null  // lambda_param_with_default*
                &&
                ((d = _tmp_123_rule()) != null || true)  // lambda_star_etc?
            )
            {
                _res = factory.createArguments(a,null,b,c,d);
                cache.putResult(_mark, LAMBDA_PARAMETERS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // lambda_slash_with_default lambda_param_with_default* lambda_star_etc?
            SlashWithDefault a;
            NameDefaultPair[] b;
            StarEtc c;
            if (
                (a = lambda_slash_with_default_rule()) != null  // lambda_slash_with_default
                &&
                (b = _loop0_124_rule()) != null  // lambda_param_with_default*
                &&
                ((c = _tmp_125_rule()) != null || true)  // lambda_star_etc?
            )
            {
                _res = factory.createArguments(null,a,null,b,c);
                cache.putResult(_mark, LAMBDA_PARAMETERS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // lambda_param_no_default+ lambda_param_with_default* lambda_star_etc?
            ArgTy[] a;
            NameDefaultPair[] b;
            StarEtc c;
            if (
                (a = (ArgTy[])_loop1_126_rule()) != null  // lambda_param_no_default+
                &&
                (b = _loop0_127_rule()) != null  // lambda_param_with_default*
                &&
                ((c = _tmp_128_rule()) != null || true)  // lambda_star_etc?
            )
            {
                _res = factory.createArguments(null,null,a,b,c);
                cache.putResult(_mark, LAMBDA_PARAMETERS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // lambda_param_with_default+ lambda_star_etc?
            NameDefaultPair[] a;
            StarEtc b;
            if (
                (a = _loop1_129_rule()) != null  // lambda_param_with_default+
                &&
                ((b = _tmp_130_rule()) != null || true)  // lambda_star_etc?
            )
            {
                _res = factory.createArguments(null,null,null,a,b);
                cache.putResult(_mark, LAMBDA_PARAMETERS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // lambda_star_etc
            StarEtc a;
            if (
                (a = lambda_star_etc_rule()) != null  // lambda_star_etc
            )
            {
                _res = factory.createArguments(null,null,null,null,a);
                cache.putResult(_mark, LAMBDA_PARAMETERS_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LAMBDA_PARAMETERS_ID, _res);
        return (ArgumentsTy)_res;
    }

    // lambda_slash_no_default:
    //     | lambda_param_no_default+ '/' ','
    //     | lambda_param_no_default+ '/' &':'
    public ArgTy[] lambda_slash_no_default_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LAMBDA_SLASH_NO_DEFAULT_ID)) {
            _res = (ArgTy[])cache.getResult(_mark, LAMBDA_SLASH_NO_DEFAULT_ID);
            return (ArgTy[])_res;
        }
        { // lambda_param_no_default+ '/' ','
            Token _literal;
            Token _literal_1;
            ArgTy[] a;
            if (
                (a = (ArgTy[])_loop1_131_rule()) != null  // lambda_param_no_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                (_literal_1 = expect(12)) != null  // token=','
            )
            {
                _res = a;
                cache.putResult(_mark, LAMBDA_SLASH_NO_DEFAULT_ID, _res);
                return (ArgTy[])_res;
            }
            reset(_mark);
        }
        { // lambda_param_no_default+ '/' &':'
            Token _literal;
            ArgTy[] a;
            if (
                (a = (ArgTy[])_loop1_132_rule()) != null  // lambda_param_no_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                genLookahead_expect(true, 11)  // token=':'
            )
            {
                _res = a;
                cache.putResult(_mark, LAMBDA_SLASH_NO_DEFAULT_ID, _res);
                return (ArgTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LAMBDA_SLASH_NO_DEFAULT_ID, _res);
        return (ArgTy[])_res;
    }

    // lambda_slash_with_default:
    //     | lambda_param_no_default* lambda_param_with_default+ '/' ','
    //     | lambda_param_no_default* lambda_param_with_default+ '/' &':'
    public SlashWithDefault lambda_slash_with_default_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LAMBDA_SLASH_WITH_DEFAULT_ID)) {
            _res = (SlashWithDefault)cache.getResult(_mark, LAMBDA_SLASH_WITH_DEFAULT_ID);
            return (SlashWithDefault)_res;
        }
        { // lambda_param_no_default* lambda_param_with_default+ '/' ','
            Token _literal;
            Token _literal_1;
            ArgTy[] a;
            NameDefaultPair[] b;
            if (
                (a = _loop0_133_rule()) != null  // lambda_param_no_default*
                &&
                (b = _loop1_134_rule()) != null  // lambda_param_with_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                (_literal_1 = expect(12)) != null  // token=','
            )
            {
                // TODO: node.action: _PyPegen_slash_with_default ( p , ( asdl_arg_seq * ) a , b )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_slash_with_default ( p , ( asdl_arg_seq * ) a , b ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, LAMBDA_SLASH_WITH_DEFAULT_ID, _res);
                return (SlashWithDefault)_res;
            }
            reset(_mark);
        }
        { // lambda_param_no_default* lambda_param_with_default+ '/' &':'
            Token _literal;
            ArgTy[] a;
            NameDefaultPair[] b;
            if (
                (a = _loop0_135_rule()) != null  // lambda_param_no_default*
                &&
                (b = _loop1_136_rule()) != null  // lambda_param_with_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                genLookahead_expect(true, 11)  // token=':'
            )
            {
                // TODO: node.action: _PyPegen_slash_with_default ( p , ( asdl_arg_seq * ) a , b )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_slash_with_default ( p , ( asdl_arg_seq * ) a , b ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, LAMBDA_SLASH_WITH_DEFAULT_ID, _res);
                return (SlashWithDefault)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LAMBDA_SLASH_WITH_DEFAULT_ID, _res);
        return (SlashWithDefault)_res;
    }

    // lambda_star_etc:
    //     | '*' lambda_param_no_default lambda_param_maybe_default* lambda_kwds?
    //     | '*' ',' lambda_param_maybe_default+ lambda_kwds?
    //     | lambda_kwds
    //     | invalid_lambda_star_etc
    public StarEtc lambda_star_etc_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LAMBDA_STAR_ETC_ID)) {
            _res = (StarEtc)cache.getResult(_mark, LAMBDA_STAR_ETC_ID);
            return (StarEtc)_res;
        }
        { // '*' lambda_param_no_default lambda_param_maybe_default* lambda_kwds?
            Token _literal;
            ArgTy a;
            NameDefaultPair[] b;
            ArgTy c;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = lambda_param_no_default_rule()) != null  // lambda_param_no_default
                &&
                (b = _loop0_137_rule()) != null  // lambda_param_maybe_default*
                &&
                ((c = _tmp_138_rule()) != null || true)  // lambda_kwds?
            )
            {
                // TODO: node.action: _PyPegen_star_etc ( p , a , b , c )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_star_etc ( p , a , b , c ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, LAMBDA_STAR_ETC_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        { // '*' ',' lambda_param_maybe_default+ lambda_kwds?
            Token _literal;
            Token _literal_1;
            NameDefaultPair[] b;
            ArgTy c;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (_literal_1 = expect(12)) != null  // token=','
                &&
                (b = _loop1_139_rule()) != null  // lambda_param_maybe_default+
                &&
                ((c = _tmp_140_rule()) != null || true)  // lambda_kwds?
            )
            {
                // TODO: node.action: _PyPegen_star_etc ( p , NULL , b , c )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_star_etc ( p , NULL , b , c ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, LAMBDA_STAR_ETC_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        { // lambda_kwds
            ArgTy a;
            if (
                (a = lambda_kwds_rule()) != null  // lambda_kwds
            )
            {
                // TODO: node.action: _PyPegen_star_etc ( p , NULL , NULL , a )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_star_etc ( p , NULL , NULL , a ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, LAMBDA_STAR_ETC_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_lambda_star_etc
            Token invalid_lambda_star_etc_var;
            if (
                (invalid_lambda_star_etc_var = invalid_lambda_star_etc_rule()) != null  // invalid_lambda_star_etc
            )
            {
                _res = invalid_lambda_star_etc_var;
                cache.putResult(_mark, LAMBDA_STAR_ETC_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LAMBDA_STAR_ETC_ID, _res);
        return (StarEtc)_res;
    }

    // lambda_kwds: '**' lambda_param_no_default
    public ArgTy lambda_kwds_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LAMBDA_KWDS_ID)) {
            _res = (ArgTy)cache.getResult(_mark, LAMBDA_KWDS_ID);
            return (ArgTy)_res;
        }
        { // '**' lambda_param_no_default
            Token _literal;
            ArgTy a;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (a = lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = a;
                cache.putResult(_mark, LAMBDA_KWDS_ID, _res);
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LAMBDA_KWDS_ID, _res);
        return (ArgTy)_res;
    }

    // lambda_param_no_default: lambda_param ',' | lambda_param &':'
    public ArgTy lambda_param_no_default_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LAMBDA_PARAM_NO_DEFAULT_ID)) {
            _res = (ArgTy)cache.getResult(_mark, LAMBDA_PARAM_NO_DEFAULT_ID);
            return (ArgTy)_res;
        }
        { // lambda_param ','
            Token _literal;
            ArgTy a;
            if (
                (a = lambda_param_rule()) != null  // lambda_param
                &&
                (_literal = expect(12)) != null  // token=','
            )
            {
                _res = a;
                cache.putResult(_mark, LAMBDA_PARAM_NO_DEFAULT_ID, _res);
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        { // lambda_param &':'
            ArgTy a;
            if (
                (a = lambda_param_rule()) != null  // lambda_param
                &&
                genLookahead_expect(true, 11)  // token=':'
            )
            {
                _res = a;
                cache.putResult(_mark, LAMBDA_PARAM_NO_DEFAULT_ID, _res);
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LAMBDA_PARAM_NO_DEFAULT_ID, _res);
        return (ArgTy)_res;
    }

    // lambda_param_with_default:
    //     | lambda_param default_param ','
    //     | lambda_param default_param &':'
    public NameDefaultPair lambda_param_with_default_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LAMBDA_PARAM_WITH_DEFAULT_ID)) {
            _res = (NameDefaultPair)cache.getResult(_mark, LAMBDA_PARAM_WITH_DEFAULT_ID);
            return (NameDefaultPair)_res;
        }
        { // lambda_param default_param ','
            Token _literal;
            ArgTy a;
            ExprTy c;
            if (
                (a = lambda_param_rule()) != null  // lambda_param
                &&
                (c = default_param_rule()) != null  // default_param
                &&
                (_literal = expect(12)) != null  // token=','
            )
            {
                // TODO: node.action: _PyPegen_name_default_pair ( p , a , c , NULL )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_name_default_pair ( p , a , c , NULL ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, LAMBDA_PARAM_WITH_DEFAULT_ID, _res);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        { // lambda_param default_param &':'
            ArgTy a;
            ExprTy c;
            if (
                (a = lambda_param_rule()) != null  // lambda_param
                &&
                (c = default_param_rule()) != null  // default_param
                &&
                genLookahead_expect(true, 11)  // token=':'
            )
            {
                // TODO: node.action: _PyPegen_name_default_pair ( p , a , c , NULL )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_name_default_pair ( p , a , c , NULL ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, LAMBDA_PARAM_WITH_DEFAULT_ID, _res);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LAMBDA_PARAM_WITH_DEFAULT_ID, _res);
        return (NameDefaultPair)_res;
    }

    // lambda_param_maybe_default:
    //     | lambda_param default_param? ','
    //     | lambda_param default_param? &':'
    public NameDefaultPair lambda_param_maybe_default_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LAMBDA_PARAM_MAYBE_DEFAULT_ID)) {
            _res = (NameDefaultPair)cache.getResult(_mark, LAMBDA_PARAM_MAYBE_DEFAULT_ID);
            return (NameDefaultPair)_res;
        }
        { // lambda_param default_param? ','
            Token _literal;
            ArgTy a;
            ExprTy c;
            if (
                (a = lambda_param_rule()) != null  // lambda_param
                &&
                ((c = default_param_rule()) != null || true)  // default_param?
                &&
                (_literal = expect(12)) != null  // token=','
            )
            {
                // TODO: node.action: _PyPegen_name_default_pair ( p , a , c , NULL )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_name_default_pair ( p , a , c , NULL ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, LAMBDA_PARAM_MAYBE_DEFAULT_ID, _res);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        { // lambda_param default_param? &':'
            ArgTy a;
            ExprTy c;
            if (
                (a = lambda_param_rule()) != null  // lambda_param
                &&
                ((c = default_param_rule()) != null || true)  // default_param?
                &&
                genLookahead_expect(true, 11)  // token=':'
            )
            {
                // TODO: node.action: _PyPegen_name_default_pair ( p , a , c , NULL )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_name_default_pair ( p , a , c , NULL ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, LAMBDA_PARAM_MAYBE_DEFAULT_ID, _res);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LAMBDA_PARAM_MAYBE_DEFAULT_ID, _res);
        return (NameDefaultPair)_res;
    }

    // lambda_param: NAME
    public ArgTy lambda_param_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LAMBDA_PARAM_ID)) {
            _res = (ArgTy)cache.getResult(_mark, LAMBDA_PARAM_ID);
            return (ArgTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // NAME
            ExprTy a;
            if (
                (a = name_token()) != null  // NAME
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createArgument(((ExprTy.Name)a).id,null,null,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, LAMBDA_PARAM_ID, _res);
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LAMBDA_PARAM_ID, _res);
        return (ArgTy)_res;
    }

    // disjunction: conjunction (('or' conjunction))+ | conjunction
    public ExprTy disjunction_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DISJUNCTION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, DISJUNCTION_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // conjunction (('or' conjunction))+
            ExprTy a;
            ExprTy[] b;
            if (
                (a = conjunction_rule()) != null  // conjunction
                &&
                (b = _loop1_141_rule()) != null  // (('or' conjunction))+
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createOr(this.insertInFront(a,b),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, DISJUNCTION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // conjunction
            ExprTy conjunction_var;
            if (
                (conjunction_var = conjunction_rule()) != null  // conjunction
            )
            {
                _res = conjunction_var;
                cache.putResult(_mark, DISJUNCTION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DISJUNCTION_ID, _res);
        return (ExprTy)_res;
    }

    // conjunction: inversion (('and' inversion))+ | inversion
    public ExprTy conjunction_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, CONJUNCTION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, CONJUNCTION_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // inversion (('and' inversion))+
            ExprTy a;
            ExprTy[] b;
            if (
                (a = inversion_rule()) != null  // inversion
                &&
                (b = _loop1_142_rule()) != null  // (('and' inversion))+
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAnd(this.insertInFront(a,b),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, CONJUNCTION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // inversion
            ExprTy inversion_var;
            if (
                (inversion_var = inversion_rule()) != null  // inversion
            )
            {
                _res = inversion_var;
                cache.putResult(_mark, CONJUNCTION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, CONJUNCTION_ID, _res);
        return (ExprTy)_res;
    }

    // inversion: 'not' inversion | comparison
    public ExprTy inversion_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVERSION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVERSION_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'not' inversion
            Token _keyword;
            ExprTy a;
            if (
                (_keyword = expect(531)) != null  // token='not'
                &&
                (a = inversion_rule()) != null  // inversion
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createUnaryOp(ExprTy.UnaryOp.Operator.NOT,a,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, INVERSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // comparison
            ExprTy comparison_var;
            if (
                (comparison_var = comparison_rule()) != null  // comparison
            )
            {
                _res = comparison_var;
                cache.putResult(_mark, INVERSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVERSION_ID, _res);
        return (ExprTy)_res;
    }

    // comparison: bitwise_or compare_op_bitwise_or_pair+ | bitwise_or
    public ExprTy comparison_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, COMPARISON_ID)) {
            _res = (ExprTy)cache.getResult(_mark, COMPARISON_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // bitwise_or compare_op_bitwise_or_pair+
            ExprTy a;
            CmpopExprPair[] b;
            if (
                (a = bitwise_or_rule()) != null  // bitwise_or
                &&
                (b = _loop1_143_rule()) != null  // compare_op_bitwise_or_pair+
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createComparison(a,b,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, COMPARISON_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // bitwise_or
            ExprTy bitwise_or_var;
            if (
                (bitwise_or_var = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = bitwise_or_var;
                cache.putResult(_mark, COMPARISON_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, COMPARISON_ID, _res);
        return (ExprTy)_res;
    }

    // compare_op_bitwise_or_pair:
    //     | eq_bitwise_or
    //     | noteq_bitwise_or
    //     | lte_bitwise_or
    //     | lt_bitwise_or
    //     | gte_bitwise_or
    //     | gt_bitwise_or
    //     | notin_bitwise_or
    //     | in_bitwise_or
    //     | isnot_bitwise_or
    //     | is_bitwise_or
    public CmpopExprPair compare_op_bitwise_or_pair_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID)) {
            _res = (CmpopExprPair)cache.getResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID);
            return (CmpopExprPair)_res;
        }
        { // eq_bitwise_or
            CmpopExprPair eq_bitwise_or_var;
            if (
                (eq_bitwise_or_var = eq_bitwise_or_rule()) != null  // eq_bitwise_or
            )
            {
                _res = eq_bitwise_or_var;
                cache.putResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // noteq_bitwise_or
            CmpopExprPair noteq_bitwise_or_var;
            if (
                (noteq_bitwise_or_var = noteq_bitwise_or_rule()) != null  // noteq_bitwise_or
            )
            {
                _res = noteq_bitwise_or_var;
                cache.putResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // lte_bitwise_or
            CmpopExprPair lte_bitwise_or_var;
            if (
                (lte_bitwise_or_var = lte_bitwise_or_rule()) != null  // lte_bitwise_or
            )
            {
                _res = lte_bitwise_or_var;
                cache.putResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // lt_bitwise_or
            CmpopExprPair lt_bitwise_or_var;
            if (
                (lt_bitwise_or_var = lt_bitwise_or_rule()) != null  // lt_bitwise_or
            )
            {
                _res = lt_bitwise_or_var;
                cache.putResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // gte_bitwise_or
            CmpopExprPair gte_bitwise_or_var;
            if (
                (gte_bitwise_or_var = gte_bitwise_or_rule()) != null  // gte_bitwise_or
            )
            {
                _res = gte_bitwise_or_var;
                cache.putResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // gt_bitwise_or
            CmpopExprPair gt_bitwise_or_var;
            if (
                (gt_bitwise_or_var = gt_bitwise_or_rule()) != null  // gt_bitwise_or
            )
            {
                _res = gt_bitwise_or_var;
                cache.putResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // notin_bitwise_or
            CmpopExprPair notin_bitwise_or_var;
            if (
                (notin_bitwise_or_var = notin_bitwise_or_rule()) != null  // notin_bitwise_or
            )
            {
                _res = notin_bitwise_or_var;
                cache.putResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // in_bitwise_or
            CmpopExprPair in_bitwise_or_var;
            if (
                (in_bitwise_or_var = in_bitwise_or_rule()) != null  // in_bitwise_or
            )
            {
                _res = in_bitwise_or_var;
                cache.putResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // isnot_bitwise_or
            CmpopExprPair isnot_bitwise_or_var;
            if (
                (isnot_bitwise_or_var = isnot_bitwise_or_rule()) != null  // isnot_bitwise_or
            )
            {
                _res = isnot_bitwise_or_var;
                cache.putResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // is_bitwise_or
            CmpopExprPair is_bitwise_or_var;
            if (
                (is_bitwise_or_var = is_bitwise_or_rule()) != null  // is_bitwise_or
            )
            {
                _res = is_bitwise_or_var;
                cache.putResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, COMPARE_OP_BITWISE_OR_PAIR_ID, _res);
        return (CmpopExprPair)_res;
    }

    // eq_bitwise_or: '==' bitwise_or
    public CmpopExprPair eq_bitwise_or_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, EQ_BITWISE_OR_ID)) {
            _res = (CmpopExprPair)cache.getResult(_mark, EQ_BITWISE_OR_ID);
            return (CmpopExprPair)_res;
        }
        { // '==' bitwise_or
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(27)) != null  // token='=='
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(ExprTy.Compare.Operator.EQ,a);;
                cache.putResult(_mark, EQ_BITWISE_OR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, EQ_BITWISE_OR_ID, _res);
        return (CmpopExprPair)_res;
    }

    // noteq_bitwise_or: ('!=') bitwise_or
    public CmpopExprPair noteq_bitwise_or_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, NOTEQ_BITWISE_OR_ID)) {
            _res = (CmpopExprPair)cache.getResult(_mark, NOTEQ_BITWISE_OR_ID);
            return (CmpopExprPair)_res;
        }
        { // ('!=') bitwise_or
            Token _tmp_144_var;
            ExprTy a;
            if (
                (_tmp_144_var = _tmp_144_rule()) != null  // '!='
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(ExprTy.Compare.Operator.NOTEQ,a);
                cache.putResult(_mark, NOTEQ_BITWISE_OR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, NOTEQ_BITWISE_OR_ID, _res);
        return (CmpopExprPair)_res;
    }

    // lte_bitwise_or: '<=' bitwise_or
    public CmpopExprPair lte_bitwise_or_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LTE_BITWISE_OR_ID)) {
            _res = (CmpopExprPair)cache.getResult(_mark, LTE_BITWISE_OR_ID);
            return (CmpopExprPair)_res;
        }
        { // '<=' bitwise_or
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(29)) != null  // token='<='
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(ExprTy.Compare.Operator.LTE,a);
                cache.putResult(_mark, LTE_BITWISE_OR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LTE_BITWISE_OR_ID, _res);
        return (CmpopExprPair)_res;
    }

    // lt_bitwise_or: '<' bitwise_or
    public CmpopExprPair lt_bitwise_or_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LT_BITWISE_OR_ID)) {
            _res = (CmpopExprPair)cache.getResult(_mark, LT_BITWISE_OR_ID);
            return (CmpopExprPair)_res;
        }
        { // '<' bitwise_or
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(20)) != null  // token='<'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(ExprTy.Compare.Operator.LT,a);
                cache.putResult(_mark, LT_BITWISE_OR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LT_BITWISE_OR_ID, _res);
        return (CmpopExprPair)_res;
    }

    // gte_bitwise_or: '>=' bitwise_or
    public CmpopExprPair gte_bitwise_or_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, GTE_BITWISE_OR_ID)) {
            _res = (CmpopExprPair)cache.getResult(_mark, GTE_BITWISE_OR_ID);
            return (CmpopExprPair)_res;
        }
        { // '>=' bitwise_or
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(30)) != null  // token='>='
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(ExprTy.Compare.Operator.GTE,a);
                cache.putResult(_mark, GTE_BITWISE_OR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, GTE_BITWISE_OR_ID, _res);
        return (CmpopExprPair)_res;
    }

    // gt_bitwise_or: '>' bitwise_or
    public CmpopExprPair gt_bitwise_or_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, GT_BITWISE_OR_ID)) {
            _res = (CmpopExprPair)cache.getResult(_mark, GT_BITWISE_OR_ID);
            return (CmpopExprPair)_res;
        }
        { // '>' bitwise_or
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(21)) != null  // token='>'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(ExprTy.Compare.Operator.GT,a);
                cache.putResult(_mark, GT_BITWISE_OR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, GT_BITWISE_OR_ID, _res);
        return (CmpopExprPair)_res;
    }

    // notin_bitwise_or: 'not' 'in' bitwise_or
    public CmpopExprPair notin_bitwise_or_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, NOTIN_BITWISE_OR_ID)) {
            _res = (CmpopExprPair)cache.getResult(_mark, NOTIN_BITWISE_OR_ID);
            return (CmpopExprPair)_res;
        }
        { // 'not' 'in' bitwise_or
            Token _keyword;
            Token _keyword_1;
            ExprTy a;
            if (
                (_keyword = expect(531)) != null  // token='not'
                &&
                (_keyword_1 = expect(522)) != null  // token='in'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(ExprTy.Compare.Operator.NOTIN,a);
                cache.putResult(_mark, NOTIN_BITWISE_OR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, NOTIN_BITWISE_OR_ID, _res);
        return (CmpopExprPair)_res;
    }

    // in_bitwise_or: 'in' bitwise_or
    public CmpopExprPair in_bitwise_or_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, IN_BITWISE_OR_ID)) {
            _res = (CmpopExprPair)cache.getResult(_mark, IN_BITWISE_OR_ID);
            return (CmpopExprPair)_res;
        }
        { // 'in' bitwise_or
            Token _keyword;
            ExprTy a;
            if (
                (_keyword = expect(522)) != null  // token='in'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(ExprTy.Compare.Operator.IN,a);
                cache.putResult(_mark, IN_BITWISE_OR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, IN_BITWISE_OR_ID, _res);
        return (CmpopExprPair)_res;
    }

    // isnot_bitwise_or: 'is' 'not' bitwise_or
    public CmpopExprPair isnot_bitwise_or_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ISNOT_BITWISE_OR_ID)) {
            _res = (CmpopExprPair)cache.getResult(_mark, ISNOT_BITWISE_OR_ID);
            return (CmpopExprPair)_res;
        }
        { // 'is' 'not' bitwise_or
            Token _keyword;
            Token _keyword_1;
            ExprTy a;
            if (
                (_keyword = expect(532)) != null  // token='is'
                &&
                (_keyword_1 = expect(531)) != null  // token='not'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(ExprTy.Compare.Operator.ISNOT,a);
                cache.putResult(_mark, ISNOT_BITWISE_OR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, ISNOT_BITWISE_OR_ID, _res);
        return (CmpopExprPair)_res;
    }

    // is_bitwise_or: 'is' bitwise_or
    public CmpopExprPair is_bitwise_or_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, IS_BITWISE_OR_ID)) {
            _res = (CmpopExprPair)cache.getResult(_mark, IS_BITWISE_OR_ID);
            return (CmpopExprPair)_res;
        }
        { // 'is' bitwise_or
            Token _keyword;
            ExprTy a;
            if (
                (_keyword = expect(532)) != null  // token='is'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(ExprTy.Compare.Operator.IS,a);
                cache.putResult(_mark, IS_BITWISE_OR_ID, _res);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, IS_BITWISE_OR_ID, _res);
        return (CmpopExprPair)_res;
    }

    // Left-recursive
    // bitwise_or: bitwise_or '|' bitwise_xor | bitwise_xor
    public ExprTy bitwise_or_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, BITWISE_OR_ID)) {
            _res = cache.getResult(_mark, BITWISE_OR_ID);
            return (ExprTy)_res;
        }
        int _resmark = mark();
        while (true) {
            cache.putResult(_mark, BITWISE_OR_ID, _res);
            reset(_mark);
            Object _raw = bitwise_or_raw();
            if (_raw == null || mark() <= _resmark)
                break;
            _resmark = mark();
            _res = _raw;
        }
        reset(_resmark);
        return (ExprTy)_res;
    }
    private ExprTy bitwise_or_raw()
    {
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // bitwise_or '|' bitwise_xor
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = bitwise_or_rule()) != null  // bitwise_or
                &&
                (_literal = expect(18)) != null  // token='|'
                &&
                (b = bitwise_xor_rule()) != null  // bitwise_xor
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.BITOR,a,b,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // bitwise_xor
            ExprTy bitwise_xor_var;
            if (
                (bitwise_xor_var = bitwise_xor_rule()) != null  // bitwise_xor
            )
            {
                _res = bitwise_xor_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // Left-recursive
    // bitwise_xor: bitwise_xor '^' bitwise_and | bitwise_and
    public ExprTy bitwise_xor_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, BITWISE_XOR_ID)) {
            _res = cache.getResult(_mark, BITWISE_XOR_ID);
            return (ExprTy)_res;
        }
        int _resmark = mark();
        while (true) {
            cache.putResult(_mark, BITWISE_XOR_ID, _res);
            reset(_mark);
            Object _raw = bitwise_xor_raw();
            if (_raw == null || mark() <= _resmark)
                break;
            _resmark = mark();
            _res = _raw;
        }
        reset(_resmark);
        return (ExprTy)_res;
    }
    private ExprTy bitwise_xor_raw()
    {
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // bitwise_xor '^' bitwise_and
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = bitwise_xor_rule()) != null  // bitwise_xor
                &&
                (_literal = expect(32)) != null  // token='^'
                &&
                (b = bitwise_and_rule()) != null  // bitwise_and
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.BITXOR,a,b,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // bitwise_and
            ExprTy bitwise_and_var;
            if (
                (bitwise_and_var = bitwise_and_rule()) != null  // bitwise_and
            )
            {
                _res = bitwise_and_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // Left-recursive
    // bitwise_and: bitwise_and '&' shift_expr | shift_expr
    public ExprTy bitwise_and_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, BITWISE_AND_ID)) {
            _res = cache.getResult(_mark, BITWISE_AND_ID);
            return (ExprTy)_res;
        }
        int _resmark = mark();
        while (true) {
            cache.putResult(_mark, BITWISE_AND_ID, _res);
            reset(_mark);
            Object _raw = bitwise_and_raw();
            if (_raw == null || mark() <= _resmark)
                break;
            _resmark = mark();
            _res = _raw;
        }
        reset(_resmark);
        return (ExprTy)_res;
    }
    private ExprTy bitwise_and_raw()
    {
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // bitwise_and '&' shift_expr
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = bitwise_and_rule()) != null  // bitwise_and
                &&
                (_literal = expect(19)) != null  // token='&'
                &&
                (b = shift_expr_rule()) != null  // shift_expr
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.BITAND,a,b,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // shift_expr
            ExprTy shift_expr_var;
            if (
                (shift_expr_var = shift_expr_rule()) != null  // shift_expr
            )
            {
                _res = shift_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // Left-recursive
    // shift_expr: shift_expr '<<' sum | shift_expr '>>' sum | sum
    public ExprTy shift_expr_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SHIFT_EXPR_ID)) {
            _res = cache.getResult(_mark, SHIFT_EXPR_ID);
            return (ExprTy)_res;
        }
        int _resmark = mark();
        while (true) {
            cache.putResult(_mark, SHIFT_EXPR_ID, _res);
            reset(_mark);
            Object _raw = shift_expr_raw();
            if (_raw == null || mark() <= _resmark)
                break;
            _resmark = mark();
            _res = _raw;
        }
        reset(_resmark);
        return (ExprTy)_res;
    }
    private ExprTy shift_expr_raw()
    {
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // shift_expr '<<' sum
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = shift_expr_rule()) != null  // shift_expr
                &&
                (_literal = expect(33)) != null  // token='<<'
                &&
                (b = sum_rule()) != null  // sum
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.LSHIFT,a,b,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // shift_expr '>>' sum
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = shift_expr_rule()) != null  // shift_expr
                &&
                (_literal = expect(34)) != null  // token='>>'
                &&
                (b = sum_rule()) != null  // sum
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.RSHIFT,a,b,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // sum
            ExprTy sum_var;
            if (
                (sum_var = sum_rule()) != null  // sum
            )
            {
                _res = sum_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // Left-recursive
    // sum: sum '+' term | sum '-' term | term
    public ExprTy sum_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SUM_ID)) {
            _res = cache.getResult(_mark, SUM_ID);
            return (ExprTy)_res;
        }
        int _resmark = mark();
        while (true) {
            cache.putResult(_mark, SUM_ID, _res);
            reset(_mark);
            Object _raw = sum_raw();
            if (_raw == null || mark() <= _resmark)
                break;
            _resmark = mark();
            _res = _raw;
        }
        reset(_resmark);
        return (ExprTy)_res;
    }
    private ExprTy sum_raw()
    {
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // sum '+' term
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = sum_rule()) != null  // sum
                &&
                (_literal = expect(14)) != null  // token='+'
                &&
                (b = term_rule()) != null  // term
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.ADD,a,b,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // sum '-' term
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = sum_rule()) != null  // sum
                &&
                (_literal = expect(15)) != null  // token='-'
                &&
                (b = term_rule()) != null  // term
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.SUB,a,b,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // term
            ExprTy term_var;
            if (
                (term_var = term_rule()) != null  // term
            )
            {
                _res = term_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // Left-recursive
    // term:
    //     | term '*' factor
    //     | term '/' factor
    //     | term '//' factor
    //     | term '%' factor
    //     | term '@' factor
    //     | factor
    public ExprTy term_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, TERM_ID)) {
            _res = cache.getResult(_mark, TERM_ID);
            return (ExprTy)_res;
        }
        int _resmark = mark();
        while (true) {
            cache.putResult(_mark, TERM_ID, _res);
            reset(_mark);
            Object _raw = term_raw();
            if (_raw == null || mark() <= _resmark)
                break;
            _resmark = mark();
            _res = _raw;
        }
        reset(_resmark);
        return (ExprTy)_res;
    }
    private ExprTy term_raw()
    {
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // term '*' factor
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = term_rule()) != null  // term
                &&
                (_literal = expect(16)) != null  // token='*'
                &&
                (b = factor_rule()) != null  // factor
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.MULT,a,b,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // term '/' factor
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = term_rule()) != null  // term
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                (b = factor_rule()) != null  // factor
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.DIV,a,b,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // term '//' factor
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = term_rule()) != null  // term
                &&
                (_literal = expect(47)) != null  // token='//'
                &&
                (b = factor_rule()) != null  // factor
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.FLOORDIV,a,b,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // term '%' factor
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = term_rule()) != null  // term
                &&
                (_literal = expect(24)) != null  // token='%'
                &&
                (b = factor_rule()) != null  // factor
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.MOD,a,b,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // term '@' factor
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = term_rule()) != null  // term
                &&
                (_literal = expect(49)) != null  // token='@'
                &&
                (b = factor_rule()) != null  // factor
            )
            {
                // TODO: node.action: CHECK_VERSION ( expr_ty , 5 , "The '@' operator is" , _PyAST_BinOp ( a , MatMult , b , EXTRA ) )
                debugMessageln("[33;5;7m!!! TODO: Convert CHECK_VERSION ( expr_ty , 5 , 'The '@' operator is' , _PyAST_BinOp ( a , MatMult , b , EXTRA ) ) to Java !!![0m");
                _res = null;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // factor
            ExprTy factor_var;
            if (
                (factor_var = factor_rule()) != null  // factor
            )
            {
                _res = factor_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // factor: '+' factor | '-' factor | '~' factor | power
    public ExprTy factor_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, FACTOR_ID)) {
            _res = (ExprTy)cache.getResult(_mark, FACTOR_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '+' factor
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(14)) != null  // token='+'
                &&
                (a = factor_rule()) != null  // factor
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createUnaryOp(ExprTy.UnaryOp.Operator.ADD,a,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, FACTOR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '-' factor
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(15)) != null  // token='-'
                &&
                (a = factor_rule()) != null  // factor
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createUnaryOp(ExprTy.UnaryOp.Operator.SUB,a,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, FACTOR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '~' factor
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(31)) != null  // token='~'
                &&
                (a = factor_rule()) != null  // factor
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createUnaryOp(ExprTy.UnaryOp.Operator.INVERT,a,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, FACTOR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // power
            ExprTy power_var;
            if (
                (power_var = power_rule()) != null  // power
            )
            {
                _res = power_var;
                cache.putResult(_mark, FACTOR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, FACTOR_ID, _res);
        return (ExprTy)_res;
    }

    // power: await_primary '**' factor | await_primary
    public ExprTy power_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, POWER_ID)) {
            _res = (ExprTy)cache.getResult(_mark, POWER_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // await_primary '**' factor
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = await_primary_rule()) != null  // await_primary
                &&
                (_literal = expect(35)) != null  // token='**'
                &&
                (b = factor_rule()) != null  // factor
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(ExprTy.BinOp.Operator.POW,a,b,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, POWER_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // await_primary
            ExprTy await_primary_var;
            if (
                (await_primary_var = await_primary_rule()) != null  // await_primary
            )
            {
                _res = await_primary_var;
                cache.putResult(_mark, POWER_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, POWER_ID, _res);
        return (ExprTy)_res;
    }

    // await_primary: AWAIT primary | primary
    public ExprTy await_primary_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, AWAIT_PRIMARY_ID)) {
            _res = (ExprTy)cache.getResult(_mark, AWAIT_PRIMARY_ID);
            return (ExprTy)_res;
        }
        { // AWAIT primary
            ExprTy a;
            Token await_var;
            if (
                (await_var = expect(Token.Kind.AWAIT)) != null  // token='AWAIT'
                &&
                (a = primary_rule()) != null  // primary
            )
            {
                // TODO: node.action: CHECK_VERSION ( expr_ty , 5 , "Await expressions are" , _PyAST_Await ( a , EXTRA ) )
                debugMessageln("[33;5;7m!!! TODO: Convert CHECK_VERSION ( expr_ty , 5 , 'Await expressions are' , _PyAST_Await ( a , EXTRA ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, AWAIT_PRIMARY_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // primary
            ExprTy primary_var;
            if (
                (primary_var = primary_rule()) != null  // primary
            )
            {
                _res = primary_var;
                cache.putResult(_mark, AWAIT_PRIMARY_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, AWAIT_PRIMARY_ID, _res);
        return (ExprTy)_res;
    }

    // Left-recursive
    // primary:
    //     | invalid_primary
    //     | primary '.' NAME
    //     | primary genexp
    //     | primary '(' arguments? ')'
    //     | primary '[' slices ']'
    //     | atom
    public ExprTy primary_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, PRIMARY_ID)) {
            _res = cache.getResult(_mark, PRIMARY_ID);
            return (ExprTy)_res;
        }
        int _resmark = mark();
        while (true) {
            cache.putResult(_mark, PRIMARY_ID, _res);
            reset(_mark);
            Object _raw = primary_raw();
            if (_raw == null || mark() <= _resmark)
                break;
            _resmark = mark();
            _res = _raw;
        }
        reset(_resmark);
        return (ExprTy)_res;
    }
    private ExprTy primary_raw()
    {
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_primary
            ExprTy invalid_primary_var;
            if (
                (invalid_primary_var = invalid_primary_rule()) != null  // invalid_primary
            )
            {
                _res = invalid_primary_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // primary '.' NAME
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = primary_rule()) != null  // primary
                &&
                (_literal = expect(23)) != null  // token='.'
                &&
                (b = name_token()) != null  // NAME
            )
            {
                _res = factory.createGetAttribute(a,((ExprTy.Name)b).id,ExprContext.Load,startToken.startOffset,startToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // primary genexp
            ExprTy a;
            ExprTy b;
            if (
                (a = primary_rule()) != null  // primary
                &&
                (b = genexp_rule()) != null  // genexp
            )
            {
                // TODO: node.action: _PyAST_Call ( a , CHECK ( asdl_expr_seq * , ( ExprTy [ ] ) this . singletonSequence ( b ) ) , NULL , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Call ( a , CHECK ( asdl_expr_seq * , ( ExprTy [ ] ) this . singletonSequence ( b ) ) , NULL , EXTRA ) to Java !!![0m");
                _res = null;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // primary '(' arguments? ')'
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ExprTy b;
            if (
                (a = primary_rule()) != null  // primary
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                ((b = _tmp_145_rule()) != null || true)  // arguments?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createCall(a,b != null ?((ExprTy.Call)b).args : EMPTY_EXPR,b != null ?((ExprTy.Call)b).keywords : EMPTY_KWDS,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // primary '[' slices ']'
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ExprTy b;
            if (
                (a = primary_rule()) != null  // primary
                &&
                (_literal = expect(9)) != null  // token='['
                &&
                (b = slices_rule()) != null  // slices
                &&
                (_literal_1 = expect(10)) != null  // token=']'
            )
            {
                _res = factory.createSubscript(a,b,ExprContext.Load,startToken.startOffset,startToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // atom
            ExprTy atom_var;
            if (
                (atom_var = atom_rule()) != null  // atom
            )
            {
                _res = atom_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // slices: slice !',' | ','.slice+ ','?
    public ExprTy slices_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SLICES_ID)) {
            _res = (ExprTy)cache.getResult(_mark, SLICES_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // slice !','
            ExprTy a;
            if (
                (a = slice_rule()) != null  // slice
                &&
                genLookahead_expect(false, 12)  // token=','
            )
            {
                _res = a;
                cache.putResult(_mark, SLICES_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // ','.slice+ ','?
            Token _opt_var;
            ExprTy[] a;
            if (
                (a = (ExprTy[])_gather_146_rule()) != null  // ','.slice+
                &&
                ((_opt_var = _tmp_148_rule()) != null || true)  // ','?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(a,ExprContext.Load,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, SLICES_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SLICES_ID, _res);
        return (ExprTy)_res;
    }

    // slice: expression? ':' expression? [':' expression?] | named_expression
    public ExprTy slice_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SLICE_ID)) {
            _res = (ExprTy)cache.getResult(_mark, SLICE_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // expression? ':' expression? [':' expression?]
            Token _literal;
            ExprTy a;
            ExprTy b;
            ExprTy c;
            if (
                ((a = _tmp_149_rule()) != null || true)  // expression?
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                ((b = _tmp_150_rule()) != null || true)  // expression?
                &&
                ((c = _tmp_151_rule()) != null || true)  // [':' expression?]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createSlice(a,b,c,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, SLICE_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // named_expression
            ExprTy a;
            if (
                (a = named_expression_rule()) != null  // named_expression
            )
            {
                _res = a;
                cache.putResult(_mark, SLICE_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SLICE_ID, _res);
        return (ExprTy)_res;
    }

    // atom:
    //     | NAME
    //     | 'True'
    //     | 'False'
    //     | 'None'
    //     | &STRING strings
    //     | NUMBER
    //     | &'(' (tuple | group | genexp)
    //     | &'[' (list | listcomp)
    //     | &'{' (dict | set | dictcomp | setcomp)
    //     | '...'
    public ExprTy atom_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ATOM_ID)) {
            _res = (ExprTy)cache.getResult(_mark, ATOM_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // NAME
            ExprTy name_var;
            if (
                (name_var = name_token()) != null  // NAME
            )
            {
                _res = name_var;
                cache.putResult(_mark, ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'True'
            Token _keyword;
            if (
                (_keyword = expect(526)) != null  // token='True'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBooleanLiteral(true,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'False'
            Token _keyword;
            if (
                (_keyword = expect(527)) != null  // token='False'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBooleanLiteral(false,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'None'
            Token _keyword;
            if (
                (_keyword = expect(525)) != null  // token='None'
            )
            {
                _res = factory.createNone(startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // &STRING strings
            ExprTy strings_var;
            if (
                genLookahead_string_token(true)
                &&
                (strings_var = strings_rule()) != null  // strings
            )
            {
                _res = strings_var;
                cache.putResult(_mark, ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // NUMBER
            ExprTy number_var;
            if (
                (number_var = number_token()) != null  // NUMBER
            )
            {
                _res = number_var;
                cache.putResult(_mark, ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // &'(' (tuple | group | genexp)
            ExprTy _tmp_152_var;
            if (
                genLookahead_expect(true, 7)  // token='('
                &&
                (_tmp_152_var = _tmp_152_rule()) != null  // tuple | group | genexp
            )
            {
                _res = _tmp_152_var;
                cache.putResult(_mark, ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // &'[' (list | listcomp)
            ExprTy _tmp_153_var;
            if (
                genLookahead_expect(true, 9)  // token='['
                &&
                (_tmp_153_var = _tmp_153_rule()) != null  // list | listcomp
            )
            {
                _res = _tmp_153_var;
                cache.putResult(_mark, ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // &'{' (dict | set | dictcomp | setcomp)
            ExprTy _tmp_154_var;
            if (
                genLookahead_expect(true, 25)  // token='{'
                &&
                (_tmp_154_var = _tmp_154_rule()) != null  // dict | set | dictcomp | setcomp
            )
            {
                _res = _tmp_154_var;
                cache.putResult(_mark, ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '...'
            Token _literal;
            if (
                (_literal = expect(52)) != null  // token='...'
            )
            {
                _res = factory.createEllipsis(startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, ATOM_ID, _res);
        return (ExprTy)_res;
    }

    // strings: STRING+
    public ExprTy strings_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STRINGS_ID)) {
            _res = (ExprTy)cache.getResult(_mark, STRINGS_ID);
            return (ExprTy)_res;
        }
        { // STRING+
            Token[] a;
            if (
                (a = _loop1_155_rule()) != null  // STRING+
            )
            {
                _res = this.concatenateStrings(a);
                cache.putResult(_mark, STRINGS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STRINGS_ID, _res);
        return (ExprTy)_res;
    }

    // list: '[' star_named_expressions? ']'
    public ExprTy list_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LIST_ID)) {
            _res = (ExprTy)cache.getResult(_mark, LIST_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '[' star_named_expressions? ']'
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            if (
                (_literal = expect(9)) != null  // token='['
                &&
                ((a = _tmp_156_rule()) != null || true)  // star_named_expressions?
                &&
                (_literal_1 = expect(10)) != null  // token=']'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createList(a,ExprContext.Load,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, LIST_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LIST_ID, _res);
        return (ExprTy)_res;
    }

    // listcomp: '[' named_expression for_if_clauses ']' | invalid_comprehension
    public ExprTy listcomp_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, LISTCOMP_ID)) {
            _res = (ExprTy)cache.getResult(_mark, LISTCOMP_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '[' named_expression for_if_clauses ']'
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ComprehensionTy[] b;
            if (
                (_literal = expect(9)) != null  // token='['
                &&
                (a = named_expression_rule()) != null  // named_expression
                &&
                (b = for_if_clauses_rule()) != null  // for_if_clauses
                &&
                (_literal_1 = expect(10)) != null  // token=']'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createListComprehension(a,b,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, LISTCOMP_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_comprehension
            Object invalid_comprehension_var;
            if (
                (invalid_comprehension_var = invalid_comprehension_rule()) != null  // invalid_comprehension
            )
            {
                _res = invalid_comprehension_var;
                cache.putResult(_mark, LISTCOMP_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, LISTCOMP_ID, _res);
        return (ExprTy)_res;
    }

    // tuple: '(' [star_named_expression ',' star_named_expressions?] ')'
    public ExprTy tuple_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, TUPLE_ID)) {
            _res = (ExprTy)cache.getResult(_mark, TUPLE_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '(' [star_named_expression ',' star_named_expressions?] ')'
            Token _literal;
            Token _literal_1;
            Object a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                ((a = _tmp_157_rule()) != null || true)  // [star_named_expression ',' star_named_expressions?]
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple((ExprTy [ ])a,ExprContext.Load,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, TUPLE_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, TUPLE_ID, _res);
        return (ExprTy)_res;
    }

    // group: '(' (yield_expr | named_expression) ')' | invalid_group
    public ExprTy group_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, GROUP_ID)) {
            _res = (ExprTy)cache.getResult(_mark, GROUP_ID);
            return (ExprTy)_res;
        }
        { // '(' (yield_expr | named_expression) ')'
            Token _literal;
            Token _literal_1;
            ExprTy a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (a = _tmp_158_rule()) != null  // yield_expr | named_expression
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                _res = a;
                cache.putResult(_mark, GROUP_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_group
            ExprTy invalid_group_var;
            if (
                (invalid_group_var = invalid_group_rule()) != null  // invalid_group
            )
            {
                _res = invalid_group_var;
                cache.putResult(_mark, GROUP_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, GROUP_ID, _res);
        return (ExprTy)_res;
    }

    // genexp: '(' direct_named_expression for_if_clauses ')' | invalid_comprehension
    public ExprTy genexp_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, GENEXP_ID)) {
            _res = (ExprTy)cache.getResult(_mark, GENEXP_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '(' direct_named_expression for_if_clauses ')'
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ComprehensionTy[] b;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (a = direct_named_expression_rule()) != null  // direct_named_expression
                &&
                (b = for_if_clauses_rule()) != null  // for_if_clauses
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createGenerator(a,b,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, GENEXP_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_comprehension
            Object invalid_comprehension_var;
            if (
                (invalid_comprehension_var = invalid_comprehension_rule()) != null  // invalid_comprehension
            )
            {
                _res = invalid_comprehension_var;
                cache.putResult(_mark, GENEXP_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, GENEXP_ID, _res);
        return (ExprTy)_res;
    }

    // set: '{' star_named_expressions '}'
    public ExprTy set_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SET_ID)) {
            _res = (ExprTy)cache.getResult(_mark, SET_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '{' star_named_expressions '}'
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            if (
                (_literal = expect(25)) != null  // token='{'
                &&
                (a = star_named_expressions_rule()) != null  // star_named_expressions
                &&
                (_literal_1 = expect(26)) != null  // token='}'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createSet(a,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, SET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SET_ID, _res);
        return (ExprTy)_res;
    }

    // setcomp: '{' named_expression for_if_clauses '}' | invalid_comprehension
    public ExprTy setcomp_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SETCOMP_ID)) {
            _res = (ExprTy)cache.getResult(_mark, SETCOMP_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '{' named_expression for_if_clauses '}'
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ComprehensionTy[] b;
            if (
                (_literal = expect(25)) != null  // token='{'
                &&
                (a = named_expression_rule()) != null  // named_expression
                &&
                (b = for_if_clauses_rule()) != null  // for_if_clauses
                &&
                (_literal_1 = expect(26)) != null  // token='}'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createSetComprehension(a,b,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, SETCOMP_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_comprehension
            Object invalid_comprehension_var;
            if (
                (invalid_comprehension_var = invalid_comprehension_rule()) != null  // invalid_comprehension
            )
            {
                _res = invalid_comprehension_var;
                cache.putResult(_mark, SETCOMP_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SETCOMP_ID, _res);
        return (ExprTy)_res;
    }

    // dict: '{' double_starred_kvpairs? '}' | '{' invalid_double_starred_kvpairs '}'
    public ExprTy dict_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DICT_ID)) {
            _res = (ExprTy)cache.getResult(_mark, DICT_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '{' double_starred_kvpairs? '}'
            Token _literal;
            Token _literal_1;
            KeyValuePair[] a;
            if (
                (_literal = expect(25)) != null  // token='{'
                &&
                ((a = _tmp_159_rule()) != null || true)  // double_starred_kvpairs?
                &&
                (_literal_1 = expect(26)) != null  // token='}'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createDict(extractKeys(a),extractValues(a),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, DICT_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '{' invalid_double_starred_kvpairs '}'
            Token _literal;
            Token _literal_1;
            Object invalid_double_starred_kvpairs_var;
            if (
                (_literal = expect(25)) != null  // token='{'
                &&
                (invalid_double_starred_kvpairs_var = invalid_double_starred_kvpairs_rule()) != null  // invalid_double_starred_kvpairs
                &&
                (_literal_1 = expect(26)) != null  // token='}'
            )
            {
                _res = dummyName(_literal, invalid_double_starred_kvpairs_var, _literal_1);
                cache.putResult(_mark, DICT_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DICT_ID, _res);
        return (ExprTy)_res;
    }

    // dictcomp: '{' kvpair for_if_clauses '}' | invalid_dict_comprehension
    public ExprTy dictcomp_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DICTCOMP_ID)) {
            _res = (ExprTy)cache.getResult(_mark, DICTCOMP_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '{' kvpair for_if_clauses '}'
            Token _literal;
            Token _literal_1;
            KeyValuePair a;
            ComprehensionTy[] b;
            if (
                (_literal = expect(25)) != null  // token='{'
                &&
                (a = kvpair_rule()) != null  // kvpair
                &&
                (b = for_if_clauses_rule()) != null  // for_if_clauses
                &&
                (_literal_1 = expect(26)) != null  // token='}'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createDictComprehension(a,b,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, DICTCOMP_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_dict_comprehension
            Object invalid_dict_comprehension_var;
            if (
                (invalid_dict_comprehension_var = invalid_dict_comprehension_rule()) != null  // invalid_dict_comprehension
            )
            {
                _res = invalid_dict_comprehension_var;
                cache.putResult(_mark, DICTCOMP_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DICTCOMP_ID, _res);
        return (ExprTy)_res;
    }

    // double_starred_kvpairs: ','.double_starred_kvpair+ ','?
    public KeyValuePair[] double_starred_kvpairs_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DOUBLE_STARRED_KVPAIRS_ID)) {
            _res = (KeyValuePair[])cache.getResult(_mark, DOUBLE_STARRED_KVPAIRS_ID);
            return (KeyValuePair[])_res;
        }
        { // ','.double_starred_kvpair+ ','?
            Token _opt_var;
            KeyValuePair[] a;
            if (
                (a = (KeyValuePair[])_gather_160_rule()) != null  // ','.double_starred_kvpair+
                &&
                ((_opt_var = (Token)_tmp_162_rule()) != null || true)  // ','?
            )
            {
                _res = a;
                cache.putResult(_mark, DOUBLE_STARRED_KVPAIRS_ID, _res);
                return (KeyValuePair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DOUBLE_STARRED_KVPAIRS_ID, _res);
        return (KeyValuePair[])_res;
    }

    // double_starred_kvpair: '**' bitwise_or | kvpair
    public KeyValuePair double_starred_kvpair_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DOUBLE_STARRED_KVPAIR_ID)) {
            _res = (KeyValuePair)cache.getResult(_mark, DOUBLE_STARRED_KVPAIR_ID);
            return (KeyValuePair)_res;
        }
        { // '**' bitwise_or
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new KeyValuePair(null,a);
                cache.putResult(_mark, DOUBLE_STARRED_KVPAIR_ID, _res);
                return (KeyValuePair)_res;
            }
            reset(_mark);
        }
        { // kvpair
            KeyValuePair kvpair_var;
            if (
                (kvpair_var = kvpair_rule()) != null  // kvpair
            )
            {
                _res = kvpair_var;
                cache.putResult(_mark, DOUBLE_STARRED_KVPAIR_ID, _res);
                return (KeyValuePair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DOUBLE_STARRED_KVPAIR_ID, _res);
        return (KeyValuePair)_res;
    }

    // kvpair: expression ':' expression
    public KeyValuePair kvpair_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, KVPAIR_ID)) {
            _res = (KeyValuePair)cache.getResult(_mark, KVPAIR_ID);
            return (KeyValuePair)_res;
        }
        { // expression ':' expression
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = expression_rule()) != null  // expression
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = expression_rule()) != null  // expression
            )
            {
                _res = new KeyValuePair(a,b);
                cache.putResult(_mark, KVPAIR_ID, _res);
                return (KeyValuePair)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, KVPAIR_ID, _res);
        return (KeyValuePair)_res;
    }

    // for_if_clauses: for_if_clause+
    public ComprehensionTy[] for_if_clauses_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, FOR_IF_CLAUSES_ID)) {
            _res = (ComprehensionTy[])cache.getResult(_mark, FOR_IF_CLAUSES_ID);
            return (ComprehensionTy[])_res;
        }
        { // for_if_clause+
            ComprehensionTy[] a;
            if (
                (a = (ComprehensionTy[])_loop1_163_rule()) != null  // for_if_clause+
            )
            {
                _res = a;
                cache.putResult(_mark, FOR_IF_CLAUSES_ID, _res);
                return (ComprehensionTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, FOR_IF_CLAUSES_ID, _res);
        return (ComprehensionTy[])_res;
    }

    // for_if_clause:
    //     | ASYNC 'for' star_targets 'in' ~ disjunction (('if' disjunction))*
    //     | 'for' star_targets 'in' ~ disjunction (('if' disjunction))*
    //     | invalid_for_target
    public ComprehensionTy for_if_clause_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, FOR_IF_CLAUSE_ID)) {
            _res = (ComprehensionTy)cache.getResult(_mark, FOR_IF_CLAUSE_ID);
            return (ComprehensionTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // ASYNC 'for' star_targets 'in' ~ disjunction (('if' disjunction))*
            int _cut_var = 0;
            Token _keyword;
            Token _keyword_1;
            ExprTy a;
            Token async_var;
            ExprTy b;
            ExprTy[] c;
            if (
                (async_var = expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
                &&
                (_keyword = expect(516)) != null  // token='for'
                &&
                (a = star_targets_rule()) != null  // star_targets
                &&
                (_keyword_1 = expect(522)) != null  // token='in'
                &&
                (_cut_var = 1) != 0
                &&
                (b = disjunction_rule()) != null  // disjunction
                &&
                (c = (ExprTy[])_loop0_164_rule()) != null  // (('if' disjunction))*
            )
            {
                // TODO: node.action: CHECK_VERSION ( comprehension_ty , 6 , "Async comprehensions are" , _PyAST_comprehension ( a , b , c , 1 , p -> arena ) )
                debugMessageln("[33;5;7m!!! TODO: Convert CHECK_VERSION ( comprehension_ty , 6 , 'Async comprehensions are' , _PyAST_comprehension ( a , b , c , 1 , p -> arena ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, FOR_IF_CLAUSE_ID, _res);
                return (ComprehensionTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        { // 'for' star_targets 'in' ~ disjunction (('if' disjunction))*
            int _cut_var = 0;
            Token _keyword;
            Token _keyword_1;
            ExprTy a;
            ExprTy b;
            ExprTy[] c;
            if (
                (_keyword = expect(516)) != null  // token='for'
                &&
                (a = star_targets_rule()) != null  // star_targets
                &&
                (_keyword_1 = expect(522)) != null  // token='in'
                &&
                (_cut_var = 1) != 0
                &&
                (b = disjunction_rule()) != null  // disjunction
                &&
                (c = (ExprTy[])_loop0_165_rule()) != null  // (('if' disjunction))*
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createComprehension(a,b,c,false,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, FOR_IF_CLAUSE_ID, _res);
                return (ComprehensionTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        if (callInvalidRules) { // invalid_for_target
            ExprTy invalid_for_target_var;
            if (
                (invalid_for_target_var = invalid_for_target_rule()) != null  // invalid_for_target
            )
            {
                _res = invalid_for_target_var;
                cache.putResult(_mark, FOR_IF_CLAUSE_ID, _res);
                return (ComprehensionTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, FOR_IF_CLAUSE_ID, _res);
        return (ComprehensionTy)_res;
    }

    // yield_expr: 'yield' 'from' expression | 'yield' star_expressions?
    public ExprTy yield_expr_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, YIELD_EXPR_ID)) {
            _res = (ExprTy)cache.getResult(_mark, YIELD_EXPR_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'yield' 'from' expression
            Token _keyword;
            Token _keyword_1;
            ExprTy a;
            if (
                (_keyword = expect(506)) != null  // token='yield'
                &&
                (_keyword_1 = expect(502)) != null  // token='from'
                &&
                (a = expression_rule()) != null  // expression
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createYield(a,true,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, YIELD_EXPR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'yield' star_expressions?
            Token _keyword;
            ExprTy a;
            if (
                (_keyword = expect(506)) != null  // token='yield'
                &&
                ((a = _tmp_166_rule()) != null || true)  // star_expressions?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createYield(a,false,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, YIELD_EXPR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, YIELD_EXPR_ID, _res);
        return (ExprTy)_res;
    }

    // arguments: args ','? &')' | invalid_arguments
    public ExprTy arguments_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ARGUMENTS_ID)) {
            _res = (ExprTy)cache.getResult(_mark, ARGUMENTS_ID);
            return (ExprTy)_res;
        }
        { // args ','? &')'
            Token _opt_var;
            ExprTy a;
            if (
                (a = args_rule()) != null  // args
                &&
                ((_opt_var = _tmp_167_rule()) != null || true)  // ','?
                &&
                genLookahead_expect(true, 8)  // token=')'
            )
            {
                _res = a;
                cache.putResult(_mark, ARGUMENTS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_arguments
            Object invalid_arguments_var;
            if (
                (invalid_arguments_var = invalid_arguments_rule()) != null  // invalid_arguments
            )
            {
                _res = invalid_arguments_var;
                cache.putResult(_mark, ARGUMENTS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, ARGUMENTS_ID, _res);
        return (ExprTy)_res;
    }

    // args: ','.(starred_expression | direct_named_expression !'=')+ [',' kwargs] | kwargs
    public ExprTy args_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ARGS_ID)) {
            _res = (ExprTy)cache.getResult(_mark, ARGS_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // ','.(starred_expression | direct_named_expression !'=')+ [',' kwargs]
            ExprTy[] a;
            KeywordOrStarred[] b;
            if (
                (a = (ExprTy[])_gather_168_rule()) != null  // ','.(starred_expression | direct_named_expression !'=')+
                &&
                ((b = _tmp_170_rule()) != null || true)  // [',' kwargs]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = this.collectCallSequences(a,b,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, ARGS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // kwargs
            KeywordOrStarred[] a;
            if (
                (a = kwargs_rule()) != null  // kwargs
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createCall(dummyName(),extractStarredExpressions(a),deleteStarredExpressions(a),startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, ARGS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, ARGS_ID, _res);
        return (ExprTy)_res;
    }

    // kwargs:
    //     | ','.kwarg_or_starred+ ',' ','.kwarg_or_double_starred+
    //     | ','.kwarg_or_starred+
    //     | ','.kwarg_or_double_starred+
    public KeywordOrStarred[] kwargs_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, KWARGS_ID)) {
            _res = (KeywordOrStarred[])cache.getResult(_mark, KWARGS_ID);
            return (KeywordOrStarred[])_res;
        }
        { // ','.kwarg_or_starred+ ',' ','.kwarg_or_double_starred+
            Token _literal;
            KeywordOrStarred[] a;
            KeywordOrStarred[] b;
            if (
                (a = (KeywordOrStarred[])_gather_171_rule()) != null  // ','.kwarg_or_starred+
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (b = (KeywordOrStarred[])_gather_173_rule()) != null  // ','.kwarg_or_double_starred+
            )
            {
                _res = this.join(a,b);
                cache.putResult(_mark, KWARGS_ID, _res);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        { // ','.kwarg_or_starred+
            KeywordOrStarred[] _gather_175_var;
            if (
                (_gather_175_var = (KeywordOrStarred[])_gather_175_rule()) != null  // ','.kwarg_or_starred+
            )
            {
                _res = _gather_175_var;
                cache.putResult(_mark, KWARGS_ID, _res);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        { // ','.kwarg_or_double_starred+
            KeywordOrStarred[] _gather_177_var;
            if (
                (_gather_177_var = (KeywordOrStarred[])_gather_177_rule()) != null  // ','.kwarg_or_double_starred+
            )
            {
                _res = _gather_177_var;
                cache.putResult(_mark, KWARGS_ID, _res);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, KWARGS_ID, _res);
        return (KeywordOrStarred[])_res;
    }

    // starred_expression: '*' expression
    public ExprTy starred_expression_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STARRED_EXPRESSION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, STARRED_EXPRESSION_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '*' expression
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = expression_rule()) != null  // expression
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createStarred(a,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, STARRED_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STARRED_EXPRESSION_ID, _res);
        return (ExprTy)_res;
    }

    // kwarg_or_starred: NAME '=' expression | starred_expression | invalid_kwarg
    public KeywordOrStarred kwarg_or_starred_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, KWARG_OR_STARRED_ID)) {
            _res = (KeywordOrStarred)cache.getResult(_mark, KWARG_OR_STARRED_ID);
            return (KeywordOrStarred)_res;
        }
        Token startToken = getAndInitializeToken();
        { // NAME '=' expression
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = name_token()) != null  // NAME
                &&
                (_literal = expect(22)) != null  // token='='
                &&
                (b = expression_rule()) != null  // expression
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = new KeywordOrStarred(factory.createKeyword(((ExprTy.Name)a).id,b,startToken.startOffset,endToken.endOffset),true);
                cache.putResult(_mark, KWARG_OR_STARRED_ID, _res);
                return (KeywordOrStarred)_res;
            }
            reset(_mark);
        }
        { // starred_expression
            ExprTy a;
            if (
                (a = starred_expression_rule()) != null  // starred_expression
            )
            {
                _res = a;
                cache.putResult(_mark, KWARG_OR_STARRED_ID, _res);
                return (KeywordOrStarred)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_kwarg
            ExprTy invalid_kwarg_var;
            if (
                (invalid_kwarg_var = invalid_kwarg_rule()) != null  // invalid_kwarg
            )
            {
                _res = invalid_kwarg_var;
                cache.putResult(_mark, KWARG_OR_STARRED_ID, _res);
                return (KeywordOrStarred)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, KWARG_OR_STARRED_ID, _res);
        return (KeywordOrStarred)_res;
    }

    // kwarg_or_double_starred: NAME '=' expression | '**' expression | invalid_kwarg
    public KeywordOrStarred kwarg_or_double_starred_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, KWARG_OR_DOUBLE_STARRED_ID)) {
            _res = (KeywordOrStarred)cache.getResult(_mark, KWARG_OR_DOUBLE_STARRED_ID);
            return (KeywordOrStarred)_res;
        }
        Token startToken = getAndInitializeToken();
        { // NAME '=' expression
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = name_token()) != null  // NAME
                &&
                (_literal = expect(22)) != null  // token='='
                &&
                (b = expression_rule()) != null  // expression
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = new KeywordOrStarred(factory.createKeyword(((ExprTy.Name)a).id,b,startToken.startOffset,endToken.endOffset),true);
                cache.putResult(_mark, KWARG_OR_DOUBLE_STARRED_ID, _res);
                return (KeywordOrStarred)_res;
            }
            reset(_mark);
        }
        { // '**' expression
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (a = expression_rule()) != null  // expression
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = new KeywordOrStarred(factory.createKeyword(null,a,startToken.startOffset,endToken.endOffset),true);
                cache.putResult(_mark, KWARG_OR_DOUBLE_STARRED_ID, _res);
                return (KeywordOrStarred)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_kwarg
            ExprTy invalid_kwarg_var;
            if (
                (invalid_kwarg_var = invalid_kwarg_rule()) != null  // invalid_kwarg
            )
            {
                _res = invalid_kwarg_var;
                cache.putResult(_mark, KWARG_OR_DOUBLE_STARRED_ID, _res);
                return (KeywordOrStarred)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, KWARG_OR_DOUBLE_STARRED_ID, _res);
        return (KeywordOrStarred)_res;
    }

    // star_targets: star_target !',' | star_target ((',' star_target))* ','?
    public ExprTy star_targets_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_TARGETS_ID)) {
            _res = (ExprTy)cache.getResult(_mark, STAR_TARGETS_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // star_target !','
            ExprTy a;
            if (
                (a = star_target_rule()) != null  // star_target
                &&
                genLookahead_expect(false, 12)  // token=','
            )
            {
                _res = a;
                cache.putResult(_mark, STAR_TARGETS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_target ((',' star_target))* ','?
            Token _opt_var;
            ExprTy a;
            ExprTy[] b;
            if (
                (a = star_target_rule()) != null  // star_target
                &&
                (b = _loop0_179_rule()) != null  // ((',' star_target))*
                &&
                ((_opt_var = _tmp_180_rule()) != null || true)  // ','?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(this.insertInFront(a,b),ExprContext.Store,startToken.startOffset,endToken.endOffset);;
                cache.putResult(_mark, STAR_TARGETS_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STAR_TARGETS_ID, _res);
        return (ExprTy)_res;
    }

    // star_targets_list_seq: ','.star_target+ ','?
    public ExprTy[] star_targets_list_seq_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_TARGETS_LIST_SEQ_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, STAR_TARGETS_LIST_SEQ_ID);
            return (ExprTy[])_res;
        }
        { // ','.star_target+ ','?
            Token _opt_var;
            ExprTy[] a;
            if (
                (a = (ExprTy[])_gather_181_rule()) != null  // ','.star_target+
                &&
                ((_opt_var = _tmp_183_rule()) != null || true)  // ','?
            )
            {
                _res = a;
                cache.putResult(_mark, STAR_TARGETS_LIST_SEQ_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STAR_TARGETS_LIST_SEQ_ID, _res);
        return (ExprTy[])_res;
    }

    // star_targets_tuple_seq: star_target ((',' star_target))+ ','? | star_target ','
    public ExprTy[] star_targets_tuple_seq_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_TARGETS_TUPLE_SEQ_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, STAR_TARGETS_TUPLE_SEQ_ID);
            return (ExprTy[])_res;
        }
        { // star_target ((',' star_target))+ ','?
            Token _opt_var;
            ExprTy a;
            ExprTy[] b;
            if (
                (a = star_target_rule()) != null  // star_target
                &&
                (b = _loop1_184_rule()) != null  // ((',' star_target))+
                &&
                ((_opt_var = _tmp_185_rule()) != null || true)  // ','?
            )
            {
                _res = this.insertInFront(a,b);
                cache.putResult(_mark, STAR_TARGETS_TUPLE_SEQ_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // star_target ','
            Token _literal;
            ExprTy a;
            if (
                (a = star_target_rule()) != null  // star_target
                &&
                (_literal = expect(12)) != null  // token=','
            )
            {
                _res = this.singletonSequence(a);
                cache.putResult(_mark, STAR_TARGETS_TUPLE_SEQ_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STAR_TARGETS_TUPLE_SEQ_ID, _res);
        return (ExprTy[])_res;
    }

    // star_target: '*' (!'*' star_target) | target_with_star_atom
    public ExprTy star_target_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_TARGET_ID)) {
            _res = (ExprTy)cache.getResult(_mark, STAR_TARGET_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '*' (!'*' star_target)
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = _tmp_186_rule()) != null  // !'*' star_target
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createStarred(this.setExprContext(a,ExprContext.Store),ExprContext.Store,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, STAR_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // target_with_star_atom
            ExprTy target_with_star_atom_var;
            if (
                (target_with_star_atom_var = target_with_star_atom_rule()) != null  // target_with_star_atom
            )
            {
                _res = target_with_star_atom_var;
                cache.putResult(_mark, STAR_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STAR_TARGET_ID, _res);
        return (ExprTy)_res;
    }

    // target_with_star_atom:
    //     | t_primary '.' NAME !t_lookahead
    //     | t_primary '[' slices ']' !t_lookahead
    //     | star_atom
    public ExprTy target_with_star_atom_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, TARGET_WITH_STAR_ATOM_ID)) {
            _res = (ExprTy)cache.getResult(_mark, TARGET_WITH_STAR_ATOM_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // t_primary '.' NAME !t_lookahead
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (_literal = expect(23)) != null  // token='.'
                &&
                (b = name_token()) != null  // NAME
                &&
                genLookahead_t_lookahead_rule(false)
            )
            {
                _res = factory.createGetAttribute(a,((ExprTy.Name)b).id,ExprContext.Store,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, TARGET_WITH_STAR_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary '[' slices ']' !t_lookahead
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (_literal = expect(9)) != null  // token='['
                &&
                (b = slices_rule()) != null  // slices
                &&
                (_literal_1 = expect(10)) != null  // token=']'
                &&
                genLookahead_t_lookahead_rule(false)
            )
            {
                _res = factory.createSubscript(a,b,ExprContext.Store,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, TARGET_WITH_STAR_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_atom
            ExprTy star_atom_var;
            if (
                (star_atom_var = star_atom_rule()) != null  // star_atom
            )
            {
                _res = star_atom_var;
                cache.putResult(_mark, TARGET_WITH_STAR_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, TARGET_WITH_STAR_ATOM_ID, _res);
        return (ExprTy)_res;
    }

    // star_atom:
    //     | NAME
    //     | '(' target_with_star_atom ')'
    //     | '(' star_targets_tuple_seq? ')'
    //     | '[' star_targets_list_seq? ']'
    public ExprTy star_atom_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_ATOM_ID)) {
            _res = (ExprTy)cache.getResult(_mark, STAR_ATOM_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // NAME
            ExprTy a;
            if (
                (a = name_token()) != null  // NAME
            )
            {
                _res = this.setExprContext(a,ExprContext.Store);
                cache.putResult(_mark, STAR_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' target_with_star_atom ')'
            Token _literal;
            Token _literal_1;
            ExprTy a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (a = target_with_star_atom_rule()) != null  // target_with_star_atom
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                _res = this.setExprContext(a,ExprContext.Store);
                cache.putResult(_mark, STAR_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' star_targets_tuple_seq? ')'
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                ((a = _tmp_187_rule()) != null || true)  // star_targets_tuple_seq?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(a,ExprContext.Store,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, STAR_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '[' star_targets_list_seq? ']'
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            if (
                (_literal = expect(9)) != null  // token='['
                &&
                ((a = _tmp_188_rule()) != null || true)  // star_targets_list_seq?
                &&
                (_literal_1 = expect(10)) != null  // token=']'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createList(a,ExprContext.Store,startToken.startOffset,endToken.endOffset);
                cache.putResult(_mark, STAR_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STAR_ATOM_ID, _res);
        return (ExprTy)_res;
    }

    // single_target: single_subscript_attribute_target | NAME | '(' single_target ')'
    public ExprTy single_target_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SINGLE_TARGET_ID)) {
            _res = (ExprTy)cache.getResult(_mark, SINGLE_TARGET_ID);
            return (ExprTy)_res;
        }
        { // single_subscript_attribute_target
            ExprTy single_subscript_attribute_target_var;
            if (
                (single_subscript_attribute_target_var = single_subscript_attribute_target_rule()) != null  // single_subscript_attribute_target
            )
            {
                _res = single_subscript_attribute_target_var;
                cache.putResult(_mark, SINGLE_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // NAME
            ExprTy a;
            if (
                (a = name_token()) != null  // NAME
            )
            {
                _res = this.setExprContext(a,ExprContext.Store);
                cache.putResult(_mark, SINGLE_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' single_target ')'
            Token _literal;
            Token _literal_1;
            ExprTy a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (a = single_target_rule()) != null  // single_target
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                _res = a;
                cache.putResult(_mark, SINGLE_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SINGLE_TARGET_ID, _res);
        return (ExprTy)_res;
    }

    // single_subscript_attribute_target:
    //     | t_primary '.' NAME !t_lookahead
    //     | t_primary '[' slices ']' !t_lookahead
    public ExprTy single_subscript_attribute_target_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SINGLE_SUBSCRIPT_ATTRIBUTE_TARGET_ID)) {
            _res = (ExprTy)cache.getResult(_mark, SINGLE_SUBSCRIPT_ATTRIBUTE_TARGET_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // t_primary '.' NAME !t_lookahead
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (_literal = expect(23)) != null  // token='.'
                &&
                (b = name_token()) != null  // NAME
                &&
                genLookahead_t_lookahead_rule(false)
            )
            {
                _res = factory.createGetAttribute(a,((ExprTy.Name)b).id,ExprContext.Store,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, SINGLE_SUBSCRIPT_ATTRIBUTE_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary '[' slices ']' !t_lookahead
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (_literal = expect(9)) != null  // token='['
                &&
                (b = slices_rule()) != null  // slices
                &&
                (_literal_1 = expect(10)) != null  // token=']'
                &&
                genLookahead_t_lookahead_rule(false)
            )
            {
                _res = factory.createSubscript(a,b,ExprContext.Store,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, SINGLE_SUBSCRIPT_ATTRIBUTE_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, SINGLE_SUBSCRIPT_ATTRIBUTE_TARGET_ID, _res);
        return (ExprTy)_res;
    }

    // del_targets: ','.del_target+ ','?
    public ExprTy[] del_targets_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DEL_TARGETS_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, DEL_TARGETS_ID);
            return (ExprTy[])_res;
        }
        { // ','.del_target+ ','?
            Token _opt_var;
            ExprTy[] a;
            if (
                (a = (ExprTy[])_gather_189_rule()) != null  // ','.del_target+
                &&
                ((_opt_var = _tmp_191_rule()) != null || true)  // ','?
            )
            {
                _res = a;
                cache.putResult(_mark, DEL_TARGETS_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DEL_TARGETS_ID, _res);
        return (ExprTy[])_res;
    }

    // del_target:
    //     | t_primary '.' NAME !t_lookahead
    //     | t_primary '[' slices ']' !t_lookahead
    //     | del_t_atom
    public ExprTy del_target_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DEL_TARGET_ID)) {
            _res = (ExprTy)cache.getResult(_mark, DEL_TARGET_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // t_primary '.' NAME !t_lookahead
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (_literal = expect(23)) != null  // token='.'
                &&
                (b = name_token()) != null  // NAME
                &&
                genLookahead_t_lookahead_rule(false)
            )
            {
                _res = factory.createGetAttribute(a,((ExprTy.Name)b).id,ExprContext.Delete,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, DEL_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary '[' slices ']' !t_lookahead
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (_literal = expect(9)) != null  // token='['
                &&
                (b = slices_rule()) != null  // slices
                &&
                (_literal_1 = expect(10)) != null  // token=']'
                &&
                genLookahead_t_lookahead_rule(false)
            )
            {
                _res = factory.createSubscript(a,b,ExprContext.Delete,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, DEL_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // del_t_atom
            ExprTy del_t_atom_var;
            if (
                (del_t_atom_var = del_t_atom_rule()) != null  // del_t_atom
            )
            {
                _res = del_t_atom_var;
                cache.putResult(_mark, DEL_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DEL_TARGET_ID, _res);
        return (ExprTy)_res;
    }

    // del_t_atom: NAME | '(' del_target ')' | '(' del_targets? ')' | '[' del_targets? ']'
    public ExprTy del_t_atom_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DEL_T_ATOM_ID)) {
            _res = (ExprTy)cache.getResult(_mark, DEL_T_ATOM_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // NAME
            ExprTy a;
            if (
                (a = name_token()) != null  // NAME
            )
            {
                _res = this.setExprContext(a,ExprContext.Delete);
                cache.putResult(_mark, DEL_T_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' del_target ')'
            Token _literal;
            Token _literal_1;
            ExprTy a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (a = del_target_rule()) != null  // del_target
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                _res = this.setExprContext(a,ExprContext.Delete);
                cache.putResult(_mark, DEL_T_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' del_targets? ')'
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                ((a = _tmp_192_rule()) != null || true)  // del_targets?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                _res = factory.createTuple(a,ExprContext.Delete,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, DEL_T_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '[' del_targets? ']'
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            if (
                (_literal = expect(9)) != null  // token='['
                &&
                ((a = _tmp_193_rule()) != null || true)  // del_targets?
                &&
                (_literal_1 = expect(10)) != null  // token=']'
            )
            {
                _res = factory.createList(a,ExprContext.Delete,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, DEL_T_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, DEL_T_ATOM_ID, _res);
        return (ExprTy)_res;
    }

    // targets: ','.target+ ','?
    public ExprTy[] targets_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, TARGETS_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, TARGETS_ID);
            return (ExprTy[])_res;
        }
        { // ','.target+ ','?
            Token _opt_var;
            ExprTy[] a;
            if (
                (a = (ExprTy[])_gather_194_rule()) != null  // ','.target+
                &&
                ((_opt_var = _tmp_196_rule()) != null || true)  // ','?
            )
            {
                _res = a;
                cache.putResult(_mark, TARGETS_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, TARGETS_ID, _res);
        return (ExprTy[])_res;
    }

    // target:
    //     | t_primary '.' NAME !t_lookahead
    //     | t_primary '[' slices ']' !t_lookahead
    //     | t_atom
    public ExprTy target_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, TARGET_ID)) {
            _res = (ExprTy)cache.getResult(_mark, TARGET_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // t_primary '.' NAME !t_lookahead
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (_literal = expect(23)) != null  // token='.'
                &&
                (b = name_token()) != null  // NAME
                &&
                genLookahead_t_lookahead_rule(false)
            )
            {
                _res = factory.createGetAttribute(a,((ExprTy.Name)b).id,ExprContext.Store,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary '[' slices ']' !t_lookahead
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (_literal = expect(9)) != null  // token='['
                &&
                (b = slices_rule()) != null  // slices
                &&
                (_literal_1 = expect(10)) != null  // token=']'
                &&
                genLookahead_t_lookahead_rule(false)
            )
            {
                _res = factory.createSubscript(a,b,ExprContext.Store,startToken.startOffset,startToken.endOffset);
                cache.putResult(_mark, TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_atom
            ExprTy t_atom_var;
            if (
                (t_atom_var = t_atom_rule()) != null  // t_atom
            )
            {
                _res = t_atom_var;
                cache.putResult(_mark, TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, TARGET_ID, _res);
        return (ExprTy)_res;
    }

    // Left-recursive
    // t_primary:
    //     | t_primary '.' NAME &t_lookahead
    //     | t_primary '[' slices ']' &t_lookahead
    //     | t_primary genexp &t_lookahead
    //     | t_primary '(' arguments? ')' &t_lookahead
    //     | atom &t_lookahead
    public ExprTy t_primary_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, T_PRIMARY_ID)) {
            _res = cache.getResult(_mark, T_PRIMARY_ID);
            return (ExprTy)_res;
        }
        int _resmark = mark();
        while (true) {
            cache.putResult(_mark, T_PRIMARY_ID, _res);
            reset(_mark);
            Object _raw = t_primary_raw();
            if (_raw == null || mark() <= _resmark)
                break;
            _resmark = mark();
            _res = _raw;
        }
        reset(_resmark);
        return (ExprTy)_res;
    }
    private ExprTy t_primary_raw()
    {
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // t_primary '.' NAME &t_lookahead
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (_literal = expect(23)) != null  // token='.'
                &&
                (b = name_token()) != null  // NAME
                &&
                genLookahead_t_lookahead_rule(true)
            )
            {
                _res = factory.createGetAttribute(a,((ExprTy.Name)b).id,ExprContext.Load,startToken.startOffset,startToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary '[' slices ']' &t_lookahead
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (_literal = expect(9)) != null  // token='['
                &&
                (b = slices_rule()) != null  // slices
                &&
                (_literal_1 = expect(10)) != null  // token=']'
                &&
                genLookahead_t_lookahead_rule(true)
            )
            {
                _res = factory.createSubscript(a,b,ExprContext.Load,startToken.startOffset,startToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary genexp &t_lookahead
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (b = genexp_rule()) != null  // genexp
                &&
                genLookahead_t_lookahead_rule(true)
            )
            {
                // TODO: node.action: _PyAST_Call ( a , CHECK ( asdl_expr_seq * , ( ExprTy [ ] ) this . singletonSequence ( b ) ) , NULL , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Call ( a , CHECK ( asdl_expr_seq * , ( ExprTy [ ] ) this . singletonSequence ( b ) ) , NULL , EXTRA ) to Java !!![0m");
                _res = null;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary '(' arguments? ')' &t_lookahead
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                ((b = _tmp_197_rule()) != null || true)  // arguments?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
                &&
                genLookahead_t_lookahead_rule(true)
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createCall(a,b != null ?((ExprTy.Call)b).args : EMPTY_EXPR,b != null ?((ExprTy.Call)b).keywords : EMPTY_KWDS,startToken.startOffset,endToken.endOffset);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // atom &t_lookahead
            ExprTy a;
            if (
                (a = atom_rule()) != null  // atom
                &&
                genLookahead_t_lookahead_rule(true)
            )
            {
                _res = a;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // t_lookahead: '(' | '[' | '.'
    public Token t_lookahead_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, T_LOOKAHEAD_ID)) {
            _res = (Token)cache.getResult(_mark, T_LOOKAHEAD_ID);
            return (Token)_res;
        }
        { // '('
            Token _literal;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
            )
            {
                _res = _literal;
                cache.putResult(_mark, T_LOOKAHEAD_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '['
            Token _literal;
            if (
                (_literal = (Token)expect(9)) != null  // token='['
            )
            {
                _res = _literal;
                cache.putResult(_mark, T_LOOKAHEAD_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '.'
            Token _literal;
            if (
                (_literal = (Token)expect(23)) != null  // token='.'
            )
            {
                _res = _literal;
                cache.putResult(_mark, T_LOOKAHEAD_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, T_LOOKAHEAD_ID, _res);
        return (Token)_res;
    }

    // t_atom: NAME | '(' target ')' | '(' targets? ')' | '[' targets? ']'
    public ExprTy t_atom_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, T_ATOM_ID)) {
            _res = (ExprTy)cache.getResult(_mark, T_ATOM_ID);
            return (ExprTy)_res;
        }
        { // NAME
            ExprTy a;
            if (
                (a = name_token()) != null  // NAME
            )
            {
                // TODO: node.action: _PyPegen_set_expr_context ( p , a , Store )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_set_expr_context ( p , a , Store ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, T_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' target ')'
            Token _literal;
            Token _literal_1;
            ExprTy a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (a = target_rule()) != null  // target
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                // TODO: node.action: _PyPegen_set_expr_context ( p , a , Store )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_set_expr_context ( p , a , Store ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, T_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' targets? ')'
            Token _literal;
            Token _literal_1;
            ExprTy[] b;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                ((b = _tmp_198_rule()) != null || true)  // targets?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                // TODO: node.action: _PyAST_Tuple ( b , Store , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_Tuple ( b , Store , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, T_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '[' targets? ']'
            Token _literal;
            Token _literal_1;
            ExprTy[] b;
            if (
                (_literal = expect(9)) != null  // token='['
                &&
                ((b = _tmp_199_rule()) != null || true)  // targets?
                &&
                (_literal_1 = expect(10)) != null  // token=']'
            )
            {
                // TODO: node.action: _PyAST_List ( b , Store , EXTRA )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyAST_List ( b , Store , EXTRA ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, T_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, T_ATOM_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_arguments:
    //     | args ',' '*'
    //     | expression for_if_clauses ',' [args | expression for_if_clauses]
    //     | args for_if_clauses
    //     | args ',' expression for_if_clauses
    //     | args ',' args
    public Object invalid_arguments_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_ARGUMENTS_ID)) {
            _res = (Object)cache.getResult(_mark, INVALID_ARGUMENTS_ID);
            return (Object)_res;
        }
        { // args ',' '*'
            Token _literal;
            Token _literal_1;
            ExprTy args_var;
            if (
                (args_var = (ExprTy)args_rule()) != null  // args
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (_literal_1 = (Token)expect(16)) != null  // token='*'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR ( "iterable argument unpacking follows keyword argument unpacking" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR ( 'iterable argument unpacking follows keyword argument unpacking' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_ARGUMENTS_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // expression for_if_clauses ',' [args | expression for_if_clauses]
            Token _literal;
            Object _opt_var;
            ExprTy a;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                ((_opt_var = (Object)_tmp_200_rule()) != null || true)  // [args | expression for_if_clauses]
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "Generator expression must be parenthesized" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'Generator expression must be parenthesized' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_ARGUMENTS_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // args for_if_clauses
            ExprTy a;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (a = (ExprTy)args_rule()) != null  // args
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                // TODO: node.action: _PyPegen_nonparen_genexp_in_call ( p , a )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_nonparen_genexp_in_call ( p , a ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_ARGUMENTS_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // args ',' expression for_if_clauses
            Token _literal;
            ExprTy a;
            ExprTy args_var;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (args_var = (ExprTy)args_rule()) != null  // args
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "Generator expression must be parenthesized" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'Generator expression must be parenthesized' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_ARGUMENTS_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // args ',' args
            Token _literal;
            ExprTy a;
            ExprTy args_var;
            if (
                (a = (ExprTy)args_rule()) != null  // args
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (args_var = (ExprTy)args_rule()) != null  // args
            )
            {
                // TODO: node.action: _PyPegen_arguments_parsing_error ( p , a )
                debugMessageln("[33;5;7m!!! TODO: Convert _PyPegen_arguments_parsing_error ( p , a ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_ARGUMENTS_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_ARGUMENTS_ID, _res);
        return (Object)_res;
    }

    // invalid_kwarg: expression '='
    public ExprTy invalid_kwarg_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_KWARG_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_KWARG_ID);
            return (ExprTy)_res;
        }
        { // expression '='
            Token a;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (a = (Token)expect(22)) != null  // token='='
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "expression cannot contain assignment, perhaps you meant \"==\"?" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'expression cannot contain assignment, perhaps you meant \'==\'?' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_KWARG_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_KWARG_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_expression: !(NAME STRING | SOFT_KEYWORD) disjunction expression
    public ExprTy invalid_expression_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_EXPRESSION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_EXPRESSION_ID);
            return (ExprTy)_res;
        }
        { // !(NAME STRING | SOFT_KEYWORD) disjunction expression
            ExprTy a;
            ExprTy expression_var;
            if (
                genLookahead__tmp_201_rule(false)
                &&
                (a = (ExprTy)disjunction_rule()) != null  // disjunction
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                // TODO: node.action: RAISE_ERROR_KNOWN_LOCATION ( p , PyExc_SyntaxError , a -> lineno , a -> end_col_offset - 1 , "invalid syntax. Perhaps you forgot a comma?" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_ERROR_KNOWN_LOCATION ( p , PyExc_SyntaxError , a -> lineno , a -> end_col_offset - 1 , 'invalid syntax. Perhaps you forgot a comma?' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_EXPRESSION_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_named_expression:
    //     | expression ':=' expression
    //     | NAME '=' bitwise_or !('=' | ':=' | ',')
    //     | !(list | tuple | genexp | 'True' | 'None' | 'False') bitwise_or '=' bitwise_or !('=' | ':=' | ',')
    public Object invalid_named_expression_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_NAMED_EXPRESSION_ID)) {
            _res = (Object)cache.getResult(_mark, INVALID_NAMED_EXPRESSION_ID);
            return (Object)_res;
        }
        { // expression ':=' expression
            Token _literal;
            ExprTy a;
            ExprTy expression_var;
            if (
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                (_literal = (Token)expect(53)) != null  // token=':='
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "cannot use assignment expressions with %s" , _PyPegen_get_expr_name ( a ) )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'cannot use assignment expressions with %s' , _PyPegen_get_expr_name ( a ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_NAMED_EXPRESSION_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // NAME '=' bitwise_or !('=' | ':=' | ',')
            ExprTy a;
            Token b;
            ExprTy bitwise_or_var;
            if (
                (a = (ExprTy)name_token()) != null  // NAME
                &&
                (b = (Token)expect(22)) != null  // token='='
                &&
                (bitwise_or_var = (ExprTy)bitwise_or_rule()) != null  // bitwise_or
                &&
                genLookahead__tmp_202_rule(false)
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( b , "invalid syntax. Maybe you meant '==' or ':=' instead of '='?" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( b , 'invalid syntax. Maybe you meant '==' or ':=' instead of '='?' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_NAMED_EXPRESSION_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // !(list | tuple | genexp | 'True' | 'None' | 'False') bitwise_or '=' bitwise_or !('=' | ':=' | ',')
            ExprTy a;
            Token b;
            ExprTy bitwise_or_var;
            if (
                genLookahead__tmp_203_rule(false)
                &&
                (a = (ExprTy)bitwise_or_rule()) != null  // bitwise_or
                &&
                (b = (Token)expect(22)) != null  // token='='
                &&
                (bitwise_or_var = (ExprTy)bitwise_or_rule()) != null  // bitwise_or
                &&
                genLookahead__tmp_204_rule(false)
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( b , "cannot assign to %s here. Maybe you meant '==' instead of '='?" , _PyPegen_get_expr_name ( a ) )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( b , 'cannot assign to %s here. Maybe you meant '==' instead of '='?' , _PyPegen_get_expr_name ( a ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_NAMED_EXPRESSION_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_NAMED_EXPRESSION_ID, _res);
        return (Object)_res;
    }

    // invalid_assignment:
    //     | invalid_ann_assign_target ':' expression
    //     | star_named_expression ',' star_named_expressions* ':' expression
    //     | expression ':' expression
    //     | ((star_targets '='))* star_expressions '='
    //     | ((star_targets '='))* yield_expr '='
    //     | star_expressions augassign (yield_expr | star_expressions)
    public Object invalid_assignment_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_ASSIGNMENT_ID)) {
            _res = (Object)cache.getResult(_mark, INVALID_ASSIGNMENT_ID);
            return (Object)_res;
        }
        { // invalid_ann_assign_target ':' expression
            Token _literal;
            ExprTy a;
            ExprTy expression_var;
            if (
                (a = (ExprTy)invalid_ann_assign_target_rule()) != null  // invalid_ann_assign_target
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "only single target (not %s) can be annotated" , _PyPegen_get_expr_name ( a ) )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'only single target (not %s) can be annotated' , _PyPegen_get_expr_name ( a ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_ASSIGNMENT_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // star_named_expression ',' star_named_expressions* ':' expression
            Token _literal;
            Token _literal_1;
            ExprTy[] _loop0_205_var;
            ExprTy a;
            ExprTy expression_var;
            if (
                (a = (ExprTy)star_named_expression_rule()) != null  // star_named_expression
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (_loop0_205_var = (ExprTy[])_loop0_205_rule()) != null  // star_named_expressions*
                &&
                (_literal_1 = (Token)expect(11)) != null  // token=':'
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "only single target (not tuple) can be annotated" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'only single target (not tuple) can be annotated' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_ASSIGNMENT_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // expression ':' expression
            Token _literal;
            ExprTy a;
            ExprTy expression_var;
            if (
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "illegal target for annotation" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'illegal target for annotation' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_ASSIGNMENT_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ((star_targets '='))* star_expressions '='
            Token _literal;
            ExprTy[] _loop0_206_var;
            ExprTy a;
            if (
                (_loop0_206_var = (ExprTy[])_loop0_206_rule()) != null  // ((star_targets '='))*
                &&
                (a = (ExprTy)star_expressions_rule()) != null  // star_expressions
                &&
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_INVALID_TARGET ( STAR_TARGETS , a )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_INVALID_TARGET ( STAR_TARGETS , a ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_ASSIGNMENT_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ((star_targets '='))* yield_expr '='
            Token _literal;
            ExprTy[] _loop0_207_var;
            ExprTy a;
            if (
                (_loop0_207_var = (ExprTy[])_loop0_207_rule()) != null  // ((star_targets '='))*
                &&
                (a = (ExprTy)yield_expr_rule()) != null  // yield_expr
                &&
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "assignment to yield expression not possible" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'assignment to yield expression not possible' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_ASSIGNMENT_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // star_expressions augassign (yield_expr | star_expressions)
            ExprTy _tmp_208_var;
            ExprTy a;
            ExprTy.BinOp.Operator augassign_var;
            if (
                (a = (ExprTy)star_expressions_rule()) != null  // star_expressions
                &&
                (augassign_var = (ExprTy.BinOp.Operator)augassign_rule()) != null  // augassign
                &&
                (_tmp_208_var = (ExprTy)_tmp_208_rule()) != null  // yield_expr | star_expressions
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "'%s' is an illegal expression for augmented assignment" , _PyPegen_get_expr_name ( a ) )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , ''%s' is an illegal expression for augmented assignment' , _PyPegen_get_expr_name ( a ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_ASSIGNMENT_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_ASSIGNMENT_ID, _res);
        return (Object)_res;
    }

    // invalid_ann_assign_target: list | tuple | '(' invalid_ann_assign_target ')'
    public ExprTy invalid_ann_assign_target_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_ANN_ASSIGN_TARGET_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_ANN_ASSIGN_TARGET_ID);
            return (ExprTy)_res;
        }
        { // list
            ExprTy list_var;
            if (
                (list_var = list_rule()) != null  // list
            )
            {
                _res = list_var;
                cache.putResult(_mark, INVALID_ANN_ASSIGN_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // tuple
            ExprTy tuple_var;
            if (
                (tuple_var = tuple_rule()) != null  // tuple
            )
            {
                _res = tuple_var;
                cache.putResult(_mark, INVALID_ANN_ASSIGN_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' invalid_ann_assign_target ')'
            Token _literal;
            Token _literal_1;
            ExprTy a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (a = invalid_ann_assign_target_rule()) != null  // invalid_ann_assign_target
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                _res = a;
                cache.putResult(_mark, INVALID_ANN_ASSIGN_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_ANN_ASSIGN_TARGET_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_del_stmt: 'del' star_expressions
    public ExprTy invalid_del_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_DEL_STMT_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_DEL_STMT_ID);
            return (ExprTy)_res;
        }
        { // 'del' star_expressions
            Token _keyword;
            ExprTy a;
            if (
                (_keyword = (Token)expect(505)) != null  // token='del'
                &&
                (a = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_INVALID_TARGET ( DEL_TARGETS , a )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_INVALID_TARGET ( DEL_TARGETS , a ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_DEL_STMT_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_DEL_STMT_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_block: NEWLINE !INDENT
    public Token invalid_block_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_BLOCK_ID)) {
            _res = (Token)cache.getResult(_mark, INVALID_BLOCK_ID);
            return (Token)_res;
        }
        { // NEWLINE !INDENT
            Token newline_var;
            if (
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                // TODO: node.action: RAISE_INDENTATION_ERROR ( "expected an indented block" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_INDENTATION_ERROR ( 'expected an indented block' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_BLOCK_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_BLOCK_ID, _res);
        return (Token)_res;
    }

    // Left-recursive
    // invalid_primary: primary '{'
    public ExprTy invalid_primary_rule()
    {
        int _mark = mark();
        Object _res = null;
        { // primary '{'
            Token a;
            ExprTy primary_var;
            if (
                (primary_var = (ExprTy)primary_rule()) != null  // primary
                &&
                (a = (Token)expect(25)) != null  // token='{'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "invalid syntax" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'invalid syntax' ) to Java !!![0m");
                _res = null;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_comprehension:
    //     | ('[' | '(' | '{') starred_expression for_if_clauses
    //     | ('[' | '{') star_named_expression ',' star_named_expressions? for_if_clauses
    public Object invalid_comprehension_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_COMPREHENSION_ID)) {
            _res = (Object)cache.getResult(_mark, INVALID_COMPREHENSION_ID);
            return (Object)_res;
        }
        { // ('[' | '(' | '{') starred_expression for_if_clauses
            Token _tmp_209_var;
            ExprTy a;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (_tmp_209_var = (Token)_tmp_209_rule()) != null  // '[' | '(' | '{'
                &&
                (a = (ExprTy)starred_expression_rule()) != null  // starred_expression
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "iterable unpacking cannot be used in comprehension" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'iterable unpacking cannot be used in comprehension' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_COMPREHENSION_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ('[' | '{') star_named_expression ',' star_named_expressions? for_if_clauses
            Token _literal;
            ExprTy[] _opt_var;
            Token _tmp_210_var;
            ExprTy a;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (_tmp_210_var = (Token)_tmp_210_rule()) != null  // '[' | '{'
                &&
                (a = (ExprTy)star_named_expression_rule()) != null  // star_named_expression
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                ((_opt_var = (ExprTy[])_tmp_211_rule()) != null || true)  // star_named_expressions?
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "did you forget parentheses around the comprehension target?" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'did you forget parentheses around the comprehension target?' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_COMPREHENSION_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_COMPREHENSION_ID, _res);
        return (Object)_res;
    }

    // invalid_dict_comprehension: '{' '**' bitwise_or for_if_clauses '}'
    public Object invalid_dict_comprehension_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_DICT_COMPREHENSION_ID)) {
            _res = (Object)cache.getResult(_mark, INVALID_DICT_COMPREHENSION_ID);
            return (Object)_res;
        }
        { // '{' '**' bitwise_or for_if_clauses '}'
            Token _literal;
            Token _literal_1;
            Token a;
            ExprTy bitwise_or_var;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
                &&
                (a = (Token)expect(35)) != null  // token='**'
                &&
                (bitwise_or_var = (ExprTy)bitwise_or_rule()) != null  // bitwise_or
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
                &&
                (_literal_1 = (Token)expect(26)) != null  // token='}'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "dict unpacking cannot be used in dict comprehension" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'dict unpacking cannot be used in dict comprehension' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_DICT_COMPREHENSION_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_DICT_COMPREHENSION_ID, _res);
        return (Object)_res;
    }

    // invalid_parameters: param_no_default* invalid_parameters_helper param_no_default
    public Object invalid_parameters_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_PARAMETERS_ID)) {
            _res = (Object)cache.getResult(_mark, INVALID_PARAMETERS_ID);
            return (Object)_res;
        }
        { // param_no_default* invalid_parameters_helper param_no_default
            ArgTy[] _loop0_212_var;
            Object invalid_parameters_helper_var;
            ArgTy param_no_default_var;
            if (
                (_loop0_212_var = (ArgTy[])_loop0_212_rule()) != null  // param_no_default*
                &&
                (invalid_parameters_helper_var = (Object)invalid_parameters_helper_rule()) != null  // invalid_parameters_helper
                &&
                (param_no_default_var = (ArgTy)param_no_default_rule()) != null  // param_no_default
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR ( "non-default argument follows default argument" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR ( 'non-default argument follows default argument' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_PARAMETERS_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_PARAMETERS_ID, _res);
        return (Object)_res;
    }

    // invalid_parameters_helper: slash_with_default | param_with_default+
    public Object invalid_parameters_helper_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_PARAMETERS_HELPER_ID)) {
            _res = (Object)cache.getResult(_mark, INVALID_PARAMETERS_HELPER_ID);
            return (Object)_res;
        }
        { // slash_with_default
            SlashWithDefault a;
            if (
                (a = (SlashWithDefault)slash_with_default_rule()) != null  // slash_with_default
            )
            {
                _res = this.singletonSequence(a);
                cache.putResult(_mark, INVALID_PARAMETERS_HELPER_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // param_with_default+
            NameDefaultPair[] _loop1_213_var;
            if (
                (_loop1_213_var = (NameDefaultPair[])_loop1_213_rule()) != null  // param_with_default+
            )
            {
                _res = _loop1_213_var;
                cache.putResult(_mark, INVALID_PARAMETERS_HELPER_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_PARAMETERS_HELPER_ID, _res);
        return (Object)_res;
    }

    // invalid_lambda_parameters:
    //     | lambda_param_no_default* invalid_lambda_parameters_helper lambda_param_no_default
    public Object invalid_lambda_parameters_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_LAMBDA_PARAMETERS_ID)) {
            _res = (Object)cache.getResult(_mark, INVALID_LAMBDA_PARAMETERS_ID);
            return (Object)_res;
        }
        { // lambda_param_no_default* invalid_lambda_parameters_helper lambda_param_no_default
            ArgTy[] _loop0_214_var;
            Object invalid_lambda_parameters_helper_var;
            ArgTy lambda_param_no_default_var;
            if (
                (_loop0_214_var = (ArgTy[])_loop0_214_rule()) != null  // lambda_param_no_default*
                &&
                (invalid_lambda_parameters_helper_var = (Object)invalid_lambda_parameters_helper_rule()) != null  // invalid_lambda_parameters_helper
                &&
                (lambda_param_no_default_var = (ArgTy)lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR ( "non-default argument follows default argument" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR ( 'non-default argument follows default argument' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_LAMBDA_PARAMETERS_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_LAMBDA_PARAMETERS_ID, _res);
        return (Object)_res;
    }

    // invalid_lambda_parameters_helper:
    //     | lambda_slash_with_default
    //     | lambda_param_with_default+
    public Object invalid_lambda_parameters_helper_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_LAMBDA_PARAMETERS_HELPER_ID)) {
            _res = (Object)cache.getResult(_mark, INVALID_LAMBDA_PARAMETERS_HELPER_ID);
            return (Object)_res;
        }
        { // lambda_slash_with_default
            SlashWithDefault a;
            if (
                (a = (SlashWithDefault)lambda_slash_with_default_rule()) != null  // lambda_slash_with_default
            )
            {
                _res = this.singletonSequence(a);
                cache.putResult(_mark, INVALID_LAMBDA_PARAMETERS_HELPER_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // lambda_param_with_default+
            NameDefaultPair[] _loop1_215_var;
            if (
                (_loop1_215_var = (NameDefaultPair[])_loop1_215_rule()) != null  // lambda_param_with_default+
            )
            {
                _res = _loop1_215_var;
                cache.putResult(_mark, INVALID_LAMBDA_PARAMETERS_HELPER_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_LAMBDA_PARAMETERS_HELPER_ID, _res);
        return (Object)_res;
    }

    // invalid_star_etc: '*' (')' | ',' (')' | '**')) | '*' ',' TYPE_COMMENT
    public Token invalid_star_etc_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_STAR_ETC_ID)) {
            _res = (Token)cache.getResult(_mark, INVALID_STAR_ETC_ID);
            return (Token)_res;
        }
        { // '*' (')' | ',' (')' | '**'))
            Token _literal;
            Token _tmp_216_var;
            if (
                (_literal = (Token)expect(16)) != null  // token='*'
                &&
                (_tmp_216_var = (Token)_tmp_216_rule()) != null  // ')' | ',' (')' | '**')
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR ( "named arguments must follow bare *" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR ( 'named arguments must follow bare *' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_STAR_ETC_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '*' ',' TYPE_COMMENT
            Token _literal;
            Token _literal_1;
            Token type_comment_var;
            if (
                (_literal = (Token)expect(16)) != null  // token='*'
                &&
                (_literal_1 = (Token)expect(12)) != null  // token=','
                &&
                (type_comment_var = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR ( "bare * has associated type comment" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR ( 'bare * has associated type comment' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_STAR_ETC_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_STAR_ETC_ID, _res);
        return (Token)_res;
    }

    // invalid_lambda_star_etc: '*' (':' | ',' (':' | '**'))
    public Token invalid_lambda_star_etc_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_LAMBDA_STAR_ETC_ID)) {
            _res = (Token)cache.getResult(_mark, INVALID_LAMBDA_STAR_ETC_ID);
            return (Token)_res;
        }
        { // '*' (':' | ',' (':' | '**'))
            Token _literal;
            Token _tmp_217_var;
            if (
                (_literal = (Token)expect(16)) != null  // token='*'
                &&
                (_tmp_217_var = (Token)_tmp_217_rule()) != null  // ':' | ',' (':' | '**')
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR ( "named arguments must follow bare *" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR ( 'named arguments must follow bare *' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_LAMBDA_STAR_ETC_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_LAMBDA_STAR_ETC_ID, _res);
        return (Token)_res;
    }

    // invalid_double_type_comments: TYPE_COMMENT NEWLINE TYPE_COMMENT NEWLINE INDENT
    public Token invalid_double_type_comments_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_DOUBLE_TYPE_COMMENTS_ID)) {
            _res = (Token)cache.getResult(_mark, INVALID_DOUBLE_TYPE_COMMENTS_ID);
            return (Token)_res;
        }
        { // TYPE_COMMENT NEWLINE TYPE_COMMENT NEWLINE INDENT
            Token indent_var;
            Token newline_var;
            Token newline_var_1;
            Token type_comment_var;
            Token type_comment_var_1;
            if (
                (type_comment_var = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                (type_comment_var_1 = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
                &&
                (newline_var_1 = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                (indent_var = (Token)expect(Token.Kind.INDENT)) != null  // token='INDENT'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR ( "Cannot have two type comments on def" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR ( 'Cannot have two type comments on def' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_DOUBLE_TYPE_COMMENTS_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_DOUBLE_TYPE_COMMENTS_ID, _res);
        return (Token)_res;
    }

    // invalid_with_item: expression 'as' expression &(',' | ')' | ':')
    public ExprTy invalid_with_item_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_WITH_ITEM_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_WITH_ITEM_ID);
            return (ExprTy)_res;
        }
        { // expression 'as' expression &(',' | ')' | ':')
            Token _keyword;
            ExprTy a;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (_keyword = (Token)expect(519)) != null  // token='as'
                &&
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                genLookahead__tmp_218_rule(true)
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_INVALID_TARGET ( STAR_TARGETS , a )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_INVALID_TARGET ( STAR_TARGETS , a ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_WITH_ITEM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_WITH_ITEM_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_for_target: ASYNC? 'for' star_expressions
    public ExprTy invalid_for_target_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_FOR_TARGET_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_FOR_TARGET_ID);
            return (ExprTy)_res;
        }
        { // ASYNC? 'for' star_expressions
            Token _keyword;
            Token _opt_var;
            ExprTy a;
            if (
                ((_opt_var = (Token)expect(Token.Kind.ASYNC)) != null || true)  // ASYNC?
                &&
                (_keyword = (Token)expect(516)) != null  // token='for'
                &&
                (a = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_INVALID_TARGET ( FOR_TARGETS , a )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_INVALID_TARGET ( FOR_TARGETS , a ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_FOR_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_FOR_TARGET_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_group: '(' starred_expression ')' | '(' '**' expression ')'
    public ExprTy invalid_group_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_GROUP_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_GROUP_ID);
            return (ExprTy)_res;
        }
        { // '(' starred_expression ')'
            Token _literal;
            Token _literal_1;
            ExprTy a;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
                &&
                (a = (ExprTy)starred_expression_rule()) != null  // starred_expression
                &&
                (_literal_1 = (Token)expect(8)) != null  // token=')'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "cannot use starred expression here" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'cannot use starred expression here' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_GROUP_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' '**' expression ')'
            Token _literal;
            Token _literal_1;
            Token a;
            ExprTy expression_var;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
                &&
                (a = (Token)expect(35)) != null  // token='**'
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (_literal_1 = (Token)expect(8)) != null  // token=')'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "cannot use double starred expression here" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'cannot use double starred expression here' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_GROUP_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_GROUP_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_import_from_targets: import_from_as_names ','
    public AliasTy[] invalid_import_from_targets_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_IMPORT_FROM_TARGETS_ID)) {
            _res = (AliasTy[])cache.getResult(_mark, INVALID_IMPORT_FROM_TARGETS_ID);
            return (AliasTy[])_res;
        }
        { // import_from_as_names ','
            Token _literal;
            AliasTy[] import_from_as_names_var;
            if (
                (import_from_as_names_var = (AliasTy[])import_from_as_names_rule()) != null  // import_from_as_names
                &&
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR ( "trailing comma not allowed without surrounding parentheses" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR ( 'trailing comma not allowed without surrounding parentheses' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_IMPORT_FROM_TARGETS_ID, _res);
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_IMPORT_FROM_TARGETS_ID, _res);
        return (AliasTy[])_res;
    }

    // invalid_with_stmt:
    //     | ASYNC? 'with' ','.(expression ['as' star_target])+ &&':'
    //     | ASYNC? 'with' '(' ','.(expressions ['as' star_target])+ ','? ')' &&':'
    public ExprTy[] invalid_with_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_WITH_STMT_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, INVALID_WITH_STMT_ID);
            return (ExprTy[])_res;
        }
        { // ASYNC? 'with' ','.(expression ['as' star_target])+ &&':'
            ExprTy[] _gather_220_var;
            Token _keyword;
            Token _literal;
            Token _opt_var;
            if (
                ((_opt_var = (Token)_tmp_219_rule()) != null || true)  // ASYNC?
                &&
                (_keyword = (Token)expect(515)) != null  // token='with'
                &&
                (_gather_220_var = (ExprTy[])_gather_220_rule()) != null  // ','.(expression ['as' star_target])+
                &&
                (_literal = (Token)expect_forced_token(11, ":")) != null  // forced_token=':'
            )
            {
                _res = dummyName(_opt_var, _keyword, _gather_220_var, _literal);
                cache.putResult(_mark, INVALID_WITH_STMT_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // ASYNC? 'with' '(' ','.(expressions ['as' star_target])+ ','? ')' &&':'
            ExprTy[] _gather_223_var;
            Token _keyword;
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token _opt_var;
            Token _opt_var_1;
            if (
                ((_opt_var = (Token)_tmp_222_rule()) != null || true)  // ASYNC?
                &&
                (_keyword = (Token)expect(515)) != null  // token='with'
                &&
                (_literal = (Token)expect(7)) != null  // token='('
                &&
                (_gather_223_var = (ExprTy[])_gather_223_rule()) != null  // ','.(expressions ['as' star_target])+
                &&
                ((_opt_var_1 = (Token)expect(12)) != null || true)  // ','?
                &&
                (_literal_1 = (Token)expect(8)) != null  // token=')'
                &&
                (_literal_2 = (Token)expect_forced_token(11, ":")) != null  // forced_token=':'
            )
            {
                _res = dummyName(_opt_var, _keyword, _literal, _gather_223_var, _opt_var_1, _literal_1, _literal_2);
                cache.putResult(_mark, INVALID_WITH_STMT_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_WITH_STMT_ID, _res);
        return (ExprTy[])_res;
    }

    // invalid_except_block:
    //     | 'except' expression ',' expressions ['as' NAME] ':'
    //     | 'except' expression ['as' NAME] &&':'
    //     | 'except' &&':'
    public Object invalid_except_block_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_EXCEPT_BLOCK_ID)) {
            _res = (Object)cache.getResult(_mark, INVALID_EXCEPT_BLOCK_ID);
            return (Object)_res;
        }
        { // 'except' expression ',' expressions ['as' NAME] ':'
            Token _keyword;
            Token _literal;
            Token _literal_1;
            ExprTy _opt_var;
            ExprTy a;
            ExprTy expressions_var;
            if (
                (_keyword = (Token)expect(523)) != null  // token='except'
                &&
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (expressions_var = (ExprTy)expressions_rule()) != null  // expressions
                &&
                ((_opt_var = (ExprTy)_tmp_225_rule()) != null || true)  // ['as' NAME]
                &&
                (_literal_1 = (Token)expect(11)) != null  // token=':'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "exception group must be parenthesized" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'exception group must be parenthesized' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_EXCEPT_BLOCK_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'except' expression ['as' NAME] &&':'
            Token _keyword;
            Token _literal;
            ExprTy _opt_var;
            ExprTy expression_var;
            if (
                (_keyword = (Token)expect(523)) != null  // token='except'
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                ((_opt_var = (ExprTy)_tmp_226_rule()) != null || true)  // ['as' NAME]
                &&
                (_literal = (Token)expect_forced_token(11, ":")) != null  // forced_token=':'
            )
            {
                _res = dummyName(_keyword, expression_var, _opt_var, _literal);
                cache.putResult(_mark, INVALID_EXCEPT_BLOCK_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'except' &&':'
            Token _keyword;
            Token _literal;
            if (
                (_keyword = (Token)expect(523)) != null  // token='except'
                &&
                (_literal = (Token)expect_forced_token(11, ":")) != null  // forced_token=':'
            )
            {
                _res = dummyName(_keyword, _literal);
                cache.putResult(_mark, INVALID_EXCEPT_BLOCK_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_EXCEPT_BLOCK_ID, _res);
        return (Object)_res;
    }

    // invalid_match_stmt: "match" subject_expr !':'
    public ExprTy invalid_match_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_MATCH_STMT_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_MATCH_STMT_ID);
            return (ExprTy)_res;
        }
        { // "match" subject_expr !':'
            ExprTy _keyword;
            ExprTy subject_expr_var;
            if (
                (_keyword = (ExprTy)expect_SOFT_KEYWORD("match")) != null  // soft_keyword='"match"'
                &&
                (subject_expr_var = (ExprTy)subject_expr_rule()) != null  // subject_expr
                &&
                genLookahead_expect(false, 11)  // token=':'
            )
            {
                // TODO: node.action: CHECK_VERSION ( void * , 10 , "Pattern matching is" , RAISE_SYNTAX_ERROR ( "expected ':'" ) )
                debugMessageln("[33;5;7m!!! TODO: Convert CHECK_VERSION ( void * , 10 , 'Pattern matching is' , RAISE_SYNTAX_ERROR ( 'expected ':'' ) ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_MATCH_STMT_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_MATCH_STMT_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_case_block: "case" patterns guard? !':'
    public ExprTy invalid_case_block_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_CASE_BLOCK_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_CASE_BLOCK_ID);
            return (ExprTy)_res;
        }
        { // "case" patterns guard? !':'
            ExprTy _keyword;
            ExprTy _opt_var;
            ExprTy patterns_var;
            if (
                (_keyword = (ExprTy)expect_SOFT_KEYWORD("case")) != null  // soft_keyword='"case"'
                &&
                (patterns_var = (ExprTy)patterns_rule()) != null  // patterns
                &&
                ((_opt_var = (ExprTy)guard_rule()) != null || true)  // guard?
                &&
                genLookahead_expect(false, 11)  // token=':'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR ( "expected ':'" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR ( 'expected ':'' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_CASE_BLOCK_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_CASE_BLOCK_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_if_stmt: 'if' named_expression NEWLINE
    public ExprTy invalid_if_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_IF_STMT_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_IF_STMT_ID);
            return (ExprTy)_res;
        }
        { // 'if' named_expression NEWLINE
            Token _keyword;
            ExprTy named_expression_var;
            Token newline_var;
            if (
                (_keyword = (Token)expect(513)) != null  // token='if'
                &&
                (named_expression_var = (ExprTy)named_expression_rule()) != null  // named_expression
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR ( "expected ':'" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR ( 'expected ':'' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_IF_STMT_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_IF_STMT_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_elif_stmt: 'elif' named_expression NEWLINE
    public ExprTy invalid_elif_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_ELIF_STMT_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_ELIF_STMT_ID);
            return (ExprTy)_res;
        }
        { // 'elif' named_expression NEWLINE
            Token _keyword;
            ExprTy named_expression_var;
            Token newline_var;
            if (
                (_keyword = (Token)expect(520)) != null  // token='elif'
                &&
                (named_expression_var = (ExprTy)named_expression_rule()) != null  // named_expression
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR ( "expected ':'" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR ( 'expected ':'' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_ELIF_STMT_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_ELIF_STMT_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_while_stmt: 'while' named_expression NEWLINE
    public ExprTy invalid_while_stmt_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_WHILE_STMT_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_WHILE_STMT_ID);
            return (ExprTy)_res;
        }
        { // 'while' named_expression NEWLINE
            Token _keyword;
            ExprTy named_expression_var;
            Token newline_var;
            if (
                (_keyword = (Token)expect(518)) != null  // token='while'
                &&
                (named_expression_var = (ExprTy)named_expression_rule()) != null  // named_expression
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR ( "expected ':'" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR ( 'expected ':'' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_WHILE_STMT_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_WHILE_STMT_ID, _res);
        return (ExprTy)_res;
    }

    // invalid_double_starred_kvpairs:
    //     | ','.double_starred_kvpair+ ',' invalid_kvpair
    //     | expression ':' '*' bitwise_or
    //     | expression ':' &('}' | ',')
    public Object invalid_double_starred_kvpairs_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_DOUBLE_STARRED_KVPAIRS_ID)) {
            _res = (Object)cache.getResult(_mark, INVALID_DOUBLE_STARRED_KVPAIRS_ID);
            return (Object)_res;
        }
        { // ','.double_starred_kvpair+ ',' invalid_kvpair
            KeyValuePair[] _gather_227_var;
            Token _literal;
            ExprTy invalid_kvpair_var;
            if (
                (_gather_227_var = (KeyValuePair[])_gather_227_rule()) != null  // ','.double_starred_kvpair+
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (invalid_kvpair_var = (ExprTy)invalid_kvpair_rule()) != null  // invalid_kvpair
            )
            {
                _res = dummyName(_gather_227_var, _literal, invalid_kvpair_var);
                cache.putResult(_mark, INVALID_DOUBLE_STARRED_KVPAIRS_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // expression ':' '*' bitwise_or
            Token _literal;
            Token a;
            ExprTy bitwise_or_var;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (a = (Token)expect(16)) != null  // token='*'
                &&
                (bitwise_or_var = (ExprTy)bitwise_or_rule()) != null  // bitwise_or
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "cannot use a starred expression in a dictionary value" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'cannot use a starred expression in a dictionary value' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_DOUBLE_STARRED_KVPAIRS_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // expression ':' &('}' | ',')
            Token a;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (a = (Token)expect(11)) != null  // token=':'
                &&
                genLookahead__tmp_229_rule(true)
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "expression expected after dictionary key and ':'" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'expression expected after dictionary key and ':'' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_DOUBLE_STARRED_KVPAIRS_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_DOUBLE_STARRED_KVPAIRS_ID, _res);
        return (Object)_res;
    }

    // invalid_kvpair: expression !(':') | expression ':' '*' bitwise_or | expression ':'
    public ExprTy invalid_kvpair_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_KVPAIR_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_KVPAIR_ID);
            return (ExprTy)_res;
        }
        { // expression !(':')
            ExprTy a;
            if (
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                genLookahead__tmp_230_rule(false)
            )
            {
                // TODO: node.action: RAISE_ERROR_KNOWN_LOCATION ( p , PyExc_SyntaxError , a -> lineno , a -> end_col_offset - 1 , "':' expected after dictionary key" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_ERROR_KNOWN_LOCATION ( p , PyExc_SyntaxError , a -> lineno , a -> end_col_offset - 1 , '':' expected after dictionary key' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_KVPAIR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression ':' '*' bitwise_or
            Token _literal;
            Token a;
            ExprTy bitwise_or_var;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (a = (Token)expect(16)) != null  // token='*'
                &&
                (bitwise_or_var = (ExprTy)bitwise_or_rule()) != null  // bitwise_or
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "cannot use a starred expression in a dictionary value" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'cannot use a starred expression in a dictionary value' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_KVPAIR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression ':'
            Token a;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (a = (Token)expect(11)) != null  // token=':'
            )
            {
                // TODO: node.action: RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "expression expected after dictionary key and ':'" )
                debugMessageln("[33;5;7m!!! TODO: Convert RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , 'expression expected after dictionary key and ':'' ) to Java !!![0m");
                _res = null;
                cache.putResult(_mark, INVALID_KVPAIR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_KVPAIR_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_1: statements
    public StmtTy[] _tmp_1_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_1_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, _TMP_1_ID);
            return (StmtTy[])_res;
        }
        { // statements
            StmtTy[] statements_var;
            if (
                (statements_var = (StmtTy[])statements_rule()) != null  // statements
            )
            {
                _res = statements_var;
                cache.putResult(_mark, _TMP_1_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_1_ID, _res);
        return (StmtTy[])_res;
    }

    // _loop0_2: NEWLINE
    public Token[] _loop0_2_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_2_ID)) {
            _res = cache.getResult(_mark, _LOOP0_2_ID);
            return (Token[])_res;
        }
        int _start_mark = mark();
        List<Token> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // NEWLINE
            Token newline_var;
            while (
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = newline_var;
                if (_res instanceof Token) {
                    _children.add((Token)_res);
                } else {
                    _children.addAll(Arrays.asList((Token[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        Token[] _seq = _children.toArray(new Token[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_2_ID, _seq);
        return _seq;
    }

    // _tmp_3: type_expressions
    public ExprTy[] _tmp_3_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_3_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _TMP_3_ID);
            return (ExprTy[])_res;
        }
        { // type_expressions
            ExprTy[] type_expressions_var;
            if (
                (type_expressions_var = (ExprTy[])type_expressions_rule()) != null  // type_expressions
            )
            {
                _res = type_expressions_var;
                cache.putResult(_mark, _TMP_3_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_3_ID, _res);
        return (ExprTy[])_res;
    }

    // _loop0_4: NEWLINE
    public Token[] _loop0_4_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_4_ID)) {
            _res = cache.getResult(_mark, _LOOP0_4_ID);
            return (Token[])_res;
        }
        int _start_mark = mark();
        List<Token> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // NEWLINE
            Token newline_var;
            while (
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = newline_var;
                if (_res instanceof Token) {
                    _children.add((Token)_res);
                } else {
                    _children.addAll(Arrays.asList((Token[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        Token[] _seq = _children.toArray(new Token[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_4_ID, _seq);
        return _seq;
    }

    // _loop0_6: ',' expression
    public ExprTy[] _loop0_6_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_6_ID)) {
            _res = cache.getResult(_mark, _LOOP0_6_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' expression
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_6_ID, _seq);
        return _seq;
    }

    // _gather_5: expression _loop0_6
    public ExprTy[] _gather_5_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_5_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_5_ID);
            return (ExprTy[])_res;
        }
        { // expression _loop0_6
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)expression_rule()) != null  // expression
                &&
                (seq = (ExprTy[])_loop0_6_rule()) != null  // _loop0_6
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_5_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_5_ID, _res);
        return (ExprTy[])_res;
    }

    // _loop0_8: ',' expression
    public ExprTy[] _loop0_8_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_8_ID)) {
            _res = cache.getResult(_mark, _LOOP0_8_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' expression
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_8_ID, _seq);
        return _seq;
    }

    // _gather_7: expression _loop0_8
    public ExprTy[] _gather_7_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_7_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_7_ID);
            return (ExprTy[])_res;
        }
        { // expression _loop0_8
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)expression_rule()) != null  // expression
                &&
                (seq = (ExprTy[])_loop0_8_rule()) != null  // _loop0_8
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_7_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_7_ID, _res);
        return (ExprTy[])_res;
    }

    // _loop0_10: ',' expression
    public ExprTy[] _loop0_10_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_10_ID)) {
            _res = cache.getResult(_mark, _LOOP0_10_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' expression
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_10_ID, _seq);
        return _seq;
    }

    // _gather_9: expression _loop0_10
    public ExprTy[] _gather_9_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_9_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_9_ID);
            return (ExprTy[])_res;
        }
        { // expression _loop0_10
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)expression_rule()) != null  // expression
                &&
                (seq = (ExprTy[])_loop0_10_rule()) != null  // _loop0_10
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_9_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_9_ID, _res);
        return (ExprTy[])_res;
    }

    // _loop0_12: ',' expression
    public ExprTy[] _loop0_12_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_12_ID)) {
            _res = cache.getResult(_mark, _LOOP0_12_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' expression
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_12_ID, _seq);
        return _seq;
    }

    // _gather_11: expression _loop0_12
    public ExprTy[] _gather_11_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_11_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_11_ID);
            return (ExprTy[])_res;
        }
        { // expression _loop0_12
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)expression_rule()) != null  // expression
                &&
                (seq = (ExprTy[])_loop0_12_rule()) != null  // _loop0_12
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_11_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_11_ID, _res);
        return (ExprTy[])_res;
    }

    // _loop1_13: statement
    public StmtTy[] _loop1_13_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_13_ID)) {
            _res = cache.getResult(_mark, _LOOP1_13_ID);
            return (StmtTy[])_res;
        }
        int _start_mark = mark();
        List<StmtTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // statement
            StmtTy[] statement_var;
            while (
                (statement_var = (StmtTy[])statement_rule()) != null  // statement
            )
            {
                _res = statement_var;
                if (_res instanceof StmtTy) {
                    _children.add((StmtTy)_res);
                } else {
                    _children.addAll(Arrays.asList((StmtTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        StmtTy[] _seq = _children.toArray(new StmtTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_13_ID, _seq);
        return _seq;
    }

    // _loop0_15: ';' simple_stmt
    public StmtTy[] _loop0_15_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_15_ID)) {
            _res = cache.getResult(_mark, _LOOP0_15_ID);
            return (StmtTy[])_res;
        }
        int _start_mark = mark();
        List<StmtTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ';' simple_stmt
            Token _literal;
            StmtTy elem;
            while (
                (_literal = (Token)expect(13)) != null  // token=';'
                &&
                (elem = (StmtTy)simple_stmt_rule()) != null  // simple_stmt
            )
            {
                _res = elem;
                if (_res instanceof StmtTy) {
                    _children.add((StmtTy)_res);
                } else {
                    _children.addAll(Arrays.asList((StmtTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        StmtTy[] _seq = _children.toArray(new StmtTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_15_ID, _seq);
        return _seq;
    }

    // _gather_14: simple_stmt _loop0_15
    public StmtTy[] _gather_14_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_14_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, _GATHER_14_ID);
            return (StmtTy[])_res;
        }
        { // simple_stmt _loop0_15
            StmtTy elem;
            StmtTy[] seq;
            if (
                (elem = (StmtTy)simple_stmt_rule()) != null  // simple_stmt
                &&
                (seq = (StmtTy[])_loop0_15_rule()) != null  // _loop0_15
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_14_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_14_ID, _res);
        return (StmtTy[])_res;
    }

    // _tmp_16: ';'
    public Token _tmp_16_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_16_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_16_ID);
            return (Token)_res;
        }
        { // ';'
            Token _literal;
            if (
                (_literal = (Token)expect(13)) != null  // token=';'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_16_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_16_ID, _res);
        return (Token)_res;
    }

    // _tmp_17: 'import' | 'from'
    public Token _tmp_17_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_17_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_17_ID);
            return (Token)_res;
        }
        { // 'import'
            Token _keyword;
            if (
                (_keyword = (Token)expect(501)) != null  // token='import'
            )
            {
                _res = _keyword;
                cache.putResult(_mark, _TMP_17_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // 'from'
            Token _keyword;
            if (
                (_keyword = (Token)expect(502)) != null  // token='from'
            )
            {
                _res = _keyword;
                cache.putResult(_mark, _TMP_17_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_17_ID, _res);
        return (Token)_res;
    }

    // _tmp_18: 'def' | '@' | ASYNC
    public Token _tmp_18_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_18_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_18_ID);
            return (Token)_res;
        }
        { // 'def'
            Token _keyword;
            if (
                (_keyword = (Token)expect(512)) != null  // token='def'
            )
            {
                _res = _keyword;
                cache.putResult(_mark, _TMP_18_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '@'
            Token _literal;
            if (
                (_literal = (Token)expect(49)) != null  // token='@'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_18_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ASYNC
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                cache.putResult(_mark, _TMP_18_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_18_ID, _res);
        return (Token)_res;
    }

    // _tmp_19: 'class' | '@'
    public Token _tmp_19_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_19_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_19_ID);
            return (Token)_res;
        }
        { // 'class'
            Token _keyword;
            if (
                (_keyword = (Token)expect(514)) != null  // token='class'
            )
            {
                _res = _keyword;
                cache.putResult(_mark, _TMP_19_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '@'
            Token _literal;
            if (
                (_literal = (Token)expect(49)) != null  // token='@'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_19_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_19_ID, _res);
        return (Token)_res;
    }

    // _tmp_20: 'with' | ASYNC
    public Token _tmp_20_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_20_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_20_ID);
            return (Token)_res;
        }
        { // 'with'
            Token _keyword;
            if (
                (_keyword = (Token)expect(515)) != null  // token='with'
            )
            {
                _res = _keyword;
                cache.putResult(_mark, _TMP_20_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ASYNC
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                cache.putResult(_mark, _TMP_20_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_20_ID, _res);
        return (Token)_res;
    }

    // _tmp_21: 'for' | ASYNC
    public Token _tmp_21_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_21_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_21_ID);
            return (Token)_res;
        }
        { // 'for'
            Token _keyword;
            if (
                (_keyword = (Token)expect(516)) != null  // token='for'
            )
            {
                _res = _keyword;
                cache.putResult(_mark, _TMP_21_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ASYNC
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                cache.putResult(_mark, _TMP_21_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_21_ID, _res);
        return (Token)_res;
    }

    // _tmp_22: '=' annotated_rhs
    public ExprTy _tmp_22_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_22_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_22_ID);
            return (ExprTy)_res;
        }
        { // '=' annotated_rhs
            Token _literal;
            ExprTy d;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
                &&
                (d = (ExprTy)annotated_rhs_rule()) != null  // annotated_rhs
            )
            {
                _res = d;
                cache.putResult(_mark, _TMP_22_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_22_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_23: '(' single_target ')' | single_subscript_attribute_target
    public ExprTy _tmp_23_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_23_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_23_ID);
            return (ExprTy)_res;
        }
        { // '(' single_target ')'
            Token _literal;
            Token _literal_1;
            ExprTy b;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
                &&
                (b = (ExprTy)single_target_rule()) != null  // single_target
                &&
                (_literal_1 = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = b;
                cache.putResult(_mark, _TMP_23_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // single_subscript_attribute_target
            ExprTy single_subscript_attribute_target_var;
            if (
                (single_subscript_attribute_target_var = (ExprTy)single_subscript_attribute_target_rule()) != null  // single_subscript_attribute_target
            )
            {
                _res = single_subscript_attribute_target_var;
                cache.putResult(_mark, _TMP_23_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_23_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_24: '=' annotated_rhs
    public ExprTy _tmp_24_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_24_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_24_ID);
            return (ExprTy)_res;
        }
        { // '=' annotated_rhs
            Token _literal;
            ExprTy d;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
                &&
                (d = (ExprTy)annotated_rhs_rule()) != null  // annotated_rhs
            )
            {
                _res = d;
                cache.putResult(_mark, _TMP_24_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_24_ID, _res);
        return (ExprTy)_res;
    }

    // _loop1_25: (star_targets '=')
    public ExprTy[] _loop1_25_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_25_ID)) {
            _res = cache.getResult(_mark, _LOOP1_25_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (star_targets '=')
            ExprTy _tmp_231_var;
            while (
                (_tmp_231_var = (ExprTy)_tmp_231_rule()) != null  // star_targets '='
            )
            {
                _res = _tmp_231_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_25_ID, _seq);
        return _seq;
    }

    // _tmp_26: yield_expr | star_expressions
    public ExprTy _tmp_26_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_26_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_26_ID);
            return (ExprTy)_res;
        }
        { // yield_expr
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                cache.putResult(_mark, _TMP_26_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                cache.putResult(_mark, _TMP_26_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_26_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_27: TYPE_COMMENT
    public Token _tmp_27_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_27_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_27_ID);
            return (Token)_res;
        }
        { // TYPE_COMMENT
            Token type_comment_var;
            if (
                (type_comment_var = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                _res = type_comment_var;
                cache.putResult(_mark, _TMP_27_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_27_ID, _res);
        return (Token)_res;
    }

    // _tmp_28: yield_expr | star_expressions
    public ExprTy _tmp_28_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_28_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_28_ID);
            return (ExprTy)_res;
        }
        { // yield_expr
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                cache.putResult(_mark, _TMP_28_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                cache.putResult(_mark, _TMP_28_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_28_ID, _res);
        return (ExprTy)_res;
    }

    // _loop0_30: ',' NAME
    public ExprTy[] _loop0_30_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_30_ID)) {
            _res = cache.getResult(_mark, _LOOP0_30_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' NAME
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_30_ID, _seq);
        return _seq;
    }

    // _gather_29: NAME _loop0_30
    public ExprTy[] _gather_29_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_29_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_29_ID);
            return (ExprTy[])_res;
        }
        { // NAME _loop0_30
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)name_token()) != null  // NAME
                &&
                (seq = (ExprTy[])_loop0_30_rule()) != null  // _loop0_30
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_29_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_29_ID, _res);
        return (ExprTy[])_res;
    }

    // _loop0_32: ',' NAME
    public ExprTy[] _loop0_32_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_32_ID)) {
            _res = cache.getResult(_mark, _LOOP0_32_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' NAME
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_32_ID, _seq);
        return _seq;
    }

    // _gather_31: NAME _loop0_32
    public ExprTy[] _gather_31_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_31_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_31_ID);
            return (ExprTy[])_res;
        }
        { // NAME _loop0_32
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)name_token()) != null  // NAME
                &&
                (seq = (ExprTy[])_loop0_32_rule()) != null  // _loop0_32
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_31_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_31_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_33: ',' expression
    public ExprTy _tmp_33_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_33_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_33_ID);
            return (ExprTy)_res;
        }
        { // ',' expression
            Token _literal;
            ExprTy z;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (z = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = z;
                cache.putResult(_mark, _TMP_33_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_33_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_34: ';' | NEWLINE
    public Token _tmp_34_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_34_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_34_ID);
            return (Token)_res;
        }
        { // ';'
            Token _literal;
            if (
                (_literal = (Token)expect(13)) != null  // token=';'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_34_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // NEWLINE
            Token newline_var;
            if (
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = newline_var;
                cache.putResult(_mark, _TMP_34_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_34_ID, _res);
        return (Token)_res;
    }

    // _loop0_35: ('.' | '...')
    public Token[] _loop0_35_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_35_ID)) {
            _res = cache.getResult(_mark, _LOOP0_35_ID);
            return (Token[])_res;
        }
        int _start_mark = mark();
        List<Token> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('.' | '...')
            Token _tmp_232_var;
            while (
                (_tmp_232_var = (Token)_tmp_232_rule()) != null  // '.' | '...'
            )
            {
                _res = _tmp_232_var;
                if (_res instanceof Token) {
                    _children.add((Token)_res);
                } else {
                    _children.addAll(Arrays.asList((Token[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        Token[] _seq = _children.toArray(new Token[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_35_ID, _seq);
        return _seq;
    }

    // _loop1_36: ('.' | '...')
    public Token[] _loop1_36_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_36_ID)) {
            _res = cache.getResult(_mark, _LOOP1_36_ID);
            return (Token[])_res;
        }
        int _start_mark = mark();
        List<Token> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('.' | '...')
            Token _tmp_233_var;
            while (
                (_tmp_233_var = (Token)_tmp_233_rule()) != null  // '.' | '...'
            )
            {
                _res = _tmp_233_var;
                if (_res instanceof Token) {
                    _children.add((Token)_res);
                } else {
                    _children.addAll(Arrays.asList((Token[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        Token[] _seq = _children.toArray(new Token[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_36_ID, _seq);
        return _seq;
    }

    // _tmp_37: ','
    public Token _tmp_37_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_37_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_37_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_37_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_37_ID, _res);
        return (Token)_res;
    }

    // _loop0_39: ',' import_from_as_name
    public AliasTy[] _loop0_39_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_39_ID)) {
            _res = cache.getResult(_mark, _LOOP0_39_ID);
            return (AliasTy[])_res;
        }
        int _start_mark = mark();
        List<AliasTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' import_from_as_name
            Token _literal;
            AliasTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (AliasTy)import_from_as_name_rule()) != null  // import_from_as_name
            )
            {
                _res = elem;
                if (_res instanceof AliasTy) {
                    _children.add((AliasTy)_res);
                } else {
                    _children.addAll(Arrays.asList((AliasTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        AliasTy[] _seq = _children.toArray(new AliasTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_39_ID, _seq);
        return _seq;
    }

    // _gather_38: import_from_as_name _loop0_39
    public AliasTy[] _gather_38_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_38_ID)) {
            _res = (AliasTy[])cache.getResult(_mark, _GATHER_38_ID);
            return (AliasTy[])_res;
        }
        { // import_from_as_name _loop0_39
            AliasTy elem;
            AliasTy[] seq;
            if (
                (elem = (AliasTy)import_from_as_name_rule()) != null  // import_from_as_name
                &&
                (seq = (AliasTy[])_loop0_39_rule()) != null  // _loop0_39
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_38_ID, _res);
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_38_ID, _res);
        return (AliasTy[])_res;
    }

    // _tmp_40: 'as' NAME
    public ExprTy _tmp_40_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_40_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_40_ID);
            return (ExprTy)_res;
        }
        { // 'as' NAME
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(519)) != null  // token='as'
                &&
                (z = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = z;
                cache.putResult(_mark, _TMP_40_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_40_ID, _res);
        return (ExprTy)_res;
    }

    // _loop0_42: ',' dotted_as_name
    public AliasTy[] _loop0_42_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_42_ID)) {
            _res = cache.getResult(_mark, _LOOP0_42_ID);
            return (AliasTy[])_res;
        }
        int _start_mark = mark();
        List<AliasTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' dotted_as_name
            Token _literal;
            AliasTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (AliasTy)dotted_as_name_rule()) != null  // dotted_as_name
            )
            {
                _res = elem;
                if (_res instanceof AliasTy) {
                    _children.add((AliasTy)_res);
                } else {
                    _children.addAll(Arrays.asList((AliasTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        AliasTy[] _seq = _children.toArray(new AliasTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_42_ID, _seq);
        return _seq;
    }

    // _gather_41: dotted_as_name _loop0_42
    public AliasTy[] _gather_41_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_41_ID)) {
            _res = (AliasTy[])cache.getResult(_mark, _GATHER_41_ID);
            return (AliasTy[])_res;
        }
        { // dotted_as_name _loop0_42
            AliasTy elem;
            AliasTy[] seq;
            if (
                (elem = (AliasTy)dotted_as_name_rule()) != null  // dotted_as_name
                &&
                (seq = (AliasTy[])_loop0_42_rule()) != null  // _loop0_42
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_41_ID, _res);
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_41_ID, _res);
        return (AliasTy[])_res;
    }

    // _tmp_43: 'as' NAME
    public ExprTy _tmp_43_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_43_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_43_ID);
            return (ExprTy)_res;
        }
        { // 'as' NAME
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(519)) != null  // token='as'
                &&
                (z = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = z;
                cache.putResult(_mark, _TMP_43_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_43_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_44: else_block
    public StmtTy[] _tmp_44_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_44_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, _TMP_44_ID);
            return (StmtTy[])_res;
        }
        { // else_block
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                cache.putResult(_mark, _TMP_44_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_44_ID, _res);
        return (StmtTy[])_res;
    }

    // _tmp_45: else_block
    public StmtTy[] _tmp_45_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_45_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, _TMP_45_ID);
            return (StmtTy[])_res;
        }
        { // else_block
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                cache.putResult(_mark, _TMP_45_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_45_ID, _res);
        return (StmtTy[])_res;
    }

    // _tmp_46: else_block
    public StmtTy[] _tmp_46_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_46_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, _TMP_46_ID);
            return (StmtTy[])_res;
        }
        { // else_block
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                cache.putResult(_mark, _TMP_46_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_46_ID, _res);
        return (StmtTy[])_res;
    }

    // _tmp_47: TYPE_COMMENT
    public Token _tmp_47_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_47_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_47_ID);
            return (Token)_res;
        }
        { // TYPE_COMMENT
            Token type_comment_var;
            if (
                (type_comment_var = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                _res = type_comment_var;
                cache.putResult(_mark, _TMP_47_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_47_ID, _res);
        return (Token)_res;
    }

    // _tmp_48: else_block
    public StmtTy[] _tmp_48_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_48_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, _TMP_48_ID);
            return (StmtTy[])_res;
        }
        { // else_block
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                cache.putResult(_mark, _TMP_48_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_48_ID, _res);
        return (StmtTy[])_res;
    }

    // _tmp_49: TYPE_COMMENT
    public Token _tmp_49_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_49_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_49_ID);
            return (Token)_res;
        }
        { // TYPE_COMMENT
            Token type_comment_var;
            if (
                (type_comment_var = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                _res = type_comment_var;
                cache.putResult(_mark, _TMP_49_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_49_ID, _res);
        return (Token)_res;
    }

    // _tmp_50: else_block
    public StmtTy[] _tmp_50_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_50_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, _TMP_50_ID);
            return (StmtTy[])_res;
        }
        { // else_block
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                cache.putResult(_mark, _TMP_50_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_50_ID, _res);
        return (StmtTy[])_res;
    }

    // _loop0_52: ',' with_item
    public StmtTy.With.Item[] _loop0_52_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_52_ID)) {
            _res = cache.getResult(_mark, _LOOP0_52_ID);
            return (StmtTy.With.Item[])_res;
        }
        int _start_mark = mark();
        List<StmtTy.With.Item> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' with_item
            Token _literal;
            StmtTy.With.Item elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (StmtTy.With.Item)with_item_rule()) != null  // with_item
            )
            {
                _res = elem;
                if (_res instanceof StmtTy.With.Item) {
                    _children.add((StmtTy.With.Item)_res);
                } else {
                    _children.addAll(Arrays.asList((StmtTy.With.Item[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        StmtTy.With.Item[] _seq = _children.toArray(new StmtTy.With.Item[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_52_ID, _seq);
        return _seq;
    }

    // _gather_51: with_item _loop0_52
    public StmtTy.With.Item[] _gather_51_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_51_ID)) {
            _res = (StmtTy.With.Item[])cache.getResult(_mark, _GATHER_51_ID);
            return (StmtTy.With.Item[])_res;
        }
        { // with_item _loop0_52
            StmtTy.With.Item elem;
            StmtTy.With.Item[] seq;
            if (
                (elem = (StmtTy.With.Item)with_item_rule()) != null  // with_item
                &&
                (seq = (StmtTy.With.Item[])_loop0_52_rule()) != null  // _loop0_52
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_51_ID, _res);
                return (StmtTy.With.Item[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_51_ID, _res);
        return (StmtTy.With.Item[])_res;
    }

    // _loop0_54: ',' with_item
    public StmtTy.With.Item[] _loop0_54_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_54_ID)) {
            _res = cache.getResult(_mark, _LOOP0_54_ID);
            return (StmtTy.With.Item[])_res;
        }
        int _start_mark = mark();
        List<StmtTy.With.Item> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' with_item
            Token _literal;
            StmtTy.With.Item elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (StmtTy.With.Item)with_item_rule()) != null  // with_item
            )
            {
                _res = elem;
                if (_res instanceof StmtTy.With.Item) {
                    _children.add((StmtTy.With.Item)_res);
                } else {
                    _children.addAll(Arrays.asList((StmtTy.With.Item[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        StmtTy.With.Item[] _seq = _children.toArray(new StmtTy.With.Item[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_54_ID, _seq);
        return _seq;
    }

    // _gather_53: with_item _loop0_54
    public StmtTy.With.Item[] _gather_53_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_53_ID)) {
            _res = (StmtTy.With.Item[])cache.getResult(_mark, _GATHER_53_ID);
            return (StmtTy.With.Item[])_res;
        }
        { // with_item _loop0_54
            StmtTy.With.Item elem;
            StmtTy.With.Item[] seq;
            if (
                (elem = (StmtTy.With.Item)with_item_rule()) != null  // with_item
                &&
                (seq = (StmtTy.With.Item[])_loop0_54_rule()) != null  // _loop0_54
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_53_ID, _res);
                return (StmtTy.With.Item[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_53_ID, _res);
        return (StmtTy.With.Item[])_res;
    }

    // _tmp_55: TYPE_COMMENT
    public Token _tmp_55_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_55_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_55_ID);
            return (Token)_res;
        }
        { // TYPE_COMMENT
            Token type_comment_var;
            if (
                (type_comment_var = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                _res = type_comment_var;
                cache.putResult(_mark, _TMP_55_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_55_ID, _res);
        return (Token)_res;
    }

    // _loop0_57: ',' with_item
    public StmtTy.With.Item[] _loop0_57_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_57_ID)) {
            _res = cache.getResult(_mark, _LOOP0_57_ID);
            return (StmtTy.With.Item[])_res;
        }
        int _start_mark = mark();
        List<StmtTy.With.Item> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' with_item
            Token _literal;
            StmtTy.With.Item elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (StmtTy.With.Item)with_item_rule()) != null  // with_item
            )
            {
                _res = elem;
                if (_res instanceof StmtTy.With.Item) {
                    _children.add((StmtTy.With.Item)_res);
                } else {
                    _children.addAll(Arrays.asList((StmtTy.With.Item[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        StmtTy.With.Item[] _seq = _children.toArray(new StmtTy.With.Item[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_57_ID, _seq);
        return _seq;
    }

    // _gather_56: with_item _loop0_57
    public StmtTy.With.Item[] _gather_56_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_56_ID)) {
            _res = (StmtTy.With.Item[])cache.getResult(_mark, _GATHER_56_ID);
            return (StmtTy.With.Item[])_res;
        }
        { // with_item _loop0_57
            StmtTy.With.Item elem;
            StmtTy.With.Item[] seq;
            if (
                (elem = (StmtTy.With.Item)with_item_rule()) != null  // with_item
                &&
                (seq = (StmtTy.With.Item[])_loop0_57_rule()) != null  // _loop0_57
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_56_ID, _res);
                return (StmtTy.With.Item[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_56_ID, _res);
        return (StmtTy.With.Item[])_res;
    }

    // _loop0_59: ',' with_item
    public StmtTy.With.Item[] _loop0_59_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_59_ID)) {
            _res = cache.getResult(_mark, _LOOP0_59_ID);
            return (StmtTy.With.Item[])_res;
        }
        int _start_mark = mark();
        List<StmtTy.With.Item> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' with_item
            Token _literal;
            StmtTy.With.Item elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (StmtTy.With.Item)with_item_rule()) != null  // with_item
            )
            {
                _res = elem;
                if (_res instanceof StmtTy.With.Item) {
                    _children.add((StmtTy.With.Item)_res);
                } else {
                    _children.addAll(Arrays.asList((StmtTy.With.Item[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        StmtTy.With.Item[] _seq = _children.toArray(new StmtTy.With.Item[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_59_ID, _seq);
        return _seq;
    }

    // _gather_58: with_item _loop0_59
    public StmtTy.With.Item[] _gather_58_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_58_ID)) {
            _res = (StmtTy.With.Item[])cache.getResult(_mark, _GATHER_58_ID);
            return (StmtTy.With.Item[])_res;
        }
        { // with_item _loop0_59
            StmtTy.With.Item elem;
            StmtTy.With.Item[] seq;
            if (
                (elem = (StmtTy.With.Item)with_item_rule()) != null  // with_item
                &&
                (seq = (StmtTy.With.Item[])_loop0_59_rule()) != null  // _loop0_59
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_58_ID, _res);
                return (StmtTy.With.Item[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_58_ID, _res);
        return (StmtTy.With.Item[])_res;
    }

    // _tmp_60: TYPE_COMMENT
    public Token _tmp_60_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_60_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_60_ID);
            return (Token)_res;
        }
        { // TYPE_COMMENT
            Token type_comment_var;
            if (
                (type_comment_var = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                _res = type_comment_var;
                cache.putResult(_mark, _TMP_60_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_60_ID, _res);
        return (Token)_res;
    }

    // _tmp_61: ',' | ')' | ':'
    public Token _tmp_61_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_61_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_61_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_61_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ')'
            Token _literal;
            if (
                (_literal = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_61_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ':'
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_61_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_61_ID, _res);
        return (Token)_res;
    }

    // _loop1_62: except_block
    public StmtTy.Try.ExceptHandler[] _loop1_62_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_62_ID)) {
            _res = cache.getResult(_mark, _LOOP1_62_ID);
            return (StmtTy.Try.ExceptHandler[])_res;
        }
        int _start_mark = mark();
        List<StmtTy.Try.ExceptHandler> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // except_block
            StmtTy.Try.ExceptHandler except_block_var;
            while (
                (except_block_var = (StmtTy.Try.ExceptHandler)except_block_rule()) != null  // except_block
            )
            {
                _res = except_block_var;
                if (_res instanceof StmtTy.Try.ExceptHandler) {
                    _children.add((StmtTy.Try.ExceptHandler)_res);
                } else {
                    _children.addAll(Arrays.asList((StmtTy.Try.ExceptHandler[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        StmtTy.Try.ExceptHandler[] _seq = _children.toArray(new StmtTy.Try.ExceptHandler[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_62_ID, _seq);
        return _seq;
    }

    // _tmp_63: else_block
    public StmtTy[] _tmp_63_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_63_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, _TMP_63_ID);
            return (StmtTy[])_res;
        }
        { // else_block
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                cache.putResult(_mark, _TMP_63_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_63_ID, _res);
        return (StmtTy[])_res;
    }

    // _tmp_64: finally_block
    public StmtTy[] _tmp_64_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_64_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, _TMP_64_ID);
            return (StmtTy[])_res;
        }
        { // finally_block
            StmtTy[] finally_block_var;
            if (
                (finally_block_var = (StmtTy[])finally_block_rule()) != null  // finally_block
            )
            {
                _res = finally_block_var;
                cache.putResult(_mark, _TMP_64_ID, _res);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_64_ID, _res);
        return (StmtTy[])_res;
    }

    // _tmp_65: 'as' NAME
    public ExprTy _tmp_65_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_65_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_65_ID);
            return (ExprTy)_res;
        }
        { // 'as' NAME
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(519)) != null  // token='as'
                &&
                (z = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = z;
                cache.putResult(_mark, _TMP_65_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_65_ID, _res);
        return (ExprTy)_res;
    }

    // _loop1_66: case_block
    public StmtTy.Match.Case[] _loop1_66_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_66_ID)) {
            _res = cache.getResult(_mark, _LOOP1_66_ID);
            return (StmtTy.Match.Case[])_res;
        }
        int _start_mark = mark();
        List<StmtTy.Match.Case> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // case_block
            StmtTy.Match.Case case_block_var;
            while (
                (case_block_var = (StmtTy.Match.Case)case_block_rule()) != null  // case_block
            )
            {
                _res = case_block_var;
                if (_res instanceof StmtTy.Match.Case) {
                    _children.add((StmtTy.Match.Case)_res);
                } else {
                    _children.addAll(Arrays.asList((StmtTy.Match.Case[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        StmtTy.Match.Case[] _seq = _children.toArray(new StmtTy.Match.Case[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_66_ID, _seq);
        return _seq;
    }

    // _loop0_68: '|' closed_pattern
    public ExprTy[] _loop0_68_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_68_ID)) {
            _res = cache.getResult(_mark, _LOOP0_68_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // '|' closed_pattern
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(18)) != null  // token='|'
                &&
                (elem = (ExprTy)closed_pattern_rule()) != null  // closed_pattern
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_68_ID, _seq);
        return _seq;
    }

    // _gather_67: closed_pattern _loop0_68
    public ExprTy[] _gather_67_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_67_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_67_ID);
            return (ExprTy[])_res;
        }
        { // closed_pattern _loop0_68
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)closed_pattern_rule()) != null  // closed_pattern
                &&
                (seq = (ExprTy[])_loop0_68_rule()) != null  // _loop0_68
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_67_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_67_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_69: '+' | '-'
    public Token _tmp_69_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_69_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_69_ID);
            return (Token)_res;
        }
        { // '+'
            Token _literal;
            if (
                (_literal = (Token)expect(14)) != null  // token='+'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_69_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '-'
            Token _literal;
            if (
                (_literal = (Token)expect(15)) != null  // token='-'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_69_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_69_ID, _res);
        return (Token)_res;
    }

    // _tmp_70: '.' | '(' | '='
    public Token _tmp_70_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_70_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_70_ID);
            return (Token)_res;
        }
        { // '.'
            Token _literal;
            if (
                (_literal = (Token)expect(23)) != null  // token='.'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_70_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '('
            Token _literal;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_70_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '='
            Token _literal;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_70_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_70_ID, _res);
        return (Token)_res;
    }

    // _tmp_71: '.' | '(' | '='
    public Token _tmp_71_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_71_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_71_ID);
            return (Token)_res;
        }
        { // '.'
            Token _literal;
            if (
                (_literal = (Token)expect(23)) != null  // token='.'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_71_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '('
            Token _literal;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_71_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '='
            Token _literal;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_71_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_71_ID, _res);
        return (Token)_res;
    }

    // _loop0_73: ',' maybe_star_pattern
    public ExprTy[] _loop0_73_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_73_ID)) {
            _res = cache.getResult(_mark, _LOOP0_73_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' maybe_star_pattern
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)maybe_star_pattern_rule()) != null  // maybe_star_pattern
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_73_ID, _seq);
        return _seq;
    }

    // _gather_72: maybe_star_pattern _loop0_73
    public ExprTy[] _gather_72_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_72_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_72_ID);
            return (ExprTy[])_res;
        }
        { // maybe_star_pattern _loop0_73
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)maybe_star_pattern_rule()) != null  // maybe_star_pattern
                &&
                (seq = (ExprTy[])_loop0_73_rule()) != null  // _loop0_73
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_72_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_72_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_74: capture_pattern | wildcard_pattern
    public ExprTy _tmp_74_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_74_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_74_ID);
            return (ExprTy)_res;
        }
        { // capture_pattern
            ExprTy capture_pattern_var;
            if (
                (capture_pattern_var = (ExprTy)capture_pattern_rule()) != null  // capture_pattern
            )
            {
                _res = capture_pattern_var;
                cache.putResult(_mark, _TMP_74_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // wildcard_pattern
            ExprTy wildcard_pattern_var;
            if (
                (wildcard_pattern_var = (ExprTy)wildcard_pattern_rule()) != null  // wildcard_pattern
            )
            {
                _res = wildcard_pattern_var;
                cache.putResult(_mark, _TMP_74_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_74_ID, _res);
        return (ExprTy)_res;
    }

    // _loop0_76: ',' key_value_pattern
    public KeyValuePair[] _loop0_76_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_76_ID)) {
            _res = cache.getResult(_mark, _LOOP0_76_ID);
            return (KeyValuePair[])_res;
        }
        int _start_mark = mark();
        List<KeyValuePair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' key_value_pattern
            Token _literal;
            KeyValuePair elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (KeyValuePair)key_value_pattern_rule()) != null  // key_value_pattern
            )
            {
                _res = elem;
                if (_res instanceof KeyValuePair) {
                    _children.add((KeyValuePair)_res);
                } else {
                    _children.addAll(Arrays.asList((KeyValuePair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        KeyValuePair[] _seq = _children.toArray(new KeyValuePair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_76_ID, _seq);
        return _seq;
    }

    // _gather_75: key_value_pattern _loop0_76
    public KeyValuePair[] _gather_75_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_75_ID)) {
            _res = (KeyValuePair[])cache.getResult(_mark, _GATHER_75_ID);
            return (KeyValuePair[])_res;
        }
        { // key_value_pattern _loop0_76
            KeyValuePair elem;
            KeyValuePair[] seq;
            if (
                (elem = (KeyValuePair)key_value_pattern_rule()) != null  // key_value_pattern
                &&
                (seq = (KeyValuePair[])_loop0_76_rule()) != null  // _loop0_76
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_75_ID, _res);
                return (KeyValuePair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_75_ID, _res);
        return (KeyValuePair[])_res;
    }

    // _tmp_77: literal_pattern | value_pattern
    public ExprTy _tmp_77_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_77_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_77_ID);
            return (ExprTy)_res;
        }
        { // literal_pattern
            ExprTy literal_pattern_var;
            if (
                (literal_pattern_var = (ExprTy)literal_pattern_rule()) != null  // literal_pattern
            )
            {
                _res = literal_pattern_var;
                cache.putResult(_mark, _TMP_77_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // value_pattern
            ExprTy value_pattern_var;
            if (
                (value_pattern_var = (ExprTy)value_pattern_rule()) != null  // value_pattern
            )
            {
                _res = value_pattern_var;
                cache.putResult(_mark, _TMP_77_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_77_ID, _res);
        return (ExprTy)_res;
    }

    // _loop0_79: ',' pattern
    public ExprTy[] _loop0_79_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_79_ID)) {
            _res = cache.getResult(_mark, _LOOP0_79_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' pattern
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)pattern_rule()) != null  // pattern
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_79_ID, _seq);
        return _seq;
    }

    // _gather_78: pattern _loop0_79
    public ExprTy[] _gather_78_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_78_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_78_ID);
            return (ExprTy[])_res;
        }
        { // pattern _loop0_79
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)pattern_rule()) != null  // pattern
                &&
                (seq = (ExprTy[])_loop0_79_rule()) != null  // _loop0_79
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_78_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_78_ID, _res);
        return (ExprTy[])_res;
    }

    // _loop0_81: ',' keyword_pattern
    public KeywordTy[] _loop0_81_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_81_ID)) {
            _res = cache.getResult(_mark, _LOOP0_81_ID);
            return (KeywordTy[])_res;
        }
        int _start_mark = mark();
        List<KeywordTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' keyword_pattern
            Token _literal;
            KeywordTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (KeywordTy)keyword_pattern_rule()) != null  // keyword_pattern
            )
            {
                _res = elem;
                if (_res instanceof KeywordTy) {
                    _children.add((KeywordTy)_res);
                } else {
                    _children.addAll(Arrays.asList((KeywordTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        KeywordTy[] _seq = _children.toArray(new KeywordTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_81_ID, _seq);
        return _seq;
    }

    // _gather_80: keyword_pattern _loop0_81
    public KeywordTy[] _gather_80_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_80_ID)) {
            _res = (KeywordTy[])cache.getResult(_mark, _GATHER_80_ID);
            return (KeywordTy[])_res;
        }
        { // keyword_pattern _loop0_81
            KeywordTy elem;
            KeywordTy[] seq;
            if (
                (elem = (KeywordTy)keyword_pattern_rule()) != null  // keyword_pattern
                &&
                (seq = (KeywordTy[])_loop0_81_rule()) != null  // _loop0_81
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_80_ID, _res);
                return (KeywordTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_80_ID, _res);
        return (KeywordTy[])_res;
    }

    // _tmp_82: star_expressions
    public ExprTy _tmp_82_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_82_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_82_ID);
            return (ExprTy)_res;
        }
        { // star_expressions
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                cache.putResult(_mark, _TMP_82_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_82_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_83: 'from' expression
    public ExprTy _tmp_83_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_83_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_83_ID);
            return (ExprTy)_res;
        }
        { // 'from' expression
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(502)) != null  // token='from'
                &&
                (z = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = z;
                cache.putResult(_mark, _TMP_83_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_83_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_84: params
    public ArgumentsTy _tmp_84_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_84_ID)) {
            _res = (ArgumentsTy)cache.getResult(_mark, _TMP_84_ID);
            return (ArgumentsTy)_res;
        }
        { // params
            ArgumentsTy params_var;
            if (
                (params_var = (ArgumentsTy)params_rule()) != null  // params
            )
            {
                _res = params_var;
                cache.putResult(_mark, _TMP_84_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_84_ID, _res);
        return (ArgumentsTy)_res;
    }

    // _tmp_85: '->' expression
    public ExprTy _tmp_85_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_85_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_85_ID);
            return (ExprTy)_res;
        }
        { // '->' expression
            Token _literal;
            ExprTy z;
            if (
                (_literal = (Token)expect(51)) != null  // token='->'
                &&
                (z = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = z;
                cache.putResult(_mark, _TMP_85_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_85_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_86: func_type_comment
    public Token _tmp_86_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_86_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_86_ID);
            return (Token)_res;
        }
        { // func_type_comment
            Token func_type_comment_var;
            if (
                (func_type_comment_var = (Token)func_type_comment_rule()) != null  // func_type_comment
            )
            {
                _res = func_type_comment_var;
                cache.putResult(_mark, _TMP_86_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_86_ID, _res);
        return (Token)_res;
    }

    // _tmp_87: params
    public ArgumentsTy _tmp_87_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_87_ID)) {
            _res = (ArgumentsTy)cache.getResult(_mark, _TMP_87_ID);
            return (ArgumentsTy)_res;
        }
        { // params
            ArgumentsTy params_var;
            if (
                (params_var = (ArgumentsTy)params_rule()) != null  // params
            )
            {
                _res = params_var;
                cache.putResult(_mark, _TMP_87_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_87_ID, _res);
        return (ArgumentsTy)_res;
    }

    // _tmp_88: '->' expression
    public ExprTy _tmp_88_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_88_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_88_ID);
            return (ExprTy)_res;
        }
        { // '->' expression
            Token _literal;
            ExprTy z;
            if (
                (_literal = (Token)expect(51)) != null  // token='->'
                &&
                (z = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = z;
                cache.putResult(_mark, _TMP_88_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_88_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_89: func_type_comment
    public Token _tmp_89_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_89_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_89_ID);
            return (Token)_res;
        }
        { // func_type_comment
            Token func_type_comment_var;
            if (
                (func_type_comment_var = (Token)func_type_comment_rule()) != null  // func_type_comment
            )
            {
                _res = func_type_comment_var;
                cache.putResult(_mark, _TMP_89_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_89_ID, _res);
        return (Token)_res;
    }

    // _tmp_90: NEWLINE INDENT
    public Token _tmp_90_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_90_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_90_ID);
            return (Token)_res;
        }
        { // NEWLINE INDENT
            Token indent_var;
            Token newline_var;
            if (
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                (indent_var = (Token)expect(Token.Kind.INDENT)) != null  // token='INDENT'
            )
            {
                _res = dummyName(newline_var, indent_var);
                cache.putResult(_mark, _TMP_90_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_90_ID, _res);
        return (Token)_res;
    }

    // _loop0_91: param_no_default
    public ArgTy[] _loop0_91_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_91_ID)) {
            _res = cache.getResult(_mark, _LOOP0_91_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            ArgTy param_no_default_var;
            while (
                (param_no_default_var = (ArgTy)param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_91_ID, _seq);
        return _seq;
    }

    // _loop0_92: param_with_default
    public NameDefaultPair[] _loop0_92_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_92_ID)) {
            _res = cache.getResult(_mark, _LOOP0_92_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            NameDefaultPair param_with_default_var;
            while (
                (param_with_default_var = (NameDefaultPair)param_with_default_rule()) != null  // param_with_default
            )
            {
                _res = param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_92_ID, _seq);
        return _seq;
    }

    // _tmp_93: star_etc
    public StarEtc _tmp_93_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_93_ID)) {
            _res = (StarEtc)cache.getResult(_mark, _TMP_93_ID);
            return (StarEtc)_res;
        }
        { // star_etc
            StarEtc star_etc_var;
            if (
                (star_etc_var = (StarEtc)star_etc_rule()) != null  // star_etc
            )
            {
                _res = star_etc_var;
                cache.putResult(_mark, _TMP_93_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_93_ID, _res);
        return (StarEtc)_res;
    }

    // _loop0_94: param_with_default
    public NameDefaultPair[] _loop0_94_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_94_ID)) {
            _res = cache.getResult(_mark, _LOOP0_94_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            NameDefaultPair param_with_default_var;
            while (
                (param_with_default_var = (NameDefaultPair)param_with_default_rule()) != null  // param_with_default
            )
            {
                _res = param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_94_ID, _seq);
        return _seq;
    }

    // _tmp_95: star_etc
    public StarEtc _tmp_95_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_95_ID)) {
            _res = (StarEtc)cache.getResult(_mark, _TMP_95_ID);
            return (StarEtc)_res;
        }
        { // star_etc
            StarEtc star_etc_var;
            if (
                (star_etc_var = (StarEtc)star_etc_rule()) != null  // star_etc
            )
            {
                _res = star_etc_var;
                cache.putResult(_mark, _TMP_95_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_95_ID, _res);
        return (StarEtc)_res;
    }

    // _loop1_96: param_no_default
    public ArgTy[] _loop1_96_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_96_ID)) {
            _res = cache.getResult(_mark, _LOOP1_96_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            ArgTy param_no_default_var;
            while (
                (param_no_default_var = (ArgTy)param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_96_ID, _seq);
        return _seq;
    }

    // _loop0_97: param_with_default
    public NameDefaultPair[] _loop0_97_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_97_ID)) {
            _res = cache.getResult(_mark, _LOOP0_97_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            NameDefaultPair param_with_default_var;
            while (
                (param_with_default_var = (NameDefaultPair)param_with_default_rule()) != null  // param_with_default
            )
            {
                _res = param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_97_ID, _seq);
        return _seq;
    }

    // _tmp_98: star_etc
    public StarEtc _tmp_98_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_98_ID)) {
            _res = (StarEtc)cache.getResult(_mark, _TMP_98_ID);
            return (StarEtc)_res;
        }
        { // star_etc
            StarEtc star_etc_var;
            if (
                (star_etc_var = (StarEtc)star_etc_rule()) != null  // star_etc
            )
            {
                _res = star_etc_var;
                cache.putResult(_mark, _TMP_98_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_98_ID, _res);
        return (StarEtc)_res;
    }

    // _loop1_99: param_with_default
    public NameDefaultPair[] _loop1_99_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_99_ID)) {
            _res = cache.getResult(_mark, _LOOP1_99_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            NameDefaultPair param_with_default_var;
            while (
                (param_with_default_var = (NameDefaultPair)param_with_default_rule()) != null  // param_with_default
            )
            {
                _res = param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_99_ID, _seq);
        return _seq;
    }

    // _tmp_100: star_etc
    public StarEtc _tmp_100_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_100_ID)) {
            _res = (StarEtc)cache.getResult(_mark, _TMP_100_ID);
            return (StarEtc)_res;
        }
        { // star_etc
            StarEtc star_etc_var;
            if (
                (star_etc_var = (StarEtc)star_etc_rule()) != null  // star_etc
            )
            {
                _res = star_etc_var;
                cache.putResult(_mark, _TMP_100_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_100_ID, _res);
        return (StarEtc)_res;
    }

    // _loop1_101: param_no_default
    public ArgTy[] _loop1_101_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_101_ID)) {
            _res = cache.getResult(_mark, _LOOP1_101_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            ArgTy param_no_default_var;
            while (
                (param_no_default_var = (ArgTy)param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_101_ID, _seq);
        return _seq;
    }

    // _loop1_102: param_no_default
    public ArgTy[] _loop1_102_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_102_ID)) {
            _res = cache.getResult(_mark, _LOOP1_102_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            ArgTy param_no_default_var;
            while (
                (param_no_default_var = (ArgTy)param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_102_ID, _seq);
        return _seq;
    }

    // _loop0_103: param_no_default
    public ArgTy[] _loop0_103_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_103_ID)) {
            _res = cache.getResult(_mark, _LOOP0_103_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            ArgTy param_no_default_var;
            while (
                (param_no_default_var = (ArgTy)param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_103_ID, _seq);
        return _seq;
    }

    // _loop1_104: param_with_default
    public NameDefaultPair[] _loop1_104_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_104_ID)) {
            _res = cache.getResult(_mark, _LOOP1_104_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            NameDefaultPair param_with_default_var;
            while (
                (param_with_default_var = (NameDefaultPair)param_with_default_rule()) != null  // param_with_default
            )
            {
                _res = param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_104_ID, _seq);
        return _seq;
    }

    // _loop0_105: param_no_default
    public ArgTy[] _loop0_105_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_105_ID)) {
            _res = cache.getResult(_mark, _LOOP0_105_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            ArgTy param_no_default_var;
            while (
                (param_no_default_var = (ArgTy)param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_105_ID, _seq);
        return _seq;
    }

    // _loop1_106: param_with_default
    public NameDefaultPair[] _loop1_106_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_106_ID)) {
            _res = cache.getResult(_mark, _LOOP1_106_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            NameDefaultPair param_with_default_var;
            while (
                (param_with_default_var = (NameDefaultPair)param_with_default_rule()) != null  // param_with_default
            )
            {
                _res = param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_106_ID, _seq);
        return _seq;
    }

    // _loop0_107: param_maybe_default
    public NameDefaultPair[] _loop0_107_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_107_ID)) {
            _res = cache.getResult(_mark, _LOOP0_107_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_maybe_default
            NameDefaultPair param_maybe_default_var;
            while (
                (param_maybe_default_var = (NameDefaultPair)param_maybe_default_rule()) != null  // param_maybe_default
            )
            {
                _res = param_maybe_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_107_ID, _seq);
        return _seq;
    }

    // _tmp_108: kwds
    public ArgTy _tmp_108_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_108_ID)) {
            _res = (ArgTy)cache.getResult(_mark, _TMP_108_ID);
            return (ArgTy)_res;
        }
        { // kwds
            ArgTy kwds_var;
            if (
                (kwds_var = (ArgTy)kwds_rule()) != null  // kwds
            )
            {
                _res = kwds_var;
                cache.putResult(_mark, _TMP_108_ID, _res);
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_108_ID, _res);
        return (ArgTy)_res;
    }

    // _loop1_109: param_maybe_default
    public NameDefaultPair[] _loop1_109_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_109_ID)) {
            _res = cache.getResult(_mark, _LOOP1_109_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_maybe_default
            NameDefaultPair param_maybe_default_var;
            while (
                (param_maybe_default_var = (NameDefaultPair)param_maybe_default_rule()) != null  // param_maybe_default
            )
            {
                _res = param_maybe_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_109_ID, _seq);
        return _seq;
    }

    // _tmp_110: kwds
    public ArgTy _tmp_110_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_110_ID)) {
            _res = (ArgTy)cache.getResult(_mark, _TMP_110_ID);
            return (ArgTy)_res;
        }
        { // kwds
            ArgTy kwds_var;
            if (
                (kwds_var = (ArgTy)kwds_rule()) != null  // kwds
            )
            {
                _res = kwds_var;
                cache.putResult(_mark, _TMP_110_ID, _res);
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_110_ID, _res);
        return (ArgTy)_res;
    }

    // _loop1_111: ('@' named_expression NEWLINE)
    public ExprTy[] _loop1_111_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_111_ID)) {
            _res = cache.getResult(_mark, _LOOP1_111_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('@' named_expression NEWLINE)
            ExprTy _tmp_234_var;
            while (
                (_tmp_234_var = (ExprTy)_tmp_234_rule()) != null  // '@' named_expression NEWLINE
            )
            {
                _res = _tmp_234_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_111_ID, _seq);
        return _seq;
    }

    // _tmp_112: '(' arguments? ')'
    public ExprTy _tmp_112_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_112_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_112_ID);
            return (ExprTy)_res;
        }
        { // '(' arguments? ')'
            Token _literal;
            Token _literal_1;
            ExprTy z;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
                &&
                ((z = (ExprTy)_tmp_235_rule()) != null || true)  // arguments?
                &&
                (_literal_1 = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = z;
                cache.putResult(_mark, _TMP_112_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_112_ID, _res);
        return (ExprTy)_res;
    }

    // _loop1_113: (',' star_expression)
    public ExprTy[] _loop1_113_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_113_ID)) {
            _res = cache.getResult(_mark, _LOOP1_113_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (',' star_expression)
            ExprTy _tmp_236_var;
            while (
                (_tmp_236_var = (ExprTy)_tmp_236_rule()) != null  // ',' star_expression
            )
            {
                _res = _tmp_236_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_113_ID, _seq);
        return _seq;
    }

    // _tmp_114: ','
    public Token _tmp_114_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_114_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_114_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_114_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_114_ID, _res);
        return (Token)_res;
    }

    // _loop0_116: ',' star_named_expression
    public ExprTy[] _loop0_116_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_116_ID)) {
            _res = cache.getResult(_mark, _LOOP0_116_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' star_named_expression
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)star_named_expression_rule()) != null  // star_named_expression
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_116_ID, _seq);
        return _seq;
    }

    // _gather_115: star_named_expression _loop0_116
    public ExprTy[] _gather_115_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_115_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_115_ID);
            return (ExprTy[])_res;
        }
        { // star_named_expression _loop0_116
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)star_named_expression_rule()) != null  // star_named_expression
                &&
                (seq = (ExprTy[])_loop0_116_rule()) != null  // _loop0_116
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_115_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_115_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_117: ','
    public Token _tmp_117_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_117_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_117_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_117_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_117_ID, _res);
        return (Token)_res;
    }

    // _loop1_118: (',' expression)
    public ExprTy[] _loop1_118_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_118_ID)) {
            _res = cache.getResult(_mark, _LOOP1_118_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (',' expression)
            ExprTy _tmp_237_var;
            while (
                (_tmp_237_var = (ExprTy)_tmp_237_rule()) != null  // ',' expression
            )
            {
                _res = _tmp_237_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_118_ID, _seq);
        return _seq;
    }

    // _tmp_119: ','
    public Token _tmp_119_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_119_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_119_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_119_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_119_ID, _res);
        return (Token)_res;
    }

    // _tmp_120: lambda_params
    public ArgumentsTy _tmp_120_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_120_ID)) {
            _res = (ArgumentsTy)cache.getResult(_mark, _TMP_120_ID);
            return (ArgumentsTy)_res;
        }
        { // lambda_params
            ArgumentsTy lambda_params_var;
            if (
                (lambda_params_var = (ArgumentsTy)lambda_params_rule()) != null  // lambda_params
            )
            {
                _res = lambda_params_var;
                cache.putResult(_mark, _TMP_120_ID, _res);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_120_ID, _res);
        return (ArgumentsTy)_res;
    }

    // _loop0_121: lambda_param_no_default
    public ArgTy[] _loop0_121_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_121_ID)) {
            _res = cache.getResult(_mark, _LOOP0_121_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            ArgTy lambda_param_no_default_var;
            while (
                (lambda_param_no_default_var = (ArgTy)lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = lambda_param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_121_ID, _seq);
        return _seq;
    }

    // _loop0_122: lambda_param_with_default
    public NameDefaultPair[] _loop0_122_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_122_ID)) {
            _res = cache.getResult(_mark, _LOOP0_122_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            NameDefaultPair lambda_param_with_default_var;
            while (
                (lambda_param_with_default_var = (NameDefaultPair)lambda_param_with_default_rule()) != null  // lambda_param_with_default
            )
            {
                _res = lambda_param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_122_ID, _seq);
        return _seq;
    }

    // _tmp_123: lambda_star_etc
    public StarEtc _tmp_123_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_123_ID)) {
            _res = (StarEtc)cache.getResult(_mark, _TMP_123_ID);
            return (StarEtc)_res;
        }
        { // lambda_star_etc
            StarEtc lambda_star_etc_var;
            if (
                (lambda_star_etc_var = (StarEtc)lambda_star_etc_rule()) != null  // lambda_star_etc
            )
            {
                _res = lambda_star_etc_var;
                cache.putResult(_mark, _TMP_123_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_123_ID, _res);
        return (StarEtc)_res;
    }

    // _loop0_124: lambda_param_with_default
    public NameDefaultPair[] _loop0_124_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_124_ID)) {
            _res = cache.getResult(_mark, _LOOP0_124_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            NameDefaultPair lambda_param_with_default_var;
            while (
                (lambda_param_with_default_var = (NameDefaultPair)lambda_param_with_default_rule()) != null  // lambda_param_with_default
            )
            {
                _res = lambda_param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_124_ID, _seq);
        return _seq;
    }

    // _tmp_125: lambda_star_etc
    public StarEtc _tmp_125_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_125_ID)) {
            _res = (StarEtc)cache.getResult(_mark, _TMP_125_ID);
            return (StarEtc)_res;
        }
        { // lambda_star_etc
            StarEtc lambda_star_etc_var;
            if (
                (lambda_star_etc_var = (StarEtc)lambda_star_etc_rule()) != null  // lambda_star_etc
            )
            {
                _res = lambda_star_etc_var;
                cache.putResult(_mark, _TMP_125_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_125_ID, _res);
        return (StarEtc)_res;
    }

    // _loop1_126: lambda_param_no_default
    public ArgTy[] _loop1_126_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_126_ID)) {
            _res = cache.getResult(_mark, _LOOP1_126_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            ArgTy lambda_param_no_default_var;
            while (
                (lambda_param_no_default_var = (ArgTy)lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = lambda_param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_126_ID, _seq);
        return _seq;
    }

    // _loop0_127: lambda_param_with_default
    public NameDefaultPair[] _loop0_127_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_127_ID)) {
            _res = cache.getResult(_mark, _LOOP0_127_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            NameDefaultPair lambda_param_with_default_var;
            while (
                (lambda_param_with_default_var = (NameDefaultPair)lambda_param_with_default_rule()) != null  // lambda_param_with_default
            )
            {
                _res = lambda_param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_127_ID, _seq);
        return _seq;
    }

    // _tmp_128: lambda_star_etc
    public StarEtc _tmp_128_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_128_ID)) {
            _res = (StarEtc)cache.getResult(_mark, _TMP_128_ID);
            return (StarEtc)_res;
        }
        { // lambda_star_etc
            StarEtc lambda_star_etc_var;
            if (
                (lambda_star_etc_var = (StarEtc)lambda_star_etc_rule()) != null  // lambda_star_etc
            )
            {
                _res = lambda_star_etc_var;
                cache.putResult(_mark, _TMP_128_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_128_ID, _res);
        return (StarEtc)_res;
    }

    // _loop1_129: lambda_param_with_default
    public NameDefaultPair[] _loop1_129_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_129_ID)) {
            _res = cache.getResult(_mark, _LOOP1_129_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            NameDefaultPair lambda_param_with_default_var;
            while (
                (lambda_param_with_default_var = (NameDefaultPair)lambda_param_with_default_rule()) != null  // lambda_param_with_default
            )
            {
                _res = lambda_param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_129_ID, _seq);
        return _seq;
    }

    // _tmp_130: lambda_star_etc
    public StarEtc _tmp_130_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_130_ID)) {
            _res = (StarEtc)cache.getResult(_mark, _TMP_130_ID);
            return (StarEtc)_res;
        }
        { // lambda_star_etc
            StarEtc lambda_star_etc_var;
            if (
                (lambda_star_etc_var = (StarEtc)lambda_star_etc_rule()) != null  // lambda_star_etc
            )
            {
                _res = lambda_star_etc_var;
                cache.putResult(_mark, _TMP_130_ID, _res);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_130_ID, _res);
        return (StarEtc)_res;
    }

    // _loop1_131: lambda_param_no_default
    public ArgTy[] _loop1_131_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_131_ID)) {
            _res = cache.getResult(_mark, _LOOP1_131_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            ArgTy lambda_param_no_default_var;
            while (
                (lambda_param_no_default_var = (ArgTy)lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = lambda_param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_131_ID, _seq);
        return _seq;
    }

    // _loop1_132: lambda_param_no_default
    public ArgTy[] _loop1_132_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_132_ID)) {
            _res = cache.getResult(_mark, _LOOP1_132_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            ArgTy lambda_param_no_default_var;
            while (
                (lambda_param_no_default_var = (ArgTy)lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = lambda_param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_132_ID, _seq);
        return _seq;
    }

    // _loop0_133: lambda_param_no_default
    public ArgTy[] _loop0_133_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_133_ID)) {
            _res = cache.getResult(_mark, _LOOP0_133_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            ArgTy lambda_param_no_default_var;
            while (
                (lambda_param_no_default_var = (ArgTy)lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = lambda_param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_133_ID, _seq);
        return _seq;
    }

    // _loop1_134: lambda_param_with_default
    public NameDefaultPair[] _loop1_134_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_134_ID)) {
            _res = cache.getResult(_mark, _LOOP1_134_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            NameDefaultPair lambda_param_with_default_var;
            while (
                (lambda_param_with_default_var = (NameDefaultPair)lambda_param_with_default_rule()) != null  // lambda_param_with_default
            )
            {
                _res = lambda_param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_134_ID, _seq);
        return _seq;
    }

    // _loop0_135: lambda_param_no_default
    public ArgTy[] _loop0_135_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_135_ID)) {
            _res = cache.getResult(_mark, _LOOP0_135_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            ArgTy lambda_param_no_default_var;
            while (
                (lambda_param_no_default_var = (ArgTy)lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = lambda_param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_135_ID, _seq);
        return _seq;
    }

    // _loop1_136: lambda_param_with_default
    public NameDefaultPair[] _loop1_136_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_136_ID)) {
            _res = cache.getResult(_mark, _LOOP1_136_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            NameDefaultPair lambda_param_with_default_var;
            while (
                (lambda_param_with_default_var = (NameDefaultPair)lambda_param_with_default_rule()) != null  // lambda_param_with_default
            )
            {
                _res = lambda_param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_136_ID, _seq);
        return _seq;
    }

    // _loop0_137: lambda_param_maybe_default
    public NameDefaultPair[] _loop0_137_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_137_ID)) {
            _res = cache.getResult(_mark, _LOOP0_137_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_maybe_default
            NameDefaultPair lambda_param_maybe_default_var;
            while (
                (lambda_param_maybe_default_var = (NameDefaultPair)lambda_param_maybe_default_rule()) != null  // lambda_param_maybe_default
            )
            {
                _res = lambda_param_maybe_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_137_ID, _seq);
        return _seq;
    }

    // _tmp_138: lambda_kwds
    public ArgTy _tmp_138_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_138_ID)) {
            _res = (ArgTy)cache.getResult(_mark, _TMP_138_ID);
            return (ArgTy)_res;
        }
        { // lambda_kwds
            ArgTy lambda_kwds_var;
            if (
                (lambda_kwds_var = (ArgTy)lambda_kwds_rule()) != null  // lambda_kwds
            )
            {
                _res = lambda_kwds_var;
                cache.putResult(_mark, _TMP_138_ID, _res);
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_138_ID, _res);
        return (ArgTy)_res;
    }

    // _loop1_139: lambda_param_maybe_default
    public NameDefaultPair[] _loop1_139_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_139_ID)) {
            _res = cache.getResult(_mark, _LOOP1_139_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_maybe_default
            NameDefaultPair lambda_param_maybe_default_var;
            while (
                (lambda_param_maybe_default_var = (NameDefaultPair)lambda_param_maybe_default_rule()) != null  // lambda_param_maybe_default
            )
            {
                _res = lambda_param_maybe_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_139_ID, _seq);
        return _seq;
    }

    // _tmp_140: lambda_kwds
    public ArgTy _tmp_140_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_140_ID)) {
            _res = (ArgTy)cache.getResult(_mark, _TMP_140_ID);
            return (ArgTy)_res;
        }
        { // lambda_kwds
            ArgTy lambda_kwds_var;
            if (
                (lambda_kwds_var = (ArgTy)lambda_kwds_rule()) != null  // lambda_kwds
            )
            {
                _res = lambda_kwds_var;
                cache.putResult(_mark, _TMP_140_ID, _res);
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_140_ID, _res);
        return (ArgTy)_res;
    }

    // _loop1_141: ('or' conjunction)
    public ExprTy[] _loop1_141_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_141_ID)) {
            _res = cache.getResult(_mark, _LOOP1_141_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('or' conjunction)
            ExprTy _tmp_238_var;
            while (
                (_tmp_238_var = (ExprTy)_tmp_238_rule()) != null  // 'or' conjunction
            )
            {
                _res = _tmp_238_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_141_ID, _seq);
        return _seq;
    }

    // _loop1_142: ('and' inversion)
    public ExprTy[] _loop1_142_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_142_ID)) {
            _res = cache.getResult(_mark, _LOOP1_142_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('and' inversion)
            ExprTy _tmp_239_var;
            while (
                (_tmp_239_var = (ExprTy)_tmp_239_rule()) != null  // 'and' inversion
            )
            {
                _res = _tmp_239_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_142_ID, _seq);
        return _seq;
    }

    // _loop1_143: compare_op_bitwise_or_pair
    public CmpopExprPair[] _loop1_143_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_143_ID)) {
            _res = cache.getResult(_mark, _LOOP1_143_ID);
            return (CmpopExprPair[])_res;
        }
        int _start_mark = mark();
        List<CmpopExprPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // compare_op_bitwise_or_pair
            CmpopExprPair compare_op_bitwise_or_pair_var;
            while (
                (compare_op_bitwise_or_pair_var = (CmpopExprPair)compare_op_bitwise_or_pair_rule()) != null  // compare_op_bitwise_or_pair
            )
            {
                _res = compare_op_bitwise_or_pair_var;
                if (_res instanceof CmpopExprPair) {
                    _children.add((CmpopExprPair)_res);
                } else {
                    _children.addAll(Arrays.asList((CmpopExprPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        CmpopExprPair[] _seq = _children.toArray(new CmpopExprPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_143_ID, _seq);
        return _seq;
    }

    // _tmp_144: '!='
    public Token _tmp_144_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_144_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_144_ID);
            return (Token)_res;
        }
        { // '!='
            Token tok;
            if (
                (tok = (Token)expect(28)) != null  // token='!='
            )
            {
                _res = this.checkBarryAsFlufl(tok)? null : tok;
                cache.putResult(_mark, _TMP_144_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_144_ID, _res);
        return (Token)_res;
    }

    // _tmp_145: arguments
    public ExprTy _tmp_145_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_145_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_145_ID);
            return (ExprTy)_res;
        }
        { // arguments
            ExprTy arguments_var;
            if (
                (arguments_var = (ExprTy)arguments_rule()) != null  // arguments
            )
            {
                _res = arguments_var;
                cache.putResult(_mark, _TMP_145_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_145_ID, _res);
        return (ExprTy)_res;
    }

    // _loop0_147: ',' slice
    public ExprTy[] _loop0_147_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_147_ID)) {
            _res = cache.getResult(_mark, _LOOP0_147_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' slice
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)slice_rule()) != null  // slice
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_147_ID, _seq);
        return _seq;
    }

    // _gather_146: slice _loop0_147
    public ExprTy[] _gather_146_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_146_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_146_ID);
            return (ExprTy[])_res;
        }
        { // slice _loop0_147
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)slice_rule()) != null  // slice
                &&
                (seq = (ExprTy[])_loop0_147_rule()) != null  // _loop0_147
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_146_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_146_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_148: ','
    public Token _tmp_148_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_148_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_148_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_148_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_148_ID, _res);
        return (Token)_res;
    }

    // _tmp_149: expression
    public ExprTy _tmp_149_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_149_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_149_ID);
            return (ExprTy)_res;
        }
        { // expression
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = expression_var;
                cache.putResult(_mark, _TMP_149_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_149_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_150: expression
    public ExprTy _tmp_150_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_150_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_150_ID);
            return (ExprTy)_res;
        }
        { // expression
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = expression_var;
                cache.putResult(_mark, _TMP_150_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_150_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_151: ':' expression?
    public ExprTy _tmp_151_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_151_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_151_ID);
            return (ExprTy)_res;
        }
        { // ':' expression?
            Token _literal;
            ExprTy d;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                ((d = (ExprTy)_tmp_240_rule()) != null || true)  // expression?
            )
            {
                _res = d;
                cache.putResult(_mark, _TMP_151_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_151_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_152: tuple | group | genexp
    public ExprTy _tmp_152_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_152_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_152_ID);
            return (ExprTy)_res;
        }
        { // tuple
            ExprTy tuple_var;
            if (
                (tuple_var = (ExprTy)tuple_rule()) != null  // tuple
            )
            {
                _res = tuple_var;
                cache.putResult(_mark, _TMP_152_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // group
            ExprTy group_var;
            if (
                (group_var = (ExprTy)group_rule()) != null  // group
            )
            {
                _res = group_var;
                cache.putResult(_mark, _TMP_152_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // genexp
            ExprTy genexp_var;
            if (
                (genexp_var = (ExprTy)genexp_rule()) != null  // genexp
            )
            {
                _res = genexp_var;
                cache.putResult(_mark, _TMP_152_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_152_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_153: list | listcomp
    public ExprTy _tmp_153_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_153_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_153_ID);
            return (ExprTy)_res;
        }
        { // list
            ExprTy list_var;
            if (
                (list_var = (ExprTy)list_rule()) != null  // list
            )
            {
                _res = list_var;
                cache.putResult(_mark, _TMP_153_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // listcomp
            ExprTy listcomp_var;
            if (
                (listcomp_var = (ExprTy)listcomp_rule()) != null  // listcomp
            )
            {
                _res = listcomp_var;
                cache.putResult(_mark, _TMP_153_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_153_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_154: dict | set | dictcomp | setcomp
    public ExprTy _tmp_154_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_154_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_154_ID);
            return (ExprTy)_res;
        }
        { // dict
            ExprTy dict_var;
            if (
                (dict_var = (ExprTy)dict_rule()) != null  // dict
            )
            {
                _res = dict_var;
                cache.putResult(_mark, _TMP_154_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // set
            ExprTy set_var;
            if (
                (set_var = (ExprTy)set_rule()) != null  // set
            )
            {
                _res = set_var;
                cache.putResult(_mark, _TMP_154_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // dictcomp
            ExprTy dictcomp_var;
            if (
                (dictcomp_var = (ExprTy)dictcomp_rule()) != null  // dictcomp
            )
            {
                _res = dictcomp_var;
                cache.putResult(_mark, _TMP_154_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // setcomp
            ExprTy setcomp_var;
            if (
                (setcomp_var = (ExprTy)setcomp_rule()) != null  // setcomp
            )
            {
                _res = setcomp_var;
                cache.putResult(_mark, _TMP_154_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_154_ID, _res);
        return (ExprTy)_res;
    }

    // _loop1_155: STRING
    public Token[] _loop1_155_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_155_ID)) {
            _res = cache.getResult(_mark, _LOOP1_155_ID);
            return (Token[])_res;
        }
        int _start_mark = mark();
        List<Token> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // STRING
            Token string_var;
            while (
                (string_var = (Token)string_token()) != null  // STRING
            )
            {
                _res = string_var;
                if (_res instanceof Token) {
                    _children.add((Token)_res);
                } else {
                    _children.addAll(Arrays.asList((Token[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        Token[] _seq = _children.toArray(new Token[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_155_ID, _seq);
        return _seq;
    }

    // _tmp_156: star_named_expressions
    public ExprTy[] _tmp_156_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_156_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _TMP_156_ID);
            return (ExprTy[])_res;
        }
        { // star_named_expressions
            ExprTy[] star_named_expressions_var;
            if (
                (star_named_expressions_var = (ExprTy[])star_named_expressions_rule()) != null  // star_named_expressions
            )
            {
                _res = star_named_expressions_var;
                cache.putResult(_mark, _TMP_156_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_156_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_157: star_named_expression ',' star_named_expressions?
    public Object _tmp_157_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_157_ID)) {
            _res = (Object)cache.getResult(_mark, _TMP_157_ID);
            return (Object)_res;
        }
        { // star_named_expression ',' star_named_expressions?
            Token _literal;
            ExprTy y;
            ExprTy[] z;
            if (
                (y = (ExprTy)star_named_expression_rule()) != null  // star_named_expression
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                ((z = (ExprTy[])_tmp_241_rule()) != null || true)  // star_named_expressions?
            )
            {
                _res = this.insertInFront(y,z);
                cache.putResult(_mark, _TMP_157_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_157_ID, _res);
        return (Object)_res;
    }

    // _tmp_158: yield_expr | named_expression
    public ExprTy _tmp_158_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_158_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_158_ID);
            return (ExprTy)_res;
        }
        { // yield_expr
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                cache.putResult(_mark, _TMP_158_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // named_expression
            ExprTy named_expression_var;
            if (
                (named_expression_var = (ExprTy)named_expression_rule()) != null  // named_expression
            )
            {
                _res = named_expression_var;
                cache.putResult(_mark, _TMP_158_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_158_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_159: double_starred_kvpairs
    public KeyValuePair[] _tmp_159_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_159_ID)) {
            _res = (KeyValuePair[])cache.getResult(_mark, _TMP_159_ID);
            return (KeyValuePair[])_res;
        }
        { // double_starred_kvpairs
            KeyValuePair[] double_starred_kvpairs_var;
            if (
                (double_starred_kvpairs_var = (KeyValuePair[])double_starred_kvpairs_rule()) != null  // double_starred_kvpairs
            )
            {
                _res = double_starred_kvpairs_var;
                cache.putResult(_mark, _TMP_159_ID, _res);
                return (KeyValuePair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_159_ID, _res);
        return (KeyValuePair[])_res;
    }

    // _loop0_161: ',' double_starred_kvpair
    public KeyValuePair[] _loop0_161_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_161_ID)) {
            _res = cache.getResult(_mark, _LOOP0_161_ID);
            return (KeyValuePair[])_res;
        }
        int _start_mark = mark();
        List<KeyValuePair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' double_starred_kvpair
            Token _literal;
            KeyValuePair elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (KeyValuePair)double_starred_kvpair_rule()) != null  // double_starred_kvpair
            )
            {
                _res = elem;
                if (_res instanceof KeyValuePair) {
                    _children.add((KeyValuePair)_res);
                } else {
                    _children.addAll(Arrays.asList((KeyValuePair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        KeyValuePair[] _seq = _children.toArray(new KeyValuePair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_161_ID, _seq);
        return _seq;
    }

    // _gather_160: double_starred_kvpair _loop0_161
    public KeyValuePair[] _gather_160_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_160_ID)) {
            _res = (KeyValuePair[])cache.getResult(_mark, _GATHER_160_ID);
            return (KeyValuePair[])_res;
        }
        { // double_starred_kvpair _loop0_161
            KeyValuePair elem;
            KeyValuePair[] seq;
            if (
                (elem = (KeyValuePair)double_starred_kvpair_rule()) != null  // double_starred_kvpair
                &&
                (seq = (KeyValuePair[])_loop0_161_rule()) != null  // _loop0_161
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_160_ID, _res);
                return (KeyValuePair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_160_ID, _res);
        return (KeyValuePair[])_res;
    }

    // _tmp_162: ','
    public Token _tmp_162_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_162_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_162_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_162_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_162_ID, _res);
        return (Token)_res;
    }

    // _loop1_163: for_if_clause
    public ComprehensionTy[] _loop1_163_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_163_ID)) {
            _res = cache.getResult(_mark, _LOOP1_163_ID);
            return (ComprehensionTy[])_res;
        }
        int _start_mark = mark();
        List<ComprehensionTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // for_if_clause
            ComprehensionTy for_if_clause_var;
            while (
                (for_if_clause_var = (ComprehensionTy)for_if_clause_rule()) != null  // for_if_clause
            )
            {
                _res = for_if_clause_var;
                if (_res instanceof ComprehensionTy) {
                    _children.add((ComprehensionTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ComprehensionTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ComprehensionTy[] _seq = _children.toArray(new ComprehensionTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_163_ID, _seq);
        return _seq;
    }

    // _loop0_164: ('if' disjunction)
    public ExprTy[] _loop0_164_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_164_ID)) {
            _res = cache.getResult(_mark, _LOOP0_164_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('if' disjunction)
            ExprTy _tmp_242_var;
            while (
                (_tmp_242_var = (ExprTy)_tmp_242_rule()) != null  // 'if' disjunction
            )
            {
                _res = _tmp_242_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_164_ID, _seq);
        return _seq;
    }

    // _loop0_165: ('if' disjunction)
    public ExprTy[] _loop0_165_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_165_ID)) {
            _res = cache.getResult(_mark, _LOOP0_165_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('if' disjunction)
            ExprTy _tmp_243_var;
            while (
                (_tmp_243_var = (ExprTy)_tmp_243_rule()) != null  // 'if' disjunction
            )
            {
                _res = _tmp_243_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_165_ID, _seq);
        return _seq;
    }

    // _tmp_166: star_expressions
    public ExprTy _tmp_166_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_166_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_166_ID);
            return (ExprTy)_res;
        }
        { // star_expressions
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                cache.putResult(_mark, _TMP_166_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_166_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_167: ','
    public Token _tmp_167_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_167_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_167_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_167_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_167_ID, _res);
        return (Token)_res;
    }

    // _loop0_169: ',' (starred_expression | direct_named_expression !'=')
    public ExprTy[] _loop0_169_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_169_ID)) {
            _res = cache.getResult(_mark, _LOOP0_169_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' (starred_expression | direct_named_expression !'=')
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)_tmp_244_rule()) != null  // starred_expression | direct_named_expression !'='
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_169_ID, _seq);
        return _seq;
    }

    // _gather_168: (starred_expression | direct_named_expression !'=') _loop0_169
    public ExprTy[] _gather_168_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_168_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_168_ID);
            return (ExprTy[])_res;
        }
        { // (starred_expression | direct_named_expression !'=') _loop0_169
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)_tmp_244_rule()) != null  // starred_expression | direct_named_expression !'='
                &&
                (seq = (ExprTy[])_loop0_169_rule()) != null  // _loop0_169
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_168_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_168_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_170: ',' kwargs
    public KeywordOrStarred[] _tmp_170_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_170_ID)) {
            _res = (KeywordOrStarred[])cache.getResult(_mark, _TMP_170_ID);
            return (KeywordOrStarred[])_res;
        }
        { // ',' kwargs
            Token _literal;
            KeywordOrStarred[] k;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (k = (KeywordOrStarred[])kwargs_rule()) != null  // kwargs
            )
            {
                _res = k;
                cache.putResult(_mark, _TMP_170_ID, _res);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_170_ID, _res);
        return (KeywordOrStarred[])_res;
    }

    // _loop0_172: ',' kwarg_or_starred
    public KeywordOrStarred[] _loop0_172_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_172_ID)) {
            _res = cache.getResult(_mark, _LOOP0_172_ID);
            return (KeywordOrStarred[])_res;
        }
        int _start_mark = mark();
        List<KeywordOrStarred> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' kwarg_or_starred
            Token _literal;
            KeywordOrStarred elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (KeywordOrStarred)kwarg_or_starred_rule()) != null  // kwarg_or_starred
            )
            {
                _res = elem;
                if (_res instanceof KeywordOrStarred) {
                    _children.add((KeywordOrStarred)_res);
                } else {
                    _children.addAll(Arrays.asList((KeywordOrStarred[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        KeywordOrStarred[] _seq = _children.toArray(new KeywordOrStarred[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_172_ID, _seq);
        return _seq;
    }

    // _gather_171: kwarg_or_starred _loop0_172
    public KeywordOrStarred[] _gather_171_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_171_ID)) {
            _res = (KeywordOrStarred[])cache.getResult(_mark, _GATHER_171_ID);
            return (KeywordOrStarred[])_res;
        }
        { // kwarg_or_starred _loop0_172
            KeywordOrStarred elem;
            KeywordOrStarred[] seq;
            if (
                (elem = (KeywordOrStarred)kwarg_or_starred_rule()) != null  // kwarg_or_starred
                &&
                (seq = (KeywordOrStarred[])_loop0_172_rule()) != null  // _loop0_172
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_171_ID, _res);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_171_ID, _res);
        return (KeywordOrStarred[])_res;
    }

    // _loop0_174: ',' kwarg_or_double_starred
    public KeywordOrStarred[] _loop0_174_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_174_ID)) {
            _res = cache.getResult(_mark, _LOOP0_174_ID);
            return (KeywordOrStarred[])_res;
        }
        int _start_mark = mark();
        List<KeywordOrStarred> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' kwarg_or_double_starred
            Token _literal;
            KeywordOrStarred elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (KeywordOrStarred)kwarg_or_double_starred_rule()) != null  // kwarg_or_double_starred
            )
            {
                _res = elem;
                if (_res instanceof KeywordOrStarred) {
                    _children.add((KeywordOrStarred)_res);
                } else {
                    _children.addAll(Arrays.asList((KeywordOrStarred[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        KeywordOrStarred[] _seq = _children.toArray(new KeywordOrStarred[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_174_ID, _seq);
        return _seq;
    }

    // _gather_173: kwarg_or_double_starred _loop0_174
    public KeywordOrStarred[] _gather_173_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_173_ID)) {
            _res = (KeywordOrStarred[])cache.getResult(_mark, _GATHER_173_ID);
            return (KeywordOrStarred[])_res;
        }
        { // kwarg_or_double_starred _loop0_174
            KeywordOrStarred elem;
            KeywordOrStarred[] seq;
            if (
                (elem = (KeywordOrStarred)kwarg_or_double_starred_rule()) != null  // kwarg_or_double_starred
                &&
                (seq = (KeywordOrStarred[])_loop0_174_rule()) != null  // _loop0_174
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_173_ID, _res);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_173_ID, _res);
        return (KeywordOrStarred[])_res;
    }

    // _loop0_176: ',' kwarg_or_starred
    public KeywordOrStarred[] _loop0_176_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_176_ID)) {
            _res = cache.getResult(_mark, _LOOP0_176_ID);
            return (KeywordOrStarred[])_res;
        }
        int _start_mark = mark();
        List<KeywordOrStarred> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' kwarg_or_starred
            Token _literal;
            KeywordOrStarred elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (KeywordOrStarred)kwarg_or_starred_rule()) != null  // kwarg_or_starred
            )
            {
                _res = elem;
                if (_res instanceof KeywordOrStarred) {
                    _children.add((KeywordOrStarred)_res);
                } else {
                    _children.addAll(Arrays.asList((KeywordOrStarred[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        KeywordOrStarred[] _seq = _children.toArray(new KeywordOrStarred[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_176_ID, _seq);
        return _seq;
    }

    // _gather_175: kwarg_or_starred _loop0_176
    public KeywordOrStarred[] _gather_175_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_175_ID)) {
            _res = (KeywordOrStarred[])cache.getResult(_mark, _GATHER_175_ID);
            return (KeywordOrStarred[])_res;
        }
        { // kwarg_or_starred _loop0_176
            KeywordOrStarred elem;
            KeywordOrStarred[] seq;
            if (
                (elem = (KeywordOrStarred)kwarg_or_starred_rule()) != null  // kwarg_or_starred
                &&
                (seq = (KeywordOrStarred[])_loop0_176_rule()) != null  // _loop0_176
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_175_ID, _res);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_175_ID, _res);
        return (KeywordOrStarred[])_res;
    }

    // _loop0_178: ',' kwarg_or_double_starred
    public KeywordOrStarred[] _loop0_178_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_178_ID)) {
            _res = cache.getResult(_mark, _LOOP0_178_ID);
            return (KeywordOrStarred[])_res;
        }
        int _start_mark = mark();
        List<KeywordOrStarred> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' kwarg_or_double_starred
            Token _literal;
            KeywordOrStarred elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (KeywordOrStarred)kwarg_or_double_starred_rule()) != null  // kwarg_or_double_starred
            )
            {
                _res = elem;
                if (_res instanceof KeywordOrStarred) {
                    _children.add((KeywordOrStarred)_res);
                } else {
                    _children.addAll(Arrays.asList((KeywordOrStarred[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        KeywordOrStarred[] _seq = _children.toArray(new KeywordOrStarred[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_178_ID, _seq);
        return _seq;
    }

    // _gather_177: kwarg_or_double_starred _loop0_178
    public KeywordOrStarred[] _gather_177_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_177_ID)) {
            _res = (KeywordOrStarred[])cache.getResult(_mark, _GATHER_177_ID);
            return (KeywordOrStarred[])_res;
        }
        { // kwarg_or_double_starred _loop0_178
            KeywordOrStarred elem;
            KeywordOrStarred[] seq;
            if (
                (elem = (KeywordOrStarred)kwarg_or_double_starred_rule()) != null  // kwarg_or_double_starred
                &&
                (seq = (KeywordOrStarred[])_loop0_178_rule()) != null  // _loop0_178
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_177_ID, _res);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_177_ID, _res);
        return (KeywordOrStarred[])_res;
    }

    // _loop0_179: (',' star_target)
    public ExprTy[] _loop0_179_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_179_ID)) {
            _res = cache.getResult(_mark, _LOOP0_179_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (',' star_target)
            ExprTy _tmp_245_var;
            while (
                (_tmp_245_var = (ExprTy)_tmp_245_rule()) != null  // ',' star_target
            )
            {
                _res = _tmp_245_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_179_ID, _seq);
        return _seq;
    }

    // _tmp_180: ','
    public Token _tmp_180_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_180_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_180_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_180_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_180_ID, _res);
        return (Token)_res;
    }

    // _loop0_182: ',' star_target
    public ExprTy[] _loop0_182_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_182_ID)) {
            _res = cache.getResult(_mark, _LOOP0_182_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' star_target
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_182_ID, _seq);
        return _seq;
    }

    // _gather_181: star_target _loop0_182
    public ExprTy[] _gather_181_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_181_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_181_ID);
            return (ExprTy[])_res;
        }
        { // star_target _loop0_182
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)star_target_rule()) != null  // star_target
                &&
                (seq = (ExprTy[])_loop0_182_rule()) != null  // _loop0_182
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_181_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_181_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_183: ','
    public Token _tmp_183_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_183_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_183_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_183_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_183_ID, _res);
        return (Token)_res;
    }

    // _loop1_184: (',' star_target)
    public ExprTy[] _loop1_184_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_184_ID)) {
            _res = cache.getResult(_mark, _LOOP1_184_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (',' star_target)
            ExprTy _tmp_246_var;
            while (
                (_tmp_246_var = (ExprTy)_tmp_246_rule()) != null  // ',' star_target
            )
            {
                _res = _tmp_246_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_184_ID, _seq);
        return _seq;
    }

    // _tmp_185: ','
    public Token _tmp_185_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_185_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_185_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_185_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_185_ID, _res);
        return (Token)_res;
    }

    // _tmp_186: !'*' star_target
    public ExprTy _tmp_186_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_186_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_186_ID);
            return (ExprTy)_res;
        }
        { // !'*' star_target
            ExprTy star_target_var;
            if (
                genLookahead_expect(false, 16)  // token='*'
                &&
                (star_target_var = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = star_target_var;
                cache.putResult(_mark, _TMP_186_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_186_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_187: star_targets_tuple_seq
    public ExprTy[] _tmp_187_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_187_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _TMP_187_ID);
            return (ExprTy[])_res;
        }
        { // star_targets_tuple_seq
            ExprTy[] star_targets_tuple_seq_var;
            if (
                (star_targets_tuple_seq_var = (ExprTy[])star_targets_tuple_seq_rule()) != null  // star_targets_tuple_seq
            )
            {
                _res = star_targets_tuple_seq_var;
                cache.putResult(_mark, _TMP_187_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_187_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_188: star_targets_list_seq
    public ExprTy[] _tmp_188_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_188_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _TMP_188_ID);
            return (ExprTy[])_res;
        }
        { // star_targets_list_seq
            ExprTy[] star_targets_list_seq_var;
            if (
                (star_targets_list_seq_var = (ExprTy[])star_targets_list_seq_rule()) != null  // star_targets_list_seq
            )
            {
                _res = star_targets_list_seq_var;
                cache.putResult(_mark, _TMP_188_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_188_ID, _res);
        return (ExprTy[])_res;
    }

    // _loop0_190: ',' del_target
    public ExprTy[] _loop0_190_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_190_ID)) {
            _res = cache.getResult(_mark, _LOOP0_190_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' del_target
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)del_target_rule()) != null  // del_target
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_190_ID, _seq);
        return _seq;
    }

    // _gather_189: del_target _loop0_190
    public ExprTy[] _gather_189_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_189_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_189_ID);
            return (ExprTy[])_res;
        }
        { // del_target _loop0_190
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)del_target_rule()) != null  // del_target
                &&
                (seq = (ExprTy[])_loop0_190_rule()) != null  // _loop0_190
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_189_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_189_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_191: ','
    public Token _tmp_191_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_191_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_191_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_191_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_191_ID, _res);
        return (Token)_res;
    }

    // _tmp_192: del_targets
    public ExprTy[] _tmp_192_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_192_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _TMP_192_ID);
            return (ExprTy[])_res;
        }
        { // del_targets
            ExprTy[] del_targets_var;
            if (
                (del_targets_var = (ExprTy[])del_targets_rule()) != null  // del_targets
            )
            {
                _res = del_targets_var;
                cache.putResult(_mark, _TMP_192_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_192_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_193: del_targets
    public ExprTy[] _tmp_193_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_193_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _TMP_193_ID);
            return (ExprTy[])_res;
        }
        { // del_targets
            ExprTy[] del_targets_var;
            if (
                (del_targets_var = (ExprTy[])del_targets_rule()) != null  // del_targets
            )
            {
                _res = del_targets_var;
                cache.putResult(_mark, _TMP_193_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_193_ID, _res);
        return (ExprTy[])_res;
    }

    // _loop0_195: ',' target
    public ExprTy[] _loop0_195_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_195_ID)) {
            _res = cache.getResult(_mark, _LOOP0_195_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' target
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)target_rule()) != null  // target
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_195_ID, _seq);
        return _seq;
    }

    // _gather_194: target _loop0_195
    public ExprTy[] _gather_194_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_194_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_194_ID);
            return (ExprTy[])_res;
        }
        { // target _loop0_195
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)target_rule()) != null  // target
                &&
                (seq = (ExprTy[])_loop0_195_rule()) != null  // _loop0_195
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_194_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_194_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_196: ','
    public Token _tmp_196_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_196_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_196_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_196_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_196_ID, _res);
        return (Token)_res;
    }

    // _tmp_197: arguments
    public ExprTy _tmp_197_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_197_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_197_ID);
            return (ExprTy)_res;
        }
        { // arguments
            ExprTy arguments_var;
            if (
                (arguments_var = (ExprTy)arguments_rule()) != null  // arguments
            )
            {
                _res = arguments_var;
                cache.putResult(_mark, _TMP_197_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_197_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_198: targets
    public ExprTy[] _tmp_198_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_198_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _TMP_198_ID);
            return (ExprTy[])_res;
        }
        { // targets
            ExprTy[] targets_var;
            if (
                (targets_var = (ExprTy[])targets_rule()) != null  // targets
            )
            {
                _res = targets_var;
                cache.putResult(_mark, _TMP_198_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_198_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_199: targets
    public ExprTy[] _tmp_199_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_199_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _TMP_199_ID);
            return (ExprTy[])_res;
        }
        { // targets
            ExprTy[] targets_var;
            if (
                (targets_var = (ExprTy[])targets_rule()) != null  // targets
            )
            {
                _res = targets_var;
                cache.putResult(_mark, _TMP_199_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_199_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_200: args | expression for_if_clauses
    public Object _tmp_200_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_200_ID)) {
            _res = (Object)cache.getResult(_mark, _TMP_200_ID);
            return (Object)_res;
        }
        { // args
            ExprTy args_var;
            if (
                (args_var = (ExprTy)args_rule()) != null  // args
            )
            {
                _res = args_var;
                cache.putResult(_mark, _TMP_200_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // expression for_if_clauses
            ExprTy expression_var;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                _res = dummyName(expression_var, for_if_clauses_var);
                cache.putResult(_mark, _TMP_200_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_200_ID, _res);
        return (Object)_res;
    }

    // _tmp_201: NAME STRING | SOFT_KEYWORD
    public ExprTy _tmp_201_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_201_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_201_ID);
            return (ExprTy)_res;
        }
        { // NAME STRING
            ExprTy name_var;
            Token string_var;
            if (
                (name_var = (ExprTy)name_token()) != null  // NAME
                &&
                (string_var = (Token)string_token()) != null  // STRING
            )
            {
                _res = dummyName(name_var, string_var);
                cache.putResult(_mark, _TMP_201_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // SOFT_KEYWORD
            ExprTy soft_keyword_var;
            if (
                (soft_keyword_var = (ExprTy)soft_keyword_token()) != null  // SOFT_KEYWORD
            )
            {
                _res = soft_keyword_var;
                cache.putResult(_mark, _TMP_201_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_201_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_202: '=' | ':=' | ','
    public Token _tmp_202_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_202_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_202_ID);
            return (Token)_res;
        }
        { // '='
            Token _literal;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_202_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ':='
            Token _literal;
            if (
                (_literal = (Token)expect(53)) != null  // token=':='
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_202_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_202_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_202_ID, _res);
        return (Token)_res;
    }

    // _tmp_203: list | tuple | genexp | 'True' | 'None' | 'False'
    public Object _tmp_203_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_203_ID)) {
            _res = (Object)cache.getResult(_mark, _TMP_203_ID);
            return (Object)_res;
        }
        { // list
            ExprTy list_var;
            if (
                (list_var = (ExprTy)list_rule()) != null  // list
            )
            {
                _res = list_var;
                cache.putResult(_mark, _TMP_203_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // tuple
            ExprTy tuple_var;
            if (
                (tuple_var = (ExprTy)tuple_rule()) != null  // tuple
            )
            {
                _res = tuple_var;
                cache.putResult(_mark, _TMP_203_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // genexp
            ExprTy genexp_var;
            if (
                (genexp_var = (ExprTy)genexp_rule()) != null  // genexp
            )
            {
                _res = genexp_var;
                cache.putResult(_mark, _TMP_203_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'True'
            Token _keyword;
            if (
                (_keyword = (Token)expect(526)) != null  // token='True'
            )
            {
                _res = _keyword;
                cache.putResult(_mark, _TMP_203_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'None'
            Token _keyword;
            if (
                (_keyword = (Token)expect(525)) != null  // token='None'
            )
            {
                _res = _keyword;
                cache.putResult(_mark, _TMP_203_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'False'
            Token _keyword;
            if (
                (_keyword = (Token)expect(527)) != null  // token='False'
            )
            {
                _res = _keyword;
                cache.putResult(_mark, _TMP_203_ID, _res);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_203_ID, _res);
        return (Object)_res;
    }

    // _tmp_204: '=' | ':=' | ','
    public Token _tmp_204_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_204_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_204_ID);
            return (Token)_res;
        }
        { // '='
            Token _literal;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_204_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ':='
            Token _literal;
            if (
                (_literal = (Token)expect(53)) != null  // token=':='
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_204_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_204_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_204_ID, _res);
        return (Token)_res;
    }

    // _loop0_205: star_named_expressions
    public ExprTy[] _loop0_205_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_205_ID)) {
            _res = cache.getResult(_mark, _LOOP0_205_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // star_named_expressions
            ExprTy[] star_named_expressions_var;
            while (
                (star_named_expressions_var = (ExprTy[])star_named_expressions_rule()) != null  // star_named_expressions
            )
            {
                _res = star_named_expressions_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_205_ID, _seq);
        return _seq;
    }

    // _loop0_206: (star_targets '=')
    public ExprTy[] _loop0_206_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_206_ID)) {
            _res = cache.getResult(_mark, _LOOP0_206_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (star_targets '=')
            ExprTy _tmp_247_var;
            while (
                (_tmp_247_var = (ExprTy)_tmp_247_rule()) != null  // star_targets '='
            )
            {
                _res = _tmp_247_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_206_ID, _seq);
        return _seq;
    }

    // _loop0_207: (star_targets '=')
    public ExprTy[] _loop0_207_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_207_ID)) {
            _res = cache.getResult(_mark, _LOOP0_207_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (star_targets '=')
            ExprTy _tmp_248_var;
            while (
                (_tmp_248_var = (ExprTy)_tmp_248_rule()) != null  // star_targets '='
            )
            {
                _res = _tmp_248_var;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_207_ID, _seq);
        return _seq;
    }

    // _tmp_208: yield_expr | star_expressions
    public ExprTy _tmp_208_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_208_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_208_ID);
            return (ExprTy)_res;
        }
        { // yield_expr
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                cache.putResult(_mark, _TMP_208_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                cache.putResult(_mark, _TMP_208_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_208_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_209: '[' | '(' | '{'
    public Token _tmp_209_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_209_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_209_ID);
            return (Token)_res;
        }
        { // '['
            Token _literal;
            if (
                (_literal = (Token)expect(9)) != null  // token='['
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_209_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '('
            Token _literal;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_209_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '{'
            Token _literal;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_209_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_209_ID, _res);
        return (Token)_res;
    }

    // _tmp_210: '[' | '{'
    public Token _tmp_210_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_210_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_210_ID);
            return (Token)_res;
        }
        { // '['
            Token _literal;
            if (
                (_literal = (Token)expect(9)) != null  // token='['
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_210_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '{'
            Token _literal;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_210_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_210_ID, _res);
        return (Token)_res;
    }

    // _tmp_211: star_named_expressions
    public ExprTy[] _tmp_211_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_211_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _TMP_211_ID);
            return (ExprTy[])_res;
        }
        { // star_named_expressions
            ExprTy[] star_named_expressions_var;
            if (
                (star_named_expressions_var = (ExprTy[])star_named_expressions_rule()) != null  // star_named_expressions
            )
            {
                _res = star_named_expressions_var;
                cache.putResult(_mark, _TMP_211_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_211_ID, _res);
        return (ExprTy[])_res;
    }

    // _loop0_212: param_no_default
    public ArgTy[] _loop0_212_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_212_ID)) {
            _res = cache.getResult(_mark, _LOOP0_212_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            ArgTy param_no_default_var;
            while (
                (param_no_default_var = (ArgTy)param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_212_ID, _seq);
        return _seq;
    }

    // _loop1_213: param_with_default
    public NameDefaultPair[] _loop1_213_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_213_ID)) {
            _res = cache.getResult(_mark, _LOOP1_213_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            NameDefaultPair param_with_default_var;
            while (
                (param_with_default_var = (NameDefaultPair)param_with_default_rule()) != null  // param_with_default
            )
            {
                _res = param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_213_ID, _seq);
        return _seq;
    }

    // _loop0_214: lambda_param_no_default
    public ArgTy[] _loop0_214_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_214_ID)) {
            _res = cache.getResult(_mark, _LOOP0_214_ID);
            return (ArgTy[])_res;
        }
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            ArgTy lambda_param_no_default_var;
            while (
                (lambda_param_no_default_var = (ArgTy)lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = lambda_param_no_default_var;
                if (_res instanceof ArgTy) {
                    _children.add((ArgTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ArgTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ArgTy[] _seq = _children.toArray(new ArgTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_214_ID, _seq);
        return _seq;
    }

    // _loop1_215: lambda_param_with_default
    public NameDefaultPair[] _loop1_215_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP1_215_ID)) {
            _res = cache.getResult(_mark, _LOOP1_215_ID);
            return (NameDefaultPair[])_res;
        }
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            NameDefaultPair lambda_param_with_default_var;
            while (
                (lambda_param_with_default_var = (NameDefaultPair)lambda_param_with_default_rule()) != null  // lambda_param_with_default
            )
            {
                _res = lambda_param_with_default_var;
                if (_res instanceof NameDefaultPair) {
                    _children.add((NameDefaultPair)_res);
                } else {
                    _children.addAll(Arrays.asList((NameDefaultPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        NameDefaultPair[] _seq = _children.toArray(new NameDefaultPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_215_ID, _seq);
        return _seq;
    }

    // _tmp_216: ')' | ',' (')' | '**')
    public Token _tmp_216_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_216_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_216_ID);
            return (Token)_res;
        }
        { // ')'
            Token _literal;
            if (
                (_literal = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_216_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ',' (')' | '**')
            Token _literal;
            Token _tmp_249_var;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (_tmp_249_var = (Token)_tmp_249_rule()) != null  // ')' | '**'
            )
            {
                _res = dummyName(_literal, _tmp_249_var);
                cache.putResult(_mark, _TMP_216_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_216_ID, _res);
        return (Token)_res;
    }

    // _tmp_217: ':' | ',' (':' | '**')
    public Token _tmp_217_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_217_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_217_ID);
            return (Token)_res;
        }
        { // ':'
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_217_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ',' (':' | '**')
            Token _literal;
            Token _tmp_250_var;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (_tmp_250_var = (Token)_tmp_250_rule()) != null  // ':' | '**'
            )
            {
                _res = dummyName(_literal, _tmp_250_var);
                cache.putResult(_mark, _TMP_217_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_217_ID, _res);
        return (Token)_res;
    }

    // _tmp_218: ',' | ')' | ':'
    public Token _tmp_218_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_218_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_218_ID);
            return (Token)_res;
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_218_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ')'
            Token _literal;
            if (
                (_literal = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_218_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ':'
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_218_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_218_ID, _res);
        return (Token)_res;
    }

    // _tmp_219: ASYNC
    public Token _tmp_219_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_219_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_219_ID);
            return (Token)_res;
        }
        { // ASYNC
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                cache.putResult(_mark, _TMP_219_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_219_ID, _res);
        return (Token)_res;
    }

    // _loop0_221: ',' (expression ['as' star_target])
    public ExprTy[] _loop0_221_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_221_ID)) {
            _res = cache.getResult(_mark, _LOOP0_221_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' (expression ['as' star_target])
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)_tmp_251_rule()) != null  // expression ['as' star_target]
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_221_ID, _seq);
        return _seq;
    }

    // _gather_220: (expression ['as' star_target]) _loop0_221
    public ExprTy[] _gather_220_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_220_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_220_ID);
            return (ExprTy[])_res;
        }
        { // (expression ['as' star_target]) _loop0_221
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)_tmp_251_rule()) != null  // expression ['as' star_target]
                &&
                (seq = (ExprTy[])_loop0_221_rule()) != null  // _loop0_221
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_220_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_220_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_222: ASYNC
    public Token _tmp_222_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_222_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_222_ID);
            return (Token)_res;
        }
        { // ASYNC
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                cache.putResult(_mark, _TMP_222_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_222_ID, _res);
        return (Token)_res;
    }

    // _loop0_224: ',' (expressions ['as' star_target])
    public ExprTy[] _loop0_224_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_224_ID)) {
            _res = cache.getResult(_mark, _LOOP0_224_ID);
            return (ExprTy[])_res;
        }
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' (expressions ['as' star_target])
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)_tmp_252_rule()) != null  // expressions ['as' star_target]
            )
            {
                _res = elem;
                if (_res instanceof ExprTy) {
                    _children.add((ExprTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExprTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        ExprTy[] _seq = _children.toArray(new ExprTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_224_ID, _seq);
        return _seq;
    }

    // _gather_223: (expressions ['as' star_target]) _loop0_224
    public ExprTy[] _gather_223_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_223_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _GATHER_223_ID);
            return (ExprTy[])_res;
        }
        { // (expressions ['as' star_target]) _loop0_224
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)_tmp_252_rule()) != null  // expressions ['as' star_target]
                &&
                (seq = (ExprTy[])_loop0_224_rule()) != null  // _loop0_224
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_223_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_223_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_225: 'as' NAME
    public ExprTy _tmp_225_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_225_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_225_ID);
            return (ExprTy)_res;
        }
        { // 'as' NAME
            Token _keyword;
            ExprTy name_var;
            if (
                (_keyword = (Token)expect(519)) != null  // token='as'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = dummyName(_keyword, name_var);
                cache.putResult(_mark, _TMP_225_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_225_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_226: 'as' NAME
    public ExprTy _tmp_226_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_226_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_226_ID);
            return (ExprTy)_res;
        }
        { // 'as' NAME
            Token _keyword;
            ExprTy name_var;
            if (
                (_keyword = (Token)expect(519)) != null  // token='as'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = dummyName(_keyword, name_var);
                cache.putResult(_mark, _TMP_226_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_226_ID, _res);
        return (ExprTy)_res;
    }

    // _loop0_228: ',' double_starred_kvpair
    public KeyValuePair[] _loop0_228_rule()
    {
        Object _res = null;
        int _mark = mark();
        if (cache.hasResult(_mark, _LOOP0_228_ID)) {
            _res = cache.getResult(_mark, _LOOP0_228_ID);
            return (KeyValuePair[])_res;
        }
        int _start_mark = mark();
        List<KeyValuePair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' double_starred_kvpair
            Token _literal;
            KeyValuePair elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (KeyValuePair)double_starred_kvpair_rule()) != null  // double_starred_kvpair
            )
            {
                _res = elem;
                if (_res instanceof KeyValuePair) {
                    _children.add((KeyValuePair)_res);
                } else {
                    _children.addAll(Arrays.asList((KeyValuePair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        KeyValuePair[] _seq = _children.toArray(new KeyValuePair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_228_ID, _seq);
        return _seq;
    }

    // _gather_227: double_starred_kvpair _loop0_228
    public KeyValuePair[] _gather_227_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _GATHER_227_ID)) {
            _res = (KeyValuePair[])cache.getResult(_mark, _GATHER_227_ID);
            return (KeyValuePair[])_res;
        }
        { // double_starred_kvpair _loop0_228
            KeyValuePair elem;
            KeyValuePair[] seq;
            if (
                (elem = (KeyValuePair)double_starred_kvpair_rule()) != null  // double_starred_kvpair
                &&
                (seq = (KeyValuePair[])_loop0_228_rule()) != null  // _loop0_228
            )
            {
                _res = insertInFront(elem, seq);
                cache.putResult(_mark, _GATHER_227_ID, _res);
                return (KeyValuePair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _GATHER_227_ID, _res);
        return (KeyValuePair[])_res;
    }

    // _tmp_229: '}' | ','
    public Token _tmp_229_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_229_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_229_ID);
            return (Token)_res;
        }
        { // '}'
            Token _literal;
            if (
                (_literal = (Token)expect(26)) != null  // token='}'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_229_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ','
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_229_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_229_ID, _res);
        return (Token)_res;
    }

    // _tmp_230: ':'
    public Token _tmp_230_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_230_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_230_ID);
            return (Token)_res;
        }
        { // ':'
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_230_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_230_ID, _res);
        return (Token)_res;
    }

    // _tmp_231: star_targets '='
    public ExprTy _tmp_231_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_231_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_231_ID);
            return (ExprTy)_res;
        }
        { // star_targets '='
            Token _literal;
            ExprTy z;
            if (
                (z = (ExprTy)star_targets_rule()) != null  // star_targets
                &&
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = z;
                cache.putResult(_mark, _TMP_231_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_231_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_232: '.' | '...'
    public Token _tmp_232_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_232_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_232_ID);
            return (Token)_res;
        }
        { // '.'
            Token _literal;
            if (
                (_literal = (Token)expect(23)) != null  // token='.'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_232_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '...'
            Token _literal;
            if (
                (_literal = (Token)expect(52)) != null  // token='...'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_232_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_232_ID, _res);
        return (Token)_res;
    }

    // _tmp_233: '.' | '...'
    public Token _tmp_233_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_233_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_233_ID);
            return (Token)_res;
        }
        { // '.'
            Token _literal;
            if (
                (_literal = (Token)expect(23)) != null  // token='.'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_233_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '...'
            Token _literal;
            if (
                (_literal = (Token)expect(52)) != null  // token='...'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_233_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_233_ID, _res);
        return (Token)_res;
    }

    // _tmp_234: '@' named_expression NEWLINE
    public ExprTy _tmp_234_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_234_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_234_ID);
            return (ExprTy)_res;
        }
        { // '@' named_expression NEWLINE
            Token _literal;
            ExprTy f;
            Token newline_var;
            if (
                (_literal = (Token)expect(49)) != null  // token='@'
                &&
                (f = (ExprTy)named_expression_rule()) != null  // named_expression
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = f;
                cache.putResult(_mark, _TMP_234_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_234_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_235: arguments
    public ExprTy _tmp_235_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_235_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_235_ID);
            return (ExprTy)_res;
        }
        { // arguments
            ExprTy arguments_var;
            if (
                (arguments_var = (ExprTy)arguments_rule()) != null  // arguments
            )
            {
                _res = arguments_var;
                cache.putResult(_mark, _TMP_235_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_235_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_236: ',' star_expression
    public ExprTy _tmp_236_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_236_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_236_ID);
            return (ExprTy)_res;
        }
        { // ',' star_expression
            Token _literal;
            ExprTy c;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (c = (ExprTy)star_expression_rule()) != null  // star_expression
            )
            {
                _res = c;
                cache.putResult(_mark, _TMP_236_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_236_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_237: ',' expression
    public ExprTy _tmp_237_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_237_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_237_ID);
            return (ExprTy)_res;
        }
        { // ',' expression
            Token _literal;
            ExprTy c;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (c = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = c;
                cache.putResult(_mark, _TMP_237_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_237_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_238: 'or' conjunction
    public ExprTy _tmp_238_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_238_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_238_ID);
            return (ExprTy)_res;
        }
        { // 'or' conjunction
            Token _keyword;
            ExprTy c;
            if (
                (_keyword = (Token)expect(529)) != null  // token='or'
                &&
                (c = (ExprTy)conjunction_rule()) != null  // conjunction
            )
            {
                _res = c;
                cache.putResult(_mark, _TMP_238_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_238_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_239: 'and' inversion
    public ExprTy _tmp_239_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_239_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_239_ID);
            return (ExprTy)_res;
        }
        { // 'and' inversion
            Token _keyword;
            ExprTy c;
            if (
                (_keyword = (Token)expect(530)) != null  // token='and'
                &&
                (c = (ExprTy)inversion_rule()) != null  // inversion
            )
            {
                _res = c;
                cache.putResult(_mark, _TMP_239_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_239_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_240: expression
    public ExprTy _tmp_240_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_240_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_240_ID);
            return (ExprTy)_res;
        }
        { // expression
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = expression_var;
                cache.putResult(_mark, _TMP_240_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_240_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_241: star_named_expressions
    public ExprTy[] _tmp_241_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_241_ID)) {
            _res = (ExprTy[])cache.getResult(_mark, _TMP_241_ID);
            return (ExprTy[])_res;
        }
        { // star_named_expressions
            ExprTy[] star_named_expressions_var;
            if (
                (star_named_expressions_var = (ExprTy[])star_named_expressions_rule()) != null  // star_named_expressions
            )
            {
                _res = star_named_expressions_var;
                cache.putResult(_mark, _TMP_241_ID, _res);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_241_ID, _res);
        return (ExprTy[])_res;
    }

    // _tmp_242: 'if' disjunction
    public ExprTy _tmp_242_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_242_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_242_ID);
            return (ExprTy)_res;
        }
        { // 'if' disjunction
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(513)) != null  // token='if'
                &&
                (z = (ExprTy)disjunction_rule()) != null  // disjunction
            )
            {
                _res = z;
                cache.putResult(_mark, _TMP_242_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_242_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_243: 'if' disjunction
    public ExprTy _tmp_243_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_243_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_243_ID);
            return (ExprTy)_res;
        }
        { // 'if' disjunction
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(513)) != null  // token='if'
                &&
                (z = (ExprTy)disjunction_rule()) != null  // disjunction
            )
            {
                _res = z;
                cache.putResult(_mark, _TMP_243_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_243_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_244: starred_expression | direct_named_expression !'='
    public ExprTy _tmp_244_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_244_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_244_ID);
            return (ExprTy)_res;
        }
        { // starred_expression
            ExprTy starred_expression_var;
            if (
                (starred_expression_var = (ExprTy)starred_expression_rule()) != null  // starred_expression
            )
            {
                _res = starred_expression_var;
                cache.putResult(_mark, _TMP_244_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // direct_named_expression !'='
            ExprTy direct_named_expression_var;
            if (
                (direct_named_expression_var = (ExprTy)direct_named_expression_rule()) != null  // direct_named_expression
                &&
                genLookahead_expect(false, 22)  // token='='
            )
            {
                _res = direct_named_expression_var;
                cache.putResult(_mark, _TMP_244_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_244_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_245: ',' star_target
    public ExprTy _tmp_245_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_245_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_245_ID);
            return (ExprTy)_res;
        }
        { // ',' star_target
            Token _literal;
            ExprTy c;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (c = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = c;
                cache.putResult(_mark, _TMP_245_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_245_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_246: ',' star_target
    public ExprTy _tmp_246_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_246_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_246_ID);
            return (ExprTy)_res;
        }
        { // ',' star_target
            Token _literal;
            ExprTy c;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (c = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = c;
                cache.putResult(_mark, _TMP_246_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_246_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_247: star_targets '='
    public ExprTy _tmp_247_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_247_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_247_ID);
            return (ExprTy)_res;
        }
        { // star_targets '='
            Token _literal;
            ExprTy star_targets_var;
            if (
                (star_targets_var = (ExprTy)star_targets_rule()) != null  // star_targets
                &&
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = dummyName(star_targets_var, _literal);
                cache.putResult(_mark, _TMP_247_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_247_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_248: star_targets '='
    public ExprTy _tmp_248_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_248_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_248_ID);
            return (ExprTy)_res;
        }
        { // star_targets '='
            Token _literal;
            ExprTy star_targets_var;
            if (
                (star_targets_var = (ExprTy)star_targets_rule()) != null  // star_targets
                &&
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = dummyName(star_targets_var, _literal);
                cache.putResult(_mark, _TMP_248_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_248_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_249: ')' | '**'
    public Token _tmp_249_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_249_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_249_ID);
            return (Token)_res;
        }
        { // ')'
            Token _literal;
            if (
                (_literal = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_249_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '**'
            Token _literal;
            if (
                (_literal = (Token)expect(35)) != null  // token='**'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_249_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_249_ID, _res);
        return (Token)_res;
    }

    // _tmp_250: ':' | '**'
    public Token _tmp_250_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_250_ID)) {
            _res = (Token)cache.getResult(_mark, _TMP_250_ID);
            return (Token)_res;
        }
        { // ':'
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_250_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '**'
            Token _literal;
            if (
                (_literal = (Token)expect(35)) != null  // token='**'
            )
            {
                _res = _literal;
                cache.putResult(_mark, _TMP_250_ID, _res);
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_250_ID, _res);
        return (Token)_res;
    }

    // _tmp_251: expression ['as' star_target]
    public ExprTy _tmp_251_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_251_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_251_ID);
            return (ExprTy)_res;
        }
        { // expression ['as' star_target]
            ExprTy _opt_var;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                ((_opt_var = (ExprTy)_tmp_253_rule()) != null || true)  // ['as' star_target]
            )
            {
                _res = dummyName(expression_var, _opt_var);
                cache.putResult(_mark, _TMP_251_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_251_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_252: expressions ['as' star_target]
    public ExprTy _tmp_252_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_252_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_252_ID);
            return (ExprTy)_res;
        }
        { // expressions ['as' star_target]
            ExprTy _opt_var;
            ExprTy expressions_var;
            if (
                (expressions_var = (ExprTy)expressions_rule()) != null  // expressions
                &&
                ((_opt_var = (ExprTy)_tmp_254_rule()) != null || true)  // ['as' star_target]
            )
            {
                _res = dummyName(expressions_var, _opt_var);
                cache.putResult(_mark, _TMP_252_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_252_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_253: 'as' star_target
    public ExprTy _tmp_253_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_253_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_253_ID);
            return (ExprTy)_res;
        }
        { // 'as' star_target
            Token _keyword;
            ExprTy star_target_var;
            if (
                (_keyword = (Token)expect(519)) != null  // token='as'
                &&
                (star_target_var = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = dummyName(_keyword, star_target_var);
                cache.putResult(_mark, _TMP_253_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_253_ID, _res);
        return (ExprTy)_res;
    }

    // _tmp_254: 'as' star_target
    public ExprTy _tmp_254_rule()
    {
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, _TMP_254_ID)) {
            _res = (ExprTy)cache.getResult(_mark, _TMP_254_ID);
            return (ExprTy)_res;
        }
        { // 'as' star_target
            Token _keyword;
            ExprTy star_target_var;
            if (
                (_keyword = (Token)expect(519)) != null  // token='as'
                &&
                (star_target_var = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = dummyName(_keyword, star_target_var);
                cache.putResult(_mark, _TMP_254_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, _TMP_254_ID, _res);
        return (ExprTy)_res;
    }

    // lookahead methods generated
    private boolean genLookahead_expect(boolean match, int arg0) {
        int tmpPos = mark();
        Token result = expect(arg0);
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_17_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_17_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_18_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_18_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_19_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_19_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_20_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_20_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_21_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_21_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_34_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_34_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_61_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_61_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_69_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_69_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead_expect_SOFT_KEYWORD(boolean match, String arg0) {
        int tmpPos = mark();
        ExprTy result = expect_SOFT_KEYWORD(arg0);
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_70_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_70_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_71_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_71_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_90_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_90_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead_string_token(boolean match) {
        int tmpPos = mark();
        Token result = string_token();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead_t_lookahead_rule(boolean match) {
        int tmpPos = mark();
        Token result = t_lookahead_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_201_rule(boolean match) {
        int tmpPos = mark();
        ExprTy result = _tmp_201_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_202_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_202_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_203_rule(boolean match) {
        int tmpPos = mark();
        Object result = _tmp_203_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_204_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_204_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_218_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_218_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_229_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_229_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_230_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_230_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

}
