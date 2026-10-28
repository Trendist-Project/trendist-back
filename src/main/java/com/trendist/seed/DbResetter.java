package com.trendist.seed;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * reset 프로필: DataSource가 만들어지는 즉시(Hibernate가 스키마를 만들기 전) 스키마의 모든 테이블을 지운다(11.3).
 * 엔티티에 없는 옛 테이블까지 지우려고 information_schema 목록을 읽는다.
 * 안전장치: 로컬 DB만, prod 프로필과 함께 켜지면 시작을 거부한다.
 */
@Component
@Profile("reset")
@RequiredArgsConstructor
public class DbResetter implements BeanPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(DbResetter.class);
    private final Environment env;
    private boolean done;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!done && bean instanceof DataSource ds) {
            done = true;
            reset(ds);
        }
        return bean;
    }

    private void reset(DataSource ds) {
        if (Arrays.asList(env.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException("reset 프로필은 prod 프로필과 함께 쓸 수 없습니다");
        }
        try (Connection c = ds.getConnection()) {
            String url = c.getMetaData().getURL();
            boolean local = url.startsWith("jdbc:h2:mem:") || url.contains("//localhost") || url.contains("//127.0.0.1");
            if (!local) throw new IllegalStateException("reset은 로컬 DB에서만 동작합니다: " + url);
            String schema = c.getCatalog() != null && !url.startsWith("jdbc:h2:") ? c.getCatalog() : c.getSchema();
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = c.getMetaData().getTables(url.startsWith("jdbc:h2:") ? null : schema,
                    url.startsWith("jdbc:h2:") ? schema : null, "%", new String[]{"TABLE"})) {
                while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
            }
            log.warn("DB reset: schema={} tables={}", schema, tables.size());
            boolean h2 = url.startsWith("jdbc:h2:");
            try (Statement st = c.createStatement()) {
                st.execute(h2 ? "SET REFERENTIAL_INTEGRITY FALSE" : "SET FOREIGN_KEY_CHECKS = 0");
                for (String t : tables) st.execute("DROP TABLE IF EXISTS `" + t.replace("`", "") + "`");
                st.execute(h2 ? "SET REFERENTIAL_INTEGRITY TRUE" : "SET FOREIGN_KEY_CHECKS = 1");
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("DB reset 실패", e);
        }
    }
}
