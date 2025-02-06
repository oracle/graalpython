/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
// Generated from python.gram by pegen
package com.oracle.graal.python.pegparser;

import com.oracle.graal.python.pegparser.sst.*;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

@SuppressWarnings({"all", "cast"})
@SuppressFBWarnings
public final class Parser extends AbstractParser {

    private static final Object[][][] reservedKeywords = new Object[][][]{
        null,
        null,
        {
            {"if", 668},
            {"as", 674},
            {"in", 677},
            {"or", 684},
            {"is", 687},
        },
        {
            {"del", 660},
            {"def", 667},
            {"for", 671},
            {"try", 672},
            {"and", 685},
            {"not", 686},
        },
        {
            {"from", 657},
            {"pass", 659},
            {"with", 670},
            {"elif", 675},
            {"else", 676},
            {"None", 680},
            {"True", 681},
        },
        {
            {"raise", 658},
            {"yield", 661},
            {"break", 663},
            {"class", 669},
            {"while", 673},
            {"False", 682},
        },
        {
            {"return", 655},
            {"import", 656},
            {"assert", 662},
            {"global", 665},
            {"except", 678},
            {"lambda", 683},
        },
        {
            {"finally", 679},
        },
        {
            {"continue", 664},
            {"nonlocal", 666},
        },
    };
    @Override
    protected Object[][][] getReservedKeywords() { return reservedKeywords; }
    private static final String[] softKeywords = new String[]{
        "_",
        "case",
        "match",
        "type",
    };
    @Override
    protected String[] getSoftKeywords() { return softKeywords; }
    private static final int FILE_ID = 1000;
    private static final int INTERACTIVE_ID = 1001;
    private static final int EVAL_ID = 1002;
    private static final int FUNC_TYPE_ID = 1003;
    private static final int STATEMENTS_ID = 1004;
    private static final int STATEMENT_ID = 1005;
    private static final int STATEMENT_NEWLINE_ID = 1006;
    private static final int SIMPLE_STMTS_ID = 1007;
    private static final int SIMPLE_STMT_ID = 1008;
    private static final int COMPOUND_STMT_ID = 1009;
    private static final int ASSIGNMENT_ID = 1010;
    private static final int ANNOTATED_RHS_ID = 1011;
    private static final int AUGASSIGN_ID = 1012;
    private static final int RETURN_STMT_ID = 1013;
    private static final int RAISE_STMT_ID = 1014;
    private static final int GLOBAL_STMT_ID = 1015;
    private static final int NONLOCAL_STMT_ID = 1016;
    private static final int DEL_STMT_ID = 1017;
    private static final int YIELD_STMT_ID = 1018;
    private static final int ASSERT_STMT_ID = 1019;
    private static final int IMPORT_STMT_ID = 1020;
    private static final int IMPORT_NAME_ID = 1021;
    private static final int IMPORT_FROM_ID = 1022;
    private static final int IMPORT_FROM_TARGETS_ID = 1023;
    private static final int IMPORT_FROM_AS_NAMES_ID = 1024;
    private static final int IMPORT_FROM_AS_NAME_ID = 1025;
    private static final int DOTTED_AS_NAMES_ID = 1026;
    private static final int DOTTED_AS_NAME_ID = 1027;
    private static final int DOTTED_NAME_ID = 1028;  // Left-recursive
    private static final int BLOCK_ID = 1029;
    private static final int DECORATORS_ID = 1030;
    private static final int CLASS_DEF_ID = 1031;
    private static final int CLASS_DEF_RAW_ID = 1032;
    private static final int FUNCTION_DEF_ID = 1033;
    private static final int FUNCTION_DEF_RAW_ID = 1034;
    private static final int PARAMS_ID = 1035;
    private static final int PARAMETERS_ID = 1036;
    private static final int SLASH_NO_DEFAULT_ID = 1037;
    private static final int SLASH_WITH_DEFAULT_ID = 1038;
    private static final int STAR_ETC_ID = 1039;
    private static final int KWDS_ID = 1040;
    private static final int PARAM_NO_DEFAULT_ID = 1041;
    private static final int PARAM_NO_DEFAULT_STAR_ANNOTATION_ID = 1042;
    private static final int PARAM_WITH_DEFAULT_ID = 1043;
    private static final int PARAM_MAYBE_DEFAULT_ID = 1044;
    private static final int PARAM_ID = 1045;
    private static final int PARAM_STAR_ANNOTATION_ID = 1046;
    private static final int ANNOTATION_ID = 1047;
    private static final int STAR_ANNOTATION_ID = 1048;
    private static final int DEFAULT_ID = 1049;
    private static final int IF_STMT_ID = 1050;
    private static final int ELIF_STMT_ID = 1051;
    private static final int ELSE_BLOCK_ID = 1052;
    private static final int WHILE_STMT_ID = 1053;
    private static final int FOR_STMT_ID = 1054;
    private static final int WITH_STMT_ID = 1055;
    private static final int WITH_ITEM_ID = 1056;
    private static final int TRY_STMT_ID = 1057;
    private static final int EXCEPT_BLOCK_ID = 1058;
    private static final int EXCEPT_STAR_BLOCK_ID = 1059;
    private static final int FINALLY_BLOCK_ID = 1060;
    private static final int MATCH_STMT_ID = 1061;
    private static final int SUBJECT_EXPR_ID = 1062;
    private static final int CASE_BLOCK_ID = 1063;
    private static final int GUARD_ID = 1064;
    private static final int PATTERNS_ID = 1065;
    private static final int PATTERN_ID = 1066;
    private static final int AS_PATTERN_ID = 1067;
    private static final int OR_PATTERN_ID = 1068;
    private static final int CLOSED_PATTERN_ID = 1069;
    private static final int LITERAL_PATTERN_ID = 1070;
    private static final int LITERAL_EXPR_ID = 1071;
    private static final int COMPLEX_NUMBER_ID = 1072;
    private static final int SIGNED_NUMBER_ID = 1073;
    private static final int SIGNED_REAL_NUMBER_ID = 1074;
    private static final int REAL_NUMBER_ID = 1075;
    private static final int IMAGINARY_NUMBER_ID = 1076;
    private static final int CAPTURE_PATTERN_ID = 1077;
    private static final int PATTERN_CAPTURE_TARGET_ID = 1078;
    private static final int WILDCARD_PATTERN_ID = 1079;
    private static final int VALUE_PATTERN_ID = 1080;
    private static final int ATTR_ID = 1081;  // Left-recursive
    private static final int NAME_OR_ATTR_ID = 1082;  // Left-recursive
    private static final int GROUP_PATTERN_ID = 1083;
    private static final int SEQUENCE_PATTERN_ID = 1084;
    private static final int OPEN_SEQUENCE_PATTERN_ID = 1085;
    private static final int MAYBE_SEQUENCE_PATTERN_ID = 1086;
    private static final int MAYBE_STAR_PATTERN_ID = 1087;
    private static final int STAR_PATTERN_ID = 1088;
    private static final int MAPPING_PATTERN_ID = 1089;
    private static final int ITEMS_PATTERN_ID = 1090;
    private static final int KEY_VALUE_PATTERN_ID = 1091;
    private static final int DOUBLE_STAR_PATTERN_ID = 1092;
    private static final int CLASS_PATTERN_ID = 1093;
    private static final int POSITIONAL_PATTERNS_ID = 1094;
    private static final int KEYWORD_PATTERNS_ID = 1095;
    private static final int KEYWORD_PATTERN_ID = 1096;
    private static final int TYPE_ALIAS_ID = 1097;
    private static final int TYPE_PARAMS_ID = 1098;
    private static final int TYPE_PARAM_SEQ_ID = 1099;
    private static final int TYPE_PARAM_ID = 1100;
    private static final int TYPE_PARAM_BOUND_ID = 1101;
    private static final int EXPRESSIONS_ID = 1102;
    private static final int EXPRESSION_ID = 1103;
    private static final int YIELD_EXPR_ID = 1104;
    private static final int STAR_EXPRESSIONS_ID = 1105;
    private static final int STAR_EXPRESSION_ID = 1106;
    private static final int STAR_NAMED_EXPRESSIONS_ID = 1107;
    private static final int STAR_NAMED_EXPRESSION_ID = 1108;
    private static final int ASSIGNMENT_EXPRESSION_ID = 1109;
    private static final int NAMED_EXPRESSION_ID = 1110;
    private static final int DISJUNCTION_ID = 1111;
    private static final int CONJUNCTION_ID = 1112;
    private static final int INVERSION_ID = 1113;
    private static final int COMPARISON_ID = 1114;
    private static final int COMPARE_OP_BITWISE_OR_PAIR_ID = 1115;
    private static final int EQ_BITWISE_OR_ID = 1116;
    private static final int NOTEQ_BITWISE_OR_ID = 1117;
    private static final int LTE_BITWISE_OR_ID = 1118;
    private static final int LT_BITWISE_OR_ID = 1119;
    private static final int GTE_BITWISE_OR_ID = 1120;
    private static final int GT_BITWISE_OR_ID = 1121;
    private static final int NOTIN_BITWISE_OR_ID = 1122;
    private static final int IN_BITWISE_OR_ID = 1123;
    private static final int ISNOT_BITWISE_OR_ID = 1124;
    private static final int IS_BITWISE_OR_ID = 1125;
    private static final int BITWISE_OR_ID = 1126;  // Left-recursive
    private static final int BITWISE_XOR_ID = 1127;  // Left-recursive
    private static final int BITWISE_AND_ID = 1128;  // Left-recursive
    private static final int SHIFT_EXPR_ID = 1129;  // Left-recursive
    private static final int SUM_ID = 1130;  // Left-recursive
    private static final int TERM_ID = 1131;  // Left-recursive
    private static final int FACTOR_ID = 1132;
    private static final int POWER_ID = 1133;
    private static final int AWAIT_PRIMARY_ID = 1134;
    private static final int PRIMARY_ID = 1135;  // Left-recursive
    private static final int SLICES_ID = 1136;
    private static final int SLICE_ID = 1137;
    private static final int ATOM_ID = 1138;
    private static final int GROUP_ID = 1139;
    private static final int LAMBDEF_ID = 1140;
    private static final int LAMBDA_PARAMS_ID = 1141;
    private static final int LAMBDA_PARAMETERS_ID = 1142;
    private static final int LAMBDA_SLASH_NO_DEFAULT_ID = 1143;
    private static final int LAMBDA_SLASH_WITH_DEFAULT_ID = 1144;
    private static final int LAMBDA_STAR_ETC_ID = 1145;
    private static final int LAMBDA_KWDS_ID = 1146;
    private static final int LAMBDA_PARAM_NO_DEFAULT_ID = 1147;
    private static final int LAMBDA_PARAM_WITH_DEFAULT_ID = 1148;
    private static final int LAMBDA_PARAM_MAYBE_DEFAULT_ID = 1149;
    private static final int LAMBDA_PARAM_ID = 1150;
    private static final int FSTRING_MIDDLE_ID = 1151;
    private static final int FSTRING_REPLACEMENT_FIELD_ID = 1152;
    private static final int FSTRING_CONVERSION_ID = 1153;
    private static final int FSTRING_FULL_FORMAT_SPEC_ID = 1154;
    private static final int FSTRING_FORMAT_SPEC_ID = 1155;
    private static final int FSTRING_ID = 1156;
    private static final int STRING_ID = 1157;
    private static final int STRINGS_ID = 1158;
    private static final int LIST_ID = 1159;
    private static final int TUPLE_ID = 1160;
    private static final int SET_ID = 1161;
    private static final int DICT_ID = 1162;
    private static final int DOUBLE_STARRED_KVPAIRS_ID = 1163;
    private static final int DOUBLE_STARRED_KVPAIR_ID = 1164;
    private static final int KVPAIR_ID = 1165;
    private static final int FOR_IF_CLAUSES_ID = 1166;
    private static final int FOR_IF_CLAUSE_ID = 1167;
    private static final int LISTCOMP_ID = 1168;
    private static final int SETCOMP_ID = 1169;
    private static final int GENEXP_ID = 1170;
    private static final int DICTCOMP_ID = 1171;
    private static final int ARGUMENTS_ID = 1172;
    private static final int ARGS_ID = 1173;
    private static final int KWARGS_ID = 1174;
    private static final int STARRED_EXPRESSION_ID = 1175;
    private static final int KWARG_OR_STARRED_ID = 1176;
    private static final int KWARG_OR_DOUBLE_STARRED_ID = 1177;
    private static final int STAR_TARGETS_ID = 1178;
    private static final int STAR_TARGETS_LIST_SEQ_ID = 1179;
    private static final int STAR_TARGETS_TUPLE_SEQ_ID = 1180;
    private static final int STAR_TARGET_ID = 1181;
    private static final int TARGET_WITH_STAR_ATOM_ID = 1182;
    private static final int STAR_ATOM_ID = 1183;
    private static final int SINGLE_TARGET_ID = 1184;
    private static final int SINGLE_SUBSCRIPT_ATTRIBUTE_TARGET_ID = 1185;
    private static final int T_PRIMARY_ID = 1186;  // Left-recursive
    private static final int T_LOOKAHEAD_ID = 1187;
    private static final int DEL_TARGETS_ID = 1188;
    private static final int DEL_TARGET_ID = 1189;
    private static final int DEL_T_ATOM_ID = 1190;
    private static final int TYPE_EXPRESSIONS_ID = 1191;
    private static final int FUNC_TYPE_COMMENT_ID = 1192;
    private static final int INVALID_ARGUMENTS_ID = 1193;
    private static final int INVALID_KWARG_ID = 1194;
    private static final int EXPRESSION_WITHOUT_INVALID_ID = 1195;
    private static final int INVALID_LEGACY_EXPRESSION_ID = 1196;
    private static final int INVALID_EXPRESSION_ID = 1197;
    private static final int INVALID_NAMED_EXPRESSION_ID = 1198;
    private static final int INVALID_ASSIGNMENT_ID = 1199;
    private static final int INVALID_ANN_ASSIGN_TARGET_ID = 1200;
    private static final int INVALID_DEL_STMT_ID = 1201;
    private static final int INVALID_BLOCK_ID = 1202;
    private static final int INVALID_COMPREHENSION_ID = 1203;
    private static final int INVALID_DICT_COMPREHENSION_ID = 1204;
    private static final int INVALID_PARAMETERS_ID = 1205;
    private static final int INVALID_DEFAULT_ID = 1206;
    private static final int INVALID_STAR_ETC_ID = 1207;
    private static final int INVALID_KWDS_ID = 1208;
    private static final int INVALID_PARAMETERS_HELPER_ID = 1209;
    private static final int INVALID_LAMBDA_PARAMETERS_ID = 1210;
    private static final int INVALID_LAMBDA_PARAMETERS_HELPER_ID = 1211;
    private static final int INVALID_LAMBDA_STAR_ETC_ID = 1212;
    private static final int INVALID_LAMBDA_KWDS_ID = 1213;
    private static final int INVALID_DOUBLE_TYPE_COMMENTS_ID = 1214;
    private static final int INVALID_WITH_ITEM_ID = 1215;
    private static final int INVALID_FOR_TARGET_ID = 1216;
    private static final int INVALID_GROUP_ID = 1217;
    private static final int INVALID_IMPORT_ID = 1218;
    private static final int INVALID_IMPORT_FROM_TARGETS_ID = 1219;
    private static final int INVALID_WITH_STMT_ID = 1220;
    private static final int INVALID_WITH_STMT_INDENT_ID = 1221;
    private static final int INVALID_TRY_STMT_ID = 1222;
    private static final int INVALID_EXCEPT_STMT_ID = 1223;
    private static final int INVALID_FINALLY_STMT_ID = 1224;
    private static final int INVALID_EXCEPT_STMT_INDENT_ID = 1225;
    private static final int INVALID_EXCEPT_STAR_STMT_INDENT_ID = 1226;
    private static final int INVALID_MATCH_STMT_ID = 1227;
    private static final int INVALID_CASE_BLOCK_ID = 1228;
    private static final int INVALID_AS_PATTERN_ID = 1229;
    private static final int INVALID_CLASS_PATTERN_ID = 1230;
    private static final int INVALID_CLASS_ARGUMENT_PATTERN_ID = 1231;
    private static final int INVALID_IF_STMT_ID = 1232;
    private static final int INVALID_ELIF_STMT_ID = 1233;
    private static final int INVALID_ELSE_STMT_ID = 1234;
    private static final int INVALID_WHILE_STMT_ID = 1235;
    private static final int INVALID_FOR_STMT_ID = 1236;
    private static final int INVALID_DEF_RAW_ID = 1237;
    private static final int INVALID_CLASS_DEF_RAW_ID = 1238;
    private static final int INVALID_DOUBLE_STARRED_KVPAIRS_ID = 1239;
    private static final int INVALID_KVPAIR_ID = 1240;
    private static final int INVALID_STARRED_EXPRESSION_ID = 1241;
    private static final int INVALID_REPLACEMENT_FIELD_ID = 1242;
    private static final int INVALID_CONVERSION_CHARACTER_ID = 1243;
    private static final int _TMP_1_ID = 1244;
    private static final int _LOOP0_2_ID = 1245;
    private static final int _TMP_3_ID = 1246;
    private static final int _LOOP0_4_ID = 1247;
    private static final int _LOOP1_5_ID = 1248;
    private static final int _LOOP0_7_ID = 1249;
    private static final int _GATHER_6_ID = 1250;
    private static final int _TMP_8_ID = 1251;
    private static final int _TMP_9_ID = 1252;
    private static final int _TMP_10_ID = 1253;
    private static final int _TMP_11_ID = 1254;
    private static final int _TMP_12_ID = 1255;
    private static final int _TMP_13_ID = 1256;
    private static final int _TMP_14_ID = 1257;
    private static final int _TMP_15_ID = 1258;
    private static final int _TMP_16_ID = 1259;
    private static final int _LOOP1_17_ID = 1260;
    private static final int _TMP_18_ID = 1261;
    private static final int _TMP_19_ID = 1262;
    private static final int _TMP_20_ID = 1263;
    private static final int _TMP_21_ID = 1264;
    private static final int _TMP_22_ID = 1265;
    private static final int _LOOP0_24_ID = 1266;
    private static final int _GATHER_23_ID = 1267;
    private static final int _LOOP0_26_ID = 1268;
    private static final int _GATHER_25_ID = 1269;
    private static final int _TMP_27_ID = 1270;
    private static final int _TMP_28_ID = 1271;
    private static final int _LOOP0_29_ID = 1272;
    private static final int _LOOP1_30_ID = 1273;
    private static final int _TMP_31_ID = 1274;
    private static final int _LOOP0_33_ID = 1275;
    private static final int _GATHER_32_ID = 1276;
    private static final int _TMP_34_ID = 1277;
    private static final int _LOOP0_36_ID = 1278;
    private static final int _GATHER_35_ID = 1279;
    private static final int _TMP_37_ID = 1280;
    private static final int _LOOP1_38_ID = 1281;
    private static final int _TMP_39_ID = 1282;
    private static final int _TMP_40_ID = 1283;
    private static final int _TMP_41_ID = 1284;
    private static final int _TMP_42_ID = 1285;
    private static final int _TMP_43_ID = 1286;
    private static final int _TMP_44_ID = 1287;
    private static final int _TMP_45_ID = 1288;
    private static final int _TMP_46_ID = 1289;
    private static final int _TMP_47_ID = 1290;
    private static final int _TMP_48_ID = 1291;
    private static final int _LOOP0_49_ID = 1292;
    private static final int _LOOP0_50_ID = 1293;
    private static final int _TMP_51_ID = 1294;
    private static final int _LOOP0_52_ID = 1295;
    private static final int _TMP_53_ID = 1296;
    private static final int _LOOP1_54_ID = 1297;
    private static final int _LOOP0_55_ID = 1298;
    private static final int _TMP_56_ID = 1299;
    private static final int _LOOP1_57_ID = 1300;
    private static final int _TMP_58_ID = 1301;
    private static final int _LOOP1_59_ID = 1302;
    private static final int _LOOP1_60_ID = 1303;
    private static final int _LOOP0_61_ID = 1304;
    private static final int _LOOP1_62_ID = 1305;
    private static final int _LOOP0_63_ID = 1306;
    private static final int _LOOP1_64_ID = 1307;
    private static final int _LOOP0_65_ID = 1308;
    private static final int _TMP_66_ID = 1309;
    private static final int _LOOP0_67_ID = 1310;
    private static final int _TMP_68_ID = 1311;
    private static final int _LOOP1_69_ID = 1312;
    private static final int _TMP_70_ID = 1313;
    private static final int _TMP_71_ID = 1314;
    private static final int _TMP_72_ID = 1315;
    private static final int _TMP_73_ID = 1316;
    private static final int _TMP_74_ID = 1317;
    private static final int _TMP_75_ID = 1318;
    private static final int _TMP_76_ID = 1319;
    private static final int _TMP_77_ID = 1320;
    private static final int _LOOP0_79_ID = 1321;
    private static final int _GATHER_78_ID = 1322;
    private static final int _LOOP0_81_ID = 1323;
    private static final int _GATHER_80_ID = 1324;
    private static final int _TMP_82_ID = 1325;
    private static final int _LOOP0_84_ID = 1326;
    private static final int _GATHER_83_ID = 1327;
    private static final int _LOOP0_86_ID = 1328;
    private static final int _GATHER_85_ID = 1329;
    private static final int _TMP_87_ID = 1330;
    private static final int _TMP_88_ID = 1331;
    private static final int _LOOP1_89_ID = 1332;
    private static final int _TMP_90_ID = 1333;
    private static final int _TMP_91_ID = 1334;
    private static final int _LOOP1_92_ID = 1335;
    private static final int _TMP_93_ID = 1336;
    private static final int _TMP_94_ID = 1337;
    private static final int _TMP_95_ID = 1338;
    private static final int _TMP_96_ID = 1339;
    private static final int _LOOP1_97_ID = 1340;
    private static final int _LOOP0_99_ID = 1341;
    private static final int _GATHER_98_ID = 1342;
    private static final int _TMP_100_ID = 1343;
    private static final int _TMP_101_ID = 1344;
    private static final int _TMP_102_ID = 1345;
    private static final int _TMP_103_ID = 1346;
    private static final int _LOOP0_105_ID = 1347;
    private static final int _GATHER_104_ID = 1348;
    private static final int _LOOP0_107_ID = 1349;
    private static final int _GATHER_106_ID = 1350;
    private static final int _TMP_108_ID = 1351;
    private static final int _LOOP0_110_ID = 1352;
    private static final int _GATHER_109_ID = 1353;
    private static final int _LOOP0_112_ID = 1354;
    private static final int _GATHER_111_ID = 1355;
    private static final int _TMP_113_ID = 1356;
    private static final int _LOOP0_115_ID = 1357;
    private static final int _GATHER_114_ID = 1358;
    private static final int _TMP_116_ID = 1359;
    private static final int _TMP_117_ID = 1360;
    private static final int _LOOP1_118_ID = 1361;
    private static final int _TMP_119_ID = 1362;
    private static final int _TMP_120_ID = 1363;
    private static final int _LOOP1_121_ID = 1364;
    private static final int _TMP_122_ID = 1365;
    private static final int _LOOP0_124_ID = 1366;
    private static final int _GATHER_123_ID = 1367;
    private static final int _TMP_125_ID = 1368;
    private static final int _LOOP1_126_ID = 1369;
    private static final int _LOOP1_127_ID = 1370;
    private static final int _LOOP1_128_ID = 1371;
    private static final int _TMP_129_ID = 1372;
    private static final int _TMP_130_ID = 1373;
    private static final int _LOOP0_132_ID = 1374;
    private static final int _GATHER_131_ID = 1375;
    private static final int _TMP_133_ID = 1376;
    private static final int _TMP_134_ID = 1377;
    private static final int _TMP_135_ID = 1378;
    private static final int _TMP_136_ID = 1379;
    private static final int _TMP_137_ID = 1380;
    private static final int _TMP_138_ID = 1381;
    private static final int _TMP_139_ID = 1382;
    private static final int _TMP_140_ID = 1383;
    private static final int _TMP_141_ID = 1384;
    private static final int _TMP_142_ID = 1385;
    private static final int _LOOP0_143_ID = 1386;
    private static final int _LOOP0_144_ID = 1387;
    private static final int _TMP_145_ID = 1388;
    private static final int _LOOP0_146_ID = 1389;
    private static final int _TMP_147_ID = 1390;
    private static final int _LOOP1_148_ID = 1391;
    private static final int _LOOP0_149_ID = 1392;
    private static final int _TMP_150_ID = 1393;
    private static final int _LOOP1_151_ID = 1394;
    private static final int _TMP_152_ID = 1395;
    private static final int _LOOP1_153_ID = 1396;
    private static final int _LOOP1_154_ID = 1397;
    private static final int _LOOP0_155_ID = 1398;
    private static final int _LOOP1_156_ID = 1399;
    private static final int _LOOP0_157_ID = 1400;
    private static final int _LOOP1_158_ID = 1401;
    private static final int _LOOP0_159_ID = 1402;
    private static final int _TMP_160_ID = 1403;
    private static final int _LOOP1_161_ID = 1404;
    private static final int _TMP_162_ID = 1405;
    private static final int _TMP_163_ID = 1406;
    private static final int _TMP_164_ID = 1407;
    private static final int _TMP_165_ID = 1408;
    private static final int _LOOP0_166_ID = 1409;
    private static final int _LOOP0_167_ID = 1410;
    private static final int _LOOP1_168_ID = 1411;
    private static final int _TMP_169_ID = 1412;
    private static final int _TMP_170_ID = 1413;
    private static final int _TMP_171_ID = 1414;
    private static final int _LOOP0_173_ID = 1415;
    private static final int _GATHER_172_ID = 1416;
    private static final int _TMP_174_ID = 1417;
    private static final int _LOOP1_175_ID = 1418;
    private static final int _LOOP0_176_ID = 1419;
    private static final int _LOOP0_177_ID = 1420;
    private static final int _TMP_178_ID = 1421;
    private static final int _TMP_179_ID = 1422;
    private static final int _LOOP0_181_ID = 1423;
    private static final int _GATHER_180_ID = 1424;
    private static final int _TMP_182_ID = 1425;
    private static final int _LOOP0_184_ID = 1426;
    private static final int _GATHER_183_ID = 1427;
    private static final int _LOOP0_186_ID = 1428;
    private static final int _GATHER_185_ID = 1429;
    private static final int _LOOP0_188_ID = 1430;
    private static final int _GATHER_187_ID = 1431;
    private static final int _LOOP0_190_ID = 1432;
    private static final int _GATHER_189_ID = 1433;
    private static final int _LOOP0_191_ID = 1434;
    private static final int _TMP_192_ID = 1435;
    private static final int _LOOP0_194_ID = 1436;
    private static final int _GATHER_193_ID = 1437;
    private static final int _TMP_195_ID = 1438;
    private static final int _LOOP1_196_ID = 1439;
    private static final int _TMP_197_ID = 1440;
    private static final int _TMP_198_ID = 1441;
    private static final int _TMP_199_ID = 1442;
    private static final int _TMP_200_ID = 1443;
    private static final int _TMP_201_ID = 1444;
    private static final int _LOOP0_203_ID = 1445;
    private static final int _GATHER_202_ID = 1446;
    private static final int _TMP_204_ID = 1447;
    private static final int _TMP_205_ID = 1448;
    private static final int _TMP_206_ID = 1449;
    private static final int _LOOP0_208_ID = 1450;
    private static final int _GATHER_207_ID = 1451;
    private static final int _LOOP0_210_ID = 1452;
    private static final int _GATHER_209_ID = 1453;
    private static final int _LOOP0_212_ID = 1454;
    private static final int _GATHER_211_ID = 1455;
    private static final int _LOOP0_214_ID = 1456;
    private static final int _GATHER_213_ID = 1457;
    private static final int _TMP_215_ID = 1458;
    private static final int _TMP_216_ID = 1459;
    private static final int _LOOP0_218_ID = 1460;
    private static final int _GATHER_217_ID = 1461;
    private static final int _TMP_219_ID = 1462;
    private static final int _TMP_220_ID = 1463;
    private static final int _TMP_221_ID = 1464;
    private static final int _TMP_222_ID = 1465;
    private static final int _TMP_223_ID = 1466;
    private static final int _TMP_224_ID = 1467;
    private static final int _TMP_225_ID = 1468;
    private static final int _TMP_226_ID = 1469;
    private static final int _TMP_227_ID = 1470;
    private static final int _TMP_228_ID = 1471;
    private static final int _TMP_229_ID = 1472;
    private static final int _LOOP0_230_ID = 1473;
    private static final int _LOOP0_231_ID = 1474;
    private static final int _LOOP0_232_ID = 1475;
    private static final int _TMP_233_ID = 1476;
    private static final int _TMP_234_ID = 1477;
    private static final int _TMP_235_ID = 1478;
    private static final int _TMP_236_ID = 1479;
    private static final int _TMP_237_ID = 1480;
    private static final int _LOOP0_238_ID = 1481;
    private static final int _LOOP0_239_ID = 1482;
    private static final int _LOOP0_240_ID = 1483;
    private static final int _LOOP1_241_ID = 1484;
    private static final int _TMP_242_ID = 1485;
    private static final int _LOOP0_243_ID = 1486;
    private static final int _TMP_244_ID = 1487;
    private static final int _LOOP0_245_ID = 1488;
    private static final int _LOOP1_246_ID = 1489;
    private static final int _TMP_247_ID = 1490;
    private static final int _TMP_248_ID = 1491;
    private static final int _TMP_249_ID = 1492;
    private static final int _LOOP0_250_ID = 1493;
    private static final int _TMP_251_ID = 1494;
    private static final int _TMP_252_ID = 1495;
    private static final int _LOOP1_253_ID = 1496;
    private static final int _TMP_254_ID = 1497;
    private static final int _LOOP0_255_ID = 1498;
    private static final int _LOOP0_256_ID = 1499;
    private static final int _LOOP0_257_ID = 1500;
    private static final int _LOOP0_259_ID = 1501;
    private static final int _GATHER_258_ID = 1502;
    private static final int _TMP_260_ID = 1503;
    private static final int _LOOP0_261_ID = 1504;
    private static final int _TMP_262_ID = 1505;
    private static final int _LOOP0_263_ID = 1506;
    private static final int _LOOP1_264_ID = 1507;
    private static final int _LOOP1_265_ID = 1508;
    private static final int _TMP_266_ID = 1509;
    private static final int _TMP_267_ID = 1510;
    private static final int _LOOP0_268_ID = 1511;
    private static final int _TMP_269_ID = 1512;
    private static final int _TMP_270_ID = 1513;
    private static final int _TMP_271_ID = 1514;
    private static final int _LOOP0_273_ID = 1515;
    private static final int _GATHER_272_ID = 1516;
    private static final int _TMP_274_ID = 1517;
    private static final int _LOOP0_276_ID = 1518;
    private static final int _GATHER_275_ID = 1519;
    private static final int _TMP_277_ID = 1520;
    private static final int _LOOP0_279_ID = 1521;
    private static final int _GATHER_278_ID = 1522;
    private static final int _TMP_280_ID = 1523;
    private static final int _LOOP0_282_ID = 1524;
    private static final int _GATHER_281_ID = 1525;
    private static final int _TMP_283_ID = 1526;
    private static final int _LOOP0_285_ID = 1527;
    private static final int _GATHER_284_ID = 1528;
    private static final int _TMP_286_ID = 1529;
    private static final int _LOOP0_287_ID = 1530;
    private static final int _LOOP1_288_ID = 1531;
    private static final int _TMP_289_ID = 1532;
    private static final int _LOOP0_290_ID = 1533;
    private static final int _LOOP1_291_ID = 1534;
    private static final int _TMP_292_ID = 1535;
    private static final int _TMP_293_ID = 1536;
    private static final int _TMP_294_ID = 1537;
    private static final int _TMP_295_ID = 1538;
    private static final int _TMP_296_ID = 1539;
    private static final int _TMP_297_ID = 1540;
    private static final int _TMP_298_ID = 1541;
    private static final int _TMP_299_ID = 1542;
    private static final int _TMP_300_ID = 1543;
    private static final int _TMP_301_ID = 1544;
    private static final int _TMP_302_ID = 1545;
    private static final int _TMP_303_ID = 1546;
    private static final int _TMP_304_ID = 1547;
    private static final int _TMP_305_ID = 1548;
    private static final int _TMP_306_ID = 1549;
    private static final int _TMP_307_ID = 1550;
    private static final int _TMP_308_ID = 1551;
    private static final int _LOOP0_310_ID = 1552;
    private static final int _GATHER_309_ID = 1553;
    private static final int _TMP_311_ID = 1554;
    private static final int _TMP_312_ID = 1555;
    private static final int _TMP_313_ID = 1556;
    private static final int _TMP_314_ID = 1557;
    private static final int _TMP_315_ID = 1558;
    private static final int _TMP_316_ID = 1559;
    private static final int _TMP_317_ID = 1560;
    private static final int _TMP_318_ID = 1561;
    private static final int _TMP_319_ID = 1562;
    private static final int _TMP_320_ID = 1563;
    private static final int _TMP_321_ID = 1564;
    private static final int _TMP_322_ID = 1565;
    private static final int _TMP_323_ID = 1566;
    private static final int _TMP_324_ID = 1567;
    private static final int _LOOP0_325_ID = 1568;
    private static final int _TMP_326_ID = 1569;
    private static final int _TMP_327_ID = 1570;
    private static final int _TMP_328_ID = 1571;
    private static final int _TMP_329_ID = 1572;
    private static final int _TMP_330_ID = 1573;
    private static final int _TMP_331_ID = 1574;
    private static final int _TMP_332_ID = 1575;
    private static final int _TMP_333_ID = 1576;
    private static final int _TMP_334_ID = 1577;
    private static final int _TMP_335_ID = 1578;
    private static final int _TMP_336_ID = 1579;
    private static final int _TMP_337_ID = 1580;
    private static final int _TMP_338_ID = 1581;
    private static final int _TMP_339_ID = 1582;
    private static final int _TMP_340_ID = 1583;
    private static final int _TMP_341_ID = 1584;
    private static final int _TMP_342_ID = 1585;
    private static final int _TMP_343_ID = 1586;
    private static final int _TMP_344_ID = 1587;
    private static final int _TMP_345_ID = 1588;
    private static final int _TMP_346_ID = 1589;
    private static final int _TMP_347_ID = 1590;
    private static final int _TMP_348_ID = 1591;
    private static final int _TMP_349_ID = 1592;
    private static final int _TMP_350_ID = 1593;
    private static final int _TMP_351_ID = 1594;
    private static final int _TMP_352_ID = 1595;
    private static final int _TMP_353_ID = 1596;
    private static final int _TMP_354_ID = 1597;
    private static final int _TMP_355_ID = 1598;
    private static final int _TMP_356_ID = 1599;
    private static final int _TMP_357_ID = 1600;
    private static final int _TMP_358_ID = 1601;
    private static final int _TMP_359_ID = 1602;
    private static final int _TMP_360_ID = 1603;
    private static final int _LOOP0_362_ID = 1604;
    private static final int _GATHER_361_ID = 1605;
    private static final int _TMP_363_ID = 1606;
    private static final int _TMP_364_ID = 1607;
    private static final int _TMP_365_ID = 1608;
    private static final int _TMP_366_ID = 1609;
    private static final int _TMP_367_ID = 1610;
    private static final int _TMP_368_ID = 1611;

    public Parser(String source, SourceRange sourceRange, ParserCallbacks parserCb, InputType startRule, EnumSet<Flags> flags, int featureVersion) {
        super(source, sourceRange, parserCb, startRule, flags, featureVersion);
    }

    public Parser(String source, ParserCallbacks parserCb, InputType startRule, EnumSet<Flags> flags, int featureVersion) {
        super(source, null, parserCb, startRule, flags, featureVersion);
    }

    // file: statements? $
    public ModTy file_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // statements? $
            if (errorIndicator) {
                return null;
            }
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
                _res = makeModule(a, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ModTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ModTy)_res;
    }

    // interactive: statement_newline
    public ModTy interactive_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // statement_newline
            if (errorIndicator) {
                return null;
            }
            StmtTy[] a;
            if (
                (a = statement_newline_rule()) != null  // statement_newline
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createInteractiveModule(a, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ModTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ModTy)_res;
    }

    // eval: expressions NEWLINE* $
    public ModTy eval_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // expressions NEWLINE* $
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createExpressionModule(a, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ModTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ModTy)_res;
    }

    // func_type: '(' type_expressions? ')' '->' expression NEWLINE* $
    public ModTy func_type_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '(' type_expressions? ')' '->' expression NEWLINE* $
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createFunctionType(a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ModTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ModTy)_res;
    }

    // statements: statement+
    public StmtTy[] statements_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // statement+
            if (errorIndicator) {
                return null;
            }
            StmtTy[] a;
            if (
                (a = _loop1_5_rule()) != null  // statement+
            )
            {
                _res = a;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // statement: compound_stmt | simple_stmts
    public StmtTy[] statement_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // compound_stmt
            if (errorIndicator) {
                return null;
            }
            StmtTy a;
            if (
                (a = compound_stmt_rule()) != null  // compound_stmt
            )
            {
                _res = new StmtTy[] {a};
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // simple_stmts
            if (errorIndicator) {
                return null;
            }
            StmtTy[] a;
            if (
                (a = (StmtTy[])simple_stmts_rule()) != null  // simple_stmts
            )
            {
                _res = a;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // statement_newline: compound_stmt NEWLINE | simple_stmts | NEWLINE | $
    public StmtTy[] statement_newline_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // compound_stmt NEWLINE
            if (errorIndicator) {
                return null;
            }
            StmtTy a;
            Token newline_var;
            if (
                (a = compound_stmt_rule()) != null  // compound_stmt
                &&
                (newline_var = expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = new StmtTy[] {a};
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // simple_stmts
            if (errorIndicator) {
                return null;
            }
            StmtTy[] simple_stmts_var;
            if (
                (simple_stmts_var = simple_stmts_rule()) != null  // simple_stmts
            )
            {
                _res = simple_stmts_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // NEWLINE
            if (errorIndicator) {
                return null;
            }
            Token newline_var;
            if (
                (newline_var = expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = new StmtTy[] {factory.createPass(startToken.sourceRange.withEnd(endToken.sourceRange))};
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // $
            if (errorIndicator) {
                return null;
            }
            Token endmarker_var;
            if (
                (endmarker_var = expect(Token.Kind.ENDMARKER)) != null  // token='ENDMARKER'
            )
            {
                _res = interactiveExit();
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // simple_stmts: simple_stmt !';' NEWLINE | ';'.simple_stmt+ ';'? NEWLINE
    public StmtTy[] simple_stmts_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // simple_stmt !';' NEWLINE
            if (errorIndicator) {
                return null;
            }
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
                _res = new StmtTy[] {a};
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // ';'.simple_stmt+ ';'? NEWLINE
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            StmtTy[] a;
            Token newline_var;
            if (
                (a = (StmtTy[])_gather_6_rule()) != null  // ';'.simple_stmt+
                &&
                ((_opt_var = _tmp_8_rule()) != null || true)  // ';'?
                &&
                (newline_var = expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = a;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // simple_stmt:
    //     | assignment
    //     | &"type" type_alias
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, SIMPLE_STMT_ID)) {
            _res = (StmtTy)cache.getResult(_mark, SIMPLE_STMT_ID);
            return (StmtTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // assignment
            if (errorIndicator) {
                return null;
            }
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
        { // &"type" type_alias
            if (errorIndicator) {
                return null;
            }
            StmtTy type_alias_var;
            if (
                genLookahead_expect_SOFT_KEYWORD(true, "type")
                &&
                (type_alias_var = type_alias_rule()) != null  // type_alias
            )
            {
                _res = type_alias_var;
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy e;
            if (
                (e = star_expressions_rule()) != null  // star_expressions
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createExpression(e, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'return' return_stmt
            if (errorIndicator) {
                return null;
            }
            StmtTy return_stmt_var;
            if (
                genLookahead_expect(true, 655)  // token='return'
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
            if (errorIndicator) {
                return null;
            }
            StmtTy import_stmt_var;
            if (
                genLookahead__tmp_9_rule(true)
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
            if (errorIndicator) {
                return null;
            }
            StmtTy raise_stmt_var;
            if (
                genLookahead_expect(true, 658)  // token='raise'
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
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(659)) != null  // token='pass'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createPass(startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'del' del_stmt
            if (errorIndicator) {
                return null;
            }
            StmtTy del_stmt_var;
            if (
                genLookahead_expect(true, 660)  // token='del'
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
            if (errorIndicator) {
                return null;
            }
            StmtTy yield_stmt_var;
            if (
                genLookahead_expect(true, 661)  // token='yield'
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
            if (errorIndicator) {
                return null;
            }
            StmtTy assert_stmt_var;
            if (
                genLookahead_expect(true, 662)  // token='assert'
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
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(663)) != null  // token='break'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBreak(startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'continue'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(664)) != null  // token='continue'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createContinue(startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, SIMPLE_STMT_ID, _res);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'global' global_stmt
            if (errorIndicator) {
                return null;
            }
            StmtTy global_stmt_var;
            if (
                genLookahead_expect(true, 665)  // token='global'
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
            if (errorIndicator) {
                return null;
            }
            StmtTy nonlocal_stmt_var;
            if (
                genLookahead_expect(true, 666)  // token='nonlocal'
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // &('def' | '@' | ASYNC) function_def
            if (errorIndicator) {
                return null;
            }
            StmtTy function_def_var;
            if (
                genLookahead__tmp_10_rule(true)
                &&
                (function_def_var = function_def_rule()) != null  // function_def
            )
            {
                _res = function_def_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'if' if_stmt
            if (errorIndicator) {
                return null;
            }
            StmtTy if_stmt_var;
            if (
                genLookahead_expect(true, 668)  // token='if'
                &&
                (if_stmt_var = if_stmt_rule()) != null  // if_stmt
            )
            {
                _res = if_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &('class' | '@') class_def
            if (errorIndicator) {
                return null;
            }
            StmtTy class_def_var;
            if (
                genLookahead__tmp_11_rule(true)
                &&
                (class_def_var = class_def_rule()) != null  // class_def
            )
            {
                _res = class_def_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &('with' | ASYNC) with_stmt
            if (errorIndicator) {
                return null;
            }
            StmtTy with_stmt_var;
            if (
                genLookahead__tmp_12_rule(true)
                &&
                (with_stmt_var = with_stmt_rule()) != null  // with_stmt
            )
            {
                _res = with_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &('for' | ASYNC) for_stmt
            if (errorIndicator) {
                return null;
            }
            StmtTy for_stmt_var;
            if (
                genLookahead__tmp_13_rule(true)
                &&
                (for_stmt_var = for_stmt_rule()) != null  // for_stmt
            )
            {
                _res = for_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'try' try_stmt
            if (errorIndicator) {
                return null;
            }
            StmtTy try_stmt_var;
            if (
                genLookahead_expect(true, 672)  // token='try'
                &&
                (try_stmt_var = try_stmt_rule()) != null  // try_stmt
            )
            {
                _res = try_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // &'while' while_stmt
            if (errorIndicator) {
                return null;
            }
            StmtTy while_stmt_var;
            if (
                genLookahead_expect(true, 673)  // token='while'
                &&
                (while_stmt_var = while_stmt_rule()) != null  // while_stmt
            )
            {
                _res = while_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // match_stmt
            if (errorIndicator) {
                return null;
            }
            StmtTy match_stmt_var;
            if (
                (match_stmt_var = match_stmt_rule()) != null  // match_stmt
            )
            {
                _res = match_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // NAME ':' expression ['=' annotated_rhs]
            if (errorIndicator) {
                return null;
            }
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
                ((c = _tmp_14_rule()) != null || true)  // ['=' annotated_rhs]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = checkVersion(6, "Variable annotation syntax is", factory.createAnnAssignment(setExprContext(a, ExprContextTy.Store), b, (ExprTy) c, true, startToken.sourceRange.withEnd(endToken.sourceRange)));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // ('(' single_target ')' | single_subscript_attribute_target) ':' expression ['=' annotated_rhs]
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            ExprTy b;
            ExprTy c;
            if (
                (a = _tmp_15_rule()) != null  // '(' single_target ')' | single_subscript_attribute_target
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = expression_rule()) != null  // expression
                &&
                ((c = _tmp_16_rule()) != null || true)  // ['=' annotated_rhs]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = checkVersion(6, "Variable annotations syntax is", factory.createAnnAssignment(a, b, (ExprTy) c, false, startToken.sourceRange.withEnd(endToken.sourceRange)));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // ((star_targets '='))+ (yield_expr | star_expressions) !'=' TYPE_COMMENT?
            if (errorIndicator) {
                return null;
            }
            ExprTy[] a;
            ExprTy b;
            Token tc;
            if (
                (a = (ExprTy[])_loop1_17_rule()) != null  // ((star_targets '='))+
                &&
                (b = _tmp_18_rule()) != null  // yield_expr | star_expressions
                &&
                genLookahead_expect(false, 22)  // token='='
                &&
                ((tc = _tmp_19_rule()) != null || true)  // TYPE_COMMENT?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAssignment(a, (ExprTy) b, newTypeComment((Token) tc), startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // single_target augassign ~ (yield_expr | star_expressions)
            if (errorIndicator) {
                return null;
            }
            int _cut_var = 0;
            ExprTy a;
            OperatorTy b;
            ExprTy c;
            if (
                (a = single_target_rule()) != null  // single_target
                &&
                (b = augassign_rule()) != null  // augassign
                &&
                (_cut_var = 1) != 0
                &&
                (c = _tmp_20_rule()) != null  // yield_expr | star_expressions
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAugAssignment(a, b, (ExprTy) c, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        if (callInvalidRules) { // invalid_assignment
            if (errorIndicator) {
                return null;
            }
            Object invalid_assignment_var;
            if (
                (invalid_assignment_var = invalid_assignment_rule()) != null  // invalid_assignment
            )
            {
                _res = invalid_assignment_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // annotated_rhs: yield_expr | star_expressions
    public ExprTy annotated_rhs_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
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
    public OperatorTy augassign_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '+='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(36)) != null  // token='+='
            )
            {
                _res = OperatorTy.Add;
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        { // '-='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(37)) != null  // token='-='
            )
            {
                _res = OperatorTy.Sub;
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        { // '*='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(38)) != null  // token='*='
            )
            {
                _res = OperatorTy.Mult;
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        { // '@='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(50)) != null  // token='@='
            )
            {
                _res = checkVersion(5, "The '@' operator is", OperatorTy.MatMult);
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        { // '/='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(39)) != null  // token='/='
            )
            {
                _res = OperatorTy.Div;
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        { // '%='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(40)) != null  // token='%='
            )
            {
                _res = OperatorTy.Mod;
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        { // '&='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(41)) != null  // token='&='
            )
            {
                _res = OperatorTy.BitAnd;
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        { // '|='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(42)) != null  // token='|='
            )
            {
                _res = OperatorTy.BitOr;
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        { // '^='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(43)) != null  // token='^='
            )
            {
                _res = OperatorTy.BitXor;
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        { // '<<='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(44)) != null  // token='<<='
            )
            {
                _res = OperatorTy.LShift;
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        { // '>>='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(45)) != null  // token='>>='
            )
            {
                _res = OperatorTy.RShift;
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        { // '**='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(46)) != null  // token='**='
            )
            {
                _res = OperatorTy.Pow;
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        { // '//='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(48)) != null  // token='//='
            )
            {
                _res = OperatorTy.FloorDiv;
                return (OperatorTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (OperatorTy)_res;
    }

    // return_stmt: 'return' star_expressions?
    public StmtTy return_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // 'return' star_expressions?
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy a;
            if (
                (_keyword = expect(655)) != null  // token='return'
                &&
                ((a = _tmp_21_rule()) != null || true)  // star_expressions?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createReturn(a, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // raise_stmt: 'raise' expression ['from' expression] | 'raise'
    public StmtTy raise_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // 'raise' expression ['from' expression]
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy a;
            ExprTy b;
            if (
                (_keyword = expect(658)) != null  // token='raise'
                &&
                (a = expression_rule()) != null  // expression
                &&
                ((b = _tmp_22_rule()) != null || true)  // ['from' expression]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createRaise(a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'raise'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(658)) != null  // token='raise'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createRaise(null, null, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // global_stmt: 'global' ','.NAME+
    public StmtTy global_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // 'global' ','.NAME+
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy[] a;
            if (
                (_keyword = expect(665)) != null  // token='global'
                &&
                (a = (ExprTy[])_gather_23_rule()) != null  // ','.NAME+
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createGlobal(extractNames(a), startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // nonlocal_stmt: 'nonlocal' ','.NAME+
    public StmtTy nonlocal_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // 'nonlocal' ','.NAME+
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy[] a;
            if (
                (_keyword = expect(666)) != null  // token='nonlocal'
                &&
                (a = (ExprTy[])_gather_25_rule()) != null  // ','.NAME+
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createNonLocal(extractNames(a), startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // del_stmt: 'del' del_targets &(';' | NEWLINE) | invalid_del_stmt
    public StmtTy del_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // 'del' del_targets &(';' | NEWLINE)
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy[] a;
            if (
                (_keyword = expect(660)) != null  // token='del'
                &&
                (a = del_targets_rule()) != null  // del_targets
                &&
                genLookahead__tmp_27_rule(true)
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createDelete(a, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_del_stmt
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_del_stmt_var;
            if (
                (invalid_del_stmt_var = invalid_del_stmt_rule()) != null  // invalid_del_stmt
            )
            {
                _res = invalid_del_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // yield_stmt: yield_expr
    public StmtTy yield_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy y;
            if (
                (y = yield_expr_rule()) != null  // yield_expr
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createExpression(y, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // assert_stmt: 'assert' expression [',' expression]
    public StmtTy assert_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // 'assert' expression [',' expression]
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy a;
            ExprTy b;
            if (
                (_keyword = expect(662)) != null  // token='assert'
                &&
                (a = expression_rule()) != null  // expression
                &&
                ((b = _tmp_28_rule()) != null || true)  // [',' expression]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAssert(a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // import_stmt: invalid_import | import_name | import_from
    public StmtTy import_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (callInvalidRules) { // invalid_import
            if (errorIndicator) {
                return null;
            }
            ExprTy[] invalid_import_var;
            if (
                (invalid_import_var = invalid_import_rule()) != null  // invalid_import
            )
            {
                _res = invalid_import_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // import_name
            if (errorIndicator) {
                return null;
            }
            StmtTy import_name_var;
            if (
                (import_name_var = import_name_rule()) != null  // import_name
            )
            {
                _res = import_name_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // import_from
            if (errorIndicator) {
                return null;
            }
            StmtTy import_from_var;
            if (
                (import_from_var = import_from_rule()) != null  // import_from
            )
            {
                _res = import_from_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // import_name: 'import' dotted_as_names
    public StmtTy import_name_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // 'import' dotted_as_names
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            AliasTy[] a;
            if (
                (_keyword = expect(656)) != null  // token='import'
                &&
                (a = dotted_as_names_rule()) != null  // dotted_as_names
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createImport(a, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // import_from:
    //     | 'from' (('.' | '...'))* dotted_name 'import' import_from_targets
    //     | 'from' (('.' | '...'))+ 'import' import_from_targets
    public StmtTy import_from_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // 'from' (('.' | '...'))* dotted_name 'import' import_from_targets
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _keyword_1;
            Token[] a;
            ExprTy b;
            AliasTy[] c;
            if (
                (_keyword = expect(657)) != null  // token='from'
                &&
                (a = _loop0_29_rule()) != null  // (('.' | '...'))*
                &&
                (b = dotted_name_rule()) != null  // dotted_name
                &&
                (_keyword_1 = expect(656)) != null  // token='import'
                &&
                (c = import_from_targets_rule()) != null  // import_from_targets
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createImportFrom(((ExprTy.Name) b).id, c, countDots(a), startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'from' (('.' | '...'))+ 'import' import_from_targets
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _keyword_1;
            Token[] a;
            AliasTy[] b;
            if (
                (_keyword = expect(657)) != null  // token='from'
                &&
                (a = _loop1_30_rule()) != null  // (('.' | '...'))+
                &&
                (_keyword_1 = expect(656)) != null  // token='import'
                &&
                (b = import_from_targets_rule()) != null  // import_from_targets
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createImportFrom(null, b, countDots(a), startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // import_from_targets:
    //     | '(' import_from_as_names ','? ')'
    //     | import_from_as_names !','
    //     | '*'
    //     | invalid_import_from_targets
    public AliasTy[] import_from_targets_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '(' import_from_as_names ','? ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            Token _opt_var;
            AliasTy[] a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (a = import_from_as_names_rule()) != null  // import_from_as_names
                &&
                ((_opt_var = _tmp_31_rule()) != null || true)  // ','?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                _res = a;
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        { // import_from_as_names !','
            if (errorIndicator) {
                return null;
            }
            AliasTy[] import_from_as_names_var;
            if (
                (import_from_as_names_var = import_from_as_names_rule()) != null  // import_from_as_names
                &&
                genLookahead_expect(false, 12)  // token=','
            )
            {
                _res = import_from_as_names_var;
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        { // '*'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(16)) != null  // token='*'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = new AliasTy [] {factory.createAlias("*", null, startToken.sourceRange.withEnd(endToken.sourceRange))};
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_import_from_targets
            if (errorIndicator) {
                return null;
            }
            AliasTy[] invalid_import_from_targets_var;
            if (
                (invalid_import_from_targets_var = invalid_import_from_targets_rule()) != null  // invalid_import_from_targets
            )
            {
                _res = invalid_import_from_targets_var;
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (AliasTy[])_res;
    }

    // import_from_as_names: ','.import_from_as_name+
    public AliasTy[] import_from_as_names_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.import_from_as_name+
            if (errorIndicator) {
                return null;
            }
            AliasTy[] a;
            if (
                (a = (AliasTy[])_gather_32_rule()) != null  // ','.import_from_as_name+
            )
            {
                _res = a;
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (AliasTy[])_res;
    }

    // import_from_as_name: NAME ['as' NAME]
    public AliasTy import_from_as_name_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // NAME ['as' NAME]
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            ExprTy b;
            if (
                (a = name_token()) != null  // NAME
                &&
                ((b = _tmp_34_rule()) != null || true)  // ['as' NAME]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAlias(((ExprTy.Name) a).id, b == null ? null : ((ExprTy.Name) b).id, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (AliasTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (AliasTy)_res;
    }

    // dotted_as_names: ','.dotted_as_name+
    public AliasTy[] dotted_as_names_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.dotted_as_name+
            if (errorIndicator) {
                return null;
            }
            AliasTy[] a;
            if (
                (a = (AliasTy[])_gather_35_rule()) != null  // ','.dotted_as_name+
            )
            {
                _res = a;
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (AliasTy[])_res;
    }

    // dotted_as_name: dotted_name ['as' NAME]
    public AliasTy dotted_as_name_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // dotted_name ['as' NAME]
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            ExprTy b;
            if (
                (a = dotted_name_rule()) != null  // dotted_name
                &&
                ((b = _tmp_37_rule()) != null || true)  // ['as' NAME]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAlias(((ExprTy.Name) a).id, b == null ? null : ((ExprTy.Name) b).id, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (AliasTy)_res;
            }
            reset(_mark);
        }
        _res = null;
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // dotted_name '.' NAME
            if (errorIndicator) {
                return null;
            }
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
            if (errorIndicator) {
                return null;
            }
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

    // block: NEWLINE INDENT statements DEDENT | simple_stmts | invalid_block
    public StmtTy[] block_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, BLOCK_ID)) {
            _res = (StmtTy[])cache.getResult(_mark, BLOCK_ID);
            return (StmtTy[])_res;
        }
        { // NEWLINE INDENT statements DEDENT
            if (errorIndicator) {
                return null;
            }
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
            if (errorIndicator) {
                return null;
            }
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
            if (errorIndicator) {
                return null;
            }
            Object invalid_block_var;
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

    // decorators: (('@' named_expression NEWLINE))+
    public ExprTy[] decorators_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // (('@' named_expression NEWLINE))+
            if (errorIndicator) {
                return null;
            }
            ExprTy[] a;
            if (
                (a = (ExprTy[])_loop1_38_rule()) != null  // (('@' named_expression NEWLINE))+
            )
            {
                _res = a;
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // class_def: decorators class_def_raw | class_def_raw
    public StmtTy class_def_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // decorators class_def_raw
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createClassDef(b, a, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // class_def_raw
            if (errorIndicator) {
                return null;
            }
            StmtTy class_def_raw_var;
            if (
                (class_def_raw_var = class_def_raw_rule()) != null  // class_def_raw
            )
            {
                _res = class_def_raw_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // class_def_raw:
    //     | invalid_class_def_raw
    //     | 'class' NAME type_params? ['(' arguments? ')'] ':' block
    public StmtTy class_def_raw_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_class_def_raw
            if (errorIndicator) {
                return null;
            }
            Object invalid_class_def_raw_var;
            if (
                (invalid_class_def_raw_var = invalid_class_def_raw_rule()) != null  // invalid_class_def_raw
            )
            {
                _res = invalid_class_def_raw_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'class' NAME type_params? ['(' arguments? ')'] ':' block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            ExprTy a;
            ExprTy b;
            StmtTy[] c;
            TypeParamTy[] t;
            if (
                (_keyword = expect(669)) != null  // token='class'
                &&
                (a = name_token()) != null  // NAME
                &&
                ((t = _tmp_39_rule()) != null || true)  // type_params?
                &&
                ((b = _tmp_40_rule()) != null || true)  // ['(' arguments? ')']
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (c = block_rule()) != null  // block
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createClassDef(a, b, c, t, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // function_def: decorators function_def_raw | function_def_raw
    public StmtTy function_def_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // decorators function_def_raw
            if (errorIndicator) {
                return null;
            }
            ExprTy[] d;
            StmtTy f;
            if (
                (d = decorators_rule()) != null  // decorators
                &&
                (f = function_def_raw_rule()) != null  // function_def_raw
            )
            {
                _res = factory.createFunctionDefWithDecorators(f,d);
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // function_def_raw
            if (errorIndicator) {
                return null;
            }
            StmtTy function_def_raw_var;
            if (
                (function_def_raw_var = function_def_raw_rule()) != null  // function_def_raw
            )
            {
                _res = function_def_raw_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // function_def_raw:
    //     | invalid_def_raw
    //     | 'def' NAME type_params? &&'(' params? ')' ['->' expression] &&':' func_type_comment? block
    //     | ASYNC 'def' NAME type_params? &&'(' params? ')' ['->' expression] &&':' func_type_comment? block
    public StmtTy function_def_raw_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_def_raw
            if (errorIndicator) {
                return null;
            }
            Object invalid_def_raw_var;
            if (
                (invalid_def_raw_var = invalid_def_raw_rule()) != null  // invalid_def_raw
            )
            {
                _res = invalid_def_raw_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'def' NAME type_params? &&'(' params? ')' ['->' expression] &&':' func_type_comment? block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            ExprTy a;
            StmtTy[] b;
            ExprTy n;
            ArgumentsTy params;
            TypeParamTy[] t;
            Token tc;
            if (
                (_keyword = expect(667)) != null  // token='def'
                &&
                (n = name_token()) != null  // NAME
                &&
                ((t = _tmp_41_rule()) != null || true)  // type_params?
                &&
                (_literal = expect_forced_token(7, "(")) != null  // forced_token='('
                &&
                ((params = _tmp_42_rule()) != null || true)  // params?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
                &&
                ((a = _tmp_43_rule()) != null || true)  // ['->' expression]
                &&
                (_literal_2 = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                ((tc = _tmp_44_rule()) != null || true)  // func_type_comment?
                &&
                (b = block_rule()) != null  // block
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createFunctionDef(((ExprTy.Name) n).id, params == null ? factory.emptyArguments() : params, b, (ExprTy) a, newTypeComment((Token) tc), t, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // ASYNC 'def' NAME type_params? &&'(' params? ')' ['->' expression] &&':' func_type_comment? block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            ExprTy a;
            Token async_var;
            StmtTy[] b;
            ExprTy n;
            ArgumentsTy params;
            TypeParamTy[] t;
            Token tc;
            if (
                (async_var = expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
                &&
                (_keyword = expect(667)) != null  // token='def'
                &&
                (n = name_token()) != null  // NAME
                &&
                ((t = _tmp_45_rule()) != null || true)  // type_params?
                &&
                (_literal = expect_forced_token(7, "(")) != null  // forced_token='('
                &&
                ((params = _tmp_46_rule()) != null || true)  // params?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
                &&
                ((a = _tmp_47_rule()) != null || true)  // ['->' expression]
                &&
                (_literal_2 = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                ((tc = _tmp_48_rule()) != null || true)  // func_type_comment?
                &&
                (b = block_rule()) != null  // block
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = checkVersion(5, "Async functions are", factory.createAsyncFunctionDef(((ExprTy.Name) n).id, params == null ? factory.emptyArguments() : params, b, a, newTypeComment((Token) tc), t, startToken.sourceRange.withEnd(endToken.sourceRange)));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // params: invalid_parameters | parameters
    public ArgumentsTy params_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (callInvalidRules) { // invalid_parameters
            if (errorIndicator) {
                return null;
            }
            Object invalid_parameters_var;
            if (
                (invalid_parameters_var = invalid_parameters_rule()) != null  // invalid_parameters
            )
            {
                _res = invalid_parameters_var;
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // parameters
            if (errorIndicator) {
                return null;
            }
            ArgumentsTy parameters_var;
            if (
                (parameters_var = parameters_rule()) != null  // parameters
            )
            {
                _res = parameters_var;
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // slash_no_default param_no_default* param_with_default* star_etc?
            if (errorIndicator) {
                return null;
            }
            ArgTy[] a;
            ArgTy[] b;
            NameDefaultPair[] c;
            StarEtc d;
            if (
                (a = slash_no_default_rule()) != null  // slash_no_default
                &&
                (b = (ArgTy[])_loop0_49_rule()) != null  // param_no_default*
                &&
                (c = _loop0_50_rule()) != null  // param_with_default*
                &&
                ((d = _tmp_51_rule()) != null || true)  // star_etc?
            )
            {
                _res = checkVersion(8, "Positional-only parameters are", factory.createArguments(a, null, b, c, d));
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // slash_with_default param_with_default* star_etc?
            if (errorIndicator) {
                return null;
            }
            SlashWithDefault a;
            NameDefaultPair[] b;
            StarEtc c;
            if (
                (a = slash_with_default_rule()) != null  // slash_with_default
                &&
                (b = _loop0_52_rule()) != null  // param_with_default*
                &&
                ((c = _tmp_53_rule()) != null || true)  // star_etc?
            )
            {
                _res = checkVersion(8, "Positional-only parameters are", factory.createArguments(null, a, null, b, c));
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // param_no_default+ param_with_default* star_etc?
            if (errorIndicator) {
                return null;
            }
            ArgTy[] a;
            NameDefaultPair[] b;
            StarEtc c;
            if (
                (a = (ArgTy[])_loop1_54_rule()) != null  // param_no_default+
                &&
                (b = _loop0_55_rule()) != null  // param_with_default*
                &&
                ((c = _tmp_56_rule()) != null || true)  // star_etc?
            )
            {
                _res = factory.createArguments(null, null, a, b, c);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // param_with_default+ star_etc?
            if (errorIndicator) {
                return null;
            }
            NameDefaultPair[] a;
            StarEtc b;
            if (
                (a = _loop1_57_rule()) != null  // param_with_default+
                &&
                ((b = _tmp_58_rule()) != null || true)  // star_etc?
            )
            {
                _res = factory.createArguments(null, null, null, a, b);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // star_etc
            if (errorIndicator) {
                return null;
            }
            StarEtc a;
            if (
                (a = star_etc_rule()) != null  // star_etc
            )
            {
                _res = factory.createArguments(null, null, null, null, a);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgumentsTy)_res;
    }

    // slash_no_default: param_no_default+ '/' ',' | param_no_default+ '/' &')'
    public ArgTy[] slash_no_default_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // param_no_default+ '/' ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ArgTy[] a;
            if (
                (a = (ArgTy[])_loop1_59_rule()) != null  // param_no_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                (_literal_1 = expect(12)) != null  // token=','
            )
            {
                _res = a;
                return (ArgTy[])_res;
            }
            reset(_mark);
        }
        { // param_no_default+ '/' &')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy[] a;
            if (
                (a = (ArgTy[])_loop1_60_rule()) != null  // param_no_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                genLookahead_expect(true, 8)  // token=')'
            )
            {
                _res = a;
                return (ArgTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy[])_res;
    }

    // slash_with_default:
    //     | param_no_default* param_with_default+ '/' ','
    //     | param_no_default* param_with_default+ '/' &')'
    public SlashWithDefault slash_with_default_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // param_no_default* param_with_default+ '/' ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ArgTy[] a;
            NameDefaultPair[] b;
            if (
                (a = _loop0_61_rule()) != null  // param_no_default*
                &&
                (b = _loop1_62_rule()) != null  // param_with_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                (_literal_1 = expect(12)) != null  // token=','
            )
            {
                _res = new SlashWithDefault(a,b);
                return (SlashWithDefault)_res;
            }
            reset(_mark);
        }
        { // param_no_default* param_with_default+ '/' &')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy[] a;
            NameDefaultPair[] b;
            if (
                (a = _loop0_63_rule()) != null  // param_no_default*
                &&
                (b = _loop1_64_rule()) != null  // param_with_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                genLookahead_expect(true, 8)  // token=')'
            )
            {
                _res = new SlashWithDefault(a,b);
                return (SlashWithDefault)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (SlashWithDefault)_res;
    }

    // star_etc:
    //     | invalid_star_etc
    //     | '*' param_no_default param_maybe_default* kwds?
    //     | '*' param_no_default_star_annotation param_maybe_default* kwds?
    //     | '*' ',' param_maybe_default+ kwds?
    //     | kwds
    public StarEtc star_etc_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (callInvalidRules) { // invalid_star_etc
            if (errorIndicator) {
                return null;
            }
            Object invalid_star_etc_var;
            if (
                (invalid_star_etc_var = invalid_star_etc_rule()) != null  // invalid_star_etc
            )
            {
                _res = invalid_star_etc_var;
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        { // '*' param_no_default param_maybe_default* kwds?
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy a;
            NameDefaultPair[] b;
            ArgTy c;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = param_no_default_rule()) != null  // param_no_default
                &&
                (b = _loop0_65_rule()) != null  // param_maybe_default*
                &&
                ((c = _tmp_66_rule()) != null || true)  // kwds?
            )
            {
                _res = new StarEtc(a,b,c);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        { // '*' param_no_default_star_annotation param_maybe_default* kwds?
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy a;
            NameDefaultPair[] b;
            ArgTy c;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = param_no_default_star_annotation_rule()) != null  // param_no_default_star_annotation
                &&
                (b = _loop0_67_rule()) != null  // param_maybe_default*
                &&
                ((c = _tmp_68_rule()) != null || true)  // kwds?
            )
            {
                _res = new StarEtc(a,b,c);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        { // '*' ',' param_maybe_default+ kwds?
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            NameDefaultPair[] b;
            ArgTy c;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (_literal_1 = expect(12)) != null  // token=','
                &&
                (b = _loop1_69_rule()) != null  // param_maybe_default+
                &&
                ((c = _tmp_70_rule()) != null || true)  // kwds?
            )
            {
                _res = new StarEtc(null,b,c);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        { // kwds
            if (errorIndicator) {
                return null;
            }
            ArgTy a;
            if (
                (a = kwds_rule()) != null  // kwds
            )
            {
                _res = new StarEtc(null,null,a);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StarEtc)_res;
    }

    // kwds: invalid_kwds | '**' param_no_default
    public ArgTy kwds_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (callInvalidRules) { // invalid_kwds
            if (errorIndicator) {
                return null;
            }
            ArgTy invalid_kwds_var;
            if (
                (invalid_kwds_var = invalid_kwds_rule()) != null  // invalid_kwds
            )
            {
                _res = invalid_kwds_var;
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        { // '**' param_no_default
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy a;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (a = param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = a;
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // param_no_default: param ',' TYPE_COMMENT? | param TYPE_COMMENT? &')'
    public ArgTy param_no_default_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // param ',' TYPE_COMMENT?
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createArgument(a.arg, a.annotation, newTypeComment(tc), a.getSourceRange());
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        { // param TYPE_COMMENT? &')'
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createArgument(a.arg, a.annotation, newTypeComment(tc), a.getSourceRange());
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // param_no_default_star_annotation:
    //     | param_star_annotation ',' TYPE_COMMENT?
    //     | param_star_annotation TYPE_COMMENT? &')'
    public ArgTy param_no_default_star_annotation_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // param_star_annotation ',' TYPE_COMMENT?
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy a;
            Token tc;
            if (
                (a = param_star_annotation_rule()) != null  // param_star_annotation
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                ((tc = expect(Token.Kind.TYPE_COMMENT)) != null || true)  // TYPE_COMMENT?
            )
            {
                _res = factory.createArgument(a.arg, a.annotation, newTypeComment(tc), a.getSourceRange());
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        { // param_star_annotation TYPE_COMMENT? &')'
            if (errorIndicator) {
                return null;
            }
            ArgTy a;
            Token tc;
            if (
                (a = param_star_annotation_rule()) != null  // param_star_annotation
                &&
                ((tc = expect(Token.Kind.TYPE_COMMENT)) != null || true)  // TYPE_COMMENT?
                &&
                genLookahead_expect(true, 8)  // token=')'
            )
            {
                _res = factory.createArgument(a.arg, a.annotation, newTypeComment(tc), a.getSourceRange());
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // param_with_default: param default ',' TYPE_COMMENT? | param default TYPE_COMMENT? &')'
    public NameDefaultPair param_with_default_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // param default ',' TYPE_COMMENT?
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy a;
            ExprTy c;
            Token tc;
            if (
                (a = param_rule()) != null  // param
                &&
                (c = default_rule()) != null  // default
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                ((tc = expect(Token.Kind.TYPE_COMMENT)) != null || true)  // TYPE_COMMENT?
            )
            {
                _res = new NameDefaultPair(factory.createArgument(a.arg, a.annotation, newTypeComment(tc), a.getSourceRange()), c);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        { // param default TYPE_COMMENT? &')'
            if (errorIndicator) {
                return null;
            }
            ArgTy a;
            ExprTy c;
            Token tc;
            if (
                (a = param_rule()) != null  // param
                &&
                (c = default_rule()) != null  // default
                &&
                ((tc = expect(Token.Kind.TYPE_COMMENT)) != null || true)  // TYPE_COMMENT?
                &&
                genLookahead_expect(true, 8)  // token=')'
            )
            {
                _res = new NameDefaultPair(factory.createArgument(a.arg, a.annotation, newTypeComment(tc), a.getSourceRange()), c);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (NameDefaultPair)_res;
    }

    // param_maybe_default:
    //     | param default? ',' TYPE_COMMENT?
    //     | param default? TYPE_COMMENT? &')'
    public NameDefaultPair param_maybe_default_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // param default? ',' TYPE_COMMENT?
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy a;
            ExprTy c;
            Token tc;
            if (
                (a = param_rule()) != null  // param
                &&
                ((c = default_rule()) != null || true)  // default?
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                ((tc = expect(Token.Kind.TYPE_COMMENT)) != null || true)  // TYPE_COMMENT?
            )
            {
                _res = new NameDefaultPair(factory.createArgument(a.arg, a.annotation, newTypeComment(tc), a.getSourceRange()), c);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        { // param default? TYPE_COMMENT? &')'
            if (errorIndicator) {
                return null;
            }
            ArgTy a;
            ExprTy c;
            Token tc;
            if (
                (a = param_rule()) != null  // param
                &&
                ((c = default_rule()) != null || true)  // default?
                &&
                ((tc = expect(Token.Kind.TYPE_COMMENT)) != null || true)  // TYPE_COMMENT?
                &&
                genLookahead_expect(true, 8)  // token=')'
            )
            {
                _res = new NameDefaultPair(factory.createArgument(a.arg, a.annotation, newTypeComment(tc), a.getSourceRange()), c);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (NameDefaultPair)_res;
    }

    // param: NAME annotation?
    public ArgTy param_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // NAME annotation?
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            ExprTy b;
            if (
                (a = name_token()) != null  // NAME
                &&
                ((b = annotation_rule()) != null || true)  // annotation?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createArgument(((ExprTy.Name) a).id, b, null, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // param_star_annotation: NAME star_annotation
    public ArgTy param_star_annotation_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // NAME star_annotation
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            ExprTy b;
            if (
                (a = name_token()) != null  // NAME
                &&
                (b = star_annotation_rule()) != null  // star_annotation
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createArgument(((ExprTy.Name) a).id, b, null, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // annotation: ':' expression
    public ExprTy annotation_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ':' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(11)) != null  // token=':'
                &&
                (a = expression_rule()) != null  // expression
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

    // star_annotation: ':' star_expression
    public ExprTy star_annotation_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ':' star_expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(11)) != null  // token=':'
                &&
                (a = star_expression_rule()) != null  // star_expression
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

    // default: '=' expression | invalid_default
    public ExprTy default_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '=' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(22)) != null  // token='='
                &&
                (a = expression_rule()) != null  // expression
            )
            {
                _res = a;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_default
            if (errorIndicator) {
                return null;
            }
            Object invalid_default_var;
            if (
                (invalid_default_var = invalid_default_rule()) != null  // invalid_default
            )
            {
                _res = invalid_default_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // if_stmt:
    //     | invalid_if_stmt
    //     | 'if' named_expression ':' block elif_stmt
    //     | 'if' named_expression ':' block else_block?
    public StmtTy if_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_if_stmt
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_if_stmt_var;
            if (
                (invalid_if_stmt_var = invalid_if_stmt_rule()) != null  // invalid_if_stmt
            )
            {
                _res = invalid_if_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'if' named_expression ':' block elif_stmt
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            ExprTy a;
            StmtTy[] b;
            StmtTy c;
            if (
                (_keyword = expect(668)) != null  // token='if'
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
                _res = factory.createIf(a, b, new StmtTy[] {c}, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'if' named_expression ':' block else_block?
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            ExprTy a;
            StmtTy[] b;
            StmtTy[] c;
            if (
                (_keyword = expect(668)) != null  // token='if'
                &&
                (a = named_expression_rule()) != null  // named_expression
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                ((c = _tmp_71_rule()) != null || true)  // else_block?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createIf(a, b, c, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // elif_stmt:
    //     | invalid_elif_stmt
    //     | 'elif' named_expression ':' block elif_stmt
    //     | 'elif' named_expression ':' block else_block?
    public StmtTy elif_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_elif_stmt
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_elif_stmt_var;
            if (
                (invalid_elif_stmt_var = invalid_elif_stmt_rule()) != null  // invalid_elif_stmt
            )
            {
                _res = invalid_elif_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'elif' named_expression ':' block elif_stmt
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            ExprTy a;
            StmtTy[] b;
            StmtTy c;
            if (
                (_keyword = expect(675)) != null  // token='elif'
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
                _res = factory.createIf(a, b, new StmtTy[] {c}, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'elif' named_expression ':' block else_block?
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            ExprTy a;
            StmtTy[] b;
            StmtTy[] c;
            if (
                (_keyword = expect(675)) != null  // token='elif'
                &&
                (a = named_expression_rule()) != null  // named_expression
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                ((c = _tmp_72_rule()) != null || true)  // else_block?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createIf(a, b, c, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // else_block: invalid_else_stmt | 'else' &&':' block
    public StmtTy[] else_block_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (callInvalidRules) { // invalid_else_stmt
            if (errorIndicator) {
                return null;
            }
            Object invalid_else_stmt_var;
            if (
                (invalid_else_stmt_var = invalid_else_stmt_rule()) != null  // invalid_else_stmt
            )
            {
                _res = invalid_else_stmt_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // 'else' &&':' block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            StmtTy[] b;
            if (
                (_keyword = expect(676)) != null  // token='else'
                &&
                (_literal = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                (b = block_rule()) != null  // block
            )
            {
                _res = b;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // while_stmt: invalid_while_stmt | 'while' named_expression ':' block else_block?
    public StmtTy while_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_while_stmt
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_while_stmt_var;
            if (
                (invalid_while_stmt_var = invalid_while_stmt_rule()) != null  // invalid_while_stmt
            )
            {
                _res = invalid_while_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'while' named_expression ':' block else_block?
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            ExprTy a;
            StmtTy[] b;
            StmtTy[] c;
            if (
                (_keyword = expect(673)) != null  // token='while'
                &&
                (a = named_expression_rule()) != null  // named_expression
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                ((c = _tmp_73_rule()) != null || true)  // else_block?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createWhile(a, b, c, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // for_stmt:
    //     | invalid_for_stmt
    //     | 'for' star_targets 'in' ~ star_expressions ':' TYPE_COMMENT? block else_block?
    //     | ASYNC 'for' star_targets 'in' ~ star_expressions ':' TYPE_COMMENT? block else_block?
    //     | invalid_for_target
    public StmtTy for_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_for_stmt
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_for_stmt_var;
            if (
                (invalid_for_stmt_var = invalid_for_stmt_rule()) != null  // invalid_for_stmt
            )
            {
                _res = invalid_for_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'for' star_targets 'in' ~ star_expressions ':' TYPE_COMMENT? block else_block?
            if (errorIndicator) {
                return null;
            }
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
                (_keyword = expect(671)) != null  // token='for'
                &&
                (t = star_targets_rule()) != null  // star_targets
                &&
                (_keyword_1 = expect(677)) != null  // token='in'
                &&
                (_cut_var = 1) != 0
                &&
                (ex = star_expressions_rule()) != null  // star_expressions
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                ((tc = _tmp_74_rule()) != null || true)  // TYPE_COMMENT?
                &&
                (b = block_rule()) != null  // block
                &&
                ((el = _tmp_75_rule()) != null || true)  // else_block?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createFor(t, ex, b, el, newTypeComment(tc), startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        { // ASYNC 'for' star_targets 'in' ~ star_expressions ':' TYPE_COMMENT? block else_block?
            if (errorIndicator) {
                return null;
            }
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
                (_keyword = expect(671)) != null  // token='for'
                &&
                (t = star_targets_rule()) != null  // star_targets
                &&
                (_keyword_1 = expect(677)) != null  // token='in'
                &&
                (_cut_var = 1) != 0
                &&
                (ex = star_expressions_rule()) != null  // star_expressions
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                ((tc = _tmp_76_rule()) != null || true)  // TYPE_COMMENT?
                &&
                (b = block_rule()) != null  // block
                &&
                ((el = _tmp_77_rule()) != null || true)  // else_block?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = checkVersion(5, "Async for loops are", factory.createAsyncFor(t, ex, b, el, newTypeComment(tc), startToken.sourceRange.withEnd(endToken.sourceRange)));
                return (StmtTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        if (callInvalidRules) { // invalid_for_target
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_for_target_var;
            if (
                (invalid_for_target_var = invalid_for_target_rule()) != null  // invalid_for_target
            )
            {
                _res = invalid_for_target_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // with_stmt:
    //     | invalid_with_stmt_indent
    //     | 'with' '(' ','.with_item+ ','? ')' ':' block
    //     | 'with' ','.with_item+ ':' TYPE_COMMENT? block
    //     | ASYNC 'with' '(' ','.with_item+ ','? ')' ':' block
    //     | ASYNC 'with' ','.with_item+ ':' TYPE_COMMENT? block
    //     | invalid_with_stmt
    public StmtTy with_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_with_stmt_indent
            if (errorIndicator) {
                return null;
            }
            Object[] invalid_with_stmt_indent_var;
            if (
                (invalid_with_stmt_indent_var = invalid_with_stmt_indent_rule()) != null  // invalid_with_stmt_indent
            )
            {
                _res = invalid_with_stmt_indent_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'with' '(' ','.with_item+ ','? ')' ':' block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token _opt_var;
            WithItemTy[] a;
            StmtTy[] b;
            if (
                (_keyword = expect(670)) != null  // token='with'
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                (a = (WithItemTy[])_gather_78_rule()) != null  // ','.with_item+
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createWith(a, b, null, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'with' ','.with_item+ ':' TYPE_COMMENT? block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            WithItemTy[] a;
            StmtTy[] b;
            Token tc;
            if (
                (_keyword = expect(670)) != null  // token='with'
                &&
                (a = (WithItemTy[])_gather_80_rule()) != null  // ','.with_item+
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                ((tc = _tmp_82_rule()) != null || true)  // TYPE_COMMENT?
                &&
                (b = block_rule()) != null  // block
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createWith(a, b, newTypeComment(tc), startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // ASYNC 'with' '(' ','.with_item+ ','? ')' ':' block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token _opt_var;
            WithItemTy[] a;
            Token async_var;
            StmtTy[] b;
            if (
                (async_var = expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
                &&
                (_keyword = expect(670)) != null  // token='with'
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                (a = (WithItemTy[])_gather_83_rule()) != null  // ','.with_item+
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = checkVersion(5, "Async with statements are", factory.createAsyncWith(a, b, null, startToken.sourceRange.withEnd(endToken.sourceRange)));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // ASYNC 'with' ','.with_item+ ':' TYPE_COMMENT? block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            WithItemTy[] a;
            Token async_var;
            StmtTy[] b;
            Token tc;
            if (
                (async_var = expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
                &&
                (_keyword = expect(670)) != null  // token='with'
                &&
                (a = (WithItemTy[])_gather_85_rule()) != null  // ','.with_item+
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                ((tc = _tmp_87_rule()) != null || true)  // TYPE_COMMENT?
                &&
                (b = block_rule()) != null  // block
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = checkVersion(5, "Async with statements are", factory.createAsyncWith(a, b, newTypeComment(tc), startToken.sourceRange.withEnd(endToken.sourceRange)));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_with_stmt
            if (errorIndicator) {
                return null;
            }
            Object[] invalid_with_stmt_var;
            if (
                (invalid_with_stmt_var = invalid_with_stmt_rule()) != null  // invalid_with_stmt
            )
            {
                _res = invalid_with_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // with_item:
    //     | expression 'as' star_target &(',' | ')' | ':')
    //     | invalid_with_item
    //     | expression
    public WithItemTy with_item_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // expression 'as' star_target &(',' | ')' | ':')
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy e;
            ExprTy t;
            if (
                (e = expression_rule()) != null  // expression
                &&
                (_keyword = expect(674)) != null  // token='as'
                &&
                (t = star_target_rule()) != null  // star_target
                &&
                genLookahead__tmp_88_rule(true)
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createWithItem(e, t, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (WithItemTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_with_item
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_with_item_var;
            if (
                (invalid_with_item_var = invalid_with_item_rule()) != null  // invalid_with_item
            )
            {
                _res = invalid_with_item_var;
                return (WithItemTy)_res;
            }
            reset(_mark);
        }
        { // expression
            if (errorIndicator) {
                return null;
            }
            ExprTy e;
            if (
                (e = expression_rule()) != null  // expression
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createWithItem(e, null, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (WithItemTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (WithItemTy)_res;
    }

    // try_stmt:
    //     | invalid_try_stmt
    //     | 'try' &&':' block finally_block
    //     | 'try' &&':' block except_block+ else_block? finally_block?
    //     | 'try' &&':' block except_star_block+ else_block? finally_block?
    public StmtTy try_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_try_stmt
            if (errorIndicator) {
                return null;
            }
            Object invalid_try_stmt_var;
            if (
                (invalid_try_stmt_var = invalid_try_stmt_rule()) != null  // invalid_try_stmt
            )
            {
                _res = invalid_try_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'try' &&':' block finally_block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            StmtTy[] b;
            StmtTy[] f;
            if (
                (_keyword = expect(672)) != null  // token='try'
                &&
                (_literal = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                (f = finally_block_rule()) != null  // finally_block
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTry(b, null, null, f, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'try' &&':' block except_block+ else_block? finally_block?
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            StmtTy[] b;
            StmtTy[] el;
            ExceptHandlerTy[] ex;
            StmtTy[] f;
            if (
                (_keyword = expect(672)) != null  // token='try'
                &&
                (_literal = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                (ex = (ExceptHandlerTy[])_loop1_89_rule()) != null  // except_block+
                &&
                ((el = _tmp_90_rule()) != null || true)  // else_block?
                &&
                ((f = _tmp_91_rule()) != null || true)  // finally_block?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTry(b, ex, el, f, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        { // 'try' &&':' block except_star_block+ else_block? finally_block?
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            StmtTy[] b;
            StmtTy[] el;
            ExceptHandlerTy[] ex;
            StmtTy[] f;
            if (
                (_keyword = expect(672)) != null  // token='try'
                &&
                (_literal = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                (b = block_rule()) != null  // block
                &&
                (ex = (ExceptHandlerTy[])_loop1_92_rule()) != null  // except_star_block+
                &&
                ((el = _tmp_93_rule()) != null || true)  // else_block?
                &&
                ((f = _tmp_94_rule()) != null || true)  // finally_block?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = checkVersion(10, "Pattern matching is", factory.createTryStar(b, ex, el, f, startToken.sourceRange.withEnd(endToken.sourceRange)));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // except_block:
    //     | invalid_except_stmt_indent
    //     | 'except' expression ['as' NAME] ':' block
    //     | 'except' ':' block
    //     | invalid_except_stmt
    public ExceptHandlerTy except_block_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_except_stmt_indent
            if (errorIndicator) {
                return null;
            }
            Object invalid_except_stmt_indent_var;
            if (
                (invalid_except_stmt_indent_var = invalid_except_stmt_indent_rule()) != null  // invalid_except_stmt_indent
            )
            {
                _res = invalid_except_stmt_indent_var;
                return (ExceptHandlerTy)_res;
            }
            reset(_mark);
        }
        { // 'except' expression ['as' NAME] ':' block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            StmtTy[] b;
            ExprTy e;
            ExprTy t;
            if (
                (_keyword = expect(678)) != null  // token='except'
                &&
                (e = expression_rule()) != null  // expression
                &&
                ((t = _tmp_95_rule()) != null || true)  // ['as' NAME]
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createExceptHandler(e, t != null ? ((ExprTy.Name) t).id : null, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExceptHandlerTy)_res;
            }
            reset(_mark);
        }
        { // 'except' ':' block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            StmtTy[] b;
            if (
                (_keyword = expect(678)) != null  // token='except'
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createExceptHandler(null, null, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExceptHandlerTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_except_stmt
            if (errorIndicator) {
                return null;
            }
            Object invalid_except_stmt_var;
            if (
                (invalid_except_stmt_var = invalid_except_stmt_rule()) != null  // invalid_except_stmt
            )
            {
                _res = invalid_except_stmt_var;
                return (ExceptHandlerTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExceptHandlerTy)_res;
    }

    // except_star_block:
    //     | invalid_except_star_stmt_indent
    //     | 'except' '*' expression ['as' NAME] ':' block
    //     | invalid_except_stmt
    public ExceptHandlerTy except_star_block_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_except_star_stmt_indent
            if (errorIndicator) {
                return null;
            }
            Object invalid_except_star_stmt_indent_var;
            if (
                (invalid_except_star_stmt_indent_var = invalid_except_star_stmt_indent_rule()) != null  // invalid_except_star_stmt_indent
            )
            {
                _res = invalid_except_star_stmt_indent_var;
                return (ExceptHandlerTy)_res;
            }
            reset(_mark);
        }
        { // 'except' '*' expression ['as' NAME] ':' block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            Token _literal_1;
            StmtTy[] b;
            ExprTy e;
            ExprTy t;
            if (
                (_keyword = expect(678)) != null  // token='except'
                &&
                (_literal = expect(16)) != null  // token='*'
                &&
                (e = expression_rule()) != null  // expression
                &&
                ((t = _tmp_96_rule()) != null || true)  // ['as' NAME]
                &&
                (_literal_1 = expect(11)) != null  // token=':'
                &&
                (b = block_rule()) != null  // block
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createExceptHandler(e, t != null ? ((ExprTy.Name) t).id : null, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExceptHandlerTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_except_stmt
            if (errorIndicator) {
                return null;
            }
            Object invalid_except_stmt_var;
            if (
                (invalid_except_stmt_var = invalid_except_stmt_rule()) != null  // invalid_except_stmt
            )
            {
                _res = invalid_except_stmt_var;
                return (ExceptHandlerTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExceptHandlerTy)_res;
    }

    // finally_block: invalid_finally_stmt | 'finally' &&':' block
    public StmtTy[] finally_block_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (callInvalidRules) { // invalid_finally_stmt
            if (errorIndicator) {
                return null;
            }
            Object invalid_finally_stmt_var;
            if (
                (invalid_finally_stmt_var = invalid_finally_stmt_rule()) != null  // invalid_finally_stmt
            )
            {
                _res = invalid_finally_stmt_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        { // 'finally' &&':' block
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            StmtTy[] a;
            if (
                (_keyword = expect(679)) != null  // token='finally'
                &&
                (_literal = expect_forced_token(11, ":")) != null  // forced_token=':'
                &&
                (a = block_rule()) != null  // block
            )
            {
                _res = a;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // match_stmt:
    //     | "match" subject_expr ':' NEWLINE INDENT case_block+ DEDENT
    //     | invalid_match_stmt
    public StmtTy match_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // "match" subject_expr ':' NEWLINE INDENT case_block+ DEDENT
            if (errorIndicator) {
                return null;
            }
            ExprTy _keyword;
            Token _literal;
            MatchCaseTy[] cases;
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
                (cases = (MatchCaseTy[])_loop1_97_rule()) != null  // case_block+
                &&
                (dedent_var = expect(Token.Kind.DEDENT)) != null  // token='DEDENT'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = checkVersion(10, "Pattern matching is", factory.createMatch(subject, cases, startToken.sourceRange.withEnd(endToken.sourceRange)));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_match_stmt
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_match_stmt_var;
            if (
                (invalid_match_stmt_var = invalid_match_stmt_rule()) != null  // invalid_match_stmt
            )
            {
                _res = invalid_match_stmt_var;
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // subject_expr: star_named_expression ',' star_named_expressions? | named_expression
    public ExprTy subject_expr_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // star_named_expression ',' star_named_expressions?
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(insertInFront(value, values), ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // named_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy named_expression_var;
            if (
                (named_expression_var = named_expression_rule()) != null  // named_expression
            )
            {
                _res = named_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // case_block: invalid_case_block | "case" patterns guard? ':' block
    public MatchCaseTy case_block_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_case_block
            if (errorIndicator) {
                return null;
            }
            Object invalid_case_block_var;
            if (
                (invalid_case_block_var = invalid_case_block_rule()) != null  // invalid_case_block
            )
            {
                _res = invalid_case_block_var;
                return (MatchCaseTy)_res;
            }
            reset(_mark);
        }
        { // "case" patterns guard? ':' block
            if (errorIndicator) {
                return null;
            }
            ExprTy _keyword;
            Token _literal;
            StmtTy[] body;
            ExprTy guard;
            PatternTy pattern;
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchCase(pattern, guard, body, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (MatchCaseTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (MatchCaseTy)_res;
    }

    // guard: 'if' named_expression
    public ExprTy guard_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'if' named_expression
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy guard;
            if (
                (_keyword = expect(668)) != null  // token='if'
                &&
                (guard = named_expression_rule()) != null  // named_expression
            )
            {
                _res = guard;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // patterns: open_sequence_pattern | pattern
    public PatternTy patterns_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // open_sequence_pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy[] patterns;
            if (
                (patterns = (PatternTy[])open_sequence_pattern_rule()) != null  // open_sequence_pattern
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchSequence(patterns, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy pattern_var;
            if (
                (pattern_var = pattern_rule()) != null  // pattern
            )
            {
                _res = pattern_var;
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
    }

    // pattern: as_pattern | or_pattern
    public PatternTy pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // as_pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy as_pattern_var;
            if (
                (as_pattern_var = as_pattern_rule()) != null  // as_pattern
            )
            {
                _res = as_pattern_var;
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // or_pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy or_pattern_var;
            if (
                (or_pattern_var = or_pattern_rule()) != null  // or_pattern
            )
            {
                _res = or_pattern_var;
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
    }

    // as_pattern: or_pattern 'as' pattern_capture_target | invalid_as_pattern
    public PatternTy as_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // or_pattern 'as' pattern_capture_target
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            PatternTy pattern;
            ExprTy target;
            if (
                (pattern = or_pattern_rule()) != null  // or_pattern
                &&
                (_keyword = expect(674)) != null  // token='as'
                &&
                (target = pattern_capture_target_rule()) != null  // pattern_capture_target
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchAs(pattern, ((ExprTy.Name) target).id, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_as_pattern
            if (errorIndicator) {
                return null;
            }
            Object invalid_as_pattern_var;
            if (
                (invalid_as_pattern_var = invalid_as_pattern_rule()) != null  // invalid_as_pattern
            )
            {
                _res = invalid_as_pattern_var;
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
    }

    // or_pattern: '|'.closed_pattern+
    public PatternTy or_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '|'.closed_pattern+
            if (errorIndicator) {
                return null;
            }
            PatternTy[] patterns;
            if (
                (patterns = (PatternTy[])_gather_98_rule()) != null  // '|'.closed_pattern+
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = patterns.length == 1 ? patterns[0] : factory.createMatchOr(patterns, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
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
    public PatternTy closed_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, CLOSED_PATTERN_ID)) {
            _res = (PatternTy)cache.getResult(_mark, CLOSED_PATTERN_ID);
            return (PatternTy)_res;
        }
        { // literal_pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy literal_pattern_var;
            if (
                (literal_pattern_var = literal_pattern_rule()) != null  // literal_pattern
            )
            {
                _res = literal_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // capture_pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy capture_pattern_var;
            if (
                (capture_pattern_var = capture_pattern_rule()) != null  // capture_pattern
            )
            {
                _res = capture_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // wildcard_pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy wildcard_pattern_var;
            if (
                (wildcard_pattern_var = wildcard_pattern_rule()) != null  // wildcard_pattern
            )
            {
                _res = wildcard_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // value_pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy value_pattern_var;
            if (
                (value_pattern_var = value_pattern_rule()) != null  // value_pattern
            )
            {
                _res = value_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // group_pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy group_pattern_var;
            if (
                (group_pattern_var = group_pattern_rule()) != null  // group_pattern
            )
            {
                _res = group_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // sequence_pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy sequence_pattern_var;
            if (
                (sequence_pattern_var = sequence_pattern_rule()) != null  // sequence_pattern
            )
            {
                _res = sequence_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // mapping_pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy mapping_pattern_var;
            if (
                (mapping_pattern_var = mapping_pattern_rule()) != null  // mapping_pattern
            )
            {
                _res = mapping_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // class_pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy class_pattern_var;
            if (
                (class_pattern_var = class_pattern_rule()) != null  // class_pattern
            )
            {
                _res = class_pattern_var;
                cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, CLOSED_PATTERN_ID, _res);
        return (PatternTy)_res;
    }

    // literal_pattern:
    //     | signed_number !('+' | '-')
    //     | complex_number
    //     | strings
    //     | 'None'
    //     | 'True'
    //     | 'False'
    public PatternTy literal_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // signed_number !('+' | '-')
            if (errorIndicator) {
                return null;
            }
            ExprTy value;
            if (
                (value = signed_number_rule()) != null  // signed_number
                &&
                genLookahead__tmp_100_rule(false)
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchValue(value, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // complex_number
            if (errorIndicator) {
                return null;
            }
            ExprTy value;
            if (
                (value = complex_number_rule()) != null  // complex_number
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchValue(value, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // strings
            if (errorIndicator) {
                return null;
            }
            ExprTy value;
            if (
                (value = strings_rule()) != null  // strings
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchValue(value, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // 'None'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(680)) != null  // token='None'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchSingleton(ConstantValue.NONE, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // 'True'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(681)) != null  // token='True'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchSingleton(ConstantValue.TRUE, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // 'False'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(682)) != null  // token='False'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchSingleton(ConstantValue.FALSE, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
    }

    // literal_expr:
    //     | signed_number !('+' | '-')
    //     | complex_number
    //     | strings
    //     | 'None'
    //     | 'True'
    //     | 'False'
    public ExprTy literal_expr_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // signed_number !('+' | '-')
            if (errorIndicator) {
                return null;
            }
            ExprTy signed_number_var;
            if (
                (signed_number_var = signed_number_rule()) != null  // signed_number
                &&
                genLookahead__tmp_101_rule(false)
            )
            {
                _res = signed_number_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // complex_number
            if (errorIndicator) {
                return null;
            }
            ExprTy complex_number_var;
            if (
                (complex_number_var = complex_number_rule()) != null  // complex_number
            )
            {
                _res = complex_number_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // strings
            if (errorIndicator) {
                return null;
            }
            ExprTy strings_var;
            if (
                (strings_var = strings_rule()) != null  // strings
            )
            {
                _res = strings_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'None'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(680)) != null  // token='None'
            )
            {
                _res = factory.createNone(startToken.sourceRange);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'True'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(681)) != null  // token='True'
            )
            {
                _res = factory.createBooleanLiteral(true, startToken.sourceRange);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'False'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(682)) != null  // token='False'
            )
            {
                _res = factory.createBooleanLiteral(false, startToken.sourceRange);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // complex_number:
    //     | signed_real_number '+' imaginary_number
    //     | signed_real_number '-' imaginary_number
    public ExprTy complex_number_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // signed_real_number '+' imaginary_number
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy imag;
            ExprTy real;
            if (
                (real = signed_real_number_rule()) != null  // signed_real_number
                &&
                (_literal = expect(14)) != null  // token='+'
                &&
                (imag = imaginary_number_rule()) != null  // imaginary_number
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(OperatorTy.Add, real, imag, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // signed_real_number '-' imaginary_number
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy imag;
            ExprTy real;
            if (
                (real = signed_real_number_rule()) != null  // signed_real_number
                &&
                (_literal = expect(15)) != null  // token='-'
                &&
                (imag = imaginary_number_rule()) != null  // imaginary_number
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createBinaryOp(OperatorTy.Sub, real, imag, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // signed_number: NUMBER | '-' NUMBER
    public ExprTy signed_number_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // NUMBER
            if (errorIndicator) {
                return null;
            }
            ExprTy number_var;
            if (
                (number_var = number_token()) != null  // NUMBER
            )
            {
                _res = number_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '-' NUMBER
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createUnaryOp(UnaryOpTy.USub, number, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // signed_real_number: real_number | '-' real_number
    public ExprTy signed_real_number_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // real_number
            if (errorIndicator) {
                return null;
            }
            ExprTy real_number_var;
            if (
                (real_number_var = real_number_rule()) != null  // real_number
            )
            {
                _res = real_number_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '-' real_number
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy real;
            if (
                (_literal = expect(15)) != null  // token='-'
                &&
                (real = real_number_rule()) != null  // real_number
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createUnaryOp(UnaryOpTy.USub, real, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // real_number: NUMBER
    public ExprTy real_number_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // NUMBER
            if (errorIndicator) {
                return null;
            }
            ExprTy real;
            if (
                (real = number_token()) != null  // NUMBER
            )
            {
                _res = ensureReal(real);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // imaginary_number: NUMBER
    public ExprTy imaginary_number_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // NUMBER
            if (errorIndicator) {
                return null;
            }
            ExprTy imag;
            if (
                (imag = number_token()) != null  // NUMBER
            )
            {
                _res = ensureImaginary(imag);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // capture_pattern: pattern_capture_target
    public PatternTy capture_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // pattern_capture_target
            if (errorIndicator) {
                return null;
            }
            ExprTy target;
            if (
                (target = pattern_capture_target_rule()) != null  // pattern_capture_target
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchAs(null, ((ExprTy.Name) target).id, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
    }

    // pattern_capture_target: !"_" NAME !('.' | '(' | '=')
    public ExprTy pattern_capture_target_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // !"_" NAME !('.' | '(' | '=')
            if (errorIndicator) {
                return null;
            }
            ExprTy name;
            if (
                genLookahead_expect_SOFT_KEYWORD(false, "_")
                &&
                (name = name_token()) != null  // NAME
                &&
                genLookahead__tmp_102_rule(false)
            )
            {
                _res = setExprContext(name, ExprContextTy.Store);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // wildcard_pattern: "_"
    public PatternTy wildcard_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // "_"
            if (errorIndicator) {
                return null;
            }
            ExprTy _keyword;
            if (
                (_keyword = expect_SOFT_KEYWORD("_")) != null  // soft_keyword='"_"'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchAs(null, null, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
    }

    // value_pattern: attr !('.' | '(' | '=')
    public PatternTy value_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // attr !('.' | '(' | '=')
            if (errorIndicator) {
                return null;
            }
            ExprTy attr;
            if (
                (attr = attr_rule()) != null  // attr
                &&
                genLookahead__tmp_103_rule(false)
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchValue(attr, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // name_or_attr '.' NAME
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createGetAttribute(value, ((ExprTy.Name) attr).id, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // attr
            if (errorIndicator) {
                return null;
            }
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
            if (errorIndicator) {
                return null;
            }
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
    public PatternTy group_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '(' pattern ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            PatternTy pattern;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (pattern = pattern_rule()) != null  // pattern
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                _res = pattern;
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
    }

    // sequence_pattern: '[' maybe_sequence_pattern? ']' | '(' open_sequence_pattern? ')'
    public PatternTy sequence_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '[' maybe_sequence_pattern? ']'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            PatternTy[] patterns;
            if (
                (_literal = expect(9)) != null  // token='['
                &&
                ((patterns = maybe_sequence_pattern_rule()) != null || true)  // maybe_sequence_pattern?
                &&
                (_literal_1 = expect(10)) != null  // token=']'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchSequence(patterns, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // '(' open_sequence_pattern? ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            PatternTy[] patterns;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                ((patterns = open_sequence_pattern_rule()) != null || true)  // open_sequence_pattern?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchSequence(patterns, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
    }

    // open_sequence_pattern: maybe_star_pattern ',' maybe_sequence_pattern?
    public PatternTy[] open_sequence_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // maybe_star_pattern ',' maybe_sequence_pattern?
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            PatternTy pattern;
            PatternTy[] patterns;
            if (
                (pattern = (PatternTy)maybe_star_pattern_rule()) != null  // maybe_star_pattern
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                ((patterns = (PatternTy[])maybe_sequence_pattern_rule()) != null || true)  // maybe_sequence_pattern?
            )
            {
                _res = insertInFront(pattern, patterns);
                return (PatternTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy[])_res;
    }

    // maybe_sequence_pattern: ','.maybe_star_pattern+ ','?
    public PatternTy[] maybe_sequence_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.maybe_star_pattern+ ','?
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            PatternTy[] patterns;
            if (
                (patterns = (PatternTy[])_gather_104_rule()) != null  // ','.maybe_star_pattern+
                &&
                ((_opt_var = (Token)expect(12)) != null || true)  // ','?
            )
            {
                _res = patterns;
                return (PatternTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy[])_res;
    }

    // maybe_star_pattern: star_pattern | pattern
    public PatternTy maybe_star_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy star_pattern_var;
            if (
                (star_pattern_var = star_pattern_rule()) != null  // star_pattern
            )
            {
                _res = star_pattern_var;
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // pattern
            if (errorIndicator) {
                return null;
            }
            PatternTy pattern_var;
            if (
                (pattern_var = pattern_rule()) != null  // pattern
            )
            {
                _res = pattern_var;
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
    }

    // star_pattern: '*' pattern_capture_target | '*' wildcard_pattern
    public PatternTy star_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_PATTERN_ID)) {
            _res = (PatternTy)cache.getResult(_mark, STAR_PATTERN_ID);
            return (PatternTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '*' pattern_capture_target
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy target;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (target = pattern_capture_target_rule()) != null  // pattern_capture_target
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchStar(((ExprTy.Name) target).id, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, STAR_PATTERN_ID, _res);
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // '*' wildcard_pattern
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            PatternTy wildcard_pattern_var;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (wildcard_pattern_var = wildcard_pattern_rule()) != null  // wildcard_pattern
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchStar(null, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, STAR_PATTERN_ID, _res);
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, STAR_PATTERN_ID, _res);
        return (PatternTy)_res;
    }

    // mapping_pattern:
    //     | '{' '}'
    //     | '{' double_star_pattern ','? '}'
    //     | '{' items_pattern ',' double_star_pattern ','? '}'
    //     | '{' items_pattern ','? '}'
    public PatternTy mapping_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '{' '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            if (
                (_literal = expect(25)) != null  // token='{'
                &&
                (_literal_1 = expect(26)) != null  // token='}'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchMapping(null, null, null, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // '{' double_star_pattern ','? '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            Token _opt_var;
            ExprTy rest;
            if (
                (_literal = expect(25)) != null  // token='{'
                &&
                (rest = double_star_pattern_rule()) != null  // double_star_pattern
                &&
                ((_opt_var = expect(12)) != null || true)  // ','?
                &&
                (_literal_1 = expect(26)) != null  // token='}'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchMapping(null, null, ((ExprTy.Name) rest).id, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // '{' items_pattern ',' double_star_pattern ','? '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token _opt_var;
            KeyPatternPair[] items;
            ExprTy rest;
            if (
                (_literal = expect(25)) != null  // token='{'
                &&
                (items = items_pattern_rule()) != null  // items_pattern
                &&
                (_literal_1 = expect(12)) != null  // token=','
                &&
                (rest = double_star_pattern_rule()) != null  // double_star_pattern
                &&
                ((_opt_var = expect(12)) != null || true)  // ','?
                &&
                (_literal_2 = expect(26)) != null  // token='}'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchMapping(extractKeys(items), extractPatterns(items), ((ExprTy.Name) rest).id, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // '{' items_pattern ','? '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            Token _opt_var;
            KeyPatternPair[] items;
            if (
                (_literal = expect(25)) != null  // token='{'
                &&
                (items = items_pattern_rule()) != null  // items_pattern
                &&
                ((_opt_var = expect(12)) != null || true)  // ','?
                &&
                (_literal_1 = expect(26)) != null  // token='}'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchMapping(extractKeys(items), extractPatterns(items), null, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
    }

    // items_pattern: ','.key_value_pattern+
    public KeyPatternPair[] items_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.key_value_pattern+
            if (errorIndicator) {
                return null;
            }
            KeyPatternPair[] _gather_106_var;
            if (
                (_gather_106_var = (KeyPatternPair[])_gather_106_rule()) != null  // ','.key_value_pattern+
            )
            {
                _res = _gather_106_var;
                return (KeyPatternPair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeyPatternPair[])_res;
    }

    // key_value_pattern: (literal_expr | attr) ':' pattern
    public KeyPatternPair key_value_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // (literal_expr | attr) ':' pattern
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy key;
            PatternTy pattern;
            if (
                (key = _tmp_108_rule()) != null  // literal_expr | attr
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                (pattern = pattern_rule()) != null  // pattern
            )
            {
                _res = new KeyPatternPair(key, pattern);
                return (KeyPatternPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeyPatternPair)_res;
    }

    // double_star_pattern: '**' pattern_capture_target
    public ExprTy double_star_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '**' pattern_capture_target
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy target;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (target = pattern_capture_target_rule()) != null  // pattern_capture_target
            )
            {
                _res = target;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // class_pattern:
    //     | name_or_attr '(' ')'
    //     | name_or_attr '(' positional_patterns ','? ')'
    //     | name_or_attr '(' keyword_patterns ','? ')'
    //     | name_or_attr '(' positional_patterns ',' keyword_patterns ','? ')'
    //     | invalid_class_pattern
    public PatternTy class_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // name_or_attr '(' ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy cls;
            if (
                (cls = name_or_attr_rule()) != null  // name_or_attr
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchClass(cls, null, null, null, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // name_or_attr '(' positional_patterns ','? ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            Token _opt_var;
            ExprTy cls;
            PatternTy[] patterns;
            if (
                (cls = name_or_attr_rule()) != null  // name_or_attr
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                (patterns = positional_patterns_rule()) != null  // positional_patterns
                &&
                ((_opt_var = expect(12)) != null || true)  // ','?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchClass(cls, patterns, null, null, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // name_or_attr '(' keyword_patterns ','? ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            Token _opt_var;
            ExprTy cls;
            KeyPatternPair[] keywords;
            if (
                (cls = name_or_attr_rule()) != null  // name_or_attr
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchClass(cls, null, extractNames(extractKeys(keywords)), extractPatterns(keywords), startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        { // name_or_attr '(' positional_patterns ',' keyword_patterns ','? ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token _opt_var;
            ExprTy cls;
            KeyPatternPair[] keywords;
            PatternTy[] patterns;
            if (
                (cls = name_or_attr_rule()) != null  // name_or_attr
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                (patterns = positional_patterns_rule()) != null  // positional_patterns
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createMatchClass(cls, patterns, extractNames(extractKeys(keywords)), extractPatterns(keywords), startToken.sourceRange.withEnd(endToken.sourceRange));
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_class_pattern
            if (errorIndicator) {
                return null;
            }
            Object invalid_class_pattern_var;
            if (
                (invalid_class_pattern_var = invalid_class_pattern_rule()) != null  // invalid_class_pattern
            )
            {
                _res = invalid_class_pattern_var;
                return (PatternTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy)_res;
    }

    // positional_patterns: ','.pattern+
    public PatternTy[] positional_patterns_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.pattern+
            if (errorIndicator) {
                return null;
            }
            PatternTy[] args;
            if (
                (args = (PatternTy[])_gather_109_rule()) != null  // ','.pattern+
            )
            {
                _res = args;
                return (PatternTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy[])_res;
    }

    // keyword_patterns: ','.keyword_pattern+
    public KeyPatternPair[] keyword_patterns_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.keyword_pattern+
            if (errorIndicator) {
                return null;
            }
            KeyPatternPair[] _gather_111_var;
            if (
                (_gather_111_var = (KeyPatternPair[])_gather_111_rule()) != null  // ','.keyword_pattern+
            )
            {
                _res = _gather_111_var;
                return (KeyPatternPair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeyPatternPair[])_res;
    }

    // keyword_pattern: NAME '=' pattern
    public KeyPatternPair keyword_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // NAME '=' pattern
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy arg;
            PatternTy value;
            if (
                (arg = name_token()) != null  // NAME
                &&
                (_literal = expect(22)) != null  // token='='
                &&
                (value = pattern_rule()) != null  // pattern
            )
            {
                _res = new KeyPatternPair(arg, value);
                return (KeyPatternPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeyPatternPair)_res;
    }

    // type_alias: "type" NAME type_params? '=' expression
    public StmtTy type_alias_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // "type" NAME type_params? '=' expression
            if (errorIndicator) {
                return null;
            }
            ExprTy _keyword;
            Token _literal;
            ExprTy b;
            ExprTy n;
            TypeParamTy[] t;
            if (
                (_keyword = expect_SOFT_KEYWORD("type")) != null  // soft_keyword='"type"'
                &&
                (n = name_token()) != null  // NAME
                &&
                ((t = _tmp_113_rule()) != null || true)  // type_params?
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
                _res = checkVersion(12, "Type statement is", factory.createTypeAlias(setExprContext(n, ExprContextTy.Store), t, b, startToken.sourceRange.withEnd(endToken.sourceRange)));
                return (StmtTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy)_res;
    }

    // type_params: '[' type_param_seq ']'
    public TypeParamTy[] type_params_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '[' type_param_seq ']'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            TypeParamTy[] t;
            if (
                (_literal = expect(9)) != null  // token='['
                &&
                (t = type_param_seq_rule()) != null  // type_param_seq
                &&
                (_literal_1 = expect(10)) != null  // token=']'
            )
            {
                _res = checkVersion(12, "Type parameter lists are", t);
                return (TypeParamTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (TypeParamTy[])_res;
    }

    // type_param_seq: ','.type_param+ ','?
    public TypeParamTy[] type_param_seq_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.type_param+ ','?
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            TypeParamTy[] a;
            if (
                (a = (TypeParamTy[])_gather_114_rule()) != null  // ','.type_param+
                &&
                ((_opt_var = _tmp_116_rule()) != null || true)  // ','?
            )
            {
                _res = a;
                return (TypeParamTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (TypeParamTy[])_res;
    }

    // type_param:
    //     | NAME type_param_bound?
    //     | '*' NAME ':' expression
    //     | '*' NAME
    //     | '**' NAME ':' expression
    //     | '**' NAME
    public TypeParamTy type_param_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, TYPE_PARAM_ID)) {
            _res = (TypeParamTy)cache.getResult(_mark, TYPE_PARAM_ID);
            return (TypeParamTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // NAME type_param_bound?
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            ExprTy b;
            if (
                (a = name_token()) != null  // NAME
                &&
                ((b = _tmp_117_rule()) != null || true)  // type_param_bound?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTypeVar(((ExprTy.Name) a).id, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, TYPE_PARAM_ID, _res);
                return (TypeParamTy)_res;
            }
            reset(_mark);
        }
        { // '*' NAME ':' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            Token colon;
            ExprTy e;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = name_token()) != null  // NAME
                &&
                (colon = expect(11)) != null  // token=':'
                &&
                (e = expression_rule()) != null  // expression
            )
            {
                _res = this.raiseSyntaxErrorStartingFrom(colon, e instanceof ExprTy.Tuple ? "cannot use constraints with TypeVarTuple" : "cannot use bound with TypeVarTuple");
                cache.putResult(_mark, TYPE_PARAM_ID, _res);
                return (TypeParamTy)_res;
            }
            reset(_mark);
        }
        { // '*' NAME
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = name_token()) != null  // NAME
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTypeVarTuple(((ExprTy.Name) a).id, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, TYPE_PARAM_ID, _res);
                return (TypeParamTy)_res;
            }
            reset(_mark);
        }
        { // '**' NAME ':' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            Token colon;
            ExprTy e;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (a = name_token()) != null  // NAME
                &&
                (colon = expect(11)) != null  // token=':'
                &&
                (e = expression_rule()) != null  // expression
            )
            {
                _res = this.raiseSyntaxErrorStartingFrom(colon, e instanceof ExprTy.Tuple ? "cannot use constraints with ParamSpec" : "cannot use bound with ParamSpec");
                cache.putResult(_mark, TYPE_PARAM_ID, _res);
                return (TypeParamTy)_res;
            }
            reset(_mark);
        }
        { // '**' NAME
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (a = name_token()) != null  // NAME
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createParamSpec(((ExprTy.Name) a).id, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, TYPE_PARAM_ID, _res);
                return (TypeParamTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, TYPE_PARAM_ID, _res);
        return (TypeParamTy)_res;
    }

    // type_param_bound: ':' expression
    public ExprTy type_param_bound_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ':' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy e;
            if (
                (_literal = expect(11)) != null  // token=':'
                &&
                (e = expression_rule()) != null  // expression
            )
            {
                _res = e;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // expressions: expression ((',' expression))+ ','? | expression ',' | expression
    public ExprTy expressions_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // expression ((',' expression))+ ','?
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(this.insertInFront(a, b), ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (a = expression_rule()) != null  // expression
                &&
                (_literal = expect(12)) != null  // token=','
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(new ExprTy[] {a}, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression
            if (errorIndicator) {
                return null;
            }
            ExprTy expression_var;
            if (
                (expression_var = expression_rule()) != null  // expression
            )
            {
                _res = expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // expression:
    //     | invalid_expression
    //     | invalid_legacy_expression
    //     | disjunction 'if' disjunction 'else' expression
    //     | disjunction
    //     | lambdef
    public ExprTy expression_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, EXPRESSION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, EXPRESSION_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_expression
            if (errorIndicator) {
                return null;
            }
            Object invalid_expression_var;
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
        if (callInvalidRules) { // invalid_legacy_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_legacy_expression_var;
            if (
                (invalid_legacy_expression_var = invalid_legacy_expression_rule()) != null  // invalid_legacy_expression
            )
            {
                _res = invalid_legacy_expression_var;
                cache.putResult(_mark, EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // disjunction 'if' disjunction 'else' expression
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _keyword_1;
            ExprTy a;
            ExprTy b;
            ExprTy c;
            if (
                (a = disjunction_rule()) != null  // disjunction
                &&
                (_keyword = expect(668)) != null  // token='if'
                &&
                (b = disjunction_rule()) != null  // disjunction
                &&
                (_keyword_1 = expect(676)) != null  // token='else'
                &&
                (c = expression_rule()) != null  // expression
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createIfExpression(b, a, c, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // disjunction
            if (errorIndicator) {
                return null;
            }
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
            if (errorIndicator) {
                return null;
            }
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

    // yield_expr: 'yield' 'from' expression | 'yield' star_expressions?
    public ExprTy yield_expr_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // 'yield' 'from' expression
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _keyword_1;
            ExprTy a;
            if (
                (_keyword = expect(661)) != null  // token='yield'
                &&
                (_keyword_1 = expect(657)) != null  // token='from'
                &&
                (a = expression_rule()) != null  // expression
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createYield(a, true, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'yield' star_expressions?
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy a;
            if (
                (_keyword = expect(661)) != null  // token='yield'
                &&
                ((a = _tmp_120_rule()) != null || true)  // star_expressions?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createYield(a, false, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // star_expressions:
    //     | star_expression ((',' star_expression))+ ','?
    //     | star_expression ','
    //     | star_expression
    public ExprTy star_expressions_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // star_expression ((',' star_expression))+ ','?
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            ExprTy a;
            ExprTy[] b;
            if (
                (a = star_expression_rule()) != null  // star_expression
                &&
                (b = _loop1_121_rule()) != null  // ((',' star_expression))+
                &&
                ((_opt_var = _tmp_122_rule()) != null || true)  // ','?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(this.insertInFront(a, b), ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expression ','
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createTuple(new ExprTy[] {a}, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expression_var;
            if (
                (star_expression_var = star_expression_rule()) != null  // star_expression
            )
            {
                _res = star_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // star_expression: '*' bitwise_or | expression
    public ExprTy star_expression_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_EXPRESSION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, STAR_EXPRESSION_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '*' bitwise_or
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createStarred(a, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, STAR_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.star_named_expression+ ','?
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            ExprTy[] a;
            if (
                (a = (ExprTy[])_gather_123_rule()) != null  // ','.star_named_expression+
                &&
                ((_opt_var = _tmp_125_rule()) != null || true)  // ','?
            )
            {
                _res = a;
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // star_named_expression: '*' bitwise_or | named_expression
    public ExprTy star_named_expression_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '*' bitwise_or
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createStarred(a, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // named_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy named_expression_var;
            if (
                (named_expression_var = named_expression_rule()) != null  // named_expression
            )
            {
                _res = named_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // assignment_expression: NAME ':=' ~ expression
    public ExprTy assignment_expression_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // NAME ':=' ~ expression
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = checkVersion(8, "Assignment expressions are", factory.createNamedExp(this.check(this.setExprContext(a, ExprContextTy.Store)), b, startToken.sourceRange.withEnd(endToken.sourceRange)));
                return (ExprTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        _res = null;
        return (ExprTy)_res;
    }

    // named_expression: assignment_expression | invalid_named_expression | expression !':='
    public ExprTy named_expression_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // assignment_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy assignment_expression_var;
            if (
                (assignment_expression_var = assignment_expression_rule()) != null  // assignment_expression
            )
            {
                _res = assignment_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_named_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_named_expression_var;
            if (
                (invalid_named_expression_var = invalid_named_expression_rule()) != null  // invalid_named_expression
            )
            {
                _res = invalid_named_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression !':='
            if (errorIndicator) {
                return null;
            }
            ExprTy expression_var;
            if (
                (expression_var = expression_rule()) != null  // expression
                &&
                genLookahead_expect(false, 53)  // token=':='
            )
            {
                _res = expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // disjunction: conjunction (('or' conjunction))+ | conjunction
    public ExprTy disjunction_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DISJUNCTION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, DISJUNCTION_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // conjunction (('or' conjunction))+
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            ExprTy[] b;
            if (
                (a = conjunction_rule()) != null  // conjunction
                &&
                (b = _loop1_126_rule()) != null  // (('or' conjunction))+
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createOr(this.insertInFront(a, b), startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, DISJUNCTION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // conjunction
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, CONJUNCTION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, CONJUNCTION_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // inversion (('and' inversion))+
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            ExprTy[] b;
            if (
                (a = inversion_rule()) != null  // inversion
                &&
                (b = _loop1_127_rule()) != null  // (('and' inversion))+
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createAnd(this.insertInFront(a, b), startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, CONJUNCTION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // inversion
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVERSION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVERSION_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // 'not' inversion
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy a;
            if (
                (_keyword = expect(686)) != null  // token='not'
                &&
                (a = inversion_rule()) != null  // inversion
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createUnaryOp(UnaryOpTy.Not, a, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, INVERSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // comparison
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // bitwise_or compare_op_bitwise_or_pair+
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            CmpopExprPair[] b;
            if (
                (a = bitwise_or_rule()) != null  // bitwise_or
                &&
                (b = _loop1_128_rule()) != null  // compare_op_bitwise_or_pair+
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createComparison(a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // bitwise_or
            if (errorIndicator) {
                return null;
            }
            ExprTy bitwise_or_var;
            if (
                (bitwise_or_var = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = bitwise_or_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // eq_bitwise_or
            if (errorIndicator) {
                return null;
            }
            CmpopExprPair eq_bitwise_or_var;
            if (
                (eq_bitwise_or_var = eq_bitwise_or_rule()) != null  // eq_bitwise_or
            )
            {
                _res = eq_bitwise_or_var;
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // noteq_bitwise_or
            if (errorIndicator) {
                return null;
            }
            CmpopExprPair noteq_bitwise_or_var;
            if (
                (noteq_bitwise_or_var = noteq_bitwise_or_rule()) != null  // noteq_bitwise_or
            )
            {
                _res = noteq_bitwise_or_var;
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // lte_bitwise_or
            if (errorIndicator) {
                return null;
            }
            CmpopExprPair lte_bitwise_or_var;
            if (
                (lte_bitwise_or_var = lte_bitwise_or_rule()) != null  // lte_bitwise_or
            )
            {
                _res = lte_bitwise_or_var;
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // lt_bitwise_or
            if (errorIndicator) {
                return null;
            }
            CmpopExprPair lt_bitwise_or_var;
            if (
                (lt_bitwise_or_var = lt_bitwise_or_rule()) != null  // lt_bitwise_or
            )
            {
                _res = lt_bitwise_or_var;
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // gte_bitwise_or
            if (errorIndicator) {
                return null;
            }
            CmpopExprPair gte_bitwise_or_var;
            if (
                (gte_bitwise_or_var = gte_bitwise_or_rule()) != null  // gte_bitwise_or
            )
            {
                _res = gte_bitwise_or_var;
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // gt_bitwise_or
            if (errorIndicator) {
                return null;
            }
            CmpopExprPair gt_bitwise_or_var;
            if (
                (gt_bitwise_or_var = gt_bitwise_or_rule()) != null  // gt_bitwise_or
            )
            {
                _res = gt_bitwise_or_var;
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // notin_bitwise_or
            if (errorIndicator) {
                return null;
            }
            CmpopExprPair notin_bitwise_or_var;
            if (
                (notin_bitwise_or_var = notin_bitwise_or_rule()) != null  // notin_bitwise_or
            )
            {
                _res = notin_bitwise_or_var;
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // in_bitwise_or
            if (errorIndicator) {
                return null;
            }
            CmpopExprPair in_bitwise_or_var;
            if (
                (in_bitwise_or_var = in_bitwise_or_rule()) != null  // in_bitwise_or
            )
            {
                _res = in_bitwise_or_var;
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // isnot_bitwise_or
            if (errorIndicator) {
                return null;
            }
            CmpopExprPair isnot_bitwise_or_var;
            if (
                (isnot_bitwise_or_var = isnot_bitwise_or_rule()) != null  // isnot_bitwise_or
            )
            {
                _res = isnot_bitwise_or_var;
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        { // is_bitwise_or
            if (errorIndicator) {
                return null;
            }
            CmpopExprPair is_bitwise_or_var;
            if (
                (is_bitwise_or_var = is_bitwise_or_rule()) != null  // is_bitwise_or
            )
            {
                _res = is_bitwise_or_var;
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (CmpopExprPair)_res;
    }

    // eq_bitwise_or: '==' bitwise_or
    public CmpopExprPair eq_bitwise_or_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '==' bitwise_or
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(27)) != null  // token='=='
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(CmpOpTy.Eq, a);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (CmpopExprPair)_res;
    }

    // noteq_bitwise_or: ('!=') bitwise_or
    public CmpopExprPair noteq_bitwise_or_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ('!=') bitwise_or
            if (errorIndicator) {
                return null;
            }
            Token _tmp_129_var;
            ExprTy a;
            if (
                (_tmp_129_var = _tmp_129_rule()) != null  // '!='
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(CmpOpTy.NotEq, a);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (CmpopExprPair)_res;
    }

    // lte_bitwise_or: '<=' bitwise_or
    public CmpopExprPair lte_bitwise_or_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '<=' bitwise_or
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(29)) != null  // token='<='
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(CmpOpTy.LtE, a);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (CmpopExprPair)_res;
    }

    // lt_bitwise_or: '<' bitwise_or
    public CmpopExprPair lt_bitwise_or_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '<' bitwise_or
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(20)) != null  // token='<'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(CmpOpTy.Lt, a);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (CmpopExprPair)_res;
    }

    // gte_bitwise_or: '>=' bitwise_or
    public CmpopExprPair gte_bitwise_or_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '>=' bitwise_or
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(30)) != null  // token='>='
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(CmpOpTy.GtE, a);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (CmpopExprPair)_res;
    }

    // gt_bitwise_or: '>' bitwise_or
    public CmpopExprPair gt_bitwise_or_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '>' bitwise_or
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(21)) != null  // token='>'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(CmpOpTy.Gt, a);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (CmpopExprPair)_res;
    }

    // notin_bitwise_or: 'not' 'in' bitwise_or
    public CmpopExprPair notin_bitwise_or_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'not' 'in' bitwise_or
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _keyword_1;
            ExprTy a;
            if (
                (_keyword = expect(686)) != null  // token='not'
                &&
                (_keyword_1 = expect(677)) != null  // token='in'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(CmpOpTy.NotIn, a);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (CmpopExprPair)_res;
    }

    // in_bitwise_or: 'in' bitwise_or
    public CmpopExprPair in_bitwise_or_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'in' bitwise_or
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy a;
            if (
                (_keyword = expect(677)) != null  // token='in'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(CmpOpTy.In, a);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (CmpopExprPair)_res;
    }

    // isnot_bitwise_or: 'is' 'not' bitwise_or
    public CmpopExprPair isnot_bitwise_or_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'is' 'not' bitwise_or
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _keyword_1;
            ExprTy a;
            if (
                (_keyword = expect(687)) != null  // token='is'
                &&
                (_keyword_1 = expect(686)) != null  // token='not'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(CmpOpTy.IsNot, a);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (CmpopExprPair)_res;
    }

    // is_bitwise_or: 'is' bitwise_or
    public CmpopExprPair is_bitwise_or_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'is' bitwise_or
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy a;
            if (
                (_keyword = expect(687)) != null  // token='is'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new CmpopExprPair(CmpOpTy.Is, a);
                return (CmpopExprPair)_res;
            }
            reset(_mark);
        }
        _res = null;
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // bitwise_or '|' bitwise_xor
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createBinaryOp(OperatorTy.BitOr, a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // bitwise_xor
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // bitwise_xor '^' bitwise_and
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createBinaryOp(OperatorTy.BitXor, a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // bitwise_and
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // bitwise_and '&' shift_expr
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createBinaryOp(OperatorTy.BitAnd, a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // shift_expr
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // shift_expr '<<' sum
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createBinaryOp(OperatorTy.LShift, a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // shift_expr '>>' sum
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createBinaryOp(OperatorTy.RShift, a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // sum
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // sum '+' term
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createBinaryOp(OperatorTy.Add, a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // sum '-' term
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createBinaryOp(OperatorTy.Sub, a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // term
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // term '*' factor
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createBinaryOp(OperatorTy.Mult, a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // term '/' factor
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createBinaryOp(OperatorTy.Div, a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // term '//' factor
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createBinaryOp(OperatorTy.FloorDiv, a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // term '%' factor
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createBinaryOp(OperatorTy.Mod, a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // term '@' factor
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = checkVersion(5, "The '@' operator is", factory.createBinaryOp(OperatorTy.MatMult, a, b, startToken.sourceRange.withEnd(endToken.sourceRange)));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // factor
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, FACTOR_ID)) {
            _res = (ExprTy)cache.getResult(_mark, FACTOR_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '+' factor
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createUnaryOp(UnaryOpTy.UAdd, a, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, FACTOR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '-' factor
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createUnaryOp(UnaryOpTy.USub, a, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, FACTOR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '~' factor
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createUnaryOp(UnaryOpTy.Invert, a, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, FACTOR_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // power
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // await_primary '**' factor
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createBinaryOp(OperatorTy.Pow, a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // await_primary
            if (errorIndicator) {
                return null;
            }
            ExprTy await_primary_var;
            if (
                (await_primary_var = await_primary_rule()) != null  // await_primary
            )
            {
                _res = await_primary_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // await_primary: AWAIT primary | primary
    public ExprTy await_primary_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, AWAIT_PRIMARY_ID)) {
            _res = (ExprTy)cache.getResult(_mark, AWAIT_PRIMARY_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // AWAIT primary
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            Token await_var;
            if (
                (await_var = expect(Token.Kind.AWAIT)) != null  // token='AWAIT'
                &&
                (a = primary_rule()) != null  // primary
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = checkVersion(5, "Await expressions are", factory.createAwait(a, startToken.sourceRange.withEnd(endToken.sourceRange)));
                cache.putResult(_mark, AWAIT_PRIMARY_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // primary
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // primary '.' NAME
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createGetAttribute(a, ((ExprTy.Name) b).id, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // primary genexp
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            ExprTy b;
            if (
                (a = primary_rule()) != null  // primary
                &&
                (b = genexp_rule()) != null  // genexp
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createCall(a, new ExprTy[] {b}, EMPTY_KEYWORD_ARRAY, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // primary '(' arguments? ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ExprTy b;
            if (
                (a = primary_rule()) != null  // primary
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                ((b = _tmp_130_rule()) != null || true)  // arguments?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createCall(a, b != null ? ((ExprTy.Call) b).args : EMPTY_EXPR_ARRAY, b != null ? ((ExprTy.Call) b).keywords : EMPTY_KEYWORD_ARRAY, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // primary '[' slices ']'
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createSubscript(a, b, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // atom
            if (errorIndicator) {
                return null;
            }
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

    // slices: slice !',' | ','.(slice | starred_expression)+ ','?
    public ExprTy slices_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // slice !','
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            if (
                (a = slice_rule()) != null  // slice
                &&
                genLookahead_expect(false, 12)  // token=','
            )
            {
                _res = a;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // ','.(slice | starred_expression)+ ','?
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            ExprTy[] a;
            if (
                (a = (ExprTy[])_gather_131_rule()) != null  // ','.(slice | starred_expression)+
                &&
                ((_opt_var = _tmp_133_rule()) != null || true)  // ','?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(a, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // slice: expression? ':' expression? [':' expression?] | named_expression
    public ExprTy slice_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // expression? ':' expression? [':' expression?]
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            ExprTy b;
            ExprTy c;
            if (
                ((a = _tmp_134_rule()) != null || true)  // expression?
                &&
                (_literal = expect(11)) != null  // token=':'
                &&
                ((b = _tmp_135_rule()) != null || true)  // expression?
                &&
                ((c = _tmp_136_rule()) != null || true)  // [':' expression?]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createSlice(a, b, c, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // named_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            if (
                (a = named_expression_rule()) != null  // named_expression
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

    // atom:
    //     | NAME
    //     | 'True'
    //     | 'False'
    //     | 'None'
    //     | &(STRING | FSTRING_START) strings
    //     | NUMBER
    //     | &'(' (tuple | group | genexp)
    //     | &'[' (list | listcomp)
    //     | &'{' (dict | set | dictcomp | setcomp)
    //     | '...'
    public ExprTy atom_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // NAME
            if (errorIndicator) {
                return null;
            }
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
        { // 'True'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(681)) != null  // token='True'
            )
            {
                _res = factory.createBooleanLiteral(true, startToken.sourceRange);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'False'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(682)) != null  // token='False'
            )
            {
                _res = factory.createBooleanLiteral(false, startToken.sourceRange);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'None'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = expect(680)) != null  // token='None'
            )
            {
                _res = factory.createNone(startToken.sourceRange);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // &(STRING | FSTRING_START) strings
            if (errorIndicator) {
                return null;
            }
            ExprTy strings_var;
            if (
                genLookahead__tmp_137_rule(true)
                &&
                (strings_var = strings_rule()) != null  // strings
            )
            {
                _res = strings_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // NUMBER
            if (errorIndicator) {
                return null;
            }
            ExprTy number_var;
            if (
                (number_var = number_token()) != null  // NUMBER
            )
            {
                _res = number_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // &'(' (tuple | group | genexp)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_138_var;
            if (
                genLookahead_expect(true, 7)  // token='('
                &&
                (_tmp_138_var = _tmp_138_rule()) != null  // tuple | group | genexp
            )
            {
                _res = _tmp_138_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // &'[' (list | listcomp)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_139_var;
            if (
                genLookahead_expect(true, 9)  // token='['
                &&
                (_tmp_139_var = _tmp_139_rule()) != null  // list | listcomp
            )
            {
                _res = _tmp_139_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // &'{' (dict | set | dictcomp | setcomp)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_140_var;
            if (
                genLookahead_expect(true, 25)  // token='{'
                &&
                (_tmp_140_var = _tmp_140_rule()) != null  // dict | set | dictcomp | setcomp
            )
            {
                _res = _tmp_140_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '...'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(52)) != null  // token='...'
            )
            {
                _res = factory.createEllipsis(startToken.sourceRange);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // group: '(' (yield_expr | named_expression) ')' | invalid_group
    public ExprTy group_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '(' (yield_expr | named_expression) ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (a = _tmp_141_rule()) != null  // yield_expr | named_expression
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                _res = a;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_group
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_group_var;
            if (
                (invalid_group_var = invalid_group_rule()) != null  // invalid_group
            )
            {
                _res = invalid_group_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // lambdef: 'lambda' lambda_params? ':' expression
    public ExprTy lambdef_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // 'lambda' lambda_params? ':' expression
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            ArgumentsTy a;
            ExprTy b;
            if (
                (_keyword = expect(683)) != null  // token='lambda'
                &&
                ((a = _tmp_142_rule()) != null || true)  // lambda_params?
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
                _res = factory.createLambda(a == null ? factory.emptyArguments() : a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // lambda_params: invalid_lambda_parameters | lambda_parameters
    public ArgumentsTy lambda_params_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (callInvalidRules) { // invalid_lambda_parameters
            if (errorIndicator) {
                return null;
            }
            Object invalid_lambda_parameters_var;
            if (
                (invalid_lambda_parameters_var = invalid_lambda_parameters_rule()) != null  // invalid_lambda_parameters
            )
            {
                _res = invalid_lambda_parameters_var;
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // lambda_parameters
            if (errorIndicator) {
                return null;
            }
            ArgumentsTy lambda_parameters_var;
            if (
                (lambda_parameters_var = lambda_parameters_rule()) != null  // lambda_parameters
            )
            {
                _res = lambda_parameters_var;
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_slash_no_default lambda_param_no_default* lambda_param_with_default* lambda_star_etc?
            if (errorIndicator) {
                return null;
            }
            ArgTy[] a;
            ArgTy[] b;
            NameDefaultPair[] c;
            StarEtc d;
            if (
                (a = lambda_slash_no_default_rule()) != null  // lambda_slash_no_default
                &&
                (b = (ArgTy[])_loop0_143_rule()) != null  // lambda_param_no_default*
                &&
                (c = _loop0_144_rule()) != null  // lambda_param_with_default*
                &&
                ((d = _tmp_145_rule()) != null || true)  // lambda_star_etc?
            )
            {
                _res = checkVersion(8, "Positional-only parameters are", factory.createArguments(a, null, b, c, d));
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // lambda_slash_with_default lambda_param_with_default* lambda_star_etc?
            if (errorIndicator) {
                return null;
            }
            SlashWithDefault a;
            NameDefaultPair[] b;
            StarEtc c;
            if (
                (a = lambda_slash_with_default_rule()) != null  // lambda_slash_with_default
                &&
                (b = _loop0_146_rule()) != null  // lambda_param_with_default*
                &&
                ((c = _tmp_147_rule()) != null || true)  // lambda_star_etc?
            )
            {
                _res = checkVersion(8, "Positional-only parameters are", factory.createArguments(null, a, null, b, c));
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // lambda_param_no_default+ lambda_param_with_default* lambda_star_etc?
            if (errorIndicator) {
                return null;
            }
            ArgTy[] a;
            NameDefaultPair[] b;
            StarEtc c;
            if (
                (a = (ArgTy[])_loop1_148_rule()) != null  // lambda_param_no_default+
                &&
                (b = _loop0_149_rule()) != null  // lambda_param_with_default*
                &&
                ((c = _tmp_150_rule()) != null || true)  // lambda_star_etc?
            )
            {
                _res = factory.createArguments(null, null, a, b, c);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // lambda_param_with_default+ lambda_star_etc?
            if (errorIndicator) {
                return null;
            }
            NameDefaultPair[] a;
            StarEtc b;
            if (
                (a = _loop1_151_rule()) != null  // lambda_param_with_default+
                &&
                ((b = _tmp_152_rule()) != null || true)  // lambda_star_etc?
            )
            {
                _res = factory.createArguments(null, null, null, a, b);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        { // lambda_star_etc
            if (errorIndicator) {
                return null;
            }
            StarEtc a;
            if (
                (a = lambda_star_etc_rule()) != null  // lambda_star_etc
            )
            {
                _res = factory.createArguments(null, null, null, null, a);
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgumentsTy)_res;
    }

    // lambda_slash_no_default:
    //     | lambda_param_no_default+ '/' ','
    //     | lambda_param_no_default+ '/' &':'
    public ArgTy[] lambda_slash_no_default_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_param_no_default+ '/' ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ArgTy[] a;
            if (
                (a = (ArgTy[])_loop1_153_rule()) != null  // lambda_param_no_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                (_literal_1 = expect(12)) != null  // token=','
            )
            {
                _res = a;
                return (ArgTy[])_res;
            }
            reset(_mark);
        }
        { // lambda_param_no_default+ '/' &':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy[] a;
            if (
                (a = (ArgTy[])_loop1_154_rule()) != null  // lambda_param_no_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                genLookahead_expect(true, 11)  // token=':'
            )
            {
                _res = a;
                return (ArgTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy[])_res;
    }

    // lambda_slash_with_default:
    //     | lambda_param_no_default* lambda_param_with_default+ '/' ','
    //     | lambda_param_no_default* lambda_param_with_default+ '/' &':'
    public SlashWithDefault lambda_slash_with_default_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_param_no_default* lambda_param_with_default+ '/' ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ArgTy[] a;
            NameDefaultPair[] b;
            if (
                (a = _loop0_155_rule()) != null  // lambda_param_no_default*
                &&
                (b = _loop1_156_rule()) != null  // lambda_param_with_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                (_literal_1 = expect(12)) != null  // token=','
            )
            {
                _res = new SlashWithDefault(a,b);
                return (SlashWithDefault)_res;
            }
            reset(_mark);
        }
        { // lambda_param_no_default* lambda_param_with_default+ '/' &':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy[] a;
            NameDefaultPair[] b;
            if (
                (a = _loop0_157_rule()) != null  // lambda_param_no_default*
                &&
                (b = _loop1_158_rule()) != null  // lambda_param_with_default+
                &&
                (_literal = expect(17)) != null  // token='/'
                &&
                genLookahead_expect(true, 11)  // token=':'
            )
            {
                _res = new SlashWithDefault(a,b);
                return (SlashWithDefault)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (SlashWithDefault)_res;
    }

    // lambda_star_etc:
    //     | invalid_lambda_star_etc
    //     | '*' lambda_param_no_default lambda_param_maybe_default* lambda_kwds?
    //     | '*' ',' lambda_param_maybe_default+ lambda_kwds?
    //     | lambda_kwds
    public StarEtc lambda_star_etc_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (callInvalidRules) { // invalid_lambda_star_etc
            if (errorIndicator) {
                return null;
            }
            Object invalid_lambda_star_etc_var;
            if (
                (invalid_lambda_star_etc_var = invalid_lambda_star_etc_rule()) != null  // invalid_lambda_star_etc
            )
            {
                _res = invalid_lambda_star_etc_var;
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        { // '*' lambda_param_no_default lambda_param_maybe_default* lambda_kwds?
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy a;
            NameDefaultPair[] b;
            ArgTy c;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = lambda_param_no_default_rule()) != null  // lambda_param_no_default
                &&
                (b = _loop0_159_rule()) != null  // lambda_param_maybe_default*
                &&
                ((c = _tmp_160_rule()) != null || true)  // lambda_kwds?
            )
            {
                _res = new StarEtc(a,b,c);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        { // '*' ',' lambda_param_maybe_default+ lambda_kwds?
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            NameDefaultPair[] b;
            ArgTy c;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (_literal_1 = expect(12)) != null  // token=','
                &&
                (b = _loop1_161_rule()) != null  // lambda_param_maybe_default+
                &&
                ((c = _tmp_162_rule()) != null || true)  // lambda_kwds?
            )
            {
                _res = new StarEtc(null,b,c);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        { // lambda_kwds
            if (errorIndicator) {
                return null;
            }
            ArgTy a;
            if (
                (a = lambda_kwds_rule()) != null  // lambda_kwds
            )
            {
                _res = new StarEtc(null,null,a);
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StarEtc)_res;
    }

    // lambda_kwds: invalid_lambda_kwds | '**' lambda_param_no_default
    public ArgTy lambda_kwds_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (callInvalidRules) { // invalid_lambda_kwds
            if (errorIndicator) {
                return null;
            }
            ArgTy invalid_lambda_kwds_var;
            if (
                (invalid_lambda_kwds_var = invalid_lambda_kwds_rule()) != null  // invalid_lambda_kwds
            )
            {
                _res = invalid_lambda_kwds_var;
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        { // '**' lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy a;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (a = lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = a;
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // lambda_param_no_default: lambda_param ',' | lambda_param &':'
    public ArgTy lambda_param_no_default_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_param ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy a;
            if (
                (a = lambda_param_rule()) != null  // lambda_param
                &&
                (_literal = expect(12)) != null  // token=','
            )
            {
                _res = a;
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        { // lambda_param &':'
            if (errorIndicator) {
                return null;
            }
            ArgTy a;
            if (
                (a = lambda_param_rule()) != null  // lambda_param
                &&
                genLookahead_expect(true, 11)  // token=':'
            )
            {
                _res = a;
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // lambda_param_with_default: lambda_param default ',' | lambda_param default &':'
    public NameDefaultPair lambda_param_with_default_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_param default ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy a;
            ExprTy c;
            if (
                (a = lambda_param_rule()) != null  // lambda_param
                &&
                (c = default_rule()) != null  // default
                &&
                (_literal = expect(12)) != null  // token=','
            )
            {
                _res = new NameDefaultPair(factory.createArgument(a.arg, a.annotation, null, a.getSourceRange()), c);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        { // lambda_param default &':'
            if (errorIndicator) {
                return null;
            }
            ArgTy a;
            ExprTy c;
            if (
                (a = lambda_param_rule()) != null  // lambda_param
                &&
                (c = default_rule()) != null  // default
                &&
                genLookahead_expect(true, 11)  // token=':'
            )
            {
                _res = new NameDefaultPair(factory.createArgument(a.arg, a.annotation, null, a.getSourceRange()), c);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (NameDefaultPair)_res;
    }

    // lambda_param_maybe_default: lambda_param default? ',' | lambda_param default? &':'
    public NameDefaultPair lambda_param_maybe_default_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_param default? ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy a;
            ExprTy c;
            if (
                (a = lambda_param_rule()) != null  // lambda_param
                &&
                ((c = default_rule()) != null || true)  // default?
                &&
                (_literal = expect(12)) != null  // token=','
            )
            {
                _res = new NameDefaultPair(factory.createArgument(a.arg, a.annotation, null, a.getSourceRange()), c);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        { // lambda_param default? &':'
            if (errorIndicator) {
                return null;
            }
            ArgTy a;
            ExprTy c;
            if (
                (a = lambda_param_rule()) != null  // lambda_param
                &&
                ((c = default_rule()) != null || true)  // default?
                &&
                genLookahead_expect(true, 11)  // token=':'
            )
            {
                _res = new NameDefaultPair(factory.createArgument(a.arg, a.annotation, null, a.getSourceRange()), c);
                return (NameDefaultPair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (NameDefaultPair)_res;
    }

    // lambda_param: NAME
    public ArgTy lambda_param_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // NAME
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            if (
                (a = name_token()) != null  // NAME
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createArgument(((ExprTy.Name) a).id, null, null, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // fstring_middle: fstring_replacement_field | FSTRING_MIDDLE
    public ExprTy fstring_middle_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // fstring_replacement_field
            if (errorIndicator) {
                return null;
            }
            ExprTy fstring_replacement_field_var;
            if (
                (fstring_replacement_field_var = fstring_replacement_field_rule()) != null  // fstring_replacement_field
            )
            {
                _res = fstring_replacement_field_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // FSTRING_MIDDLE
            if (errorIndicator) {
                return null;
            }
            Token t;
            if (
                (t = expect(Token.Kind.FSTRING_MIDDLE)) != null  // token='FSTRING_MIDDLE'
            )
            {
                _res = constantFromToken(t);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // fstring_replacement_field:
    //     | '{' (yield_expr | star_expressions) '='? fstring_conversion? fstring_full_format_spec? '}'
    //     | invalid_replacement_field
    public ExprTy fstring_replacement_field_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '{' (yield_expr | star_expressions) '='? fstring_conversion? fstring_full_format_spec? '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            ResultTokenWithMetadata conversion;
            Token debug_expr;
            ResultTokenWithMetadata format;
            Token rbrace;
            if (
                (_literal = expect(25)) != null  // token='{'
                &&
                (a = _tmp_163_rule()) != null  // yield_expr | star_expressions
                &&
                ((debug_expr = expect(22)) != null || true)  // '='?
                &&
                ((conversion = _tmp_164_rule()) != null || true)  // fstring_conversion?
                &&
                ((format = _tmp_165_rule()) != null || true)  // fstring_full_format_spec?
                &&
                (rbrace = expect(26)) != null  // token='}'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = formattedValue(a, debug_expr, conversion, format, rbrace, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_replacement_field
            if (errorIndicator) {
                return null;
            }
            Object invalid_replacement_field_var;
            if (
                (invalid_replacement_field_var = invalid_replacement_field_rule()) != null  // invalid_replacement_field
            )
            {
                _res = invalid_replacement_field_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // fstring_conversion: "!" NAME
    public ResultTokenWithMetadata fstring_conversion_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // "!" NAME
            if (errorIndicator) {
                return null;
            }
            ExprTy conv;
            Token conv_token;
            if (
                (conv_token = expect(54)) != null  // token='!'
                &&
                (conv = name_token()) != null  // NAME
            )
            {
                _res = checkFstringConversion(conv_token, conv);
                return (ResultTokenWithMetadata)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ResultTokenWithMetadata)_res;
    }

    // fstring_full_format_spec: ':' fstring_format_spec*
    public ResultTokenWithMetadata fstring_full_format_spec_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // ':' fstring_format_spec*
            if (errorIndicator) {
                return null;
            }
            Token colon;
            ExprTy[] spec;
            if (
                (colon = expect(11)) != null  // token=':'
                &&
                (spec = _loop0_166_rule()) != null  // fstring_format_spec*
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = setupFullFormatSpec(colon, spec, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ResultTokenWithMetadata)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ResultTokenWithMetadata)_res;
    }

    // fstring_format_spec: FSTRING_MIDDLE | fstring_replacement_field
    public ExprTy fstring_format_spec_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // FSTRING_MIDDLE
            if (errorIndicator) {
                return null;
            }
            Token t;
            if (
                (t = expect(Token.Kind.FSTRING_MIDDLE)) != null  // token='FSTRING_MIDDLE'
            )
            {
                _res = decodedConstantFromToken(t);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // fstring_replacement_field
            if (errorIndicator) {
                return null;
            }
            ExprTy fstring_replacement_field_var;
            if (
                (fstring_replacement_field_var = fstring_replacement_field_rule()) != null  // fstring_replacement_field
            )
            {
                _res = fstring_replacement_field_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // fstring: FSTRING_START fstring_middle* FSTRING_END
    public ExprTy fstring_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // FSTRING_START fstring_middle* FSTRING_END
            if (errorIndicator) {
                return null;
            }
            Token a;
            ExprTy[] b;
            Token c;
            if (
                (a = expect(Token.Kind.FSTRING_START)) != null  // token='FSTRING_START'
                &&
                (b = _loop0_167_rule()) != null  // fstring_middle*
                &&
                (c = expect(Token.Kind.FSTRING_END)) != null  // token='FSTRING_END'
            )
            {
                _res = joinedStr(a, b, c);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // string: STRING
    public ExprTy string_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // STRING
            if (errorIndicator) {
                return null;
            }
            Token s;
            if (
                (s = (Token)string_token()) != null  // STRING
            )
            {
                _res = constantFromString(s);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // strings: ((fstring | string))+
    public ExprTy strings_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STRINGS_ID)) {
            _res = (ExprTy)cache.getResult(_mark, STRINGS_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // ((fstring | string))+
            if (errorIndicator) {
                return null;
            }
            ExprTy[] a;
            if (
                (a = (ExprTy[])_loop1_168_rule()) != null  // ((fstring | string))+
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = this.concatenateStrings(a, startToken.sourceRange.withEnd(endToken.sourceRange));
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '[' star_named_expressions? ']'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            if (
                (_literal = expect(9)) != null  // token='['
                &&
                ((a = _tmp_169_rule()) != null || true)  // star_named_expressions?
                &&
                (_literal_1 = expect(10)) != null  // token=']'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createList(a, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // tuple: '(' [star_named_expression ',' star_named_expressions?] ')'
    public ExprTy tuple_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '(' [star_named_expression ',' star_named_expressions?] ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                ((a = _tmp_170_rule()) != null || true)  // [star_named_expression ',' star_named_expressions?]
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(a, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // set: '{' star_named_expressions '}'
    public ExprTy set_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '{' star_named_expressions '}'
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createSet(a, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // dict: '{' double_starred_kvpairs? '}' | '{' invalid_double_starred_kvpairs '}'
    public ExprTy dict_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '{' double_starred_kvpairs? '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            KeyValuePair[] a;
            if (
                (_literal = expect(25)) != null  // token='{'
                &&
                ((a = _tmp_171_rule()) != null || true)  // double_starred_kvpairs?
                &&
                (_literal_1 = expect(26)) != null  // token='}'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createDict(extractKeys(a), extractValues(a), startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '{' invalid_double_starred_kvpairs '}'
            if (errorIndicator) {
                return null;
            }
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
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // double_starred_kvpairs: ','.double_starred_kvpair+ ','?
    public KeyValuePair[] double_starred_kvpairs_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.double_starred_kvpair+ ','?
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            KeyValuePair[] a;
            if (
                (a = (KeyValuePair[])_gather_172_rule()) != null  // ','.double_starred_kvpair+
                &&
                ((_opt_var = (Token)_tmp_174_rule()) != null || true)  // ','?
            )
            {
                _res = a;
                return (KeyValuePair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeyValuePair[])_res;
    }

    // double_starred_kvpair: '**' bitwise_or | kvpair
    public KeyValuePair double_starred_kvpair_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '**' bitwise_or
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (a = bitwise_or_rule()) != null  // bitwise_or
            )
            {
                _res = new KeyValuePair(null,a);
                return (KeyValuePair)_res;
            }
            reset(_mark);
        }
        { // kvpair
            if (errorIndicator) {
                return null;
            }
            KeyValuePair kvpair_var;
            if (
                (kvpair_var = kvpair_rule()) != null  // kvpair
            )
            {
                _res = kvpair_var;
                return (KeyValuePair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeyValuePair)_res;
    }

    // kvpair: expression ':' expression
    public KeyValuePair kvpair_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression ':' expression
            if (errorIndicator) {
                return null;
            }
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
                return (KeyValuePair)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeyValuePair)_res;
    }

    // for_if_clauses: for_if_clause+
    public ComprehensionTy[] for_if_clauses_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // for_if_clause+
            if (errorIndicator) {
                return null;
            }
            ComprehensionTy[] a;
            if (
                (a = (ComprehensionTy[])_loop1_175_rule()) != null  // for_if_clause+
            )
            {
                _res = a;
                return (ComprehensionTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ComprehensionTy[])_res;
    }

    // for_if_clause:
    //     | ASYNC 'for' star_targets 'in' ~ disjunction (('if' disjunction))*
    //     | 'for' star_targets 'in' ~ disjunction (('if' disjunction))*
    //     | invalid_for_target
    public ComprehensionTy for_if_clause_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // ASYNC 'for' star_targets 'in' ~ disjunction (('if' disjunction))*
            if (errorIndicator) {
                return null;
            }
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
                (_keyword = expect(671)) != null  // token='for'
                &&
                (a = star_targets_rule()) != null  // star_targets
                &&
                (_keyword_1 = expect(677)) != null  // token='in'
                &&
                (_cut_var = 1) != 0
                &&
                (b = disjunction_rule()) != null  // disjunction
                &&
                (c = (ExprTy[])_loop0_176_rule()) != null  // (('if' disjunction))*
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = checkVersion(6, "Async comprehensions are", factory.createComprehension(a, b, c, true, startToken.sourceRange.withEnd(endToken.sourceRange)));
                return (ComprehensionTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        { // 'for' star_targets 'in' ~ disjunction (('if' disjunction))*
            if (errorIndicator) {
                return null;
            }
            int _cut_var = 0;
            Token _keyword;
            Token _keyword_1;
            ExprTy a;
            ExprTy b;
            ExprTy[] c;
            if (
                (_keyword = expect(671)) != null  // token='for'
                &&
                (a = star_targets_rule()) != null  // star_targets
                &&
                (_keyword_1 = expect(677)) != null  // token='in'
                &&
                (_cut_var = 1) != 0
                &&
                (b = disjunction_rule()) != null  // disjunction
                &&
                (c = (ExprTy[])_loop0_177_rule()) != null  // (('if' disjunction))*
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createComprehension(a, b, c, false, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ComprehensionTy)_res;
            }
            reset(_mark);
            if (_cut_var != 0) {
                return null;
            }
        }
        if (callInvalidRules) { // invalid_for_target
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_for_target_var;
            if (
                (invalid_for_target_var = invalid_for_target_rule()) != null  // invalid_for_target
            )
            {
                _res = invalid_for_target_var;
                return (ComprehensionTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ComprehensionTy)_res;
    }

    // listcomp: '[' named_expression for_if_clauses ']' | invalid_comprehension
    public ExprTy listcomp_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '[' named_expression for_if_clauses ']'
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createListComprehension(a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_comprehension
            if (errorIndicator) {
                return null;
            }
            Object invalid_comprehension_var;
            if (
                (invalid_comprehension_var = invalid_comprehension_rule()) != null  // invalid_comprehension
            )
            {
                _res = invalid_comprehension_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // setcomp: '{' named_expression for_if_clauses '}' | invalid_comprehension
    public ExprTy setcomp_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '{' named_expression for_if_clauses '}'
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createSetComprehension(a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_comprehension
            if (errorIndicator) {
                return null;
            }
            Object invalid_comprehension_var;
            if (
                (invalid_comprehension_var = invalid_comprehension_rule()) != null  // invalid_comprehension
            )
            {
                _res = invalid_comprehension_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // genexp:
    //     | '(' (assignment_expression | expression !':=') for_if_clauses ')'
    //     | invalid_comprehension
    public ExprTy genexp_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '(' (assignment_expression | expression !':=') for_if_clauses ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ComprehensionTy[] b;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                (a = _tmp_178_rule()) != null  // assignment_expression | expression !':='
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
                _res = factory.createGenerator(a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_comprehension
            if (errorIndicator) {
                return null;
            }
            Object invalid_comprehension_var;
            if (
                (invalid_comprehension_var = invalid_comprehension_rule()) != null  // invalid_comprehension
            )
            {
                _res = invalid_comprehension_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // dictcomp: '{' kvpair for_if_clauses '}' | invalid_dict_comprehension
    public ExprTy dictcomp_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // '{' kvpair for_if_clauses '}'
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createDictComprehension(a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_dict_comprehension
            if (errorIndicator) {
                return null;
            }
            Object invalid_dict_comprehension_var;
            if (
                (invalid_dict_comprehension_var = invalid_dict_comprehension_rule()) != null  // invalid_dict_comprehension
            )
            {
                _res = invalid_dict_comprehension_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // arguments: args ','? &')' | invalid_arguments
    public ExprTy arguments_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, ARGUMENTS_ID)) {
            _res = (ExprTy)cache.getResult(_mark, ARGUMENTS_ID);
            return (ExprTy)_res;
        }
        { // args ','? &')'
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            ExprTy a;
            if (
                (a = args_rule()) != null  // args
                &&
                ((_opt_var = _tmp_179_rule()) != null || true)  // ','?
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
            if (errorIndicator) {
                return null;
            }
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

    // args:
    //     | ','.(starred_expression | (assignment_expression | expression !':=') !'=')+ [',' kwargs]
    //     | kwargs
    public ExprTy args_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // ','.(starred_expression | (assignment_expression | expression !':=') !'=')+ [',' kwargs]
            if (errorIndicator) {
                return null;
            }
            ExprTy[] a;
            KeywordOrStarred[] b;
            if (
                (a = (ExprTy[])_gather_180_rule()) != null  // ','.(starred_expression | (assignment_expression | expression !':=') !'=')+
                &&
                ((b = _tmp_182_rule()) != null || true)  // [',' kwargs]
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = this.collectCallSequences(a, b, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // kwargs
            if (errorIndicator) {
                return null;
            }
            KeywordOrStarred[] a;
            if (
                (a = kwargs_rule()) != null  // kwargs
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createCall(dummyName(), extractStarredExpressions(a), deleteStarredExpressions(a), startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // kwargs:
    //     | ','.kwarg_or_starred+ ',' ','.kwarg_or_double_starred+
    //     | ','.kwarg_or_starred+
    //     | ','.kwarg_or_double_starred+
    public KeywordOrStarred[] kwargs_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.kwarg_or_starred+ ',' ','.kwarg_or_double_starred+
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            KeywordOrStarred[] a;
            KeywordOrStarred[] b;
            if (
                (a = (KeywordOrStarred[])_gather_183_rule()) != null  // ','.kwarg_or_starred+
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (b = (KeywordOrStarred[])_gather_185_rule()) != null  // ','.kwarg_or_double_starred+
            )
            {
                _res = this.join(a,b);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        { // ','.kwarg_or_starred+
            if (errorIndicator) {
                return null;
            }
            KeywordOrStarred[] _gather_187_var;
            if (
                (_gather_187_var = (KeywordOrStarred[])_gather_187_rule()) != null  // ','.kwarg_or_starred+
            )
            {
                _res = _gather_187_var;
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        { // ','.kwarg_or_double_starred+
            if (errorIndicator) {
                return null;
            }
            KeywordOrStarred[] _gather_189_var;
            if (
                (_gather_189_var = (KeywordOrStarred[])_gather_189_rule()) != null  // ','.kwarg_or_double_starred+
            )
            {
                _res = _gather_189_var;
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeywordOrStarred[])_res;
    }

    // starred_expression: invalid_starred_expression | '*' expression | '*'
    public ExprTy starred_expression_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_starred_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy invalid_starred_expression_var;
            if (
                (invalid_starred_expression_var = invalid_starred_expression_rule()) != null  // invalid_starred_expression
            )
            {
                _res = invalid_starred_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '*' expression
            if (errorIndicator) {
                return null;
            }
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
                _res = factory.createStarred(a, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '*'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = expect(16)) != null  // token='*'
            )
            {
                _res = this.raiseSyntaxError("Invalid star expression");
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // kwarg_or_starred: invalid_kwarg | NAME '=' expression | starred_expression
    public KeywordOrStarred kwarg_or_starred_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_kwarg
            if (errorIndicator) {
                return null;
            }
            Object invalid_kwarg_var;
            if (
                (invalid_kwarg_var = invalid_kwarg_rule()) != null  // invalid_kwarg
            )
            {
                _res = invalid_kwarg_var;
                return (KeywordOrStarred)_res;
            }
            reset(_mark);
        }
        { // NAME '=' expression
            if (errorIndicator) {
                return null;
            }
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
                _res = new KeywordOrStarred(factory.createKeyword(((ExprTy.Name) a).id, b, startToken.sourceRange.withEnd(endToken.sourceRange)), true);
                return (KeywordOrStarred)_res;
            }
            reset(_mark);
        }
        { // starred_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            if (
                (a = starred_expression_rule()) != null  // starred_expression
            )
            {
                _res = new KeywordOrStarred(a,false);
                return (KeywordOrStarred)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeywordOrStarred)_res;
    }

    // kwarg_or_double_starred: invalid_kwarg | NAME '=' expression | '**' expression
    public KeywordOrStarred kwarg_or_double_starred_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        if (callInvalidRules) { // invalid_kwarg
            if (errorIndicator) {
                return null;
            }
            Object invalid_kwarg_var;
            if (
                (invalid_kwarg_var = invalid_kwarg_rule()) != null  // invalid_kwarg
            )
            {
                _res = invalid_kwarg_var;
                return (KeywordOrStarred)_res;
            }
            reset(_mark);
        }
        { // NAME '=' expression
            if (errorIndicator) {
                return null;
            }
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
                _res = new KeywordOrStarred(factory.createKeyword(((ExprTy.Name) a).id, b, startToken.sourceRange.withEnd(endToken.sourceRange)), true);
                return (KeywordOrStarred)_res;
            }
            reset(_mark);
        }
        { // '**' expression
            if (errorIndicator) {
                return null;
            }
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
                _res = new KeywordOrStarred(factory.createKeyword(null, a, startToken.sourceRange.withEnd(endToken.sourceRange)), true);
                return (KeywordOrStarred)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeywordOrStarred)_res;
    }

    // star_targets: star_target !',' | star_target ((',' star_target))* ','?
    public ExprTy star_targets_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // star_target !','
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            if (
                (a = star_target_rule()) != null  // star_target
                &&
                genLookahead_expect(false, 12)  // token=','
            )
            {
                _res = a;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_target ((',' star_target))* ','?
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            ExprTy a;
            ExprTy[] b;
            if (
                (a = star_target_rule()) != null  // star_target
                &&
                (b = _loop0_191_rule()) != null  // ((',' star_target))*
                &&
                ((_opt_var = _tmp_192_rule()) != null || true)  // ','?
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(this.insertInFront(a,b), ExprContextTy.Store, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // star_targets_list_seq: ','.star_target+ ','?
    public ExprTy[] star_targets_list_seq_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.star_target+ ','?
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            ExprTy[] a;
            if (
                (a = (ExprTy[])_gather_193_rule()) != null  // ','.star_target+
                &&
                ((_opt_var = _tmp_195_rule()) != null || true)  // ','?
            )
            {
                _res = a;
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // star_targets_tuple_seq: star_target ((',' star_target))+ ','? | star_target ','
    public ExprTy[] star_targets_tuple_seq_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_target ((',' star_target))+ ','?
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            ExprTy a;
            ExprTy[] b;
            if (
                (a = star_target_rule()) != null  // star_target
                &&
                (b = _loop1_196_rule()) != null  // ((',' star_target))+
                &&
                ((_opt_var = _tmp_197_rule()) != null || true)  // ','?
            )
            {
                _res = this.insertInFront(a,b);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // star_target ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (a = star_target_rule()) != null  // star_target
                &&
                (_literal = expect(12)) != null  // token=','
            )
            {
                _res = new ExprTy[] {a};
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // star_target: '*' (!'*' star_target) | target_with_star_atom
    public ExprTy star_target_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, STAR_TARGET_ID)) {
            _res = (ExprTy)cache.getResult(_mark, STAR_TARGET_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // '*' (!'*' star_target)
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = _tmp_198_rule()) != null  // !'*' star_target
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createStarred(this.setExprContext(a, ExprContextTy.Store), ExprContextTy.Store, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, STAR_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // target_with_star_atom
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, TARGET_WITH_STAR_ATOM_ID)) {
            _res = (ExprTy)cache.getResult(_mark, TARGET_WITH_STAR_ATOM_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // t_primary '.' NAME !t_lookahead
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createGetAttribute(a, ((ExprTy.Name) b).id, ExprContextTy.Store, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, TARGET_WITH_STAR_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary '[' slices ']' !t_lookahead
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createSubscript(a, b, ExprContextTy.Store, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, TARGET_WITH_STAR_ATOM_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_atom
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // NAME
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            if (
                (a = name_token()) != null  // NAME
            )
            {
                _res = this.setExprContext(a, ExprContextTy.Store);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' target_with_star_atom ')'
            if (errorIndicator) {
                return null;
            }
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
                _res = this.setExprContext(a, ExprContextTy.Store);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' star_targets_tuple_seq? ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                ((a = _tmp_199_rule()) != null || true)  // star_targets_tuple_seq?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(a, ExprContextTy.Store, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '[' star_targets_list_seq? ']'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            if (
                (_literal = expect(9)) != null  // token='['
                &&
                ((a = _tmp_200_rule()) != null || true)  // star_targets_list_seq?
                &&
                (_literal_1 = expect(10)) != null  // token=']'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createList(a, ExprContextTy.Store, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // single_target: single_subscript_attribute_target | NAME | '(' single_target ')'
    public ExprTy single_target_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // single_subscript_attribute_target
            if (errorIndicator) {
                return null;
            }
            ExprTy single_subscript_attribute_target_var;
            if (
                (single_subscript_attribute_target_var = single_subscript_attribute_target_rule()) != null  // single_subscript_attribute_target
            )
            {
                _res = single_subscript_attribute_target_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // NAME
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            if (
                (a = name_token()) != null  // NAME
            )
            {
                _res = this.setExprContext(a, ExprContextTy.Store);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' single_target ')'
            if (errorIndicator) {
                return null;
            }
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
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // single_subscript_attribute_target:
    //     | t_primary '.' NAME !t_lookahead
    //     | t_primary '[' slices ']' !t_lookahead
    public ExprTy single_subscript_attribute_target_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // t_primary '.' NAME !t_lookahead
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createGetAttribute(a, ((ExprTy.Name) b).id, ExprContextTy.Store, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary '[' slices ']' !t_lookahead
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createSubscript(a, b, ExprContextTy.Store, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // t_primary '.' NAME &t_lookahead
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createGetAttribute(a, ((ExprTy.Name) b).id, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary '[' slices ']' &t_lookahead
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createSubscript(a, b, ExprContextTy.Load, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary genexp &t_lookahead
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createCall(a, new ExprTy[] {b}, EMPTY_KEYWORD_ARRAY, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary '(' arguments? ')' &t_lookahead
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy a;
            ExprTy b;
            if (
                (a = t_primary_rule()) != null  // t_primary
                &&
                (_literal = expect(7)) != null  // token='('
                &&
                ((b = _tmp_201_rule()) != null || true)  // arguments?
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
                _res = factory.createCall(a, b != null ? ((ExprTy.Call) b).args : EMPTY_EXPR_ARRAY, b != null ? ((ExprTy.Call) b).keywords : EMPTY_KEYWORD_ARRAY, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // atom &t_lookahead
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '('
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '['
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(9)) != null  // token='['
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '.'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(23)) != null  // token='.'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // del_targets: ','.del_target+ ','?
    public ExprTy[] del_targets_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.del_target+ ','?
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            ExprTy[] a;
            if (
                (a = (ExprTy[])_gather_202_rule()) != null  // ','.del_target+
                &&
                ((_opt_var = _tmp_204_rule()) != null || true)  // ','?
            )
            {
                _res = a;
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // del_target:
    //     | t_primary '.' NAME !t_lookahead
    //     | t_primary '[' slices ']' !t_lookahead
    //     | del_t_atom
    public ExprTy del_target_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, DEL_TARGET_ID)) {
            _res = (ExprTy)cache.getResult(_mark, DEL_TARGET_ID);
            return (ExprTy)_res;
        }
        Token startToken = getAndInitializeToken();
        { // t_primary '.' NAME !t_lookahead
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createGetAttribute(a, ((ExprTy.Name) b).id, ExprContextTy.Del, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, DEL_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // t_primary '[' slices ']' !t_lookahead
            if (errorIndicator) {
                return null;
            }
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
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createSubscript(a, b, ExprContextTy.Del, startToken.sourceRange.withEnd(endToken.sourceRange));
                cache.putResult(_mark, DEL_TARGET_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // del_t_atom
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        Token startToken = getAndInitializeToken();
        { // NAME
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            if (
                (a = name_token()) != null  // NAME
            )
            {
                _res = this.setExprContext(a, ExprContextTy.Del);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' del_target ')'
            if (errorIndicator) {
                return null;
            }
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
                _res = this.setExprContext(a, ExprContextTy.Del);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' del_targets? ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            if (
                (_literal = expect(7)) != null  // token='('
                &&
                ((a = _tmp_205_rule()) != null || true)  // del_targets?
                &&
                (_literal_1 = expect(8)) != null  // token=')'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createTuple(a, ExprContextTy.Del, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '[' del_targets? ']'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            if (
                (_literal = expect(9)) != null  // token='['
                &&
                ((a = _tmp_206_rule()) != null || true)  // del_targets?
                &&
                (_literal_1 = expect(10)) != null  // token=']'
            )
            {
                Token endToken = getLastNonWhitespaceToken();
                if (endToken == null) {
                    return null;
                }
                _res = factory.createList(a, ExprContextTy.Del, startToken.sourceRange.withEnd(endToken.sourceRange));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.expression+ ',' '*' expression ',' '**' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token _literal_3;
            ExprTy[] a;
            ExprTy b;
            ExprTy c;
            if (
                (a = _gather_207_rule()) != null  // ','.expression+
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
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // ','.expression+ ',' '*' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            ExprTy b;
            if (
                (a = _gather_209_rule()) != null  // ','.expression+
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                (_literal_1 = expect(16)) != null  // token='*'
                &&
                (b = expression_rule()) != null  // expression
            )
            {
                _res = this.appendToEnd(a,b);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // ','.expression+ ',' '**' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy[] a;
            ExprTy b;
            if (
                (a = _gather_211_rule()) != null  // ','.expression+
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                (_literal_1 = expect(35)) != null  // token='**'
                &&
                (b = expression_rule()) != null  // expression
            )
            {
                _res = this.appendToEnd(a,b);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // '*' expression ',' '**' expression
            if (errorIndicator) {
                return null;
            }
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
                _res = this.appendToEnd(new ExprTy[] {a},b);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // '*' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(16)) != null  // token='*'
                &&
                (a = expression_rule()) != null  // expression
            )
            {
                _res = new ExprTy[] {a};
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // '**' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            if (
                (_literal = expect(35)) != null  // token='**'
                &&
                (a = expression_rule()) != null  // expression
            )
            {
                _res = new ExprTy[] {a};
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        { // ','.expression+
            if (errorIndicator) {
                return null;
            }
            ExprTy[] a;
            if (
                (a = (ExprTy[])_gather_213_rule()) != null  // ','.expression+
            )
            {
                _res = a;
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // func_type_comment:
    //     | NEWLINE TYPE_COMMENT &(NEWLINE INDENT)
    //     | invalid_double_type_comments
    //     | TYPE_COMMENT
    public Token func_type_comment_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // NEWLINE TYPE_COMMENT &(NEWLINE INDENT)
            if (errorIndicator) {
                return null;
            }
            Token newline_var;
            Token t;
            if (
                (newline_var = expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                (t = expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
                &&
                genLookahead__tmp_215_rule(true)
            )
            {
                _res = t;
                return (Token)_res;
            }
            reset(_mark);
        }
        if (callInvalidRules) { // invalid_double_type_comments
            if (errorIndicator) {
                return null;
            }
            Object invalid_double_type_comments_var;
            if (
                (invalid_double_type_comments_var = invalid_double_type_comments_rule()) != null  // invalid_double_type_comments
            )
            {
                _res = invalid_double_type_comments_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // TYPE_COMMENT
            if (errorIndicator) {
                return null;
            }
            Token type_comment_var;
            if (
                (type_comment_var = expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                _res = type_comment_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // invalid_arguments:
    //     | ((','.(starred_expression | (assignment_expression | expression !':=') !'=')+ ',' kwargs) | kwargs) ',' ','.(starred_expression !'=')+
    //     | expression for_if_clauses ',' [args | expression for_if_clauses]
    //     | NAME '=' expression for_if_clauses
    //     | [(args ',')] NAME '=' &(',' | ')')
    //     | args for_if_clauses
    //     | args ',' expression for_if_clauses
    //     | args ',' args
    public Object invalid_arguments_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ((','.(starred_expression | (assignment_expression | expression !':=') !'=')+ ',' kwargs) | kwargs) ',' ','.(starred_expression !'=')+
            if (errorIndicator) {
                return null;
            }
            ExprTy[] _gather_217_var;
            Object _tmp_216_var;
            Token a;
            if (
                (_tmp_216_var = (Object)_tmp_216_rule()) != null  // (','.(starred_expression | (assignment_expression | expression !':=') !'=')+ ',' kwargs) | kwargs
                &&
                (a = (Token)expect(12)) != null  // token=','
                &&
                (_gather_217_var = (ExprTy[])_gather_217_rule()) != null  // ','.(starred_expression !'=')+
            )
            {
                _res = this.raiseSyntaxErrorStartingFrom(a, "iterable argument unpacking follows keyword argument unpacking");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // expression for_if_clauses ',' [args | expression for_if_clauses]
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Object _opt_var;
            ExprTy a;
            ComprehensionTy[] b;
            if (
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                (b = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                ((_opt_var = (Object)_tmp_219_rule()) != null || true)  // [args | expression for_if_clauses]
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, getLastComprehensionItem(lastItem(b)), "Generator expression must be parenthesized");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // NAME '=' expression for_if_clauses
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            Token b;
            ExprTy expression_var;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (a = (ExprTy)name_token()) != null  // NAME
                &&
                (b = (Token)expect(22)) != null  // token='='
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "invalid syntax. Maybe you meant '==' or ':=' instead of '='?");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // [(args ',')] NAME '=' &(',' | ')')
            if (errorIndicator) {
                return null;
            }
            Object _opt_var;
            ExprTy a;
            Token b;
            if (
                ((_opt_var = (Object)_tmp_220_rule()) != null || true)  // [(args ',')]
                &&
                (a = (ExprTy)name_token()) != null  // NAME
                &&
                (b = (Token)expect(22)) != null  // token='='
                &&
                genLookahead__tmp_221_rule(true)
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "expected argument value expression");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // args for_if_clauses
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            ComprehensionTy[] b;
            if (
                (a = (ExprTy)args_rule()) != null  // args
                &&
                (b = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                _res = nonparenGenexpInCall(a, b);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // args ',' expression for_if_clauses
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            ExprTy args_var;
            ComprehensionTy[] b;
            if (
                (args_var = (ExprTy)args_rule()) != null  // args
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                (b = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, getLastComprehensionItem(lastItem(b)), "Generator expression must be parenthesized");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // args ',' args
            if (errorIndicator) {
                return null;
            }
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
                _res = raiseArgumentsParsingError(a);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_kwarg:
    //     | ('True' | 'False' | 'None') '='
    //     | NAME '=' expression for_if_clauses
    //     | !(NAME '=') expression '='
    //     | '**' expression '=' expression
    public Object invalid_kwarg_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ('True' | 'False' | 'None') '='
            if (errorIndicator) {
                return null;
            }
            Token a;
            Token b;
            if (
                (a = (Token)_tmp_222_rule()) != null  // 'True' | 'False' | 'None'
                &&
                (b = (Token)expect(22)) != null  // token='='
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "cannot assign to %s", getText(a));
                return (Object)_res;
            }
            reset(_mark);
        }
        { // NAME '=' expression for_if_clauses
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            Token b;
            ExprTy expression_var;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (a = (ExprTy)name_token()) != null  // NAME
                &&
                (b = (Token)expect(22)) != null  // token='='
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "invalid syntax. Maybe you meant '==' or ':=' instead of '='?");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // !(NAME '=') expression '='
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            Token b;
            if (
                genLookahead__tmp_223_rule(false)
                &&
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                (b = (Token)expect(22)) != null  // token='='
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "expression cannot contain assignment, perhaps you meant \"==\"?");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '**' expression '=' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            ExprTy b;
            ExprTy expression_var;
            if (
                (a = (Token)expect(35)) != null  // token='**'
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (_literal = (Token)expect(22)) != null  // token='='
                &&
                (b = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "cannot assign to keyword argument unpacking");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // expression_without_invalid:
    //     | disjunction 'if' disjunction 'else' expression
    //     | disjunction
    //     | lambdef
    public ExprTy expression_without_invalid_rule()
    {
        boolean prevCallInvalidRules = callInvalidRules;
        try {
            callInvalidRules = false;
            if (errorIndicator) {
                return null;
            }
            int _mark = mark();
            Object _res = null;
            Token startToken = getAndInitializeToken();
            { // disjunction 'if' disjunction 'else' expression
                if (errorIndicator) {
                    return null;
                }
                Token _keyword;
                Token _keyword_1;
                ExprTy a;
                ExprTy b;
                ExprTy c;
                if (
                    (a = disjunction_rule()) != null  // disjunction
                    &&
                    (_keyword = expect(668)) != null  // token='if'
                    &&
                    (b = disjunction_rule()) != null  // disjunction
                    &&
                    (_keyword_1 = expect(676)) != null  // token='else'
                    &&
                    (c = expression_rule()) != null  // expression
                )
                {
                    Token endToken = getLastNonWhitespaceToken();
                    if (endToken == null) {
                        return null;
                    }
                    _res = factory.createIfExpression(b, a, c, startToken.sourceRange.withEnd(endToken.sourceRange));
                    return (ExprTy)_res;
                }
                reset(_mark);
            }
            { // disjunction
                if (errorIndicator) {
                    return null;
                }
                ExprTy disjunction_var;
                if (
                    (disjunction_var = disjunction_rule()) != null  // disjunction
                )
                {
                    _res = disjunction_var;
                    return (ExprTy)_res;
                }
                reset(_mark);
            }
            { // lambdef
                if (errorIndicator) {
                    return null;
                }
                ExprTy lambdef_var;
                if (
                    (lambdef_var = lambdef_rule()) != null  // lambdef
                )
                {
                    _res = lambdef_var;
                    return (ExprTy)_res;
                }
                reset(_mark);
            }
            _res = null;
            return (ExprTy)_res;
        } finally {
            callInvalidRules = prevCallInvalidRules;
        }
    }

    // invalid_legacy_expression: NAME !'(' star_expressions
    public ExprTy invalid_legacy_expression_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // NAME !'(' star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            ExprTy b;
            if (
                (a = (ExprTy)name_token()) != null  // NAME
                &&
                genLookahead_expect(false, 7)  // token='('
                &&
                (b = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = checkLegacyStmt(a) ? this.raiseSyntaxErrorKnownRange(a, b, "Missing parentheses in call to '%s'. Did you mean %s(...)?", ((ExprTy.Name) a).id, ((ExprTy.Name) a).id) : null;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_expression:
    //     | !(NAME STRING | SOFT_KEYWORD) disjunction expression_without_invalid
    //     | disjunction 'if' disjunction !('else' | ':')
    //     | 'lambda' lambda_params? ':' &FSTRING_MIDDLE
    public Object invalid_expression_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // !(NAME STRING | SOFT_KEYWORD) disjunction expression_without_invalid
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            ExprTy b;
            if (
                genLookahead__tmp_224_rule(false)
                &&
                (a = (ExprTy)disjunction_rule()) != null  // disjunction
                &&
                (b = (ExprTy)expression_without_invalid_rule()) != null  // expression_without_invalid
            )
            {
                _res = checkLegacyStmt(a) ? null : peekToken(mark() - 1).level == 0 ? null : this.raiseSyntaxErrorKnownRange(a, b, "invalid syntax. Perhaps you forgot a comma?");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // disjunction 'if' disjunction !('else' | ':')
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy a;
            ExprTy b;
            if (
                (a = (ExprTy)disjunction_rule()) != null  // disjunction
                &&
                (_keyword = (Token)expect(668)) != null  // token='if'
                &&
                (b = (ExprTy)disjunction_rule()) != null  // disjunction
                &&
                genLookahead__tmp_225_rule(false)
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "expected 'else' after 'if' expression");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'lambda' lambda_params? ':' &FSTRING_MIDDLE
            if (errorIndicator) {
                return null;
            }
            ArgumentsTy _opt_var;
            Token a;
            Token b;
            if (
                (a = (Token)expect(683)) != null  // token='lambda'
                &&
                ((_opt_var = (ArgumentsTy)_tmp_226_rule()) != null || true)  // lambda_params?
                &&
                (b = (Token)expect(11)) != null  // token=':'
                &&
                genLookahead_expect(true, Token.Kind.FSTRING_MIDDLE)  // token=FSTRING_MIDDLE
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "f-string: lambda expressions are not allowed without parentheses");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_named_expression:
    //     | expression ':=' expression
    //     | NAME '=' bitwise_or !('=' | ':=')
    //     | !(list | tuple | genexp | 'True' | 'None' | 'False') bitwise_or '=' bitwise_or !('=' | ':=')
    public ExprTy invalid_named_expression_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        if (cache.hasResult(_mark, INVALID_NAMED_EXPRESSION_ID)) {
            _res = (ExprTy)cache.getResult(_mark, INVALID_NAMED_EXPRESSION_ID);
            return (ExprTy)_res;
        }
        { // expression ':=' expression
            if (errorIndicator) {
                return null;
            }
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
                _res = this.raiseSyntaxErrorKnownLocation(a, "cannot use assignment expressions with %s", getExprName(a));
                cache.putResult(_mark, INVALID_NAMED_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // NAME '=' bitwise_or !('=' | ':=')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            ExprTy b;
            if (
                (a = (ExprTy)name_token()) != null  // NAME
                &&
                (_literal = (Token)expect(22)) != null  // token='='
                &&
                (b = (ExprTy)bitwise_or_rule()) != null  // bitwise_or
                &&
                genLookahead__tmp_227_rule(false)
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "invalid syntax. Maybe you meant '==' or ':=' instead of '='?");
                cache.putResult(_mark, INVALID_NAMED_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // !(list | tuple | genexp | 'True' | 'None' | 'False') bitwise_or '=' bitwise_or !('=' | ':=')
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            Token b;
            ExprTy bitwise_or_var;
            if (
                genLookahead__tmp_228_rule(false)
                &&
                (a = (ExprTy)bitwise_or_rule()) != null  // bitwise_or
                &&
                (b = (Token)expect(22)) != null  // token='='
                &&
                (bitwise_or_var = (ExprTy)bitwise_or_rule()) != null  // bitwise_or
                &&
                genLookahead__tmp_229_rule(false)
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "cannot assign to %s here. Maybe you meant '==' instead of '='?", getExprName(a));
                cache.putResult(_mark, INVALID_NAMED_EXPRESSION_ID, _res);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        cache.putResult(_mark, INVALID_NAMED_EXPRESSION_ID, _res);
        return (ExprTy)_res;
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // invalid_ann_assign_target ':' expression
            if (errorIndicator) {
                return null;
            }
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
                _res = this.raiseSyntaxErrorKnownLocation(a, "only single target (not %s) can be annotated", getExprName(a));
                return (Object)_res;
            }
            reset(_mark);
        }
        { // star_named_expression ',' star_named_expressions* ':' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy[] _loop0_230_var;
            ExprTy a;
            ExprTy expression_var;
            if (
                (a = (ExprTy)star_named_expression_rule()) != null  // star_named_expression
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (_loop0_230_var = (ExprTy[])_loop0_230_rule()) != null  // star_named_expressions*
                &&
                (_literal_1 = (Token)expect(11)) != null  // token=':'
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "only single target (not tuple) can be annotated");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // expression ':' expression
            if (errorIndicator) {
                return null;
            }
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
                _res = this.raiseSyntaxErrorKnownLocation(a, "illegal target for annotation");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ((star_targets '='))* star_expressions '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Object[] _loop0_231_var;
            ExprTy a;
            if (
                (_loop0_231_var = (Object[])_loop0_231_rule()) != null  // ((star_targets '='))*
                &&
                (a = (ExprTy)star_expressions_rule()) != null  // star_expressions
                &&
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = this.raiseSyntaxErrorInvalidTarget(TargetsType.STAR_TARGETS,a);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ((star_targets '='))* yield_expr '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Object[] _loop0_232_var;
            ExprTy a;
            if (
                (_loop0_232_var = (Object[])_loop0_232_rule()) != null  // ((star_targets '='))*
                &&
                (a = (ExprTy)yield_expr_rule()) != null  // yield_expr
                &&
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "assignment to yield expression not possible");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // star_expressions augassign (yield_expr | star_expressions)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_233_var;
            ExprTy a;
            OperatorTy augassign_var;
            if (
                (a = (ExprTy)star_expressions_rule()) != null  // star_expressions
                &&
                (augassign_var = (OperatorTy)augassign_rule()) != null  // augassign
                &&
                (_tmp_233_var = (ExprTy)_tmp_233_rule()) != null  // yield_expr | star_expressions
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "'%s' is an illegal expression for augmented assignment", getExprName(a));
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_ann_assign_target: list | tuple | '(' invalid_ann_assign_target ')'
    public ExprTy invalid_ann_assign_target_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // list
            if (errorIndicator) {
                return null;
            }
            ExprTy list_var;
            if (
                (list_var = list_rule()) != null  // list
            )
            {
                _res = list_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // tuple
            if (errorIndicator) {
                return null;
            }
            ExprTy tuple_var;
            if (
                (tuple_var = tuple_rule()) != null  // tuple
            )
            {
                _res = tuple_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' invalid_ann_assign_target ')'
            if (errorIndicator) {
                return null;
            }
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
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_del_stmt: 'del' star_expressions
    public ExprTy invalid_del_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'del' star_expressions
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy a;
            if (
                (_keyword = (Token)expect(660)) != null  // token='del'
                &&
                (a = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = this.raiseSyntaxErrorInvalidTarget(TargetsType.DEL_TARGETS,a);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_block: NEWLINE !INDENT
    public Object invalid_block_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token newline_var;
            if (
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_comprehension:
    //     | ('[' | '(' | '{') starred_expression for_if_clauses
    //     | ('[' | '{') star_named_expression ',' star_named_expressions for_if_clauses
    //     | ('[' | '{') star_named_expression ',' for_if_clauses
    public Object invalid_comprehension_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ('[' | '(' | '{') starred_expression for_if_clauses
            if (errorIndicator) {
                return null;
            }
            Token _tmp_234_var;
            ExprTy a;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (_tmp_234_var = (Token)_tmp_234_rule()) != null  // '[' | '(' | '{'
                &&
                (a = (ExprTy)starred_expression_rule()) != null  // starred_expression
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "iterable unpacking cannot be used in comprehension");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ('[' | '{') star_named_expression ',' star_named_expressions for_if_clauses
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _tmp_235_var;
            ExprTy a;
            ExprTy[] b;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (_tmp_235_var = (Token)_tmp_235_rule()) != null  // '[' | '{'
                &&
                (a = (ExprTy)star_named_expression_rule()) != null  // star_named_expression
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (b = (ExprTy[])star_named_expressions_rule()) != null  // star_named_expressions
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, lastItem(b), "did you forget parentheses around the comprehension target?");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ('[' | '{') star_named_expression ',' for_if_clauses
            if (errorIndicator) {
                return null;
            }
            Token _tmp_236_var;
            ExprTy a;
            Token b;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (_tmp_236_var = (Token)_tmp_236_rule()) != null  // '[' | '{'
                &&
                (a = (ExprTy)star_named_expression_rule()) != null  // star_named_expression
                &&
                (b = (Token)expect(12)) != null  // token=','
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "did you forget parentheses around the comprehension target?");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_dict_comprehension: '{' '**' bitwise_or for_if_clauses '}'
    public Object invalid_dict_comprehension_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '{' '**' bitwise_or for_if_clauses '}'
            if (errorIndicator) {
                return null;
            }
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
                _res = this.raiseSyntaxErrorKnownLocation(a, "dict unpacking cannot be used in dict comprehension");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_parameters:
    //     | "/" ','
    //     | (slash_no_default | slash_with_default) param_maybe_default* '/'
    //     | slash_no_default? param_no_default* invalid_parameters_helper param_no_default
    //     | param_no_default* '(' param_no_default+ ','? ')'
    //     | [(slash_no_default | slash_with_default)] param_maybe_default* '*' (',' | param_no_default) param_maybe_default* '/'
    //     | param_maybe_default+ '/' '*'
    public Object invalid_parameters_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // "/" ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            if (
                (a = (Token)expect(17)) != null  // token='/'
                &&
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "at least one argument must precede /");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // (slash_no_default | slash_with_default) param_maybe_default* '/'
            if (errorIndicator) {
                return null;
            }
            NameDefaultPair[] _loop0_238_var;
            Object _tmp_237_var;
            Token a;
            if (
                (_tmp_237_var = (Object)_tmp_237_rule()) != null  // slash_no_default | slash_with_default
                &&
                (_loop0_238_var = (NameDefaultPair[])_loop0_238_rule()) != null  // param_maybe_default*
                &&
                (a = (Token)expect(17)) != null  // token='/'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "/ may appear only once");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // slash_no_default? param_no_default* invalid_parameters_helper param_no_default
            if (errorIndicator) {
                return null;
            }
            ArgTy[] _loop0_239_var;
            ArgTy[] _opt_var;
            ArgTy a;
            Object invalid_parameters_helper_var;
            if (
                ((_opt_var = (ArgTy[])slash_no_default_rule()) != null || true)  // slash_no_default?
                &&
                (_loop0_239_var = (ArgTy[])_loop0_239_rule()) != null  // param_no_default*
                &&
                (invalid_parameters_helper_var = (Object)invalid_parameters_helper_rule()) != null  // invalid_parameters_helper
                &&
                (a = (ArgTy)param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "parameter without a default follows parameter with a default");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // param_no_default* '(' param_no_default+ ','? ')'
            if (errorIndicator) {
                return null;
            }
            ArgTy[] _loop0_240_var;
            ArgTy[] _loop1_241_var;
            Token _opt_var;
            Token a;
            Token b;
            if (
                (_loop0_240_var = (ArgTy[])_loop0_240_rule()) != null  // param_no_default*
                &&
                (a = (Token)expect(7)) != null  // token='('
                &&
                (_loop1_241_var = (ArgTy[])_loop1_241_rule()) != null  // param_no_default+
                &&
                ((_opt_var = (Token)expect(12)) != null || true)  // ','?
                &&
                (b = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "Function parameters cannot be parenthesized");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // [(slash_no_default | slash_with_default)] param_maybe_default* '*' (',' | param_no_default) param_maybe_default* '/'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            NameDefaultPair[] _loop0_243_var;
            NameDefaultPair[] _loop0_245_var;
            Object _opt_var;
            Object _tmp_244_var;
            Token a;
            if (
                ((_opt_var = (Object)_tmp_242_rule()) != null || true)  // [(slash_no_default | slash_with_default)]
                &&
                (_loop0_243_var = (NameDefaultPair[])_loop0_243_rule()) != null  // param_maybe_default*
                &&
                (_literal = (Token)expect(16)) != null  // token='*'
                &&
                (_tmp_244_var = (Object)_tmp_244_rule()) != null  // ',' | param_no_default
                &&
                (_loop0_245_var = (NameDefaultPair[])_loop0_245_rule()) != null  // param_maybe_default*
                &&
                (a = (Token)expect(17)) != null  // token='/'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "/ must be ahead of *");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // param_maybe_default+ '/' '*'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            NameDefaultPair[] _loop1_246_var;
            Token a;
            if (
                (_loop1_246_var = (NameDefaultPair[])_loop1_246_rule()) != null  // param_maybe_default+
                &&
                (_literal = (Token)expect(17)) != null  // token='/'
                &&
                (a = (Token)expect(16)) != null  // token='*'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "expected comma between / and *");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_default: '=' &(')' | ',')
    public Object invalid_default_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '=' &(')' | ',')
            if (errorIndicator) {
                return null;
            }
            Token a;
            if (
                (a = (Token)expect(22)) != null  // token='='
                &&
                genLookahead__tmp_247_rule(true)
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "expected default value expression");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_star_etc:
    //     | '*' (')' | ',' (')' | '**'))
    //     | '*' ',' TYPE_COMMENT
    //     | '*' param '='
    //     | '*' (param_no_default | ',') param_maybe_default* '*' (param_no_default | ',')
    public Object invalid_star_etc_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '*' (')' | ',' (')' | '**'))
            if (errorIndicator) {
                return null;
            }
            Object _tmp_248_var;
            Token a;
            if (
                (a = (Token)expect(16)) != null  // token='*'
                &&
                (_tmp_248_var = (Object)_tmp_248_rule()) != null  // ')' | ',' (')' | '**')
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "named arguments must follow bare *");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '*' ',' TYPE_COMMENT
            if (errorIndicator) {
                return null;
            }
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
                _res = this.raiseSyntaxError("bare * has associated type comment");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '*' param '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            ArgTy param_var;
            if (
                (_literal = (Token)expect(16)) != null  // token='*'
                &&
                (param_var = (ArgTy)param_rule()) != null  // param
                &&
                (a = (Token)expect(22)) != null  // token='='
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "var-positional argument cannot have default value");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '*' (param_no_default | ',') param_maybe_default* '*' (param_no_default | ',')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            NameDefaultPair[] _loop0_250_var;
            Object _tmp_249_var;
            Object _tmp_251_var;
            Token a;
            if (
                (_literal = (Token)expect(16)) != null  // token='*'
                &&
                (_tmp_249_var = (Object)_tmp_249_rule()) != null  // param_no_default | ','
                &&
                (_loop0_250_var = (NameDefaultPair[])_loop0_250_rule()) != null  // param_maybe_default*
                &&
                (a = (Token)expect(16)) != null  // token='*'
                &&
                (_tmp_251_var = (Object)_tmp_251_rule()) != null  // param_no_default | ','
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "* argument may appear only once");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_kwds: '**' param '=' | '**' param ',' param | '**' param ',' ('*' | '**' | '/')
    public ArgTy invalid_kwds_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '**' param '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            ArgTy param_var;
            if (
                (_literal = (Token)expect(35)) != null  // token='**'
                &&
                (param_var = (ArgTy)param_rule()) != null  // param
                &&
                (a = (Token)expect(22)) != null  // token='='
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "var-keyword argument cannot have default value");
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        { // '**' param ',' param
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ArgTy a;
            ArgTy param_var;
            if (
                (_literal = (Token)expect(35)) != null  // token='**'
                &&
                (param_var = (ArgTy)param_rule()) != null  // param
                &&
                (_literal_1 = (Token)expect(12)) != null  // token=','
                &&
                (a = (ArgTy)param_rule()) != null  // param
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "arguments cannot follow var-keyword argument");
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        { // '**' param ',' ('*' | '**' | '/')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            Token a;
            ArgTy param_var;
            if (
                (_literal = (Token)expect(35)) != null  // token='**'
                &&
                (param_var = (ArgTy)param_rule()) != null  // param
                &&
                (_literal_1 = (Token)expect(12)) != null  // token=','
                &&
                (a = (Token)_tmp_252_rule()) != null  // '*' | '**' | '/'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "arguments cannot follow var-keyword argument");
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // invalid_parameters_helper: slash_with_default | param_with_default+
    public Object invalid_parameters_helper_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // slash_with_default
            if (errorIndicator) {
                return null;
            }
            SlashWithDefault a;
            if (
                (a = (SlashWithDefault)slash_with_default_rule()) != null  // slash_with_default
            )
            {
                _res = new SlashWithDefault[] {a};
                return (Object)_res;
            }
            reset(_mark);
        }
        { // param_with_default+
            if (errorIndicator) {
                return null;
            }
            NameDefaultPair[] _loop1_253_var;
            if (
                (_loop1_253_var = (NameDefaultPair[])_loop1_253_rule()) != null  // param_with_default+
            )
            {
                _res = _loop1_253_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_lambda_parameters:
    //     | "/" ','
    //     | (lambda_slash_no_default | lambda_slash_with_default) lambda_param_maybe_default* '/'
    //     | lambda_slash_no_default? lambda_param_no_default* invalid_lambda_parameters_helper lambda_param_no_default
    //     | lambda_param_no_default* '(' ','.lambda_param+ ','? ')'
    //     | [(lambda_slash_no_default | lambda_slash_with_default)] lambda_param_maybe_default* '*' (',' | lambda_param_no_default) lambda_param_maybe_default* '/'
    //     | lambda_param_maybe_default+ '/' '*'
    public Object invalid_lambda_parameters_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // "/" ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            if (
                (a = (Token)expect(17)) != null  // token='/'
                &&
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "at least one argument must precede /");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // (lambda_slash_no_default | lambda_slash_with_default) lambda_param_maybe_default* '/'
            if (errorIndicator) {
                return null;
            }
            NameDefaultPair[] _loop0_255_var;
            Object _tmp_254_var;
            Token a;
            if (
                (_tmp_254_var = (Object)_tmp_254_rule()) != null  // lambda_slash_no_default | lambda_slash_with_default
                &&
                (_loop0_255_var = (NameDefaultPair[])_loop0_255_rule()) != null  // lambda_param_maybe_default*
                &&
                (a = (Token)expect(17)) != null  // token='/'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "/ may appear only once");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // lambda_slash_no_default? lambda_param_no_default* invalid_lambda_parameters_helper lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
            ArgTy[] _loop0_256_var;
            ArgTy[] _opt_var;
            ArgTy a;
            Object invalid_lambda_parameters_helper_var;
            if (
                ((_opt_var = (ArgTy[])lambda_slash_no_default_rule()) != null || true)  // lambda_slash_no_default?
                &&
                (_loop0_256_var = (ArgTy[])_loop0_256_rule()) != null  // lambda_param_no_default*
                &&
                (invalid_lambda_parameters_helper_var = (Object)invalid_lambda_parameters_helper_rule()) != null  // invalid_lambda_parameters_helper
                &&
                (a = (ArgTy)lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "parameter without a default follows parameter with a default");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // lambda_param_no_default* '(' ','.lambda_param+ ','? ')'
            if (errorIndicator) {
                return null;
            }
            ArgTy[] _gather_258_var;
            ArgTy[] _loop0_257_var;
            Token _opt_var;
            Token a;
            Token b;
            if (
                (_loop0_257_var = (ArgTy[])_loop0_257_rule()) != null  // lambda_param_no_default*
                &&
                (a = (Token)expect(7)) != null  // token='('
                &&
                (_gather_258_var = (ArgTy[])_gather_258_rule()) != null  // ','.lambda_param+
                &&
                ((_opt_var = (Token)expect(12)) != null || true)  // ','?
                &&
                (b = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "Lambda expression parameters cannot be parenthesized");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // [(lambda_slash_no_default | lambda_slash_with_default)] lambda_param_maybe_default* '*' (',' | lambda_param_no_default) lambda_param_maybe_default* '/'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            NameDefaultPair[] _loop0_261_var;
            NameDefaultPair[] _loop0_263_var;
            Object _opt_var;
            Object _tmp_262_var;
            Token a;
            if (
                ((_opt_var = (Object)_tmp_260_rule()) != null || true)  // [(lambda_slash_no_default | lambda_slash_with_default)]
                &&
                (_loop0_261_var = (NameDefaultPair[])_loop0_261_rule()) != null  // lambda_param_maybe_default*
                &&
                (_literal = (Token)expect(16)) != null  // token='*'
                &&
                (_tmp_262_var = (Object)_tmp_262_rule()) != null  // ',' | lambda_param_no_default
                &&
                (_loop0_263_var = (NameDefaultPair[])_loop0_263_rule()) != null  // lambda_param_maybe_default*
                &&
                (a = (Token)expect(17)) != null  // token='/'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "/ must be ahead of *");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // lambda_param_maybe_default+ '/' '*'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            NameDefaultPair[] _loop1_264_var;
            Token a;
            if (
                (_loop1_264_var = (NameDefaultPair[])_loop1_264_rule()) != null  // lambda_param_maybe_default+
                &&
                (_literal = (Token)expect(17)) != null  // token='/'
                &&
                (a = (Token)expect(16)) != null  // token='*'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "expected comma between / and *");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_lambda_parameters_helper:
    //     | lambda_slash_with_default
    //     | lambda_param_with_default+
    public Object invalid_lambda_parameters_helper_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_slash_with_default
            if (errorIndicator) {
                return null;
            }
            SlashWithDefault a;
            if (
                (a = (SlashWithDefault)lambda_slash_with_default_rule()) != null  // lambda_slash_with_default
            )
            {
                _res = new SlashWithDefault[] {a};
                return (Object)_res;
            }
            reset(_mark);
        }
        { // lambda_param_with_default+
            if (errorIndicator) {
                return null;
            }
            NameDefaultPair[] _loop1_265_var;
            if (
                (_loop1_265_var = (NameDefaultPair[])_loop1_265_rule()) != null  // lambda_param_with_default+
            )
            {
                _res = _loop1_265_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_lambda_star_etc:
    //     | '*' (':' | ',' (':' | '**'))
    //     | '*' lambda_param '='
    //     | '*' (lambda_param_no_default | ',') lambda_param_maybe_default* '*' (lambda_param_no_default | ',')
    public Object invalid_lambda_star_etc_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '*' (':' | ',' (':' | '**'))
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Object _tmp_266_var;
            if (
                (_literal = (Token)expect(16)) != null  // token='*'
                &&
                (_tmp_266_var = (Object)_tmp_266_rule()) != null  // ':' | ',' (':' | '**')
            )
            {
                _res = this.raiseSyntaxError("named arguments must follow bare *");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '*' lambda_param '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            ArgTy lambda_param_var;
            if (
                (_literal = (Token)expect(16)) != null  // token='*'
                &&
                (lambda_param_var = (ArgTy)lambda_param_rule()) != null  // lambda_param
                &&
                (a = (Token)expect(22)) != null  // token='='
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "var-positional argument cannot have default value");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '*' (lambda_param_no_default | ',') lambda_param_maybe_default* '*' (lambda_param_no_default | ',')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            NameDefaultPair[] _loop0_268_var;
            Object _tmp_267_var;
            Object _tmp_269_var;
            Token a;
            if (
                (_literal = (Token)expect(16)) != null  // token='*'
                &&
                (_tmp_267_var = (Object)_tmp_267_rule()) != null  // lambda_param_no_default | ','
                &&
                (_loop0_268_var = (NameDefaultPair[])_loop0_268_rule()) != null  // lambda_param_maybe_default*
                &&
                (a = (Token)expect(16)) != null  // token='*'
                &&
                (_tmp_269_var = (Object)_tmp_269_rule()) != null  // lambda_param_no_default | ','
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "* argument may appear only once");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_lambda_kwds:
    //     | '**' lambda_param '='
    //     | '**' lambda_param ',' lambda_param
    //     | '**' lambda_param ',' ('*' | '**' | '/')
    public ArgTy invalid_lambda_kwds_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '**' lambda_param '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            ArgTy lambda_param_var;
            if (
                (_literal = (Token)expect(35)) != null  // token='**'
                &&
                (lambda_param_var = (ArgTy)lambda_param_rule()) != null  // lambda_param
                &&
                (a = (Token)expect(22)) != null  // token='='
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "var-keyword argument cannot have default value");
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        { // '**' lambda_param ',' lambda_param
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ArgTy a;
            ArgTy lambda_param_var;
            if (
                (_literal = (Token)expect(35)) != null  // token='**'
                &&
                (lambda_param_var = (ArgTy)lambda_param_rule()) != null  // lambda_param
                &&
                (_literal_1 = (Token)expect(12)) != null  // token=','
                &&
                (a = (ArgTy)lambda_param_rule()) != null  // lambda_param
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "arguments cannot follow var-keyword argument");
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        { // '**' lambda_param ',' ('*' | '**' | '/')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            Token a;
            ArgTy lambda_param_var;
            if (
                (_literal = (Token)expect(35)) != null  // token='**'
                &&
                (lambda_param_var = (ArgTy)lambda_param_rule()) != null  // lambda_param
                &&
                (_literal_1 = (Token)expect(12)) != null  // token=','
                &&
                (a = (Token)_tmp_270_rule()) != null  // '*' | '**' | '/'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "arguments cannot follow var-keyword argument");
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // invalid_double_type_comments: TYPE_COMMENT NEWLINE TYPE_COMMENT NEWLINE INDENT
    public Object invalid_double_type_comments_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // TYPE_COMMENT NEWLINE TYPE_COMMENT NEWLINE INDENT
            if (errorIndicator) {
                return null;
            }
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
                _res = this.raiseSyntaxError("Cannot have two type comments on def");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_with_item: expression 'as' expression &(',' | ')' | ':')
    public ExprTy invalid_with_item_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression 'as' expression &(',' | ')' | ':')
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy a;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                genLookahead__tmp_271_rule(true)
            )
            {
                _res = this.raiseSyntaxErrorInvalidTarget(TargetsType.STAR_TARGETS,a);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_for_target: ASYNC? 'for' star_expressions
    public ExprTy invalid_for_target_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ASYNC? 'for' star_expressions
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _opt_var;
            ExprTy a;
            if (
                ((_opt_var = (Token)expect(Token.Kind.ASYNC)) != null || true)  // ASYNC?
                &&
                (_keyword = (Token)expect(671)) != null  // token='for'
                &&
                (a = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = this.raiseSyntaxErrorInvalidTarget(TargetsType.FOR_TARGETS,a);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_group: '(' starred_expression ')' | '(' '**' expression ')'
    public ExprTy invalid_group_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '(' starred_expression ')'
            if (errorIndicator) {
                return null;
            }
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
                _res = this.raiseSyntaxErrorKnownLocation(a, "cannot use starred expression here");
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // '(' '**' expression ')'
            if (errorIndicator) {
                return null;
            }
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
                _res = this.raiseSyntaxErrorKnownLocation(a, "cannot use double starred expression here");
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_import: 'import' ','.dotted_name+ 'from' dotted_name
    public ExprTy[] invalid_import_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'import' ','.dotted_name+ 'from' dotted_name
            if (errorIndicator) {
                return null;
            }
            ExprTy[] _gather_272_var;
            Token _keyword;
            Token a;
            ExprTy dotted_name_var;
            if (
                (a = (Token)expect(656)) != null  // token='import'
                &&
                (_gather_272_var = (ExprTy[])_gather_272_rule()) != null  // ','.dotted_name+
                &&
                (_keyword = (Token)expect(657)) != null  // token='from'
                &&
                (dotted_name_var = (ExprTy)dotted_name_rule()) != null  // dotted_name
            )
            {
                _res = this.raiseSyntaxErrorStartingFrom(a, "Did you mean to use 'from ... import ...' instead?");
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // invalid_import_from_targets: import_from_as_names ',' NEWLINE
    public AliasTy[] invalid_import_from_targets_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // import_from_as_names ',' NEWLINE
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            AliasTy[] import_from_as_names_var;
            Token newline_var;
            if (
                (import_from_as_names_var = (AliasTy[])import_from_as_names_rule()) != null  // import_from_as_names
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.raiseSyntaxError("trailing comma not allowed without surrounding parentheses");
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (AliasTy[])_res;
    }

    // invalid_with_stmt:
    //     | ASYNC? 'with' ','.(expression ['as' star_target])+ NEWLINE
    //     | ASYNC? 'with' '(' ','.(expressions ['as' star_target])+ ','? ')' NEWLINE
    public Object[] invalid_with_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ASYNC? 'with' ','.(expression ['as' star_target])+ NEWLINE
            if (errorIndicator) {
                return null;
            }
            Object[] _gather_275_var;
            Token _keyword;
            Token _opt_var;
            Token newline_var;
            if (
                ((_opt_var = (Token)_tmp_274_rule()) != null || true)  // ASYNC?
                &&
                (_keyword = (Token)expect(670)) != null  // token='with'
                &&
                (_gather_275_var = (Object[])_gather_275_rule()) != null  // ','.(expression ['as' star_target])+
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.raiseSyntaxError("expected ':'");
                return (Object[])_res;
            }
            reset(_mark);
        }
        { // ASYNC? 'with' '(' ','.(expressions ['as' star_target])+ ','? ')' NEWLINE
            if (errorIndicator) {
                return null;
            }
            Object[] _gather_278_var;
            Token _keyword;
            Token _literal;
            Token _literal_1;
            Token _opt_var;
            Token _opt_var_1;
            Token newline_var;
            if (
                ((_opt_var = (Token)_tmp_277_rule()) != null || true)  // ASYNC?
                &&
                (_keyword = (Token)expect(670)) != null  // token='with'
                &&
                (_literal = (Token)expect(7)) != null  // token='('
                &&
                (_gather_278_var = (Object[])_gather_278_rule()) != null  // ','.(expressions ['as' star_target])+
                &&
                ((_opt_var_1 = (Token)expect(12)) != null || true)  // ','?
                &&
                (_literal_1 = (Token)expect(8)) != null  // token=')'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.raiseSyntaxError("expected ':'");
                return (Object[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object[])_res;
    }

    // invalid_with_stmt_indent:
    //     | ASYNC? 'with' ','.(expression ['as' star_target])+ ':' NEWLINE !INDENT
    //     | ASYNC? 'with' '(' ','.(expressions ['as' star_target])+ ','? ')' ':' NEWLINE !INDENT
    public Object[] invalid_with_stmt_indent_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ASYNC? 'with' ','.(expression ['as' star_target])+ ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Object[] _gather_281_var;
            Token _literal;
            Token _opt_var;
            Token a;
            Token newline_var;
            if (
                ((_opt_var = (Token)_tmp_280_rule()) != null || true)  // ASYNC?
                &&
                (a = (Token)expect(670)) != null  // token='with'
                &&
                (_gather_281_var = (Object[])_gather_281_rule()) != null  // ','.(expression ['as' star_target])+
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'with' statement on line %d", a.getSourceRange().startLine);
                return (Object[])_res;
            }
            reset(_mark);
        }
        { // ASYNC? 'with' '(' ','.(expressions ['as' star_target])+ ','? ')' ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Object[] _gather_284_var;
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token _opt_var;
            Token _opt_var_1;
            Token a;
            Token newline_var;
            if (
                ((_opt_var = (Token)_tmp_283_rule()) != null || true)  // ASYNC?
                &&
                (a = (Token)expect(670)) != null  // token='with'
                &&
                (_literal = (Token)expect(7)) != null  // token='('
                &&
                (_gather_284_var = (Object[])_gather_284_rule()) != null  // ','.(expressions ['as' star_target])+
                &&
                ((_opt_var_1 = (Token)expect(12)) != null || true)  // ','?
                &&
                (_literal_1 = (Token)expect(8)) != null  // token=')'
                &&
                (_literal_2 = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'with' statement on line %d", a.getSourceRange().startLine);
                return (Object[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object[])_res;
    }

    // invalid_try_stmt:
    //     | 'try' ':' NEWLINE !INDENT
    //     | 'try' ':' block !('except' | 'finally')
    //     | 'try' ':' block* except_block+ 'except' '*' expression ['as' NAME] ':'
    //     | 'try' ':' block* except_star_block+ 'except' [expression ['as' NAME]] ':'
    public Object invalid_try_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'try' ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            Token newline_var;
            if (
                (a = (Token)expect(672)) != null  // token='try'
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'try' statement on line %d", a.getSourceRange().startLine);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'try' ':' block !('except' | 'finally')
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            StmtTy[] block_var;
            if (
                (_keyword = (Token)expect(672)) != null  // token='try'
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (block_var = (StmtTy[])block_rule()) != null  // block
                &&
                genLookahead__tmp_286_rule(false)
            )
            {
                _res = this.raiseSyntaxError("expected 'except' or 'finally' block");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'try' ':' block* except_block+ 'except' '*' expression ['as' NAME] ':'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            Token _literal_1;
            StmtTy[] _loop0_287_var;
            ExceptHandlerTy[] _loop1_288_var;
            Object _opt_var;
            Token a;
            Token b;
            ExprTy expression_var;
            if (
                (_keyword = (Token)expect(672)) != null  // token='try'
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (_loop0_287_var = (StmtTy[])_loop0_287_rule()) != null  // block*
                &&
                (_loop1_288_var = (ExceptHandlerTy[])_loop1_288_rule()) != null  // except_block+
                &&
                (a = (Token)expect(678)) != null  // token='except'
                &&
                (b = (Token)expect(16)) != null  // token='*'
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                ((_opt_var = (Object)_tmp_289_rule()) != null || true)  // ['as' NAME]
                &&
                (_literal_1 = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "cannot have both 'except' and 'except*' on the same 'try'");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'try' ':' block* except_star_block+ 'except' [expression ['as' NAME]] ':'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            Token _literal_1;
            StmtTy[] _loop0_290_var;
            ExceptHandlerTy[] _loop1_291_var;
            Object _opt_var;
            Token a;
            if (
                (_keyword = (Token)expect(672)) != null  // token='try'
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (_loop0_290_var = (StmtTy[])_loop0_290_rule()) != null  // block*
                &&
                (_loop1_291_var = (ExceptHandlerTy[])_loop1_291_rule()) != null  // except_star_block+
                &&
                (a = (Token)expect(678)) != null  // token='except'
                &&
                ((_opt_var = (Object)_tmp_292_rule()) != null || true)  // [expression ['as' NAME]]
                &&
                (_literal_1 = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "cannot have both 'except' and 'except*' on the same 'try'");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_except_stmt:
    //     | 'except' '*'? expression ',' expressions ['as' NAME] ':'
    //     | 'except' '*'? expression ['as' NAME] NEWLINE
    //     | 'except' NEWLINE
    //     | 'except' '*' (NEWLINE | ':')
    public Object invalid_except_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'except' '*'? expression ',' expressions ['as' NAME] ':'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            Token _literal_1;
            Token _opt_var;
            Object _opt_var_1;
            ExprTy a;
            ExprTy expressions_var;
            if (
                (_keyword = (Token)expect(678)) != null  // token='except'
                &&
                ((_opt_var = (Token)expect(16)) != null || true)  // '*'?
                &&
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (expressions_var = (ExprTy)expressions_rule()) != null  // expressions
                &&
                ((_opt_var_1 = (Object)_tmp_293_rule()) != null || true)  // ['as' NAME]
                &&
                (_literal_1 = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = this.raiseSyntaxErrorStartingFrom(a, "multiple exception types must be parenthesized");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'except' '*'? expression ['as' NAME] NEWLINE
            if (errorIndicator) {
                return null;
            }
            Token _opt_var;
            Object _opt_var_1;
            Token a;
            ExprTy expression_var;
            Token newline_var;
            if (
                (a = (Token)expect(678)) != null  // token='except'
                &&
                ((_opt_var = (Token)expect(16)) != null || true)  // '*'?
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                ((_opt_var_1 = (Object)_tmp_294_rule()) != null || true)  // ['as' NAME]
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.raiseSyntaxError("expected ':'");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'except' NEWLINE
            if (errorIndicator) {
                return null;
            }
            Token a;
            Token newline_var;
            if (
                (a = (Token)expect(678)) != null  // token='except'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.raiseSyntaxError("expected ':'");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'except' '*' (NEWLINE | ':')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _tmp_295_var;
            Token a;
            if (
                (a = (Token)expect(678)) != null  // token='except'
                &&
                (_literal = (Token)expect(16)) != null  // token='*'
                &&
                (_tmp_295_var = (Token)_tmp_295_rule()) != null  // NEWLINE | ':'
            )
            {
                _res = this.raiseSyntaxError("expected one or more exception types");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_finally_stmt: 'finally' ':' NEWLINE !INDENT
    public Object invalid_finally_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'finally' ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            Token newline_var;
            if (
                (a = (Token)expect(679)) != null  // token='finally'
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'finally' statement on line %d", a.getSourceRange().startLine);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_except_stmt_indent:
    //     | 'except' expression ['as' NAME] ':' NEWLINE !INDENT
    //     | 'except' ':' NEWLINE !INDENT
    public Object invalid_except_stmt_indent_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'except' expression ['as' NAME] ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Object _opt_var;
            Token a;
            ExprTy expression_var;
            Token newline_var;
            if (
                (a = (Token)expect(678)) != null  // token='except'
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                ((_opt_var = (Object)_tmp_296_rule()) != null || true)  // ['as' NAME]
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'except' statement on line %d", a.getSourceRange().startLine);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'except' ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            Token newline_var;
            if (
                (a = (Token)expect(678)) != null  // token='except'
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'except' statement on line %d", a.getSourceRange().startLine);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_except_star_stmt_indent:
    //     | 'except' '*' expression ['as' NAME] ':' NEWLINE !INDENT
    public Object invalid_except_star_stmt_indent_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'except' '*' expression ['as' NAME] ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            Object _opt_var;
            Token a;
            ExprTy expression_var;
            Token newline_var;
            if (
                (a = (Token)expect(678)) != null  // token='except'
                &&
                (_literal = (Token)expect(16)) != null  // token='*'
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                ((_opt_var = (Object)_tmp_297_rule()) != null || true)  // ['as' NAME]
                &&
                (_literal_1 = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'except*' statement on line %d", a.getSourceRange().startLine);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_match_stmt:
    //     | "match" subject_expr NEWLINE
    //     | "match" subject_expr ':' NEWLINE !INDENT
    public ExprTy invalid_match_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // "match" subject_expr NEWLINE
            if (errorIndicator) {
                return null;
            }
            ExprTy _keyword;
            Token newline_var;
            ExprTy subject_expr_var;
            if (
                (_keyword = (ExprTy)expect_SOFT_KEYWORD("match")) != null  // soft_keyword='"match"'
                &&
                (subject_expr_var = (ExprTy)subject_expr_rule()) != null  // subject_expr
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = checkVersion(10, "Pattern matching is", () -> this.raiseSyntaxError("expected ':'"));
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // "match" subject_expr ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy a;
            Token newline_var;
            ExprTy subject;
            if (
                (a = (ExprTy)expect_SOFT_KEYWORD("match")) != null  // soft_keyword='"match"'
                &&
                (subject = (ExprTy)subject_expr_rule()) != null  // subject_expr
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'match' statement on line %d", a.getSourceRange().startLine);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_case_block:
    //     | "case" patterns guard? NEWLINE
    //     | "case" patterns guard? ':' NEWLINE !INDENT
    public Object invalid_case_block_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // "case" patterns guard? NEWLINE
            if (errorIndicator) {
                return null;
            }
            ExprTy _keyword;
            ExprTy _opt_var;
            Token newline_var;
            PatternTy patterns_var;
            if (
                (_keyword = (ExprTy)expect_SOFT_KEYWORD("case")) != null  // soft_keyword='"case"'
                &&
                (patterns_var = (PatternTy)patterns_rule()) != null  // patterns
                &&
                ((_opt_var = (ExprTy)guard_rule()) != null || true)  // guard?
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.raiseSyntaxError("expected ':'");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // "case" patterns guard? ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy _opt_var;
            ExprTy a;
            Token newline_var;
            PatternTy patterns_var;
            if (
                (a = (ExprTy)expect_SOFT_KEYWORD("case")) != null  // soft_keyword='"case"'
                &&
                (patterns_var = (PatternTy)patterns_rule()) != null  // patterns
                &&
                ((_opt_var = (ExprTy)guard_rule()) != null || true)  // guard?
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'case' statement on line %d", a.getSourceRange().startLine);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_as_pattern: or_pattern 'as' "_" | or_pattern 'as' !NAME expression
    public Object invalid_as_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // or_pattern 'as' "_"
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy a;
            PatternTy or_pattern_var;
            if (
                (or_pattern_var = (PatternTy)or_pattern_rule()) != null  // or_pattern
                &&
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (a = (ExprTy)expect_SOFT_KEYWORD("_")) != null  // soft_keyword='"_"'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "cannot use '_' as a target");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // or_pattern 'as' !NAME expression
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy a;
            PatternTy or_pattern_var;
            if (
                (or_pattern_var = (PatternTy)or_pattern_rule()) != null  // or_pattern
                &&
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                genLookahead_name_token(false)
                &&
                (a = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "invalid pattern target");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_class_pattern: name_or_attr '(' invalid_class_argument_pattern
    public Object invalid_class_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // name_or_attr '(' invalid_class_argument_pattern
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            PatternTy[] a;
            ExprTy name_or_attr_var;
            if (
                (name_or_attr_var = (ExprTy)name_or_attr_rule()) != null  // name_or_attr
                &&
                (_literal = (Token)expect(7)) != null  // token='('
                &&
                (a = (PatternTy[])invalid_class_argument_pattern_rule()) != null  // invalid_class_argument_pattern
            )
            {
                _res = raiseSyntaxErrorKnownRange(a[0], a[a.length - 1], "positional patterns follow keyword patterns");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_class_argument_pattern:
    //     | [positional_patterns ','] keyword_patterns ',' positional_patterns
    public PatternTy[] invalid_class_argument_pattern_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // [positional_patterns ','] keyword_patterns ',' positional_patterns
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Object _opt_var;
            PatternTy[] a;
            KeyPatternPair[] keyword_patterns_var;
            if (
                ((_opt_var = _tmp_298_rule()) != null || true)  // [positional_patterns ',']
                &&
                (keyword_patterns_var = keyword_patterns_rule()) != null  // keyword_patterns
                &&
                (_literal = expect(12)) != null  // token=','
                &&
                (a = positional_patterns_rule()) != null  // positional_patterns
            )
            {
                _res = a;
                return (PatternTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy[])_res;
    }

    // invalid_if_stmt:
    //     | 'if' named_expression NEWLINE
    //     | 'if' named_expression ':' NEWLINE !INDENT
    public ExprTy invalid_if_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'if' named_expression NEWLINE
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy named_expression_var;
            Token newline_var;
            if (
                (_keyword = (Token)expect(668)) != null  // token='if'
                &&
                (named_expression_var = (ExprTy)named_expression_rule()) != null  // named_expression
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.raiseSyntaxError("expected ':'");
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'if' named_expression ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            ExprTy a_1;
            Token newline_var;
            if (
                (a = (Token)expect(668)) != null  // token='if'
                &&
                (a_1 = (ExprTy)named_expression_rule()) != null  // named_expression
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'if' statement on line %d", a.getSourceRange().startLine);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_elif_stmt:
    //     | 'elif' named_expression NEWLINE
    //     | 'elif' named_expression ':' NEWLINE !INDENT
    public ExprTy invalid_elif_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'elif' named_expression NEWLINE
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy named_expression_var;
            Token newline_var;
            if (
                (_keyword = (Token)expect(675)) != null  // token='elif'
                &&
                (named_expression_var = (ExprTy)named_expression_rule()) != null  // named_expression
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.raiseSyntaxError("expected ':'");
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'elif' named_expression ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            ExprTy named_expression_var;
            Token newline_var;
            if (
                (a = (Token)expect(675)) != null  // token='elif'
                &&
                (named_expression_var = (ExprTy)named_expression_rule()) != null  // named_expression
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'elif' statement on line %d", a.getSourceRange().startLine);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_else_stmt: 'else' ':' NEWLINE !INDENT
    public Object invalid_else_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'else' ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            Token newline_var;
            if (
                (a = (Token)expect(676)) != null  // token='else'
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'else' statement on line %d", a.getSourceRange().startLine);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_while_stmt:
    //     | 'while' named_expression NEWLINE
    //     | 'while' named_expression ':' NEWLINE !INDENT
    public ExprTy invalid_while_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'while' named_expression NEWLINE
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy named_expression_var;
            Token newline_var;
            if (
                (_keyword = (Token)expect(673)) != null  // token='while'
                &&
                (named_expression_var = (ExprTy)named_expression_rule()) != null  // named_expression
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.raiseSyntaxError("expected ':'");
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // 'while' named_expression ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            ExprTy named_expression_var;
            Token newline_var;
            if (
                (a = (Token)expect(673)) != null  // token='while'
                &&
                (named_expression_var = (ExprTy)named_expression_rule()) != null  // named_expression
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'while' statement on line %d", a.getSourceRange().startLine);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_for_stmt:
    //     | ASYNC? 'for' star_targets 'in' star_expressions NEWLINE
    //     | ASYNC? 'for' star_targets 'in' star_expressions ':' NEWLINE !INDENT
    public ExprTy invalid_for_stmt_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ASYNC? 'for' star_targets 'in' star_expressions NEWLINE
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _keyword_1;
            Token _opt_var;
            Token newline_var;
            ExprTy star_expressions_var;
            ExprTy star_targets_var;
            if (
                ((_opt_var = (Token)_tmp_299_rule()) != null || true)  // ASYNC?
                &&
                (_keyword = (Token)expect(671)) != null  // token='for'
                &&
                (star_targets_var = (ExprTy)star_targets_rule()) != null  // star_targets
                &&
                (_keyword_1 = (Token)expect(677)) != null  // token='in'
                &&
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.raiseSyntaxError("expected ':'");
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // ASYNC? 'for' star_targets 'in' star_expressions ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            Token _literal;
            Token _opt_var;
            Token a;
            Token newline_var;
            ExprTy star_expressions_var;
            ExprTy star_targets_var;
            if (
                ((_opt_var = (Token)_tmp_300_rule()) != null || true)  // ASYNC?
                &&
                (a = (Token)expect(671)) != null  // token='for'
                &&
                (star_targets_var = (ExprTy)star_targets_rule()) != null  // star_targets
                &&
                (_keyword = (Token)expect(677)) != null  // token='in'
                &&
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after 'for' statement on line %d", a.getSourceRange().startLine);
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_def_raw:
    //     | ASYNC? 'def' NAME type_params? '(' params? ')' ['->' expression] ':' NEWLINE !INDENT
    public Object invalid_def_raw_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ASYNC? 'def' NAME type_params? '(' params? ')' ['->' expression] ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            Token _literal_2;
            Token _opt_var;
            TypeParamTy[] _opt_var_1;
            ArgumentsTy _opt_var_2;
            Object _opt_var_3;
            Token a;
            ExprTy name_var;
            Token newline_var;
            if (
                ((_opt_var = (Token)_tmp_301_rule()) != null || true)  // ASYNC?
                &&
                (a = (Token)expect(667)) != null  // token='def'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
                &&
                ((_opt_var_1 = (TypeParamTy[])_tmp_302_rule()) != null || true)  // type_params?
                &&
                (_literal = (Token)expect(7)) != null  // token='('
                &&
                ((_opt_var_2 = (ArgumentsTy)_tmp_303_rule()) != null || true)  // params?
                &&
                (_literal_1 = (Token)expect(8)) != null  // token=')'
                &&
                ((_opt_var_3 = (Object)_tmp_304_rule()) != null || true)  // ['->' expression]
                &&
                (_literal_2 = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after function definition on line %d", a.getSourceRange().startLine);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_class_def_raw:
    //     | 'class' NAME type_params? ['(' arguments? ')'] NEWLINE
    //     | 'class' NAME type_params? ['(' arguments? ')'] ':' NEWLINE !INDENT
    public Object invalid_class_def_raw_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'class' NAME type_params? ['(' arguments? ')'] NEWLINE
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            TypeParamTy[] _opt_var;
            Object _opt_var_1;
            ExprTy name_var;
            Token newline_var;
            if (
                (_keyword = (Token)expect(669)) != null  // token='class'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
                &&
                ((_opt_var = (TypeParamTy[])_tmp_305_rule()) != null || true)  // type_params?
                &&
                ((_opt_var_1 = (Object)_tmp_306_rule()) != null || true)  // ['(' arguments? ')']
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = this.raiseSyntaxError("expected ':'");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'class' NAME type_params? ['(' arguments? ')'] ':' NEWLINE !INDENT
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            TypeParamTy[] _opt_var;
            Object _opt_var_1;
            Token a;
            ExprTy name_var;
            Token newline_var;
            if (
                (a = (Token)expect(669)) != null  // token='class'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
                &&
                ((_opt_var = (TypeParamTy[])_tmp_307_rule()) != null || true)  // type_params?
                &&
                ((_opt_var_1 = (Object)_tmp_308_rule()) != null || true)  // ['(' arguments? ')']
                &&
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                genLookahead_expect(false, Token.Kind.INDENT)  // token=INDENT
            )
            {
                _res = this.raiseIndentationError("expected an indented block after class definition on line %d", a.getSourceRange().startLine);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_double_starred_kvpairs:
    //     | ','.double_starred_kvpair+ ',' invalid_kvpair
    //     | expression ':' '*' bitwise_or
    //     | expression ':' &('}' | ',')
    public Object invalid_double_starred_kvpairs_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.double_starred_kvpair+ ',' invalid_kvpair
            if (errorIndicator) {
                return null;
            }
            KeyValuePair[] _gather_309_var;
            Token _literal;
            ExprTy invalid_kvpair_var;
            if (
                (_gather_309_var = (KeyValuePair[])_gather_309_rule()) != null  // ','.double_starred_kvpair+
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (invalid_kvpair_var = (ExprTy)invalid_kvpair_rule()) != null  // invalid_kvpair
            )
            {
                _res = dummyName(_gather_309_var, _literal, invalid_kvpair_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // expression ':' '*' bitwise_or
            if (errorIndicator) {
                return null;
            }
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
                _res = this.raiseSyntaxErrorStartingFrom(a, "cannot use a starred expression in a dictionary value");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // expression ':' &('}' | ',')
            if (errorIndicator) {
                return null;
            }
            Token a;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (a = (Token)expect(11)) != null  // token=':'
                &&
                genLookahead__tmp_311_rule(true)
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "expression expected after dictionary key and ':'");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_kvpair:
    //     | expression !(':')
    //     | expression ':' '*' bitwise_or
    //     | expression ':' &('}' | ',')
    public ExprTy invalid_kvpair_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression !(':')
            if (errorIndicator) {
                return null;
            }
            ExprTy a;
            if (
                (a = (ExprTy)expression_rule()) != null  // expression
                &&
                genLookahead__tmp_312_rule(false)
            )
            {
                _res = this.raiseErrorKnownLocation(ParserCallbacks.ErrorType.Syntax, a, "':' expected after dictionary key");
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression ':' '*' bitwise_or
            if (errorIndicator) {
                return null;
            }
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
                _res = this.raiseSyntaxErrorStartingFrom(a, "cannot use a starred expression in a dictionary value");
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression ':' &('}' | ',')
            if (errorIndicator) {
                return null;
            }
            Token a;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (a = (Token)expect(11)) != null  // token=':'
                &&
                genLookahead__tmp_313_rule(true)
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "expression expected after dictionary key and ':'");
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_starred_expression: '*' expression '=' expression
    public ExprTy invalid_starred_expression_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '*' expression '=' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            ExprTy b;
            ExprTy expression_var;
            if (
                (a = (Token)expect(16)) != null  // token='*'
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (_literal = (Token)expect(22)) != null  // token='='
                &&
                (b = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = this.raiseSyntaxErrorKnownRange(a, b, "cannot assign to iterable argument unpacking");
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // invalid_replacement_field:
    //     | '{' '='
    //     | '{' '!'
    //     | '{' ':'
    //     | '{' '}'
    //     | '{' !(yield_expr | star_expressions)
    //     | '{' (yield_expr | star_expressions) !('=' | '!' | ':' | '}')
    //     | '{' (yield_expr | star_expressions) '=' !('!' | ':' | '}')
    //     | '{' (yield_expr | star_expressions) '='? invalid_conversion_character
    //     | '{' (yield_expr | star_expressions) '='? ['!' NAME] !(':' | '}')
    //     | '{' (yield_expr | star_expressions) '='? ['!' NAME] ':' fstring_format_spec* !'}'
    //     | '{' (yield_expr | star_expressions) '='? ['!' NAME] !'}'
    public Object invalid_replacement_field_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '{' '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
                &&
                (a = (Token)expect(22)) != null  // token='='
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "f-string: valid expression required before '='");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '{' '!'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
                &&
                (a = (Token)expect(54)) != null  // token='!'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "f-string: valid expression required before '!'");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '{' ':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
                &&
                (a = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "f-string: valid expression required before ':'");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '{' '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token a;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
                &&
                (a = (Token)expect(26)) != null  // token='}'
            )
            {
                _res = this.raiseSyntaxErrorKnownLocation(a, "f-string: valid expression required before '}'");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '{' !(yield_expr | star_expressions)
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
                &&
                genLookahead__tmp_314_rule(false)
            )
            {
                _res = raiseSyntaxErrorOnNextToken("f-string: expecting a valid expression after '{'");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '{' (yield_expr | star_expressions) !('=' | '!' | ':' | '}')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy _tmp_315_var;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
                &&
                (_tmp_315_var = (ExprTy)_tmp_315_rule()) != null  // yield_expr | star_expressions
                &&
                genLookahead__tmp_316_rule(false)
            )
            {
                _res = dummyName(_literal, _tmp_315_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '{' (yield_expr | star_expressions) '=' !('!' | ':' | '}')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy _tmp_317_var;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
                &&
                (_tmp_317_var = (ExprTy)_tmp_317_rule()) != null  // yield_expr | star_expressions
                &&
                (_literal_1 = (Token)expect(22)) != null  // token='='
                &&
                genLookahead__tmp_318_rule(false)
            )
            {
                _res = raiseSyntaxErrorOnNextToken("f-string: expecting '!', or ':', or '}'");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '{' (yield_expr | star_expressions) '='? invalid_conversion_character
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _opt_var;
            ExprTy _tmp_319_var;
            Object invalid_conversion_character_var;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
                &&
                (_tmp_319_var = (ExprTy)_tmp_319_rule()) != null  // yield_expr | star_expressions
                &&
                ((_opt_var = (Token)expect(22)) != null || true)  // '='?
                &&
                (invalid_conversion_character_var = (Object)invalid_conversion_character_rule()) != null  // invalid_conversion_character
            )
            {
                _res = dummyName(_literal, _tmp_319_var, _opt_var, invalid_conversion_character_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '{' (yield_expr | star_expressions) '='? ['!' NAME] !(':' | '}')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _opt_var;
            Object _opt_var_1;
            ExprTy _tmp_320_var;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
                &&
                (_tmp_320_var = (ExprTy)_tmp_320_rule()) != null  // yield_expr | star_expressions
                &&
                ((_opt_var = (Token)expect(22)) != null || true)  // '='?
                &&
                ((_opt_var_1 = (Object)_tmp_321_rule()) != null || true)  // ['!' NAME]
                &&
                genLookahead__tmp_322_rule(false)
            )
            {
                _res = raiseSyntaxErrorOnNextToken("f-string: expecting ':' or '}'");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '{' (yield_expr | star_expressions) '='? ['!' NAME] ':' fstring_format_spec* !'}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy[] _loop0_325_var;
            Token _opt_var;
            Object _opt_var_1;
            ExprTy _tmp_323_var;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
                &&
                (_tmp_323_var = (ExprTy)_tmp_323_rule()) != null  // yield_expr | star_expressions
                &&
                ((_opt_var = (Token)expect(22)) != null || true)  // '='?
                &&
                ((_opt_var_1 = (Object)_tmp_324_rule()) != null || true)  // ['!' NAME]
                &&
                (_literal_1 = (Token)expect(11)) != null  // token=':'
                &&
                (_loop0_325_var = (ExprTy[])_loop0_325_rule()) != null  // fstring_format_spec*
                &&
                genLookahead_expect(false, 26)  // token='}'
            )
            {
                _res = raiseSyntaxErrorOnNextToken("f-string: expecting '}', or format specs");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '{' (yield_expr | star_expressions) '='? ['!' NAME] !'}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _opt_var;
            Object _opt_var_1;
            ExprTy _tmp_326_var;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
                &&
                (_tmp_326_var = (ExprTy)_tmp_326_rule()) != null  // yield_expr | star_expressions
                &&
                ((_opt_var = (Token)expect(22)) != null || true)  // '='?
                &&
                ((_opt_var_1 = (Object)_tmp_327_rule()) != null || true)  // ['!' NAME]
                &&
                genLookahead_expect(false, 26)  // token='}'
            )
            {
                _res = raiseSyntaxErrorOnNextToken("f-string: expecting '}'");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // invalid_conversion_character: '!' &(':' | '}') | '!' !NAME
    public Object invalid_conversion_character_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '!' &(':' | '}')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(54)) != null  // token='!'
                &&
                genLookahead__tmp_328_rule(true)
            )
            {
                _res = raiseSyntaxErrorOnNextToken("f-string: missing conversion character");
                return (Object)_res;
            }
            reset(_mark);
        }
        { // '!' !NAME
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(54)) != null  // token='!'
                &&
                genLookahead_name_token(false)
            )
            {
                _res = raiseSyntaxErrorOnNextToken("f-string: invalid conversion character");
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_1: statements
    public StmtTy[] _tmp_1_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // statements
            if (errorIndicator) {
                return null;
            }
            StmtTy[] statements_var;
            if (
                (statements_var = (StmtTy[])statements_rule()) != null  // statements
            )
            {
                _res = statements_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // _loop0_2: NEWLINE
    public Token[] _loop0_2_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<Token> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // NEWLINE
            if (errorIndicator) {
                return null;
            }
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // type_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy[] type_expressions_var;
            if (
                (type_expressions_var = (ExprTy[])type_expressions_rule()) != null  // type_expressions
            )
            {
                _res = type_expressions_var;
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _loop0_4: NEWLINE
    public Token[] _loop0_4_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<Token> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // NEWLINE
            if (errorIndicator) {
                return null;
            }
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

    // _loop1_5: statement
    public StmtTy[] _loop1_5_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<StmtTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // statement
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_5_ID, _seq);
        return _seq;
    }

    // _loop0_7: ';' simple_stmt
    public StmtTy[] _loop0_7_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<StmtTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ';' simple_stmt
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_7_ID, _seq);
        return _seq;
    }

    // _gather_6: simple_stmt _loop0_7
    public StmtTy[] _gather_6_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // simple_stmt _loop0_7
            if (errorIndicator) {
                return null;
            }
            StmtTy elem;
            StmtTy[] seq;
            if (
                (elem = (StmtTy)simple_stmt_rule()) != null  // simple_stmt
                &&
                (seq = (StmtTy[])_loop0_7_rule()) != null  // _loop0_7
            )
            {
                _res = insertInFront(elem, seq, StmtTy.class);
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // _tmp_8: ';'
    public Token _tmp_8_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ';'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(13)) != null  // token=';'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_9: 'import' | 'from'
    public Token _tmp_9_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'import'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(656)) != null  // token='import'
            )
            {
                _res = _keyword;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // 'from'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(657)) != null  // token='from'
            )
            {
                _res = _keyword;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_10: 'def' | '@' | ASYNC
    public Token _tmp_10_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'def'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(667)) != null  // token='def'
            )
            {
                _res = _keyword;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '@'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(49)) != null  // token='@'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ASYNC
            if (errorIndicator) {
                return null;
            }
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_11: 'class' | '@'
    public Token _tmp_11_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'class'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(669)) != null  // token='class'
            )
            {
                _res = _keyword;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '@'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(49)) != null  // token='@'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_12: 'with' | ASYNC
    public Token _tmp_12_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'with'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(670)) != null  // token='with'
            )
            {
                _res = _keyword;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ASYNC
            if (errorIndicator) {
                return null;
            }
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_13: 'for' | ASYNC
    public Token _tmp_13_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'for'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(671)) != null  // token='for'
            )
            {
                _res = _keyword;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ASYNC
            if (errorIndicator) {
                return null;
            }
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_14: '=' annotated_rhs
    public ExprTy _tmp_14_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '=' annotated_rhs
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy d;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
                &&
                (d = (ExprTy)annotated_rhs_rule()) != null  // annotated_rhs
            )
            {
                _res = d;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_15: '(' single_target ')' | single_subscript_attribute_target
    public ExprTy _tmp_15_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '(' single_target ')'
            if (errorIndicator) {
                return null;
            }
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
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // single_subscript_attribute_target
            if (errorIndicator) {
                return null;
            }
            ExprTy single_subscript_attribute_target_var;
            if (
                (single_subscript_attribute_target_var = (ExprTy)single_subscript_attribute_target_rule()) != null  // single_subscript_attribute_target
            )
            {
                _res = single_subscript_attribute_target_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_16: '=' annotated_rhs
    public ExprTy _tmp_16_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '=' annotated_rhs
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy d;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
                &&
                (d = (ExprTy)annotated_rhs_rule()) != null  // annotated_rhs
            )
            {
                _res = d;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _loop1_17: (star_targets '=')
    public ExprTy[] _loop1_17_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (star_targets '=')
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_329_var;
            while (
                (_tmp_329_var = (ExprTy)_tmp_329_rule()) != null  // star_targets '='
            )
            {
                _res = _tmp_329_var;
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
        cache.putResult(_start_mark, _LOOP1_17_ID, _seq);
        return _seq;
    }

    // _tmp_18: yield_expr | star_expressions
    public ExprTy _tmp_18_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_19: TYPE_COMMENT
    public Token _tmp_19_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // TYPE_COMMENT
            if (errorIndicator) {
                return null;
            }
            Token type_comment_var;
            if (
                (type_comment_var = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                _res = type_comment_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_20: yield_expr | star_expressions
    public ExprTy _tmp_20_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_21: star_expressions
    public ExprTy _tmp_21_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_22: 'from' expression
    public ExprTy _tmp_22_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'from' expression
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(657)) != null  // token='from'
                &&
                (z = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = z;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _loop0_24: ',' NAME
    public ExprTy[] _loop0_24_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' NAME
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_24_ID, _seq);
        return _seq;
    }

    // _gather_23: NAME _loop0_24
    public ExprTy[] _gather_23_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // NAME _loop0_24
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)name_token()) != null  // NAME
                &&
                (seq = (ExprTy[])_loop0_24_rule()) != null  // _loop0_24
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _loop0_26: ',' NAME
    public ExprTy[] _loop0_26_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' NAME
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_26_ID, _seq);
        return _seq;
    }

    // _gather_25: NAME _loop0_26
    public ExprTy[] _gather_25_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // NAME _loop0_26
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)name_token()) != null  // NAME
                &&
                (seq = (ExprTy[])_loop0_26_rule()) != null  // _loop0_26
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_27: ';' | NEWLINE
    public Token _tmp_27_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ';'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(13)) != null  // token=';'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // NEWLINE
            if (errorIndicator) {
                return null;
            }
            Token newline_var;
            if (
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = newline_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_28: ',' expression
    public ExprTy _tmp_28_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ',' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy z;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (z = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = z;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _loop0_29: ('.' | '...')
    public Token[] _loop0_29_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<Token> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('.' | '...')
            if (errorIndicator) {
                return null;
            }
            Token _tmp_330_var;
            while (
                (_tmp_330_var = (Token)_tmp_330_rule()) != null  // '.' | '...'
            )
            {
                _res = _tmp_330_var;
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
        cache.putResult(_start_mark, _LOOP0_29_ID, _seq);
        return _seq;
    }

    // _loop1_30: ('.' | '...')
    public Token[] _loop1_30_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<Token> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('.' | '...')
            if (errorIndicator) {
                return null;
            }
            Token _tmp_331_var;
            while (
                (_tmp_331_var = (Token)_tmp_331_rule()) != null  // '.' | '...'
            )
            {
                _res = _tmp_331_var;
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
        cache.putResult(_start_mark, _LOOP1_30_ID, _seq);
        return _seq;
    }

    // _tmp_31: ','
    public Token _tmp_31_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_33: ',' import_from_as_name
    public AliasTy[] _loop0_33_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<AliasTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' import_from_as_name
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_33_ID, _seq);
        return _seq;
    }

    // _gather_32: import_from_as_name _loop0_33
    public AliasTy[] _gather_32_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // import_from_as_name _loop0_33
            if (errorIndicator) {
                return null;
            }
            AliasTy elem;
            AliasTy[] seq;
            if (
                (elem = (AliasTy)import_from_as_name_rule()) != null  // import_from_as_name
                &&
                (seq = (AliasTy[])_loop0_33_rule()) != null  // _loop0_33
            )
            {
                _res = insertInFront(elem, seq, AliasTy.class);
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (AliasTy[])_res;
    }

    // _tmp_34: 'as' NAME
    public ExprTy _tmp_34_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' NAME
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (z = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = z;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _loop0_36: ',' dotted_as_name
    public AliasTy[] _loop0_36_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<AliasTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' dotted_as_name
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_36_ID, _seq);
        return _seq;
    }

    // _gather_35: dotted_as_name _loop0_36
    public AliasTy[] _gather_35_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // dotted_as_name _loop0_36
            if (errorIndicator) {
                return null;
            }
            AliasTy elem;
            AliasTy[] seq;
            if (
                (elem = (AliasTy)dotted_as_name_rule()) != null  // dotted_as_name
                &&
                (seq = (AliasTy[])_loop0_36_rule()) != null  // _loop0_36
            )
            {
                _res = insertInFront(elem, seq, AliasTy.class);
                return (AliasTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (AliasTy[])_res;
    }

    // _tmp_37: 'as' NAME
    public ExprTy _tmp_37_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' NAME
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (z = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = z;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _loop1_38: ('@' named_expression NEWLINE)
    public ExprTy[] _loop1_38_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('@' named_expression NEWLINE)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_332_var;
            while (
                (_tmp_332_var = (ExprTy)_tmp_332_rule()) != null  // '@' named_expression NEWLINE
            )
            {
                _res = _tmp_332_var;
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
        cache.putResult(_start_mark, _LOOP1_38_ID, _seq);
        return _seq;
    }

    // _tmp_39: type_params
    public TypeParamTy[] _tmp_39_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // type_params
            if (errorIndicator) {
                return null;
            }
            TypeParamTy[] type_params_var;
            if (
                (type_params_var = (TypeParamTy[])type_params_rule()) != null  // type_params
            )
            {
                _res = type_params_var;
                return (TypeParamTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (TypeParamTy[])_res;
    }

    // _tmp_40: '(' arguments? ')'
    public ExprTy _tmp_40_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '(' arguments? ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy z;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
                &&
                ((z = (ExprTy)_tmp_333_rule()) != null || true)  // arguments?
                &&
                (_literal_1 = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = z;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_41: type_params
    public TypeParamTy[] _tmp_41_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // type_params
            if (errorIndicator) {
                return null;
            }
            TypeParamTy[] type_params_var;
            if (
                (type_params_var = (TypeParamTy[])type_params_rule()) != null  // type_params
            )
            {
                _res = type_params_var;
                return (TypeParamTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (TypeParamTy[])_res;
    }

    // _tmp_42: params
    public ArgumentsTy _tmp_42_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // params
            if (errorIndicator) {
                return null;
            }
            ArgumentsTy params_var;
            if (
                (params_var = (ArgumentsTy)params_rule()) != null  // params
            )
            {
                _res = params_var;
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgumentsTy)_res;
    }

    // _tmp_43: '->' expression
    public ExprTy _tmp_43_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '->' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy z;
            if (
                (_literal = (Token)expect(51)) != null  // token='->'
                &&
                (z = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = z;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_44: func_type_comment
    public Token _tmp_44_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // func_type_comment
            if (errorIndicator) {
                return null;
            }
            Token func_type_comment_var;
            if (
                (func_type_comment_var = (Token)func_type_comment_rule()) != null  // func_type_comment
            )
            {
                _res = func_type_comment_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_45: type_params
    public TypeParamTy[] _tmp_45_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // type_params
            if (errorIndicator) {
                return null;
            }
            TypeParamTy[] type_params_var;
            if (
                (type_params_var = (TypeParamTy[])type_params_rule()) != null  // type_params
            )
            {
                _res = type_params_var;
                return (TypeParamTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (TypeParamTy[])_res;
    }

    // _tmp_46: params
    public ArgumentsTy _tmp_46_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // params
            if (errorIndicator) {
                return null;
            }
            ArgumentsTy params_var;
            if (
                (params_var = (ArgumentsTy)params_rule()) != null  // params
            )
            {
                _res = params_var;
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgumentsTy)_res;
    }

    // _tmp_47: '->' expression
    public ExprTy _tmp_47_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '->' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy z;
            if (
                (_literal = (Token)expect(51)) != null  // token='->'
                &&
                (z = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = z;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_48: func_type_comment
    public Token _tmp_48_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // func_type_comment
            if (errorIndicator) {
                return null;
            }
            Token func_type_comment_var;
            if (
                (func_type_comment_var = (Token)func_type_comment_rule()) != null  // func_type_comment
            )
            {
                _res = func_type_comment_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_49: param_no_default
    public ArgTy[] _loop0_49_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_49_ID, _seq);
        return _seq;
    }

    // _loop0_50: param_with_default
    public NameDefaultPair[] _loop0_50_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_50_ID, _seq);
        return _seq;
    }

    // _tmp_51: star_etc
    public StarEtc _tmp_51_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_etc
            if (errorIndicator) {
                return null;
            }
            StarEtc star_etc_var;
            if (
                (star_etc_var = (StarEtc)star_etc_rule()) != null  // star_etc
            )
            {
                _res = star_etc_var;
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StarEtc)_res;
    }

    // _loop0_52: param_with_default
    public NameDefaultPair[] _loop0_52_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_52_ID, _seq);
        return _seq;
    }

    // _tmp_53: star_etc
    public StarEtc _tmp_53_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_etc
            if (errorIndicator) {
                return null;
            }
            StarEtc star_etc_var;
            if (
                (star_etc_var = (StarEtc)star_etc_rule()) != null  // star_etc
            )
            {
                _res = star_etc_var;
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StarEtc)_res;
    }

    // _loop1_54: param_no_default
    public ArgTy[] _loop1_54_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_54_ID, _seq);
        return _seq;
    }

    // _loop0_55: param_with_default
    public NameDefaultPair[] _loop0_55_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_55_ID, _seq);
        return _seq;
    }

    // _tmp_56: star_etc
    public StarEtc _tmp_56_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_etc
            if (errorIndicator) {
                return null;
            }
            StarEtc star_etc_var;
            if (
                (star_etc_var = (StarEtc)star_etc_rule()) != null  // star_etc
            )
            {
                _res = star_etc_var;
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StarEtc)_res;
    }

    // _loop1_57: param_with_default
    public NameDefaultPair[] _loop1_57_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_57_ID, _seq);
        return _seq;
    }

    // _tmp_58: star_etc
    public StarEtc _tmp_58_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_etc
            if (errorIndicator) {
                return null;
            }
            StarEtc star_etc_var;
            if (
                (star_etc_var = (StarEtc)star_etc_rule()) != null  // star_etc
            )
            {
                _res = star_etc_var;
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StarEtc)_res;
    }

    // _loop1_59: param_no_default
    public ArgTy[] _loop1_59_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_59_ID, _seq);
        return _seq;
    }

    // _loop1_60: param_no_default
    public ArgTy[] _loop1_60_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_60_ID, _seq);
        return _seq;
    }

    // _loop0_61: param_no_default
    public ArgTy[] _loop0_61_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_61_ID, _seq);
        return _seq;
    }

    // _loop1_62: param_with_default
    public NameDefaultPair[] _loop1_62_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_62_ID, _seq);
        return _seq;
    }

    // _loop0_63: param_no_default
    public ArgTy[] _loop0_63_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_63_ID, _seq);
        return _seq;
    }

    // _loop1_64: param_with_default
    public NameDefaultPair[] _loop1_64_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_64_ID, _seq);
        return _seq;
    }

    // _loop0_65: param_maybe_default
    public NameDefaultPair[] _loop0_65_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_65_ID, _seq);
        return _seq;
    }

    // _tmp_66: kwds
    public ArgTy _tmp_66_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // kwds
            if (errorIndicator) {
                return null;
            }
            ArgTy kwds_var;
            if (
                (kwds_var = (ArgTy)kwds_rule()) != null  // kwds
            )
            {
                _res = kwds_var;
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // _loop0_67: param_maybe_default
    public NameDefaultPair[] _loop0_67_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_67_ID, _seq);
        return _seq;
    }

    // _tmp_68: kwds
    public ArgTy _tmp_68_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // kwds
            if (errorIndicator) {
                return null;
            }
            ArgTy kwds_var;
            if (
                (kwds_var = (ArgTy)kwds_rule()) != null  // kwds
            )
            {
                _res = kwds_var;
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // _loop1_69: param_maybe_default
    public NameDefaultPair[] _loop1_69_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_69_ID, _seq);
        return _seq;
    }

    // _tmp_70: kwds
    public ArgTy _tmp_70_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // kwds
            if (errorIndicator) {
                return null;
            }
            ArgTy kwds_var;
            if (
                (kwds_var = (ArgTy)kwds_rule()) != null  // kwds
            )
            {
                _res = kwds_var;
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // _tmp_71: else_block
    public StmtTy[] _tmp_71_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // else_block
            if (errorIndicator) {
                return null;
            }
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // _tmp_72: else_block
    public StmtTy[] _tmp_72_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // else_block
            if (errorIndicator) {
                return null;
            }
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // _tmp_73: else_block
    public StmtTy[] _tmp_73_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // else_block
            if (errorIndicator) {
                return null;
            }
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // _tmp_74: TYPE_COMMENT
    public Token _tmp_74_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // TYPE_COMMENT
            if (errorIndicator) {
                return null;
            }
            Token type_comment_var;
            if (
                (type_comment_var = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                _res = type_comment_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_75: else_block
    public StmtTy[] _tmp_75_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // else_block
            if (errorIndicator) {
                return null;
            }
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // _tmp_76: TYPE_COMMENT
    public Token _tmp_76_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // TYPE_COMMENT
            if (errorIndicator) {
                return null;
            }
            Token type_comment_var;
            if (
                (type_comment_var = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                _res = type_comment_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_77: else_block
    public StmtTy[] _tmp_77_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // else_block
            if (errorIndicator) {
                return null;
            }
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // _loop0_79: ',' with_item
    public WithItemTy[] _loop0_79_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<WithItemTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' with_item
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            WithItemTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (WithItemTy)with_item_rule()) != null  // with_item
            )
            {
                _res = elem;
                if (_res instanceof WithItemTy) {
                    _children.add((WithItemTy)_res);
                } else {
                    _children.addAll(Arrays.asList((WithItemTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        WithItemTy[] _seq = _children.toArray(new WithItemTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_79_ID, _seq);
        return _seq;
    }

    // _gather_78: with_item _loop0_79
    public WithItemTy[] _gather_78_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // with_item _loop0_79
            if (errorIndicator) {
                return null;
            }
            WithItemTy elem;
            WithItemTy[] seq;
            if (
                (elem = (WithItemTy)with_item_rule()) != null  // with_item
                &&
                (seq = (WithItemTy[])_loop0_79_rule()) != null  // _loop0_79
            )
            {
                _res = insertInFront(elem, seq, WithItemTy.class);
                return (WithItemTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (WithItemTy[])_res;
    }

    // _loop0_81: ',' with_item
    public WithItemTy[] _loop0_81_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<WithItemTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' with_item
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            WithItemTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (WithItemTy)with_item_rule()) != null  // with_item
            )
            {
                _res = elem;
                if (_res instanceof WithItemTy) {
                    _children.add((WithItemTy)_res);
                } else {
                    _children.addAll(Arrays.asList((WithItemTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        WithItemTy[] _seq = _children.toArray(new WithItemTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_81_ID, _seq);
        return _seq;
    }

    // _gather_80: with_item _loop0_81
    public WithItemTy[] _gather_80_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // with_item _loop0_81
            if (errorIndicator) {
                return null;
            }
            WithItemTy elem;
            WithItemTy[] seq;
            if (
                (elem = (WithItemTy)with_item_rule()) != null  // with_item
                &&
                (seq = (WithItemTy[])_loop0_81_rule()) != null  // _loop0_81
            )
            {
                _res = insertInFront(elem, seq, WithItemTy.class);
                return (WithItemTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (WithItemTy[])_res;
    }

    // _tmp_82: TYPE_COMMENT
    public Token _tmp_82_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // TYPE_COMMENT
            if (errorIndicator) {
                return null;
            }
            Token type_comment_var;
            if (
                (type_comment_var = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                _res = type_comment_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_84: ',' with_item
    public WithItemTy[] _loop0_84_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<WithItemTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' with_item
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            WithItemTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (WithItemTy)with_item_rule()) != null  // with_item
            )
            {
                _res = elem;
                if (_res instanceof WithItemTy) {
                    _children.add((WithItemTy)_res);
                } else {
                    _children.addAll(Arrays.asList((WithItemTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        WithItemTy[] _seq = _children.toArray(new WithItemTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_84_ID, _seq);
        return _seq;
    }

    // _gather_83: with_item _loop0_84
    public WithItemTy[] _gather_83_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // with_item _loop0_84
            if (errorIndicator) {
                return null;
            }
            WithItemTy elem;
            WithItemTy[] seq;
            if (
                (elem = (WithItemTy)with_item_rule()) != null  // with_item
                &&
                (seq = (WithItemTy[])_loop0_84_rule()) != null  // _loop0_84
            )
            {
                _res = insertInFront(elem, seq, WithItemTy.class);
                return (WithItemTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (WithItemTy[])_res;
    }

    // _loop0_86: ',' with_item
    public WithItemTy[] _loop0_86_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<WithItemTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' with_item
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            WithItemTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (WithItemTy)with_item_rule()) != null  // with_item
            )
            {
                _res = elem;
                if (_res instanceof WithItemTy) {
                    _children.add((WithItemTy)_res);
                } else {
                    _children.addAll(Arrays.asList((WithItemTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        WithItemTy[] _seq = _children.toArray(new WithItemTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_86_ID, _seq);
        return _seq;
    }

    // _gather_85: with_item _loop0_86
    public WithItemTy[] _gather_85_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // with_item _loop0_86
            if (errorIndicator) {
                return null;
            }
            WithItemTy elem;
            WithItemTy[] seq;
            if (
                (elem = (WithItemTy)with_item_rule()) != null  // with_item
                &&
                (seq = (WithItemTy[])_loop0_86_rule()) != null  // _loop0_86
            )
            {
                _res = insertInFront(elem, seq, WithItemTy.class);
                return (WithItemTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (WithItemTy[])_res;
    }

    // _tmp_87: TYPE_COMMENT
    public Token _tmp_87_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // TYPE_COMMENT
            if (errorIndicator) {
                return null;
            }
            Token type_comment_var;
            if (
                (type_comment_var = (Token)expect(Token.Kind.TYPE_COMMENT)) != null  // token='TYPE_COMMENT'
            )
            {
                _res = type_comment_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_88: ',' | ')' | ':'
    public Token _tmp_88_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop1_89: except_block
    public ExceptHandlerTy[] _loop1_89_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExceptHandlerTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // except_block
            if (errorIndicator) {
                return null;
            }
            ExceptHandlerTy except_block_var;
            while (
                (except_block_var = (ExceptHandlerTy)except_block_rule()) != null  // except_block
            )
            {
                _res = except_block_var;
                if (_res instanceof ExceptHandlerTy) {
                    _children.add((ExceptHandlerTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExceptHandlerTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ExceptHandlerTy[] _seq = _children.toArray(new ExceptHandlerTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_89_ID, _seq);
        return _seq;
    }

    // _tmp_90: else_block
    public StmtTy[] _tmp_90_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // else_block
            if (errorIndicator) {
                return null;
            }
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // _tmp_91: finally_block
    public StmtTy[] _tmp_91_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // finally_block
            if (errorIndicator) {
                return null;
            }
            StmtTy[] finally_block_var;
            if (
                (finally_block_var = (StmtTy[])finally_block_rule()) != null  // finally_block
            )
            {
                _res = finally_block_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // _loop1_92: except_star_block
    public ExceptHandlerTy[] _loop1_92_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExceptHandlerTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // except_star_block
            if (errorIndicator) {
                return null;
            }
            ExceptHandlerTy except_star_block_var;
            while (
                (except_star_block_var = (ExceptHandlerTy)except_star_block_rule()) != null  // except_star_block
            )
            {
                _res = except_star_block_var;
                if (_res instanceof ExceptHandlerTy) {
                    _children.add((ExceptHandlerTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExceptHandlerTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ExceptHandlerTy[] _seq = _children.toArray(new ExceptHandlerTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_92_ID, _seq);
        return _seq;
    }

    // _tmp_93: else_block
    public StmtTy[] _tmp_93_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // else_block
            if (errorIndicator) {
                return null;
            }
            StmtTy[] else_block_var;
            if (
                (else_block_var = (StmtTy[])else_block_rule()) != null  // else_block
            )
            {
                _res = else_block_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // _tmp_94: finally_block
    public StmtTy[] _tmp_94_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // finally_block
            if (errorIndicator) {
                return null;
            }
            StmtTy[] finally_block_var;
            if (
                (finally_block_var = (StmtTy[])finally_block_rule()) != null  // finally_block
            )
            {
                _res = finally_block_var;
                return (StmtTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StmtTy[])_res;
    }

    // _tmp_95: 'as' NAME
    public ExprTy _tmp_95_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' NAME
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (z = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = z;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_96: 'as' NAME
    public ExprTy _tmp_96_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' NAME
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (z = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = z;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _loop1_97: case_block
    public MatchCaseTy[] _loop1_97_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<MatchCaseTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // case_block
            if (errorIndicator) {
                return null;
            }
            MatchCaseTy case_block_var;
            while (
                (case_block_var = (MatchCaseTy)case_block_rule()) != null  // case_block
            )
            {
                _res = case_block_var;
                if (_res instanceof MatchCaseTy) {
                    _children.add((MatchCaseTy)_res);
                } else {
                    _children.addAll(Arrays.asList((MatchCaseTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        MatchCaseTy[] _seq = _children.toArray(new MatchCaseTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_97_ID, _seq);
        return _seq;
    }

    // _loop0_99: '|' closed_pattern
    public PatternTy[] _loop0_99_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<PatternTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // '|' closed_pattern
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            PatternTy elem;
            while (
                (_literal = (Token)expect(18)) != null  // token='|'
                &&
                (elem = (PatternTy)closed_pattern_rule()) != null  // closed_pattern
            )
            {
                _res = elem;
                if (_res instanceof PatternTy) {
                    _children.add((PatternTy)_res);
                } else {
                    _children.addAll(Arrays.asList((PatternTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        PatternTy[] _seq = _children.toArray(new PatternTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_99_ID, _seq);
        return _seq;
    }

    // _gather_98: closed_pattern _loop0_99
    public PatternTy[] _gather_98_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // closed_pattern _loop0_99
            if (errorIndicator) {
                return null;
            }
            PatternTy elem;
            PatternTy[] seq;
            if (
                (elem = (PatternTy)closed_pattern_rule()) != null  // closed_pattern
                &&
                (seq = (PatternTy[])_loop0_99_rule()) != null  // _loop0_99
            )
            {
                _res = insertInFront(elem, seq, PatternTy.class);
                return (PatternTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy[])_res;
    }

    // _tmp_100: '+' | '-'
    public Token _tmp_100_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '+'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(14)) != null  // token='+'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '-'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(15)) != null  // token='-'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_101: '+' | '-'
    public Token _tmp_101_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '+'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(14)) != null  // token='+'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '-'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(15)) != null  // token='-'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_102: '.' | '(' | '='
    public Token _tmp_102_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '.'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(23)) != null  // token='.'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '('
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_103: '.' | '(' | '='
    public Token _tmp_103_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '.'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(23)) != null  // token='.'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '('
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_105: ',' maybe_star_pattern
    public PatternTy[] _loop0_105_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<PatternTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' maybe_star_pattern
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            PatternTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (PatternTy)maybe_star_pattern_rule()) != null  // maybe_star_pattern
            )
            {
                _res = elem;
                if (_res instanceof PatternTy) {
                    _children.add((PatternTy)_res);
                } else {
                    _children.addAll(Arrays.asList((PatternTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        PatternTy[] _seq = _children.toArray(new PatternTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_105_ID, _seq);
        return _seq;
    }

    // _gather_104: maybe_star_pattern _loop0_105
    public PatternTy[] _gather_104_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // maybe_star_pattern _loop0_105
            if (errorIndicator) {
                return null;
            }
            PatternTy elem;
            PatternTy[] seq;
            if (
                (elem = (PatternTy)maybe_star_pattern_rule()) != null  // maybe_star_pattern
                &&
                (seq = (PatternTy[])_loop0_105_rule()) != null  // _loop0_105
            )
            {
                _res = insertInFront(elem, seq, PatternTy.class);
                return (PatternTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy[])_res;
    }

    // _loop0_107: ',' key_value_pattern
    public KeyPatternPair[] _loop0_107_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<KeyPatternPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' key_value_pattern
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            KeyPatternPair elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (KeyPatternPair)key_value_pattern_rule()) != null  // key_value_pattern
            )
            {
                _res = elem;
                if (_res instanceof KeyPatternPair) {
                    _children.add((KeyPatternPair)_res);
                } else {
                    _children.addAll(Arrays.asList((KeyPatternPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        KeyPatternPair[] _seq = _children.toArray(new KeyPatternPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_107_ID, _seq);
        return _seq;
    }

    // _gather_106: key_value_pattern _loop0_107
    public KeyPatternPair[] _gather_106_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // key_value_pattern _loop0_107
            if (errorIndicator) {
                return null;
            }
            KeyPatternPair elem;
            KeyPatternPair[] seq;
            if (
                (elem = (KeyPatternPair)key_value_pattern_rule()) != null  // key_value_pattern
                &&
                (seq = (KeyPatternPair[])_loop0_107_rule()) != null  // _loop0_107
            )
            {
                _res = insertInFront(elem, seq, KeyPatternPair.class);
                return (KeyPatternPair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeyPatternPair[])_res;
    }

    // _tmp_108: literal_expr | attr
    public ExprTy _tmp_108_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // literal_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy literal_expr_var;
            if (
                (literal_expr_var = (ExprTy)literal_expr_rule()) != null  // literal_expr
            )
            {
                _res = literal_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // attr
            if (errorIndicator) {
                return null;
            }
            ExprTy attr_var;
            if (
                (attr_var = (ExprTy)attr_rule()) != null  // attr
            )
            {
                _res = attr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _loop0_110: ',' pattern
    public PatternTy[] _loop0_110_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<PatternTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' pattern
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            PatternTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (PatternTy)pattern_rule()) != null  // pattern
            )
            {
                _res = elem;
                if (_res instanceof PatternTy) {
                    _children.add((PatternTy)_res);
                } else {
                    _children.addAll(Arrays.asList((PatternTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        PatternTy[] _seq = _children.toArray(new PatternTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_110_ID, _seq);
        return _seq;
    }

    // _gather_109: pattern _loop0_110
    public PatternTy[] _gather_109_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // pattern _loop0_110
            if (errorIndicator) {
                return null;
            }
            PatternTy elem;
            PatternTy[] seq;
            if (
                (elem = (PatternTy)pattern_rule()) != null  // pattern
                &&
                (seq = (PatternTy[])_loop0_110_rule()) != null  // _loop0_110
            )
            {
                _res = insertInFront(elem, seq, PatternTy.class);
                return (PatternTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (PatternTy[])_res;
    }

    // _loop0_112: ',' keyword_pattern
    public KeyPatternPair[] _loop0_112_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<KeyPatternPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' keyword_pattern
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            KeyPatternPair elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (KeyPatternPair)keyword_pattern_rule()) != null  // keyword_pattern
            )
            {
                _res = elem;
                if (_res instanceof KeyPatternPair) {
                    _children.add((KeyPatternPair)_res);
                } else {
                    _children.addAll(Arrays.asList((KeyPatternPair[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        KeyPatternPair[] _seq = _children.toArray(new KeyPatternPair[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_112_ID, _seq);
        return _seq;
    }

    // _gather_111: keyword_pattern _loop0_112
    public KeyPatternPair[] _gather_111_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // keyword_pattern _loop0_112
            if (errorIndicator) {
                return null;
            }
            KeyPatternPair elem;
            KeyPatternPair[] seq;
            if (
                (elem = (KeyPatternPair)keyword_pattern_rule()) != null  // keyword_pattern
                &&
                (seq = (KeyPatternPair[])_loop0_112_rule()) != null  // _loop0_112
            )
            {
                _res = insertInFront(elem, seq, KeyPatternPair.class);
                return (KeyPatternPair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeyPatternPair[])_res;
    }

    // _tmp_113: type_params
    public TypeParamTy[] _tmp_113_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // type_params
            if (errorIndicator) {
                return null;
            }
            TypeParamTy[] type_params_var;
            if (
                (type_params_var = (TypeParamTy[])type_params_rule()) != null  // type_params
            )
            {
                _res = type_params_var;
                return (TypeParamTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (TypeParamTy[])_res;
    }

    // _loop0_115: ',' type_param
    public TypeParamTy[] _loop0_115_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<TypeParamTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' type_param
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            TypeParamTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (TypeParamTy)type_param_rule()) != null  // type_param
            )
            {
                _res = elem;
                if (_res instanceof TypeParamTy) {
                    _children.add((TypeParamTy)_res);
                } else {
                    _children.addAll(Arrays.asList((TypeParamTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        TypeParamTy[] _seq = _children.toArray(new TypeParamTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_115_ID, _seq);
        return _seq;
    }

    // _gather_114: type_param _loop0_115
    public TypeParamTy[] _gather_114_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // type_param _loop0_115
            if (errorIndicator) {
                return null;
            }
            TypeParamTy elem;
            TypeParamTy[] seq;
            if (
                (elem = (TypeParamTy)type_param_rule()) != null  // type_param
                &&
                (seq = (TypeParamTy[])_loop0_115_rule()) != null  // _loop0_115
            )
            {
                _res = insertInFront(elem, seq, TypeParamTy.class);
                return (TypeParamTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (TypeParamTy[])_res;
    }

    // _tmp_116: ','
    public Token _tmp_116_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_117: type_param_bound
    public ExprTy _tmp_117_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // type_param_bound
            if (errorIndicator) {
                return null;
            }
            ExprTy type_param_bound_var;
            if (
                (type_param_bound_var = (ExprTy)type_param_bound_rule()) != null  // type_param_bound
            )
            {
                _res = type_param_bound_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _loop1_118: (',' expression)
    public ExprTy[] _loop1_118_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (',' expression)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_334_var;
            while (
                (_tmp_334_var = (ExprTy)_tmp_334_rule()) != null  // ',' expression
            )
            {
                _res = _tmp_334_var;
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
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_120: star_expressions
    public ExprTy _tmp_120_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _loop1_121: (',' star_expression)
    public ExprTy[] _loop1_121_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (',' star_expression)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_335_var;
            while (
                (_tmp_335_var = (ExprTy)_tmp_335_rule()) != null  // ',' star_expression
            )
            {
                _res = _tmp_335_var;
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
        cache.putResult(_start_mark, _LOOP1_121_ID, _seq);
        return _seq;
    }

    // _tmp_122: ','
    public Token _tmp_122_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_124: ',' star_named_expression
    public ExprTy[] _loop0_124_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' star_named_expression
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_124_ID, _seq);
        return _seq;
    }

    // _gather_123: star_named_expression _loop0_124
    public ExprTy[] _gather_123_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_named_expression _loop0_124
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)star_named_expression_rule()) != null  // star_named_expression
                &&
                (seq = (ExprTy[])_loop0_124_rule()) != null  // _loop0_124
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_125: ','
    public Token _tmp_125_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop1_126: ('or' conjunction)
    public ExprTy[] _loop1_126_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('or' conjunction)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_336_var;
            while (
                (_tmp_336_var = (ExprTy)_tmp_336_rule()) != null  // 'or' conjunction
            )
            {
                _res = _tmp_336_var;
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
        cache.putResult(_start_mark, _LOOP1_126_ID, _seq);
        return _seq;
    }

    // _loop1_127: ('and' inversion)
    public ExprTy[] _loop1_127_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('and' inversion)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_337_var;
            while (
                (_tmp_337_var = (ExprTy)_tmp_337_rule()) != null  // 'and' inversion
            )
            {
                _res = _tmp_337_var;
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
        cache.putResult(_start_mark, _LOOP1_127_ID, _seq);
        return _seq;
    }

    // _loop1_128: compare_op_bitwise_or_pair
    public CmpopExprPair[] _loop1_128_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<CmpopExprPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // compare_op_bitwise_or_pair
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_128_ID, _seq);
        return _seq;
    }

    // _tmp_129: '!='
    public Token _tmp_129_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '!='
            if (errorIndicator) {
                return null;
            }
            Token tok;
            if (
                (tok = (Token)expect(28)) != null  // token='!='
            )
            {
                _res = this.checkBarryAsFlufl(tok)? null : tok;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_130: arguments
    public ExprTy _tmp_130_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // arguments
            if (errorIndicator) {
                return null;
            }
            ExprTy arguments_var;
            if (
                (arguments_var = (ExprTy)arguments_rule()) != null  // arguments
            )
            {
                _res = arguments_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _loop0_132: ',' (slice | starred_expression)
    public ExprTy[] _loop0_132_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' (slice | starred_expression)
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)_tmp_338_rule()) != null  // slice | starred_expression
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
        cache.putResult(_start_mark, _LOOP0_132_ID, _seq);
        return _seq;
    }

    // _gather_131: (slice | starred_expression) _loop0_132
    public ExprTy[] _gather_131_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // (slice | starred_expression) _loop0_132
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)_tmp_338_rule()) != null  // slice | starred_expression
                &&
                (seq = (ExprTy[])_loop0_132_rule()) != null  // _loop0_132
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_133: ','
    public Token _tmp_133_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_134: expression
    public ExprTy _tmp_134_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression
            if (errorIndicator) {
                return null;
            }
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_135: expression
    public ExprTy _tmp_135_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression
            if (errorIndicator) {
                return null;
            }
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_136: ':' expression?
    public ExprTy _tmp_136_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ':' expression?
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy d;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
                &&
                ((d = (ExprTy)_tmp_339_rule()) != null || true)  // expression?
            )
            {
                _res = d;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_137: STRING | FSTRING_START
    public Token _tmp_137_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // STRING
            if (errorIndicator) {
                return null;
            }
            Token string_var;
            if (
                (string_var = (Token)string_token()) != null  // STRING
            )
            {
                _res = string_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // FSTRING_START
            if (errorIndicator) {
                return null;
            }
            Token fstring_start_var;
            if (
                (fstring_start_var = (Token)expect(Token.Kind.FSTRING_START)) != null  // token='FSTRING_START'
            )
            {
                _res = fstring_start_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_138: tuple | group | genexp
    public ExprTy _tmp_138_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // tuple
            if (errorIndicator) {
                return null;
            }
            ExprTy tuple_var;
            if (
                (tuple_var = (ExprTy)tuple_rule()) != null  // tuple
            )
            {
                _res = tuple_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // group
            if (errorIndicator) {
                return null;
            }
            ExprTy group_var;
            if (
                (group_var = (ExprTy)group_rule()) != null  // group
            )
            {
                _res = group_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // genexp
            if (errorIndicator) {
                return null;
            }
            ExprTy genexp_var;
            if (
                (genexp_var = (ExprTy)genexp_rule()) != null  // genexp
            )
            {
                _res = genexp_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_139: list | listcomp
    public ExprTy _tmp_139_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // list
            if (errorIndicator) {
                return null;
            }
            ExprTy list_var;
            if (
                (list_var = (ExprTy)list_rule()) != null  // list
            )
            {
                _res = list_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // listcomp
            if (errorIndicator) {
                return null;
            }
            ExprTy listcomp_var;
            if (
                (listcomp_var = (ExprTy)listcomp_rule()) != null  // listcomp
            )
            {
                _res = listcomp_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_140: dict | set | dictcomp | setcomp
    public ExprTy _tmp_140_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // dict
            if (errorIndicator) {
                return null;
            }
            ExprTy dict_var;
            if (
                (dict_var = (ExprTy)dict_rule()) != null  // dict
            )
            {
                _res = dict_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // set
            if (errorIndicator) {
                return null;
            }
            ExprTy set_var;
            if (
                (set_var = (ExprTy)set_rule()) != null  // set
            )
            {
                _res = set_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // dictcomp
            if (errorIndicator) {
                return null;
            }
            ExprTy dictcomp_var;
            if (
                (dictcomp_var = (ExprTy)dictcomp_rule()) != null  // dictcomp
            )
            {
                _res = dictcomp_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // setcomp
            if (errorIndicator) {
                return null;
            }
            ExprTy setcomp_var;
            if (
                (setcomp_var = (ExprTy)setcomp_rule()) != null  // setcomp
            )
            {
                _res = setcomp_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_141: yield_expr | named_expression
    public ExprTy _tmp_141_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // named_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy named_expression_var;
            if (
                (named_expression_var = (ExprTy)named_expression_rule()) != null  // named_expression
            )
            {
                _res = named_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_142: lambda_params
    public ArgumentsTy _tmp_142_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_params
            if (errorIndicator) {
                return null;
            }
            ArgumentsTy lambda_params_var;
            if (
                (lambda_params_var = (ArgumentsTy)lambda_params_rule()) != null  // lambda_params
            )
            {
                _res = lambda_params_var;
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgumentsTy)_res;
    }

    // _loop0_143: lambda_param_no_default
    public ArgTy[] _loop0_143_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_143_ID, _seq);
        return _seq;
    }

    // _loop0_144: lambda_param_with_default
    public NameDefaultPair[] _loop0_144_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_144_ID, _seq);
        return _seq;
    }

    // _tmp_145: lambda_star_etc
    public StarEtc _tmp_145_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_star_etc
            if (errorIndicator) {
                return null;
            }
            StarEtc lambda_star_etc_var;
            if (
                (lambda_star_etc_var = (StarEtc)lambda_star_etc_rule()) != null  // lambda_star_etc
            )
            {
                _res = lambda_star_etc_var;
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StarEtc)_res;
    }

    // _loop0_146: lambda_param_with_default
    public NameDefaultPair[] _loop0_146_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_146_ID, _seq);
        return _seq;
    }

    // _tmp_147: lambda_star_etc
    public StarEtc _tmp_147_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_star_etc
            if (errorIndicator) {
                return null;
            }
            StarEtc lambda_star_etc_var;
            if (
                (lambda_star_etc_var = (StarEtc)lambda_star_etc_rule()) != null  // lambda_star_etc
            )
            {
                _res = lambda_star_etc_var;
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StarEtc)_res;
    }

    // _loop1_148: lambda_param_no_default
    public ArgTy[] _loop1_148_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_148_ID, _seq);
        return _seq;
    }

    // _loop0_149: lambda_param_with_default
    public NameDefaultPair[] _loop0_149_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_149_ID, _seq);
        return _seq;
    }

    // _tmp_150: lambda_star_etc
    public StarEtc _tmp_150_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_star_etc
            if (errorIndicator) {
                return null;
            }
            StarEtc lambda_star_etc_var;
            if (
                (lambda_star_etc_var = (StarEtc)lambda_star_etc_rule()) != null  // lambda_star_etc
            )
            {
                _res = lambda_star_etc_var;
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StarEtc)_res;
    }

    // _loop1_151: lambda_param_with_default
    public NameDefaultPair[] _loop1_151_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_151_ID, _seq);
        return _seq;
    }

    // _tmp_152: lambda_star_etc
    public StarEtc _tmp_152_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_star_etc
            if (errorIndicator) {
                return null;
            }
            StarEtc lambda_star_etc_var;
            if (
                (lambda_star_etc_var = (StarEtc)lambda_star_etc_rule()) != null  // lambda_star_etc
            )
            {
                _res = lambda_star_etc_var;
                return (StarEtc)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (StarEtc)_res;
    }

    // _loop1_153: lambda_param_no_default
    public ArgTy[] _loop1_153_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_153_ID, _seq);
        return _seq;
    }

    // _loop1_154: lambda_param_no_default
    public ArgTy[] _loop1_154_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_154_ID, _seq);
        return _seq;
    }

    // _loop0_155: lambda_param_no_default
    public ArgTy[] _loop0_155_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_155_ID, _seq);
        return _seq;
    }

    // _loop1_156: lambda_param_with_default
    public NameDefaultPair[] _loop1_156_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_156_ID, _seq);
        return _seq;
    }

    // _loop0_157: lambda_param_no_default
    public ArgTy[] _loop0_157_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_157_ID, _seq);
        return _seq;
    }

    // _loop1_158: lambda_param_with_default
    public NameDefaultPair[] _loop1_158_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_158_ID, _seq);
        return _seq;
    }

    // _loop0_159: lambda_param_maybe_default
    public NameDefaultPair[] _loop0_159_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_159_ID, _seq);
        return _seq;
    }

    // _tmp_160: lambda_kwds
    public ArgTy _tmp_160_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_kwds
            if (errorIndicator) {
                return null;
            }
            ArgTy lambda_kwds_var;
            if (
                (lambda_kwds_var = (ArgTy)lambda_kwds_rule()) != null  // lambda_kwds
            )
            {
                _res = lambda_kwds_var;
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // _loop1_161: lambda_param_maybe_default
    public NameDefaultPair[] _loop1_161_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_161_ID, _seq);
        return _seq;
    }

    // _tmp_162: lambda_kwds
    public ArgTy _tmp_162_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_kwds
            if (errorIndicator) {
                return null;
            }
            ArgTy lambda_kwds_var;
            if (
                (lambda_kwds_var = (ArgTy)lambda_kwds_rule()) != null  // lambda_kwds
            )
            {
                _res = lambda_kwds_var;
                return (ArgTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy)_res;
    }

    // _tmp_163: yield_expr | star_expressions
    public ExprTy _tmp_163_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_164: fstring_conversion
    public ResultTokenWithMetadata _tmp_164_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // fstring_conversion
            if (errorIndicator) {
                return null;
            }
            ResultTokenWithMetadata fstring_conversion_var;
            if (
                (fstring_conversion_var = (ResultTokenWithMetadata)fstring_conversion_rule()) != null  // fstring_conversion
            )
            {
                _res = fstring_conversion_var;
                return (ResultTokenWithMetadata)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ResultTokenWithMetadata)_res;
    }

    // _tmp_165: fstring_full_format_spec
    public ResultTokenWithMetadata _tmp_165_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // fstring_full_format_spec
            if (errorIndicator) {
                return null;
            }
            ResultTokenWithMetadata fstring_full_format_spec_var;
            if (
                (fstring_full_format_spec_var = (ResultTokenWithMetadata)fstring_full_format_spec_rule()) != null  // fstring_full_format_spec
            )
            {
                _res = fstring_full_format_spec_var;
                return (ResultTokenWithMetadata)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ResultTokenWithMetadata)_res;
    }

    // _loop0_166: fstring_format_spec
    public ExprTy[] _loop0_166_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // fstring_format_spec
            if (errorIndicator) {
                return null;
            }
            ExprTy fstring_format_spec_var;
            while (
                (fstring_format_spec_var = (ExprTy)fstring_format_spec_rule()) != null  // fstring_format_spec
            )
            {
                _res = fstring_format_spec_var;
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
        cache.putResult(_start_mark, _LOOP0_166_ID, _seq);
        return _seq;
    }

    // _loop0_167: fstring_middle
    public ExprTy[] _loop0_167_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // fstring_middle
            if (errorIndicator) {
                return null;
            }
            ExprTy fstring_middle_var;
            while (
                (fstring_middle_var = (ExprTy)fstring_middle_rule()) != null  // fstring_middle
            )
            {
                _res = fstring_middle_var;
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
        cache.putResult(_start_mark, _LOOP0_167_ID, _seq);
        return _seq;
    }

    // _loop1_168: (fstring | string)
    public ExprTy[] _loop1_168_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (fstring | string)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_340_var;
            while (
                (_tmp_340_var = (ExprTy)_tmp_340_rule()) != null  // fstring | string
            )
            {
                _res = _tmp_340_var;
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
        cache.putResult(_start_mark, _LOOP1_168_ID, _seq);
        return _seq;
    }

    // _tmp_169: star_named_expressions
    public ExprTy[] _tmp_169_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_named_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy[] star_named_expressions_var;
            if (
                (star_named_expressions_var = (ExprTy[])star_named_expressions_rule()) != null  // star_named_expressions
            )
            {
                _res = star_named_expressions_var;
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_170: star_named_expression ',' star_named_expressions?
    public ExprTy[] _tmp_170_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_named_expression ',' star_named_expressions?
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy y;
            ExprTy[] z;
            if (
                (y = (ExprTy)star_named_expression_rule()) != null  // star_named_expression
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                ((z = (ExprTy[])_tmp_341_rule()) != null || true)  // star_named_expressions?
            )
            {
                _res = this.insertInFront(y,z);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_171: double_starred_kvpairs
    public KeyValuePair[] _tmp_171_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // double_starred_kvpairs
            if (errorIndicator) {
                return null;
            }
            KeyValuePair[] double_starred_kvpairs_var;
            if (
                (double_starred_kvpairs_var = (KeyValuePair[])double_starred_kvpairs_rule()) != null  // double_starred_kvpairs
            )
            {
                _res = double_starred_kvpairs_var;
                return (KeyValuePair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeyValuePair[])_res;
    }

    // _loop0_173: ',' double_starred_kvpair
    public KeyValuePair[] _loop0_173_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<KeyValuePair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' double_starred_kvpair
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_173_ID, _seq);
        return _seq;
    }

    // _gather_172: double_starred_kvpair _loop0_173
    public KeyValuePair[] _gather_172_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // double_starred_kvpair _loop0_173
            if (errorIndicator) {
                return null;
            }
            KeyValuePair elem;
            KeyValuePair[] seq;
            if (
                (elem = (KeyValuePair)double_starred_kvpair_rule()) != null  // double_starred_kvpair
                &&
                (seq = (KeyValuePair[])_loop0_173_rule()) != null  // _loop0_173
            )
            {
                _res = insertInFront(elem, seq, KeyValuePair.class);
                return (KeyValuePair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeyValuePair[])_res;
    }

    // _tmp_174: ','
    public Token _tmp_174_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop1_175: for_if_clause
    public ComprehensionTy[] _loop1_175_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ComprehensionTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // for_if_clause
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_175_ID, _seq);
        return _seq;
    }

    // _loop0_176: ('if' disjunction)
    public ExprTy[] _loop0_176_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('if' disjunction)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_342_var;
            while (
                (_tmp_342_var = (ExprTy)_tmp_342_rule()) != null  // 'if' disjunction
            )
            {
                _res = _tmp_342_var;
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
        cache.putResult(_start_mark, _LOOP0_176_ID, _seq);
        return _seq;
    }

    // _loop0_177: ('if' disjunction)
    public ExprTy[] _loop0_177_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ('if' disjunction)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_343_var;
            while (
                (_tmp_343_var = (ExprTy)_tmp_343_rule()) != null  // 'if' disjunction
            )
            {
                _res = _tmp_343_var;
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
        cache.putResult(_start_mark, _LOOP0_177_ID, _seq);
        return _seq;
    }

    // _tmp_178: assignment_expression | expression !':='
    public ExprTy _tmp_178_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // assignment_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy assignment_expression_var;
            if (
                (assignment_expression_var = (ExprTy)assignment_expression_rule()) != null  // assignment_expression
            )
            {
                _res = assignment_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression !':='
            if (errorIndicator) {
                return null;
            }
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                genLookahead_expect(false, 53)  // token=':='
            )
            {
                _res = expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_179: ','
    public Token _tmp_179_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_181: ',' (starred_expression | (assignment_expression | expression !':=') !'=')
    public ExprTy[] _loop0_181_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' (starred_expression | (assignment_expression | expression !':=') !'=')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)_tmp_344_rule()) != null  // starred_expression | (assignment_expression | expression !':=') !'='
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
        cache.putResult(_start_mark, _LOOP0_181_ID, _seq);
        return _seq;
    }

    // _gather_180:
    //     | (starred_expression | (assignment_expression | expression !':=') !'=') _loop0_181
    public ExprTy[] _gather_180_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // (starred_expression | (assignment_expression | expression !':=') !'=') _loop0_181
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)_tmp_344_rule()) != null  // starred_expression | (assignment_expression | expression !':=') !'='
                &&
                (seq = (ExprTy[])_loop0_181_rule()) != null  // _loop0_181
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_182: ',' kwargs
    public KeywordOrStarred[] _tmp_182_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ',' kwargs
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            KeywordOrStarred[] k;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (k = (KeywordOrStarred[])kwargs_rule()) != null  // kwargs
            )
            {
                _res = k;
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeywordOrStarred[])_res;
    }

    // _loop0_184: ',' kwarg_or_starred
    public KeywordOrStarred[] _loop0_184_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<KeywordOrStarred> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' kwarg_or_starred
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_184_ID, _seq);
        return _seq;
    }

    // _gather_183: kwarg_or_starred _loop0_184
    public KeywordOrStarred[] _gather_183_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // kwarg_or_starred _loop0_184
            if (errorIndicator) {
                return null;
            }
            KeywordOrStarred elem;
            KeywordOrStarred[] seq;
            if (
                (elem = (KeywordOrStarred)kwarg_or_starred_rule()) != null  // kwarg_or_starred
                &&
                (seq = (KeywordOrStarred[])_loop0_184_rule()) != null  // _loop0_184
            )
            {
                _res = insertInFront(elem, seq, KeywordOrStarred.class);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeywordOrStarred[])_res;
    }

    // _loop0_186: ',' kwarg_or_double_starred
    public KeywordOrStarred[] _loop0_186_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<KeywordOrStarred> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' kwarg_or_double_starred
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_186_ID, _seq);
        return _seq;
    }

    // _gather_185: kwarg_or_double_starred _loop0_186
    public KeywordOrStarred[] _gather_185_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // kwarg_or_double_starred _loop0_186
            if (errorIndicator) {
                return null;
            }
            KeywordOrStarred elem;
            KeywordOrStarred[] seq;
            if (
                (elem = (KeywordOrStarred)kwarg_or_double_starred_rule()) != null  // kwarg_or_double_starred
                &&
                (seq = (KeywordOrStarred[])_loop0_186_rule()) != null  // _loop0_186
            )
            {
                _res = insertInFront(elem, seq, KeywordOrStarred.class);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeywordOrStarred[])_res;
    }

    // _loop0_188: ',' kwarg_or_starred
    public KeywordOrStarred[] _loop0_188_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<KeywordOrStarred> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' kwarg_or_starred
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_188_ID, _seq);
        return _seq;
    }

    // _gather_187: kwarg_or_starred _loop0_188
    public KeywordOrStarred[] _gather_187_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // kwarg_or_starred _loop0_188
            if (errorIndicator) {
                return null;
            }
            KeywordOrStarred elem;
            KeywordOrStarred[] seq;
            if (
                (elem = (KeywordOrStarred)kwarg_or_starred_rule()) != null  // kwarg_or_starred
                &&
                (seq = (KeywordOrStarred[])_loop0_188_rule()) != null  // _loop0_188
            )
            {
                _res = insertInFront(elem, seq, KeywordOrStarred.class);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeywordOrStarred[])_res;
    }

    // _loop0_190: ',' kwarg_or_double_starred
    public KeywordOrStarred[] _loop0_190_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<KeywordOrStarred> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' kwarg_or_double_starred
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_190_ID, _seq);
        return _seq;
    }

    // _gather_189: kwarg_or_double_starred _loop0_190
    public KeywordOrStarred[] _gather_189_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // kwarg_or_double_starred _loop0_190
            if (errorIndicator) {
                return null;
            }
            KeywordOrStarred elem;
            KeywordOrStarred[] seq;
            if (
                (elem = (KeywordOrStarred)kwarg_or_double_starred_rule()) != null  // kwarg_or_double_starred
                &&
                (seq = (KeywordOrStarred[])_loop0_190_rule()) != null  // _loop0_190
            )
            {
                _res = insertInFront(elem, seq, KeywordOrStarred.class);
                return (KeywordOrStarred[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeywordOrStarred[])_res;
    }

    // _loop0_191: (',' star_target)
    public ExprTy[] _loop0_191_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (',' star_target)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_345_var;
            while (
                (_tmp_345_var = (ExprTy)_tmp_345_rule()) != null  // ',' star_target
            )
            {
                _res = _tmp_345_var;
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
        cache.putResult(_start_mark, _LOOP0_191_ID, _seq);
        return _seq;
    }

    // _tmp_192: ','
    public Token _tmp_192_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_194: ',' star_target
    public ExprTy[] _loop0_194_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' star_target
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_194_ID, _seq);
        return _seq;
    }

    // _gather_193: star_target _loop0_194
    public ExprTy[] _gather_193_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_target _loop0_194
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)star_target_rule()) != null  // star_target
                &&
                (seq = (ExprTy[])_loop0_194_rule()) != null  // _loop0_194
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_195: ','
    public Token _tmp_195_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop1_196: (',' star_target)
    public ExprTy[] _loop1_196_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (',' star_target)
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_346_var;
            while (
                (_tmp_346_var = (ExprTy)_tmp_346_rule()) != null  // ',' star_target
            )
            {
                _res = _tmp_346_var;
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
        cache.putResult(_start_mark, _LOOP1_196_ID, _seq);
        return _seq;
    }

    // _tmp_197: ','
    public Token _tmp_197_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_198: !'*' star_target
    public ExprTy _tmp_198_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // !'*' star_target
            if (errorIndicator) {
                return null;
            }
            ExprTy star_target_var;
            if (
                genLookahead_expect(false, 16)  // token='*'
                &&
                (star_target_var = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = star_target_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_199: star_targets_tuple_seq
    public ExprTy[] _tmp_199_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_targets_tuple_seq
            if (errorIndicator) {
                return null;
            }
            ExprTy[] star_targets_tuple_seq_var;
            if (
                (star_targets_tuple_seq_var = (ExprTy[])star_targets_tuple_seq_rule()) != null  // star_targets_tuple_seq
            )
            {
                _res = star_targets_tuple_seq_var;
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_200: star_targets_list_seq
    public ExprTy[] _tmp_200_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_targets_list_seq
            if (errorIndicator) {
                return null;
            }
            ExprTy[] star_targets_list_seq_var;
            if (
                (star_targets_list_seq_var = (ExprTy[])star_targets_list_seq_rule()) != null  // star_targets_list_seq
            )
            {
                _res = star_targets_list_seq_var;
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_201: arguments
    public ExprTy _tmp_201_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // arguments
            if (errorIndicator) {
                return null;
            }
            ExprTy arguments_var;
            if (
                (arguments_var = (ExprTy)arguments_rule()) != null  // arguments
            )
            {
                _res = arguments_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _loop0_203: ',' del_target
    public ExprTy[] _loop0_203_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' del_target
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_203_ID, _seq);
        return _seq;
    }

    // _gather_202: del_target _loop0_203
    public ExprTy[] _gather_202_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // del_target _loop0_203
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)del_target_rule()) != null  // del_target
                &&
                (seq = (ExprTy[])_loop0_203_rule()) != null  // _loop0_203
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_204: ','
    public Token _tmp_204_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_205: del_targets
    public ExprTy[] _tmp_205_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // del_targets
            if (errorIndicator) {
                return null;
            }
            ExprTy[] del_targets_var;
            if (
                (del_targets_var = (ExprTy[])del_targets_rule()) != null  // del_targets
            )
            {
                _res = del_targets_var;
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_206: del_targets
    public ExprTy[] _tmp_206_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // del_targets
            if (errorIndicator) {
                return null;
            }
            ExprTy[] del_targets_var;
            if (
                (del_targets_var = (ExprTy[])del_targets_rule()) != null  // del_targets
            )
            {
                _res = del_targets_var;
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _loop0_208: ',' expression
    public ExprTy[] _loop0_208_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' expression
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_208_ID, _seq);
        return _seq;
    }

    // _gather_207: expression _loop0_208
    public ExprTy[] _gather_207_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression _loop0_208
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)expression_rule()) != null  // expression
                &&
                (seq = (ExprTy[])_loop0_208_rule()) != null  // _loop0_208
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _loop0_210: ',' expression
    public ExprTy[] _loop0_210_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' expression
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_210_ID, _seq);
        return _seq;
    }

    // _gather_209: expression _loop0_210
    public ExprTy[] _gather_209_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression _loop0_210
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)expression_rule()) != null  // expression
                &&
                (seq = (ExprTy[])_loop0_210_rule()) != null  // _loop0_210
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _loop0_212: ',' expression
    public ExprTy[] _loop0_212_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' expression
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_212_ID, _seq);
        return _seq;
    }

    // _gather_211: expression _loop0_212
    public ExprTy[] _gather_211_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression _loop0_212
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)expression_rule()) != null  // expression
                &&
                (seq = (ExprTy[])_loop0_212_rule()) != null  // _loop0_212
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _loop0_214: ',' expression
    public ExprTy[] _loop0_214_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' expression
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_214_ID, _seq);
        return _seq;
    }

    // _gather_213: expression _loop0_214
    public ExprTy[] _gather_213_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression _loop0_214
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)expression_rule()) != null  // expression
                &&
                (seq = (ExprTy[])_loop0_214_rule()) != null  // _loop0_214
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_215: NEWLINE INDENT
    public Object _tmp_215_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // NEWLINE INDENT
            if (errorIndicator) {
                return null;
            }
            Token indent_var;
            Token newline_var;
            if (
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
                &&
                (indent_var = (Token)expect(Token.Kind.INDENT)) != null  // token='INDENT'
            )
            {
                _res = dummyName(newline_var, indent_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_216:
    //     | (','.(starred_expression | (assignment_expression | expression !':=') !'=')+ ',' kwargs)
    //     | kwargs
    public Object _tmp_216_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // (','.(starred_expression | (assignment_expression | expression !':=') !'=')+ ',' kwargs)
            if (errorIndicator) {
                return null;
            }
            Object _tmp_347_var;
            if (
                (_tmp_347_var = (Object)_tmp_347_rule()) != null  // ','.(starred_expression | (assignment_expression | expression !':=') !'=')+ ',' kwargs
            )
            {
                _res = _tmp_347_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // kwargs
            if (errorIndicator) {
                return null;
            }
            KeywordOrStarred[] kwargs_var;
            if (
                (kwargs_var = (KeywordOrStarred[])kwargs_rule()) != null  // kwargs
            )
            {
                _res = kwargs_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _loop0_218: ',' (starred_expression !'=')
    public ExprTy[] _loop0_218_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' (starred_expression !'=')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)_tmp_348_rule()) != null  // starred_expression !'='
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
        cache.putResult(_start_mark, _LOOP0_218_ID, _seq);
        return _seq;
    }

    // _gather_217: (starred_expression !'=') _loop0_218
    public ExprTy[] _gather_217_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // (starred_expression !'=') _loop0_218
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)_tmp_348_rule()) != null  // starred_expression !'='
                &&
                (seq = (ExprTy[])_loop0_218_rule()) != null  // _loop0_218
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_219: args | expression for_if_clauses
    public Object _tmp_219_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // args
            if (errorIndicator) {
                return null;
            }
            ExprTy args_var;
            if (
                (args_var = (ExprTy)args_rule()) != null  // args
            )
            {
                _res = args_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // expression for_if_clauses
            if (errorIndicator) {
                return null;
            }
            ExprTy expression_var;
            ComprehensionTy[] for_if_clauses_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                (for_if_clauses_var = (ComprehensionTy[])for_if_clauses_rule()) != null  // for_if_clauses
            )
            {
                _res = dummyName(expression_var, for_if_clauses_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_220: args ','
    public Object _tmp_220_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // args ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy args_var;
            if (
                (args_var = (ExprTy)args_rule()) != null  // args
                &&
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = dummyName(args_var, _literal);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_221: ',' | ')'
    public Token _tmp_221_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_222: 'True' | 'False' | 'None'
    public Token _tmp_222_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'True'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(681)) != null  // token='True'
            )
            {
                _res = _keyword;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // 'False'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(682)) != null  // token='False'
            )
            {
                _res = _keyword;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // 'None'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(680)) != null  // token='None'
            )
            {
                _res = _keyword;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_223: NAME '='
    public Object _tmp_223_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // NAME '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy name_var;
            if (
                (name_var = (ExprTy)name_token()) != null  // NAME
                &&
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = dummyName(name_var, _literal);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_224: NAME STRING | SOFT_KEYWORD
    public Object _tmp_224_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // NAME STRING
            if (errorIndicator) {
                return null;
            }
            ExprTy name_var;
            Token string_var;
            if (
                (name_var = (ExprTy)name_token()) != null  // NAME
                &&
                (string_var = (Token)string_token()) != null  // STRING
            )
            {
                _res = dummyName(name_var, string_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        { // SOFT_KEYWORD
            if (errorIndicator) {
                return null;
            }
            ExprTy soft_keyword_var;
            if (
                (soft_keyword_var = (ExprTy)soft_keyword_token()) != null  // SOFT_KEYWORD
            )
            {
                _res = soft_keyword_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_225: 'else' | ':'
    public Token _tmp_225_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'else'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(676)) != null  // token='else'
            )
            {
                _res = _keyword;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_226: lambda_params
    public ArgumentsTy _tmp_226_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_params
            if (errorIndicator) {
                return null;
            }
            ArgumentsTy lambda_params_var;
            if (
                (lambda_params_var = (ArgumentsTy)lambda_params_rule()) != null  // lambda_params
            )
            {
                _res = lambda_params_var;
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgumentsTy)_res;
    }

    // _tmp_227: '=' | ':='
    public Token _tmp_227_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ':='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(53)) != null  // token=':='
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_228: list | tuple | genexp | 'True' | 'None' | 'False'
    public Object _tmp_228_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // list
            if (errorIndicator) {
                return null;
            }
            ExprTy list_var;
            if (
                (list_var = (ExprTy)list_rule()) != null  // list
            )
            {
                _res = list_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // tuple
            if (errorIndicator) {
                return null;
            }
            ExprTy tuple_var;
            if (
                (tuple_var = (ExprTy)tuple_rule()) != null  // tuple
            )
            {
                _res = tuple_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // genexp
            if (errorIndicator) {
                return null;
            }
            ExprTy genexp_var;
            if (
                (genexp_var = (ExprTy)genexp_rule()) != null  // genexp
            )
            {
                _res = genexp_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'True'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(681)) != null  // token='True'
            )
            {
                _res = _keyword;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'None'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(680)) != null  // token='None'
            )
            {
                _res = _keyword;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // 'False'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(682)) != null  // token='False'
            )
            {
                _res = _keyword;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_229: '=' | ':='
    public Token _tmp_229_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ':='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(53)) != null  // token=':='
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_230: star_named_expressions
    public ExprTy[] _loop0_230_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // star_named_expressions
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_230_ID, _seq);
        return _seq;
    }

    // _loop0_231: (star_targets '=')
    public Object[] _loop0_231_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<Object> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (star_targets '=')
            if (errorIndicator) {
                return null;
            }
            Object _tmp_349_var;
            while (
                (_tmp_349_var = (Object)_tmp_349_rule()) != null  // star_targets '='
            )
            {
                _res = _tmp_349_var;
                if (_res instanceof Object) {
                    _children.add((Object)_res);
                } else {
                    _children.addAll(Arrays.asList((Object[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        Object[] _seq = _children.toArray(new Object[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_231_ID, _seq);
        return _seq;
    }

    // _loop0_232: (star_targets '=')
    public Object[] _loop0_232_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<Object> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // (star_targets '=')
            if (errorIndicator) {
                return null;
            }
            Object _tmp_350_var;
            while (
                (_tmp_350_var = (Object)_tmp_350_rule()) != null  // star_targets '='
            )
            {
                _res = _tmp_350_var;
                if (_res instanceof Object) {
                    _children.add((Object)_res);
                } else {
                    _children.addAll(Arrays.asList((Object[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        Object[] _seq = _children.toArray(new Object[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_232_ID, _seq);
        return _seq;
    }

    // _tmp_233: yield_expr | star_expressions
    public ExprTy _tmp_233_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_234: '[' | '(' | '{'
    public Token _tmp_234_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '['
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(9)) != null  // token='['
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '('
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '{'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_235: '[' | '{'
    public Token _tmp_235_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '['
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(9)) != null  // token='['
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '{'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_236: '[' | '{'
    public Token _tmp_236_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '['
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(9)) != null  // token='['
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '{'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(25)) != null  // token='{'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_237: slash_no_default | slash_with_default
    public Object _tmp_237_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // slash_no_default
            if (errorIndicator) {
                return null;
            }
            ArgTy[] slash_no_default_var;
            if (
                (slash_no_default_var = (ArgTy[])slash_no_default_rule()) != null  // slash_no_default
            )
            {
                _res = slash_no_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // slash_with_default
            if (errorIndicator) {
                return null;
            }
            SlashWithDefault slash_with_default_var;
            if (
                (slash_with_default_var = (SlashWithDefault)slash_with_default_rule()) != null  // slash_with_default
            )
            {
                _res = slash_with_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _loop0_238: param_maybe_default
    public NameDefaultPair[] _loop0_238_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_238_ID, _seq);
        return _seq;
    }

    // _loop0_239: param_no_default
    public ArgTy[] _loop0_239_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_239_ID, _seq);
        return _seq;
    }

    // _loop0_240: param_no_default
    public ArgTy[] _loop0_240_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_240_ID, _seq);
        return _seq;
    }

    // _loop1_241: param_no_default
    public ArgTy[] _loop1_241_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_241_ID, _seq);
        return _seq;
    }

    // _tmp_242: slash_no_default | slash_with_default
    public Object _tmp_242_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // slash_no_default
            if (errorIndicator) {
                return null;
            }
            ArgTy[] slash_no_default_var;
            if (
                (slash_no_default_var = (ArgTy[])slash_no_default_rule()) != null  // slash_no_default
            )
            {
                _res = slash_no_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // slash_with_default
            if (errorIndicator) {
                return null;
            }
            SlashWithDefault slash_with_default_var;
            if (
                (slash_with_default_var = (SlashWithDefault)slash_with_default_rule()) != null  // slash_with_default
            )
            {
                _res = slash_with_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _loop0_243: param_maybe_default
    public NameDefaultPair[] _loop0_243_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_243_ID, _seq);
        return _seq;
    }

    // _tmp_244: ',' | param_no_default
    public Object _tmp_244_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // param_no_default
            if (errorIndicator) {
                return null;
            }
            ArgTy param_no_default_var;
            if (
                (param_no_default_var = (ArgTy)param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = param_no_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _loop0_245: param_maybe_default
    public NameDefaultPair[] _loop0_245_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_245_ID, _seq);
        return _seq;
    }

    // _loop1_246: param_maybe_default
    public NameDefaultPair[] _loop1_246_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_246_ID, _seq);
        return _seq;
    }

    // _tmp_247: ')' | ','
    public Token _tmp_247_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_248: ')' | ',' (')' | '**')
    public Object _tmp_248_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = _literal;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ',' (')' | '**')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _tmp_351_var;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (_tmp_351_var = (Token)_tmp_351_rule()) != null  // ')' | '**'
            )
            {
                _res = dummyName(_literal, _tmp_351_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_249: param_no_default | ','
    public Object _tmp_249_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // param_no_default
            if (errorIndicator) {
                return null;
            }
            ArgTy param_no_default_var;
            if (
                (param_no_default_var = (ArgTy)param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = param_no_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _loop0_250: param_maybe_default
    public NameDefaultPair[] _loop0_250_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_250_ID, _seq);
        return _seq;
    }

    // _tmp_251: param_no_default | ','
    public Object _tmp_251_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // param_no_default
            if (errorIndicator) {
                return null;
            }
            ArgTy param_no_default_var;
            if (
                (param_no_default_var = (ArgTy)param_no_default_rule()) != null  // param_no_default
            )
            {
                _res = param_no_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_252: '*' | '**' | '/'
    public Token _tmp_252_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '*'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(16)) != null  // token='*'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '**'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(35)) != null  // token='**'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '/'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(17)) != null  // token='/'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop1_253: param_with_default
    public NameDefaultPair[] _loop1_253_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_253_ID, _seq);
        return _seq;
    }

    // _tmp_254: lambda_slash_no_default | lambda_slash_with_default
    public Object _tmp_254_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_slash_no_default
            if (errorIndicator) {
                return null;
            }
            ArgTy[] lambda_slash_no_default_var;
            if (
                (lambda_slash_no_default_var = (ArgTy[])lambda_slash_no_default_rule()) != null  // lambda_slash_no_default
            )
            {
                _res = lambda_slash_no_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // lambda_slash_with_default
            if (errorIndicator) {
                return null;
            }
            SlashWithDefault lambda_slash_with_default_var;
            if (
                (lambda_slash_with_default_var = (SlashWithDefault)lambda_slash_with_default_rule()) != null  // lambda_slash_with_default
            )
            {
                _res = lambda_slash_with_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _loop0_255: lambda_param_maybe_default
    public NameDefaultPair[] _loop0_255_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_255_ID, _seq);
        return _seq;
    }

    // _loop0_256: lambda_param_no_default
    public ArgTy[] _loop0_256_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_256_ID, _seq);
        return _seq;
    }

    // _loop0_257: lambda_param_no_default
    public ArgTy[] _loop0_257_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_257_ID, _seq);
        return _seq;
    }

    // _loop0_259: ',' lambda_param
    public ArgTy[] _loop0_259_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ArgTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' lambda_param
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ArgTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ArgTy)lambda_param_rule()) != null  // lambda_param
            )
            {
                _res = elem;
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
        cache.putResult(_start_mark, _LOOP0_259_ID, _seq);
        return _seq;
    }

    // _gather_258: lambda_param _loop0_259
    public ArgTy[] _gather_258_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_param _loop0_259
            if (errorIndicator) {
                return null;
            }
            ArgTy elem;
            ArgTy[] seq;
            if (
                (elem = (ArgTy)lambda_param_rule()) != null  // lambda_param
                &&
                (seq = (ArgTy[])_loop0_259_rule()) != null  // _loop0_259
            )
            {
                _res = insertInFront(elem, seq, ArgTy.class);
                return (ArgTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgTy[])_res;
    }

    // _tmp_260: lambda_slash_no_default | lambda_slash_with_default
    public Object _tmp_260_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_slash_no_default
            if (errorIndicator) {
                return null;
            }
            ArgTy[] lambda_slash_no_default_var;
            if (
                (lambda_slash_no_default_var = (ArgTy[])lambda_slash_no_default_rule()) != null  // lambda_slash_no_default
            )
            {
                _res = lambda_slash_no_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // lambda_slash_with_default
            if (errorIndicator) {
                return null;
            }
            SlashWithDefault lambda_slash_with_default_var;
            if (
                (lambda_slash_with_default_var = (SlashWithDefault)lambda_slash_with_default_rule()) != null  // lambda_slash_with_default
            )
            {
                _res = lambda_slash_with_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _loop0_261: lambda_param_maybe_default
    public NameDefaultPair[] _loop0_261_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_261_ID, _seq);
        return _seq;
    }

    // _tmp_262: ',' | lambda_param_no_default
    public Object _tmp_262_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
            ArgTy lambda_param_no_default_var;
            if (
                (lambda_param_no_default_var = (ArgTy)lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = lambda_param_no_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _loop0_263: lambda_param_maybe_default
    public NameDefaultPair[] _loop0_263_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_263_ID, _seq);
        return _seq;
    }

    // _loop1_264: lambda_param_maybe_default
    public NameDefaultPair[] _loop1_264_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_264_ID, _seq);
        return _seq;
    }

    // _loop1_265: lambda_param_with_default
    public NameDefaultPair[] _loop1_265_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_with_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP1_265_ID, _seq);
        return _seq;
    }

    // _tmp_266: ':' | ',' (':' | '**')
    public Object _tmp_266_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ',' (':' | '**')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _tmp_352_var;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (_tmp_352_var = (Token)_tmp_352_rule()) != null  // ':' | '**'
            )
            {
                _res = dummyName(_literal, _tmp_352_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_267: lambda_param_no_default | ','
    public Object _tmp_267_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
            ArgTy lambda_param_no_default_var;
            if (
                (lambda_param_no_default_var = (ArgTy)lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = lambda_param_no_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _loop0_268: lambda_param_maybe_default
    public NameDefaultPair[] _loop0_268_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<NameDefaultPair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // lambda_param_maybe_default
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_268_ID, _seq);
        return _seq;
    }

    // _tmp_269: lambda_param_no_default | ','
    public Object _tmp_269_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // lambda_param_no_default
            if (errorIndicator) {
                return null;
            }
            ArgTy lambda_param_no_default_var;
            if (
                (lambda_param_no_default_var = (ArgTy)lambda_param_no_default_rule()) != null  // lambda_param_no_default
            )
            {
                _res = lambda_param_no_default_var;
                return (Object)_res;
            }
            reset(_mark);
        }
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_270: '*' | '**' | '/'
    public Token _tmp_270_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '*'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(16)) != null  // token='*'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '**'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(35)) != null  // token='**'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '/'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(17)) != null  // token='/'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_271: ',' | ')' | ':'
    public Token _tmp_271_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_273: ',' dotted_name
    public ExprTy[] _loop0_273_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' dotted_name
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)dotted_name_rule()) != null  // dotted_name
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
        cache.putResult(_start_mark, _LOOP0_273_ID, _seq);
        return _seq;
    }

    // _gather_272: dotted_name _loop0_273
    public ExprTy[] _gather_272_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // dotted_name _loop0_273
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)dotted_name_rule()) != null  // dotted_name
                &&
                (seq = (ExprTy[])_loop0_273_rule()) != null  // _loop0_273
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_274: ASYNC
    public Token _tmp_274_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ASYNC
            if (errorIndicator) {
                return null;
            }
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_276: ',' (expression ['as' star_target])
    public Object[] _loop0_276_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<Object> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' (expression ['as' star_target])
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Object elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (Object)_tmp_353_rule()) != null  // expression ['as' star_target]
            )
            {
                _res = elem;
                if (_res instanceof Object) {
                    _children.add((Object)_res);
                } else {
                    _children.addAll(Arrays.asList((Object[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        Object[] _seq = _children.toArray(new Object[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_276_ID, _seq);
        return _seq;
    }

    // _gather_275: (expression ['as' star_target]) _loop0_276
    public Object[] _gather_275_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // (expression ['as' star_target]) _loop0_276
            if (errorIndicator) {
                return null;
            }
            Object elem;
            Object[] seq;
            if (
                (elem = (Object)_tmp_353_rule()) != null  // expression ['as' star_target]
                &&
                (seq = (Object[])_loop0_276_rule()) != null  // _loop0_276
            )
            {
                _res = insertInFront(elem, seq, Object.class);
                return (Object[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object[])_res;
    }

    // _tmp_277: ASYNC
    public Token _tmp_277_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ASYNC
            if (errorIndicator) {
                return null;
            }
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_279: ',' (expressions ['as' star_target])
    public Object[] _loop0_279_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<Object> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' (expressions ['as' star_target])
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Object elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (Object)_tmp_354_rule()) != null  // expressions ['as' star_target]
            )
            {
                _res = elem;
                if (_res instanceof Object) {
                    _children.add((Object)_res);
                } else {
                    _children.addAll(Arrays.asList((Object[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        Object[] _seq = _children.toArray(new Object[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_279_ID, _seq);
        return _seq;
    }

    // _gather_278: (expressions ['as' star_target]) _loop0_279
    public Object[] _gather_278_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // (expressions ['as' star_target]) _loop0_279
            if (errorIndicator) {
                return null;
            }
            Object elem;
            Object[] seq;
            if (
                (elem = (Object)_tmp_354_rule()) != null  // expressions ['as' star_target]
                &&
                (seq = (Object[])_loop0_279_rule()) != null  // _loop0_279
            )
            {
                _res = insertInFront(elem, seq, Object.class);
                return (Object[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object[])_res;
    }

    // _tmp_280: ASYNC
    public Token _tmp_280_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ASYNC
            if (errorIndicator) {
                return null;
            }
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_282: ',' (expression ['as' star_target])
    public Object[] _loop0_282_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<Object> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' (expression ['as' star_target])
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Object elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (Object)_tmp_355_rule()) != null  // expression ['as' star_target]
            )
            {
                _res = elem;
                if (_res instanceof Object) {
                    _children.add((Object)_res);
                } else {
                    _children.addAll(Arrays.asList((Object[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        Object[] _seq = _children.toArray(new Object[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_282_ID, _seq);
        return _seq;
    }

    // _gather_281: (expression ['as' star_target]) _loop0_282
    public Object[] _gather_281_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // (expression ['as' star_target]) _loop0_282
            if (errorIndicator) {
                return null;
            }
            Object elem;
            Object[] seq;
            if (
                (elem = (Object)_tmp_355_rule()) != null  // expression ['as' star_target]
                &&
                (seq = (Object[])_loop0_282_rule()) != null  // _loop0_282
            )
            {
                _res = insertInFront(elem, seq, Object.class);
                return (Object[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object[])_res;
    }

    // _tmp_283: ASYNC
    public Token _tmp_283_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ASYNC
            if (errorIndicator) {
                return null;
            }
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_285: ',' (expressions ['as' star_target])
    public Object[] _loop0_285_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<Object> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' (expressions ['as' star_target])
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Object elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (Object)_tmp_356_rule()) != null  // expressions ['as' star_target]
            )
            {
                _res = elem;
                if (_res instanceof Object) {
                    _children.add((Object)_res);
                } else {
                    _children.addAll(Arrays.asList((Object[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        Object[] _seq = _children.toArray(new Object[_children.size()]);
        cache.putResult(_start_mark, _LOOP0_285_ID, _seq);
        return _seq;
    }

    // _gather_284: (expressions ['as' star_target]) _loop0_285
    public Object[] _gather_284_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // (expressions ['as' star_target]) _loop0_285
            if (errorIndicator) {
                return null;
            }
            Object elem;
            Object[] seq;
            if (
                (elem = (Object)_tmp_356_rule()) != null  // expressions ['as' star_target]
                &&
                (seq = (Object[])_loop0_285_rule()) != null  // _loop0_285
            )
            {
                _res = insertInFront(elem, seq, Object.class);
                return (Object[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object[])_res;
    }

    // _tmp_286: 'except' | 'finally'
    public Token _tmp_286_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'except'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(678)) != null  // token='except'
            )
            {
                _res = _keyword;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // 'finally'
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            if (
                (_keyword = (Token)expect(679)) != null  // token='finally'
            )
            {
                _res = _keyword;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _loop0_287: block
    public StmtTy[] _loop0_287_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<StmtTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // block
            if (errorIndicator) {
                return null;
            }
            StmtTy[] block_var;
            while (
                (block_var = (StmtTy[])block_rule()) != null  // block
            )
            {
                _res = block_var;
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
        cache.putResult(_start_mark, _LOOP0_287_ID, _seq);
        return _seq;
    }

    // _loop1_288: except_block
    public ExceptHandlerTy[] _loop1_288_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExceptHandlerTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // except_block
            if (errorIndicator) {
                return null;
            }
            ExceptHandlerTy except_block_var;
            while (
                (except_block_var = (ExceptHandlerTy)except_block_rule()) != null  // except_block
            )
            {
                _res = except_block_var;
                if (_res instanceof ExceptHandlerTy) {
                    _children.add((ExceptHandlerTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExceptHandlerTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ExceptHandlerTy[] _seq = _children.toArray(new ExceptHandlerTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_288_ID, _seq);
        return _seq;
    }

    // _tmp_289: 'as' NAME
    public Object _tmp_289_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' NAME
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy name_var;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = dummyName(_keyword, name_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _loop0_290: block
    public StmtTy[] _loop0_290_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<StmtTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // block
            if (errorIndicator) {
                return null;
            }
            StmtTy[] block_var;
            while (
                (block_var = (StmtTy[])block_rule()) != null  // block
            )
            {
                _res = block_var;
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
        cache.putResult(_start_mark, _LOOP0_290_ID, _seq);
        return _seq;
    }

    // _loop1_291: except_star_block
    public ExceptHandlerTy[] _loop1_291_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExceptHandlerTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // except_star_block
            if (errorIndicator) {
                return null;
            }
            ExceptHandlerTy except_star_block_var;
            while (
                (except_star_block_var = (ExceptHandlerTy)except_star_block_rule()) != null  // except_star_block
            )
            {
                _res = except_star_block_var;
                if (_res instanceof ExceptHandlerTy) {
                    _children.add((ExceptHandlerTy)_res);
                } else {
                    _children.addAll(Arrays.asList((ExceptHandlerTy[])_res));
                }
                _mark = mark();
            }
            reset(_mark);
        }
        if (_children.size() == 0) {
            return null;
        }
        ExceptHandlerTy[] _seq = _children.toArray(new ExceptHandlerTy[_children.size()]);
        cache.putResult(_start_mark, _LOOP1_291_ID, _seq);
        return _seq;
    }

    // _tmp_292: expression ['as' NAME]
    public Object _tmp_292_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression ['as' NAME]
            if (errorIndicator) {
                return null;
            }
            Object _opt_var;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                ((_opt_var = (Object)_tmp_357_rule()) != null || true)  // ['as' NAME]
            )
            {
                _res = dummyName(expression_var, _opt_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_293: 'as' NAME
    public Object _tmp_293_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' NAME
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy name_var;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = dummyName(_keyword, name_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_294: 'as' NAME
    public Object _tmp_294_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' NAME
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy name_var;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = dummyName(_keyword, name_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_295: NEWLINE | ':'
    public Token _tmp_295_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // NEWLINE
            if (errorIndicator) {
                return null;
            }
            Token newline_var;
            if (
                (newline_var = (Token)expect(Token.Kind.NEWLINE)) != null  // token='NEWLINE'
            )
            {
                _res = newline_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_296: 'as' NAME
    public Object _tmp_296_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' NAME
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy name_var;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = dummyName(_keyword, name_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_297: 'as' NAME
    public Object _tmp_297_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' NAME
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy name_var;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = dummyName(_keyword, name_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_298: positional_patterns ','
    public Object _tmp_298_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // positional_patterns ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            PatternTy[] positional_patterns_var;
            if (
                (positional_patterns_var = (PatternTy[])positional_patterns_rule()) != null  // positional_patterns
                &&
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = dummyName(positional_patterns_var, _literal);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_299: ASYNC
    public Token _tmp_299_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ASYNC
            if (errorIndicator) {
                return null;
            }
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_300: ASYNC
    public Token _tmp_300_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ASYNC
            if (errorIndicator) {
                return null;
            }
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_301: ASYNC
    public Token _tmp_301_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ASYNC
            if (errorIndicator) {
                return null;
            }
            Token async_var;
            if (
                (async_var = (Token)expect(Token.Kind.ASYNC)) != null  // token='ASYNC'
            )
            {
                _res = async_var;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_302: type_params
    public TypeParamTy[] _tmp_302_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // type_params
            if (errorIndicator) {
                return null;
            }
            TypeParamTy[] type_params_var;
            if (
                (type_params_var = (TypeParamTy[])type_params_rule()) != null  // type_params
            )
            {
                _res = type_params_var;
                return (TypeParamTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (TypeParamTy[])_res;
    }

    // _tmp_303: params
    public ArgumentsTy _tmp_303_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // params
            if (errorIndicator) {
                return null;
            }
            ArgumentsTy params_var;
            if (
                (params_var = (ArgumentsTy)params_rule()) != null  // params
            )
            {
                _res = params_var;
                return (ArgumentsTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ArgumentsTy)_res;
    }

    // _tmp_304: '->' expression
    public Object _tmp_304_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '->' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy expression_var;
            if (
                (_literal = (Token)expect(51)) != null  // token='->'
                &&
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = dummyName(_literal, expression_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_305: type_params
    public TypeParamTy[] _tmp_305_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // type_params
            if (errorIndicator) {
                return null;
            }
            TypeParamTy[] type_params_var;
            if (
                (type_params_var = (TypeParamTy[])type_params_rule()) != null  // type_params
            )
            {
                _res = type_params_var;
                return (TypeParamTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (TypeParamTy[])_res;
    }

    // _tmp_306: '(' arguments? ')'
    public Object _tmp_306_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '(' arguments? ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy _opt_var;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
                &&
                ((_opt_var = (ExprTy)_tmp_358_rule()) != null || true)  // arguments?
                &&
                (_literal_1 = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = dummyName(_literal, _opt_var, _literal_1);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_307: type_params
    public TypeParamTy[] _tmp_307_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // type_params
            if (errorIndicator) {
                return null;
            }
            TypeParamTy[] type_params_var;
            if (
                (type_params_var = (TypeParamTy[])type_params_rule()) != null  // type_params
            )
            {
                _res = type_params_var;
                return (TypeParamTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (TypeParamTy[])_res;
    }

    // _tmp_308: '(' arguments? ')'
    public Object _tmp_308_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '(' arguments? ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            Token _literal_1;
            ExprTy _opt_var;
            if (
                (_literal = (Token)expect(7)) != null  // token='('
                &&
                ((_opt_var = (ExprTy)_tmp_359_rule()) != null || true)  // arguments?
                &&
                (_literal_1 = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = dummyName(_literal, _opt_var, _literal_1);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _loop0_310: ',' double_starred_kvpair
    public KeyValuePair[] _loop0_310_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<KeyValuePair> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' double_starred_kvpair
            if (errorIndicator) {
                return null;
            }
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
        cache.putResult(_start_mark, _LOOP0_310_ID, _seq);
        return _seq;
    }

    // _gather_309: double_starred_kvpair _loop0_310
    public KeyValuePair[] _gather_309_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // double_starred_kvpair _loop0_310
            if (errorIndicator) {
                return null;
            }
            KeyValuePair elem;
            KeyValuePair[] seq;
            if (
                (elem = (KeyValuePair)double_starred_kvpair_rule()) != null  // double_starred_kvpair
                &&
                (seq = (KeyValuePair[])_loop0_310_rule()) != null  // _loop0_310
            )
            {
                _res = insertInFront(elem, seq, KeyValuePair.class);
                return (KeyValuePair[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (KeyValuePair[])_res;
    }

    // _tmp_311: '}' | ','
    public Token _tmp_311_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(26)) != null  // token='}'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_312: ':'
    public Token _tmp_312_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_313: '}' | ','
    public Token _tmp_313_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(26)) != null  // token='}'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ','
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_314: yield_expr | star_expressions
    public ExprTy _tmp_314_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_315: yield_expr | star_expressions
    public ExprTy _tmp_315_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_316: '=' | '!' | ':' | '}'
    public Token _tmp_316_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '!'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(54)) != null  // token='!'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(26)) != null  // token='}'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_317: yield_expr | star_expressions
    public ExprTy _tmp_317_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_318: '!' | ':' | '}'
    public Token _tmp_318_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '!'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(54)) != null  // token='!'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // ':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(26)) != null  // token='}'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_319: yield_expr | star_expressions
    public ExprTy _tmp_319_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_320: yield_expr | star_expressions
    public ExprTy _tmp_320_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_321: '!' NAME
    public Object _tmp_321_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '!' NAME
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy name_var;
            if (
                (_literal = (Token)expect(54)) != null  // token='!'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = dummyName(_literal, name_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_322: ':' | '}'
    public Token _tmp_322_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(26)) != null  // token='}'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_323: yield_expr | star_expressions
    public ExprTy _tmp_323_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_324: '!' NAME
    public Object _tmp_324_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '!' NAME
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy name_var;
            if (
                (_literal = (Token)expect(54)) != null  // token='!'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = dummyName(_literal, name_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _loop0_325: fstring_format_spec
    public ExprTy[] _loop0_325_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // fstring_format_spec
            if (errorIndicator) {
                return null;
            }
            ExprTy fstring_format_spec_var;
            while (
                (fstring_format_spec_var = (ExprTy)fstring_format_spec_rule()) != null  // fstring_format_spec
            )
            {
                _res = fstring_format_spec_var;
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
        cache.putResult(_start_mark, _LOOP0_325_ID, _seq);
        return _seq;
    }

    // _tmp_326: yield_expr | star_expressions
    public ExprTy _tmp_326_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // yield_expr
            if (errorIndicator) {
                return null;
            }
            ExprTy yield_expr_var;
            if (
                (yield_expr_var = (ExprTy)yield_expr_rule()) != null  // yield_expr
            )
            {
                _res = yield_expr_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // star_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy star_expressions_var;
            if (
                (star_expressions_var = (ExprTy)star_expressions_rule()) != null  // star_expressions
            )
            {
                _res = star_expressions_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_327: '!' NAME
    public Object _tmp_327_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '!' NAME
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy name_var;
            if (
                (_literal = (Token)expect(54)) != null  // token='!'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = dummyName(_literal, name_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_328: ':' | '}'
    public Token _tmp_328_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '}'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(26)) != null  // token='}'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_329: star_targets '='
    public ExprTy _tmp_329_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_targets '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy z;
            if (
                (z = (ExprTy)star_targets_rule()) != null  // star_targets
                &&
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = z;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_330: '.' | '...'
    public Token _tmp_330_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '.'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(23)) != null  // token='.'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '...'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(52)) != null  // token='...'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_331: '.' | '...'
    public Token _tmp_331_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '.'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(23)) != null  // token='.'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '...'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(52)) != null  // token='...'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_332: '@' named_expression NEWLINE
    public ExprTy _tmp_332_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // '@' named_expression NEWLINE
            if (errorIndicator) {
                return null;
            }
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
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_333: arguments
    public ExprTy _tmp_333_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // arguments
            if (errorIndicator) {
                return null;
            }
            ExprTy arguments_var;
            if (
                (arguments_var = (ExprTy)arguments_rule()) != null  // arguments
            )
            {
                _res = arguments_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_334: ',' expression
    public ExprTy _tmp_334_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ',' expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy c;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (c = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = c;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_335: ',' star_expression
    public ExprTy _tmp_335_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ',' star_expression
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy c;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (c = (ExprTy)star_expression_rule()) != null  // star_expression
            )
            {
                _res = c;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_336: 'or' conjunction
    public ExprTy _tmp_336_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'or' conjunction
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy c;
            if (
                (_keyword = (Token)expect(684)) != null  // token='or'
                &&
                (c = (ExprTy)conjunction_rule()) != null  // conjunction
            )
            {
                _res = c;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_337: 'and' inversion
    public ExprTy _tmp_337_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'and' inversion
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy c;
            if (
                (_keyword = (Token)expect(685)) != null  // token='and'
                &&
                (c = (ExprTy)inversion_rule()) != null  // inversion
            )
            {
                _res = c;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_338: slice | starred_expression
    public ExprTy _tmp_338_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // slice
            if (errorIndicator) {
                return null;
            }
            ExprTy slice_var;
            if (
                (slice_var = (ExprTy)slice_rule()) != null  // slice
            )
            {
                _res = slice_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // starred_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy starred_expression_var;
            if (
                (starred_expression_var = (ExprTy)starred_expression_rule()) != null  // starred_expression
            )
            {
                _res = starred_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_339: expression
    public ExprTy _tmp_339_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression
            if (errorIndicator) {
                return null;
            }
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
            )
            {
                _res = expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_340: fstring | string
    public ExprTy _tmp_340_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // fstring
            if (errorIndicator) {
                return null;
            }
            ExprTy fstring_var;
            if (
                (fstring_var = (ExprTy)fstring_rule()) != null  // fstring
            )
            {
                _res = fstring_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // string
            if (errorIndicator) {
                return null;
            }
            ExprTy string_var;
            if (
                (string_var = (ExprTy)string_rule()) != null  // string
            )
            {
                _res = string_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_341: star_named_expressions
    public ExprTy[] _tmp_341_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_named_expressions
            if (errorIndicator) {
                return null;
            }
            ExprTy[] star_named_expressions_var;
            if (
                (star_named_expressions_var = (ExprTy[])star_named_expressions_rule()) != null  // star_named_expressions
            )
            {
                _res = star_named_expressions_var;
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_342: 'if' disjunction
    public ExprTy _tmp_342_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'if' disjunction
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(668)) != null  // token='if'
                &&
                (z = (ExprTy)disjunction_rule()) != null  // disjunction
            )
            {
                _res = z;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_343: 'if' disjunction
    public ExprTy _tmp_343_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'if' disjunction
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy z;
            if (
                (_keyword = (Token)expect(668)) != null  // token='if'
                &&
                (z = (ExprTy)disjunction_rule()) != null  // disjunction
            )
            {
                _res = z;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_344: starred_expression | (assignment_expression | expression !':=') !'='
    public ExprTy _tmp_344_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // starred_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy starred_expression_var;
            if (
                (starred_expression_var = (ExprTy)starred_expression_rule()) != null  // starred_expression
            )
            {
                _res = starred_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // (assignment_expression | expression !':=') !'='
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_360_var;
            if (
                (_tmp_360_var = (ExprTy)_tmp_360_rule()) != null  // assignment_expression | expression !':='
                &&
                genLookahead_expect(false, 22)  // token='='
            )
            {
                _res = _tmp_360_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_345: ',' star_target
    public ExprTy _tmp_345_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ',' star_target
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy c;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (c = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = c;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_346: ',' star_target
    public ExprTy _tmp_346_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ',' star_target
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy c;
            if (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (c = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = c;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_347:
    //     | ','.(starred_expression | (assignment_expression | expression !':=') !'=')+ ',' kwargs
    public Object _tmp_347_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ','.(starred_expression | (assignment_expression | expression !':=') !'=')+ ',' kwargs
            if (errorIndicator) {
                return null;
            }
            ExprTy[] _gather_361_var;
            Token _literal;
            KeywordOrStarred[] kwargs_var;
            if (
                (_gather_361_var = (ExprTy[])_gather_361_rule()) != null  // ','.(starred_expression | (assignment_expression | expression !':=') !'=')+
                &&
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (kwargs_var = (KeywordOrStarred[])kwargs_rule()) != null  // kwargs
            )
            {
                _res = dummyName(_gather_361_var, _literal, kwargs_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_348: starred_expression !'='
    public ExprTy _tmp_348_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // starred_expression !'='
            if (errorIndicator) {
                return null;
            }
            ExprTy starred_expression_var;
            if (
                (starred_expression_var = (ExprTy)starred_expression_rule()) != null  // starred_expression
                &&
                genLookahead_expect(false, 22)  // token='='
            )
            {
                _res = starred_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_349: star_targets '='
    public Object _tmp_349_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_targets '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy star_targets_var;
            if (
                (star_targets_var = (ExprTy)star_targets_rule()) != null  // star_targets
                &&
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = dummyName(star_targets_var, _literal);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_350: star_targets '='
    public Object _tmp_350_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // star_targets '='
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy star_targets_var;
            if (
                (star_targets_var = (ExprTy)star_targets_rule()) != null  // star_targets
                &&
                (_literal = (Token)expect(22)) != null  // token='='
            )
            {
                _res = dummyName(star_targets_var, _literal);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_351: ')' | '**'
    public Token _tmp_351_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ')'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(8)) != null  // token=')'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '**'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(35)) != null  // token='**'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_352: ':' | '**'
    public Token _tmp_352_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // ':'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(11)) != null  // token=':'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        { // '**'
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            if (
                (_literal = (Token)expect(35)) != null  // token='**'
            )
            {
                _res = _literal;
                return (Token)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Token)_res;
    }

    // _tmp_353: expression ['as' star_target]
    public Object _tmp_353_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression ['as' star_target]
            if (errorIndicator) {
                return null;
            }
            Object _opt_var;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                ((_opt_var = (Object)_tmp_363_rule()) != null || true)  // ['as' star_target]
            )
            {
                _res = dummyName(expression_var, _opt_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_354: expressions ['as' star_target]
    public Object _tmp_354_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expressions ['as' star_target]
            if (errorIndicator) {
                return null;
            }
            Object _opt_var;
            ExprTy expressions_var;
            if (
                (expressions_var = (ExprTy)expressions_rule()) != null  // expressions
                &&
                ((_opt_var = (Object)_tmp_364_rule()) != null || true)  // ['as' star_target]
            )
            {
                _res = dummyName(expressions_var, _opt_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_355: expression ['as' star_target]
    public Object _tmp_355_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expression ['as' star_target]
            if (errorIndicator) {
                return null;
            }
            Object _opt_var;
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                ((_opt_var = (Object)_tmp_365_rule()) != null || true)  // ['as' star_target]
            )
            {
                _res = dummyName(expression_var, _opt_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_356: expressions ['as' star_target]
    public Object _tmp_356_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // expressions ['as' star_target]
            if (errorIndicator) {
                return null;
            }
            Object _opt_var;
            ExprTy expressions_var;
            if (
                (expressions_var = (ExprTy)expressions_rule()) != null  // expressions
                &&
                ((_opt_var = (Object)_tmp_366_rule()) != null || true)  // ['as' star_target]
            )
            {
                _res = dummyName(expressions_var, _opt_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_357: 'as' NAME
    public Object _tmp_357_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' NAME
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy name_var;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (name_var = (ExprTy)name_token()) != null  // NAME
            )
            {
                _res = dummyName(_keyword, name_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_358: arguments
    public ExprTy _tmp_358_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // arguments
            if (errorIndicator) {
                return null;
            }
            ExprTy arguments_var;
            if (
                (arguments_var = (ExprTy)arguments_rule()) != null  // arguments
            )
            {
                _res = arguments_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_359: arguments
    public ExprTy _tmp_359_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // arguments
            if (errorIndicator) {
                return null;
            }
            ExprTy arguments_var;
            if (
                (arguments_var = (ExprTy)arguments_rule()) != null  // arguments
            )
            {
                _res = arguments_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_360: assignment_expression | expression !':='
    public ExprTy _tmp_360_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // assignment_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy assignment_expression_var;
            if (
                (assignment_expression_var = (ExprTy)assignment_expression_rule()) != null  // assignment_expression
            )
            {
                _res = assignment_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression !':='
            if (errorIndicator) {
                return null;
            }
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                genLookahead_expect(false, 53)  // token=':='
            )
            {
                _res = expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _loop0_362: ',' (starred_expression | (assignment_expression | expression !':=') !'=')
    public ExprTy[] _loop0_362_rule()
    {
        if (errorIndicator) {
            return null;
        }
        Object _res = null;
        int _mark = mark();
        int _start_mark = mark();
        List<ExprTy> _children = new ArrayList<>();
        int _children_capacity = 1;
        int _n = 0;
        { // ',' (starred_expression | (assignment_expression | expression !':=') !'=')
            if (errorIndicator) {
                return null;
            }
            Token _literal;
            ExprTy elem;
            while (
                (_literal = (Token)expect(12)) != null  // token=','
                &&
                (elem = (ExprTy)_tmp_367_rule()) != null  // starred_expression | (assignment_expression | expression !':=') !'='
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
        cache.putResult(_start_mark, _LOOP0_362_ID, _seq);
        return _seq;
    }

    // _gather_361:
    //     | (starred_expression | (assignment_expression | expression !':=') !'=') _loop0_362
    public ExprTy[] _gather_361_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // (starred_expression | (assignment_expression | expression !':=') !'=') _loop0_362
            if (errorIndicator) {
                return null;
            }
            ExprTy elem;
            ExprTy[] seq;
            if (
                (elem = (ExprTy)_tmp_367_rule()) != null  // starred_expression | (assignment_expression | expression !':=') !'='
                &&
                (seq = (ExprTy[])_loop0_362_rule()) != null  // _loop0_362
            )
            {
                _res = insertInFront(elem, seq, ExprTy.class);
                return (ExprTy[])_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy[])_res;
    }

    // _tmp_363: 'as' star_target
    public Object _tmp_363_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' star_target
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy star_target_var;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (star_target_var = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = dummyName(_keyword, star_target_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_364: 'as' star_target
    public Object _tmp_364_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' star_target
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy star_target_var;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (star_target_var = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = dummyName(_keyword, star_target_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_365: 'as' star_target
    public Object _tmp_365_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' star_target
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy star_target_var;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (star_target_var = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = dummyName(_keyword, star_target_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_366: 'as' star_target
    public Object _tmp_366_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // 'as' star_target
            if (errorIndicator) {
                return null;
            }
            Token _keyword;
            ExprTy star_target_var;
            if (
                (_keyword = (Token)expect(674)) != null  // token='as'
                &&
                (star_target_var = (ExprTy)star_target_rule()) != null  // star_target
            )
            {
                _res = dummyName(_keyword, star_target_var);
                return (Object)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (Object)_res;
    }

    // _tmp_367: starred_expression | (assignment_expression | expression !':=') !'='
    public ExprTy _tmp_367_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // starred_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy starred_expression_var;
            if (
                (starred_expression_var = (ExprTy)starred_expression_rule()) != null  // starred_expression
            )
            {
                _res = starred_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // (assignment_expression | expression !':=') !'='
            if (errorIndicator) {
                return null;
            }
            ExprTy _tmp_368_var;
            if (
                (_tmp_368_var = (ExprTy)_tmp_368_rule()) != null  // assignment_expression | expression !':='
                &&
                genLookahead_expect(false, 22)  // token='='
            )
            {
                _res = _tmp_368_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // _tmp_368: assignment_expression | expression !':='
    public ExprTy _tmp_368_rule()
    {
        if (errorIndicator) {
            return null;
        }
        int _mark = mark();
        Object _res = null;
        { // assignment_expression
            if (errorIndicator) {
                return null;
            }
            ExprTy assignment_expression_var;
            if (
                (assignment_expression_var = (ExprTy)assignment_expression_rule()) != null  // assignment_expression
            )
            {
                _res = assignment_expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        { // expression !':='
            if (errorIndicator) {
                return null;
            }
            ExprTy expression_var;
            if (
                (expression_var = (ExprTy)expression_rule()) != null  // expression
                &&
                genLookahead_expect(false, 53)  // token=':='
            )
            {
                _res = expression_var;
                return (ExprTy)_res;
            }
            reset(_mark);
        }
        _res = null;
        return (ExprTy)_res;
    }

    // lookahead methods generated
    private boolean genLookahead_expect(boolean match, int arg0) {
        int tmpPos = mark();
        Token result = expect(arg0);
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead_expect_SOFT_KEYWORD(boolean match, String arg0) {
        int tmpPos = mark();
        ExprTy result = expect_SOFT_KEYWORD(arg0);
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_9_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_9_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_10_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_10_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_11_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_11_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_12_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_12_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_13_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_13_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_27_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_27_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_88_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_88_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_100_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_100_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_101_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_101_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_102_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_102_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_103_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_103_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_137_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_137_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead_t_lookahead_rule(boolean match) {
        int tmpPos = mark();
        Token result = t_lookahead_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_215_rule(boolean match) {
        int tmpPos = mark();
        Object result = _tmp_215_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_221_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_221_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_223_rule(boolean match) {
        int tmpPos = mark();
        Object result = _tmp_223_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_224_rule(boolean match) {
        int tmpPos = mark();
        Object result = _tmp_224_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_225_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_225_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_227_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_227_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_228_rule(boolean match) {
        int tmpPos = mark();
        Object result = _tmp_228_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_229_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_229_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_247_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_247_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_271_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_271_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_286_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_286_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead_name_token(boolean match) {
        int tmpPos = mark();
        ExprTy result = name_token();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_311_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_311_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_312_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_312_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_313_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_313_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_314_rule(boolean match) {
        int tmpPos = mark();
        ExprTy result = _tmp_314_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_316_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_316_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_318_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_318_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_322_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_322_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    private boolean genLookahead__tmp_328_rule(boolean match) {
        int tmpPos = mark();
        Token result = _tmp_328_rule();
        reset(tmpPos);
        return (result != null) == match;
    }

    
    @Override
    protected SSTNode runParser(InputType inputType) {
        SSTNode result = null;
        switch (inputType) {
            case FILE:
                return file_rule();
            case SINGLE:
                return interactive_rule();
            case EVAL:
                return eval_rule();
            case FUNCTION_TYPE:
                return func_type_rule();
        }
        return result;
    }

}
