package com.umurinan.jpalocking.util;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

public class SqlCaptor implements StatementInspector {

    private static final ThreadLocal<List<String>> QUERIES =
        ThreadLocal.withInitial(ArrayList::new);

    @Override
    public String inspect(String sql) {
        QUERIES.get().add(sql.toLowerCase());
        return sql;
    }

    public static void reset() {
        QUERIES.get().clear();
    }

    public static List<String> getQueries() {
        return new ArrayList<>(QUERIES.get());
    }
}
