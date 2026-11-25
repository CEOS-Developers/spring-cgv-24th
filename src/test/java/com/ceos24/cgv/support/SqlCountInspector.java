package com.ceos24.cgv.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Hibernate가 실행하려는 SQL을 기록해 테스트에서 실행 횟수를 확인한다
public class SqlCountInspector implements StatementInspector {

    private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql);
        return sql;
    }

    public static void clear() {
        STATEMENTS.clear();
    }

    public static int total() {
        return STATEMENTS.size();
    }

    public static long selectCount(String table) {
        return STATEMENTS.stream()
                .filter(sql -> sql.startsWith("select") && sql.contains(" from " + table + " "))
                .count();
    }

    public static List<String> statements() {
        return List.copyOf(STATEMENTS);
    }
}
