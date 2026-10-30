package com.trendist.seed;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 엔티티에서 뺀 컬럼을 DB에서도 지운다. ddl-auto update는 컬럼을 지우지 않으므로 Flyway 전환 전까지 여기서 지운다(16.1).
 * DDL은 MySQL에서 트랜잭션을 끊으므로 시더(트랜잭션)보다 먼저, 트랜잭션 없이 실행한다.
 */
@Component
@Order(-1)
@RequiredArgsConstructor
public class DroppedColumns implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DroppedColumns.class);
    /** {테이블, 컬럼}. last_active_day는 last_played_at으로 같은 일을 하게 되어 뺐다(136판) */
    private static final List<String[]> DROPPED = List.<String[]>of(new String[] {"players", "last_active_day"});

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            for (String[] d : DROPPED) {
                if (!exists(c, d[0], d[1])) continue;
                try (Statement st = c.createStatement()) {
                    st.execute("ALTER TABLE " + d[0] + " DROP COLUMN " + d[1]);
                }
                log.info("컬럼 삭제: {}.{}", d[0], d[1]);
            }
        }
    }

    private static boolean exists(Connection c, String table, String column) throws Exception {
        // 대소문자를 DB마다 다르게 저장하므로 둘 다 본다
        for (String t : List.of(table, table.toUpperCase())) {
            for (String col : List.of(column, column.toUpperCase())) {
                try (ResultSet rs = c.getMetaData().getColumns(c.getCatalog(), null, t, col)) {
                    if (rs.next()) return true;
                }
            }
        }
        return false;
    }
}
