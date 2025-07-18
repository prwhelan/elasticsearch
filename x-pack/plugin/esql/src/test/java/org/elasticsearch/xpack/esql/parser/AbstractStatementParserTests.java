/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.parser;

import org.elasticsearch.common.logging.LoggerMessageFormat;
import org.elasticsearch.common.lucene.BytesRefs;
import org.elasticsearch.index.IndexMode;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.esql.EsqlTestUtils;
import org.elasticsearch.xpack.esql.VerificationException;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.Literal;
import org.elasticsearch.xpack.esql.core.expression.MapExpression;
import org.elasticsearch.xpack.esql.core.expression.UnresolvedAttribute;
import org.elasticsearch.xpack.esql.core.type.DataType;
import org.elasticsearch.xpack.esql.expression.function.UnresolvedFunction;
import org.elasticsearch.xpack.esql.plan.IndexPattern;
import org.elasticsearch.xpack.esql.plan.logical.LogicalPlan;
import org.elasticsearch.xpack.esql.plan.logical.UnresolvedRelation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.xpack.esql.core.tree.Source.EMPTY;
import static org.elasticsearch.xpack.esql.core.util.NumericUtils.asLongUnsigned;
import static org.elasticsearch.xpack.esql.expression.function.FunctionResolutionStrategy.DEFAULT;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

abstract class AbstractStatementParserTests extends ESTestCase {

    EsqlParser parser = new EsqlParser();

    void assertStatement(String statement, LogicalPlan expected) {
        final LogicalPlan actual;
        try {
            actual = statement(statement);
        } catch (Exception e) {
            throw new AssertionError("parsing error for [" + statement + "]", e);
        }
        assertThat(statement, actual, equalTo(expected));
    }

    LogicalPlan statement(String query, String arg) {
        return statement(LoggerMessageFormat.format(null, query, arg), new QueryParams());
    }

    LogicalPlan statement(String e) {
        return statement(e, new QueryParams());
    }

    LogicalPlan statement(String e, QueryParams params) {
        return parser.createStatement(e, params, EsqlTestUtils.TEST_CFG);
    }

    LogicalPlan processingCommand(String e) {
        return parser.createStatement("row a = 1 | " + e, EsqlTestUtils.TEST_CFG);
    }

    static UnresolvedAttribute attribute(String name) {
        return new UnresolvedAttribute(EMPTY, name);
    }

    static UnresolvedFunction function(String name, List<Expression> args) {
        return new UnresolvedFunction(EMPTY, name, DEFAULT, args);
    }

    static UnresolvedRelation relation(String name) {
        return new UnresolvedRelation(EMPTY, new IndexPattern(EMPTY, name), false, List.of(), IndexMode.STANDARD, null, "FROM");
    }

    static Literal integer(int i) {
        return new Literal(EMPTY, i, DataType.INTEGER);
    }

    static Literal integers(int... ints) {
        return new Literal(EMPTY, Arrays.stream(ints).boxed().toList(), DataType.INTEGER);
    }

    static Literal literalLong(long i) {
        return new Literal(EMPTY, i, DataType.LONG);
    }

    static Literal literalLongs(long... longs) {
        return new Literal(EMPTY, Arrays.stream(longs).boxed().toList(), DataType.LONG);
    }

    static Literal literalDouble(double d) {
        return new Literal(EMPTY, d, DataType.DOUBLE);
    }

    static Literal literalDoubles(double... doubles) {
        return new Literal(EMPTY, Arrays.stream(doubles).boxed().toList(), DataType.DOUBLE);
    }

    static Literal literalUnsignedLong(String ulong) {
        return new Literal(EMPTY, asLongUnsigned(new BigInteger(ulong)), DataType.UNSIGNED_LONG);
    }

    static Literal literalUnsignedLongs(String... ulongs) {
        return new Literal(EMPTY, Arrays.stream(ulongs).map(s -> asLongUnsigned(new BigInteger(s))).toList(), DataType.UNSIGNED_LONG);
    }

    static Literal literalBoolean(boolean b) {
        return new Literal(EMPTY, b, DataType.BOOLEAN);
    }

    static Literal literalBooleans(boolean... booleans) {
        List<Boolean> v = new ArrayList<>(booleans.length);
        for (boolean b : booleans) {
            v.add(b);
        }
        return new Literal(EMPTY, v, DataType.BOOLEAN);
    }

    static Literal literalString(String s) {
        return Literal.keyword(EMPTY, s);
    }

    static Literal literalStrings(String... strings) {
        return new Literal(EMPTY, Arrays.asList(strings).stream().map(BytesRefs::toBytesRef).toList(), DataType.KEYWORD);
    }

    static MapExpression mapExpression(Map<String, Object> keyValuePairs) {
        List<Expression> ees = new ArrayList<>(keyValuePairs.size());
        for (Map.Entry<String, Object> entry : keyValuePairs.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            DataType type = (value instanceof List<?> l) ? DataType.fromJava(l.get(0)) : DataType.fromJava(value);
            value = stringsToBytesRef(value, type);

            ees.add(Literal.keyword(EMPTY, key));
            ees.add(new Literal(EMPTY, value, type));
        }
        return new MapExpression(EMPTY, ees);
    }

    private static Object stringsToBytesRef(Object value, DataType type) {
        if (value instanceof List<?> l) {
            return l.stream().map(x -> stringsToBytesRef(x, type)).toList();
        }
        if (value instanceof String && (type == DataType.TEXT || type == DataType.KEYWORD)) {
            value = BytesRefs.toBytesRef(value);
        }
        return value;
    }

    void expectError(String query, String errorMessage) {
        expectError(query, null, errorMessage);
    }

    void expectError(String query, List<QueryParam> params, String errorMessage) {
        expectThrows(
            "Query [" + query + "] is expected to throw " + ParsingException.class + " with message [" + errorMessage + "]",
            ParsingException.class,
            containsString(errorMessage),
            () -> statement(query, new QueryParams(params))
        );
    }

    void expectVerificationError(String query, String errorMessage) {
        expectThrows(
            "Query [" + query + "] is expected to throw " + VerificationException.class + " with message [" + errorMessage + "]",
            VerificationException.class,
            containsString(errorMessage),
            () -> parser.createStatement(query, EsqlTestUtils.TEST_CFG)
        );
    }

    void expectInvalidIndexNameErrorWithLineNumber(String query, String indexString, String lineNumber) {
        if ((indexString.contains("|") || indexString.contains(" ")) == false) {
            expectInvalidIndexNameErrorWithLineNumber(query, indexString, lineNumber, indexString);
        }
        expectInvalidIndexNameErrorWithLineNumber(query, "\"" + indexString + "\"", lineNumber, indexString);
    }

    void expectErrorWithLineNumber(String query, String indexString, String lineNumber, String error) {
        expectError(LoggerMessageFormat.format(null, query, indexString), lineNumber + error);
    }

    void expectInvalidIndexNameErrorWithLineNumber(String query, String indexString, String lineNumber, String name) {
        expectError(LoggerMessageFormat.format(null, query, indexString), lineNumber + "Invalid index name [" + name);
    }

    void expectInvalidIndexNameErrorWithLineNumber(String query, String indexString, String lineNumber, String name, String error) {
        expectError(LoggerMessageFormat.format(null, query, indexString), lineNumber + "Invalid index name [" + name + "], " + error);
    }

    void expectDateMathErrorWithLineNumber(String query, String arg, String lineNumber, String error) {
        expectError(LoggerMessageFormat.format(null, query, arg), lineNumber + error);
    }

    void expectDoubleColonErrorWithLineNumber(String query, String indexString, int lineNumber) {
        expectError(LoggerMessageFormat.format(null, query, indexString), "line 1:" + lineNumber + ": mismatched input '::'");
    }
}
