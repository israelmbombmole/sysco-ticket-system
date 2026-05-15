package com.app.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL fragments that differ between SQLite and Oracle, plus Oracle rewriting for JDBC SQL strings.
 */
public final class SqlDialect {

    private static DbVendor vendor = DbVendor.SQLITE;
    private static final Pattern TS_LITERAL = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$");

    private SqlDialect() {}

    static void init(DbVendor v) {
        vendor = v != null ? v : DbVendor.SQLITE;
    }

    public static boolean isOracle() {
        return vendor == DbVendor.ORACLE;
    }

    public static String now() {
        return isOracle() ? "CURRENT_TIMESTAMP" : "datetime('now','localtime')";
    }

    public static String minutesBetweenNowAnd(String columnOrExpr) {
        if (isOracle()) {
            return "((CAST(CURRENT_TIMESTAMP AS DATE) - CAST(" + columnOrExpr + " AS DATE)) * 24 * 60)";
        }
        return "(strftime('%s','now','localtime') - strftime('%s', " + columnOrExpr + ")) / 60";
    }

    public static String ceilMinutesBetweenNowAnd(String columnOrExpr) {
        if (isOracle()) {
            return "CEIL((CAST(CURRENT_TIMESTAMP AS DATE) - CAST(" + columnOrExpr + " AS DATE)) * 24 * 60)";
        }
        return "(strftime('%s','now','localtime') - strftime('%s', " + columnOrExpr + ") + 59) / 60";
    }

    public static String ts(String column) {
        return isOracle() ? column : "datetime(" + column + ")";
    }

    public static String limitRows(int n) {
        return isOracle() ? (" FETCH FIRST " + n + " ROWS ONLY") : (" LIMIT " + n);
    }

    public static String limitOffsetParams() {
        return isOracle() ? " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY" : " LIMIT ? OFFSET ?";
    }

    /**
     * Rewrites SQLite-oriented SQL for Oracle (used by {@link DB} connection wrapper).
     */
    public static String rewriteSqlForOracle(String sql) {
        if (!isOracle() || sql == null) {
            return sql;
        }
        String s = sql;

        // Before touching datetime('now'...): full expressions that embed datetime inside strftime
        s = Pattern.compile(
                        "CAST\\(\\s*\\(\\s*strftime\\('%s',\\s*datetime\\('now',\\s*'localtime'\\)\\)\\s*-\\s*strftime\\('%s',\\s*started_at\\)\\s*\\+\\s*59\\s*\\)\\s*/\\s*60\\s*AS\\s*INTEGER\\s*\\)",
                        Pattern.DOTALL | Pattern.CASE_INSENSITIVE)
                .matcher(s)
                .replaceAll(Matcher.quoteReplacement("CAST(" + ceilMinutesBetweenNowAnd("started_at") + " AS INTEGER)"));

        s = Pattern.compile(
                        "\\(\\s*strftime\\('%s',\\s*'now',\\s*'localtime'\\)\\s*-\\s*strftime\\('%s',\\s*COALESCE\\(started_at,\\s*assigned_at\\)\\)\\s*\\)\\s*/\\s*60",
                        Pattern.DOTALL)
                .matcher(s)
                .replaceAll(Matcher.quoteReplacement(minutesBetweenNowAnd("COALESCE(started_at, assigned_at)")));
        s = Pattern.compile(
                        "\\(\\s*strftime\\('%s',\\s*'now',\\s*'localtime'\\)\\s*-\\s*strftime\\('%s',\\s*COALESCE\\(started_at,\\s*created_at\\)\\)\\s*\\)\\s*/\\s*60",
                        Pattern.DOTALL)
                .matcher(s)
                .replaceAll(Matcher.quoteReplacement(minutesBetweenNowAnd("COALESCE(started_at, created_at)")));

        s = Pattern.compile(
                        "\\(\\s*strftime\\('%s',\\s*'now',\\s*'localtime'\\)\\s*-\\s*strftime\\('%s',\\s*started_at\\)\\s*\\+\\s*59\\s*\\)\\s*/\\s*60",
                        Pattern.DOTALL)
                .matcher(s)
                .replaceAll(Matcher.quoteReplacement(ceilMinutesBetweenNowAnd("started_at")));

        s = Pattern.compile(
                        "\\(\\s*strftime\\('%s',\\s*'now'\\)\\s*-\\s*strftime\\('%s',\\s*([a-zA-Z0-9_.]+)\\)\\s*\\)\\s*/\\s*60")
                .matcher(s)
                .replaceAll(mr -> Matcher.quoteReplacement(minutesBetweenNowAnd(mr.group(1))));

        s = s.replace("datetime('now','localtime')", "CURRENT_TIMESTAMP");
        s = s.replace("datetime('now')", "CURRENT_TIMESTAMP");

        s = stripSqliteDatetimeCalls(s);

        // LIMIT <literal> -> FETCH FIRST (Oracle). Skips LIMIT ? which uses bind params (see MessageDAO).
        s = Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)\\b").matcher(s).replaceAll("FETCH FIRST $1 ROWS ONLY");

        return s;
    }

    /** Removes SQLite datetime(...) wrapper, keeping inner expression (Oracle TIMESTAMP columns). */
    static String stripSqliteDatetimeCalls(String sql) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        final String key = "datetime(";
        while (i < sql.length()) {
            int k = sql.indexOf(key, i);
            if (k < 0) {
                out.append(sql.substring(i));
                break;
            }
            out.append(sql, i, k);
            int start = k + key.length();
            int depth = 1;
            int j = start;
            while (j < sql.length() && depth > 0) {
                char ch = sql.charAt(j);
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                }
                j++;
            }
            if (depth != 0) {
                out.append(sql.substring(k));
                break;
            }
            String inner = sql.substring(start, j - 1).trim();
            out.append(inner);
            i = j;
        }
        return out.toString();
    }

    static Connection wrapForOracleRewriting(Connection raw) {
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if (args != null && args.length >= 1 && args[0] instanceof String
                            && "prepareStatement".equals(method.getName())) {
                        Object[] a = args.clone();
                        a[0] = rewriteSqlForOracle((String) a[0]);
                        Object prepared = method.invoke(raw, a);
                        if (prepared instanceof PreparedStatement ps) {
                            return wrapPreparedStatementForOracle(ps);
                        }
                        return prepared;
                    }
                    return method.invoke(raw, args);
                });
    }

    private static PreparedStatement wrapPreparedStatementForOracle(PreparedStatement rawPs) {
        return (PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                (proxy, method, args) -> {
                    if ("setString".equals(method.getName())
                            && args != null
                            && args.length == 2
                            && args[0] instanceof Integer
                            && args[1] instanceof String s
                            && TS_LITERAL.matcher(s).matches()) {
                        try {
                            rawPs.setTimestamp((Integer) args[0], Timestamp.valueOf(s));
                            return null;
                        } catch (IllegalArgumentException ignored) {
                            // Fall through to default setString when parsing fails.
                        }
                    }
                    return method.invoke(rawPs, args);
                });
    }
}
