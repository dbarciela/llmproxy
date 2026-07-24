package io.github.dbarciela.aura;

import io.github.dbarciela.aura.db.SessionHistoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;

public class PerformanceBenchmark {

    @AfterEach
    public void cleanup() {
        File dbFile = new File("test.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    public void testBaselineAndImprovement() {
        File dbFile = new File("test.db");
        if (dbFile.exists()) dbFile.delete();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:test.db");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        SessionHistoryRepository repo = new SessionHistoryRepository(jdbcTemplate);
        repo.init();

        List<String> ids1 = new ArrayList<>();
        List<String> ids2 = new ArrayList<>();
        List<String> ids3 = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            String id1 = UUID.randomUUID().toString();
            String id2 = UUID.randomUUID().toString();
            String id3 = UUID.randomUUID().toString();
            ids1.add(id1);
            ids2.add(id2);
            ids3.add(id3);
            repo.save(id1, "/test", 200, "REQUEST:\n{}RESPONSE:\n{}");
            repo.save(id2, "/test", 200, "REQUEST:\n{}RESPONSE:\n{}");
            repo.save(id3, "/test", 200, "REQUEST:\n{}RESPONSE:\n{}");
        }

        // BASELINE
        long start1 = System.currentTimeMillis();
        for (String id : ids1) {
            repo.updateImprovedTitle(id, "Improved Title");
        }
        long end1 = System.currentTimeMillis();

        // BATCH UPDATE
        long start2 = System.currentTimeMillis();
        List<Object[]> batch = ids2.stream().map(id -> new Object[]{"Batch Title", id}).collect(Collectors.toList());
        jdbcTemplate.batchUpdate("UPDATE session_history SET improved_title = ? WHERE id = ?", batch);
        long end2 = System.currentTimeMillis();

        // IN CLAUSE
        long start3 = System.currentTimeMillis();
        String inSql = String.join(",", Collections.nCopies(ids3.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add("IN Title");
        args.addAll(ids3);
        jdbcTemplate.update(String.format("UPDATE session_history SET improved_title = ? WHERE id IN (%s)", inSql), args.toArray());
        long end3 = System.currentTimeMillis();

        System.out.println("=========================================");
        System.out.println("Baseline loop update time: " + (end1 - start1) + "ms");
        System.out.println("Batch update time: " + (end2 - start2) + "ms");
        System.out.println("IN clause update time: " + (end3 - start3) + "ms");
        System.out.println("=========================================");
    }
}
