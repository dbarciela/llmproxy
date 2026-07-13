package io.github.dbarciela.aura.db;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;

@Repository
public class SessionHistoryRepository {

	private final JdbcTemplate jdbcTemplate;

	public SessionHistoryRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@PostConstruct
	public void init() {
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS session_history (" + "id TEXT PRIMARY KEY, "
				+ "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " + "endpoint TEXT, " + "status_code INTEGER, "
				+ "full_payload TEXT)");

		try {
			jdbcTemplate.execute("ALTER TABLE session_history ADD COLUMN improved_title TEXT");
		} catch (Exception e) {
			// Column likely already exists, ignore
		}

		// FTS5 table for full-text search
		jdbcTemplate.execute("CREATE VIRTUAL TABLE IF NOT EXISTS session_history_fts USING fts5("
				+ "id UNINDEXED, endpoint UNINDEXED, status_code UNINDEXED, full_payload, content='session_history', content_rowid='rowid')");

		// Triggers to keep FTS table synced with session_history
		jdbcTemplate.execute("DROP TRIGGER IF EXISTS session_history_ai");
		jdbcTemplate.execute("CREATE TRIGGER session_history_ai AFTER INSERT ON session_history BEGIN "
				+ "  INSERT INTO session_history_fts(rowid, id, endpoint, status_code, full_payload) "
				+ "  VALUES (new.rowid, new.id, new.endpoint, new.status_code, new.full_payload); " + "END;");

		jdbcTemplate.execute("DROP TRIGGER IF EXISTS session_history_ad");
		jdbcTemplate.execute("CREATE TRIGGER session_history_ad AFTER DELETE ON session_history BEGIN "
				+ "  INSERT INTO session_history_fts(session_history_fts, rowid, id, endpoint, status_code, full_payload) "
				+ "  VALUES('delete', old.rowid, old.id, old.endpoint, old.status_code, old.full_payload); " + "END;");

		jdbcTemplate.execute("DROP TRIGGER IF EXISTS session_history_au");
		jdbcTemplate.execute("CREATE TRIGGER session_history_au AFTER UPDATE ON session_history BEGIN "
				+ "  INSERT INTO session_history_fts(session_history_fts, rowid, id, endpoint, status_code, full_payload) "
				+ "  VALUES('delete', old.rowid, old.id, old.endpoint, old.status_code, old.full_payload); "
				+ "  INSERT INTO session_history_fts(rowid, id, endpoint, status_code, full_payload) "
				+ "  VALUES (new.rowid, new.id, new.endpoint, new.status_code, new.full_payload); " + "END;");
	}

	public void save(String id, String endpoint, int statusCode, String fullPayload) {
		jdbcTemplate.update("INSERT INTO session_history (id, endpoint, status_code, full_payload) VALUES (?, ?, ?, ?)",
				id, endpoint, statusCode, fullPayload);
	}

	public void updateImprovedTitle(String id, String improvedTitle) {
		jdbcTemplate.update("UPDATE session_history SET improved_title = ? WHERE id = ?", improvedTitle, id);
	}

	public List<Map<String, Object>> search(String query) {
		if (query == null || query.trim().isEmpty()) {
			return jdbcTemplate.queryForList("SELECT * FROM session_history ORDER BY created_at DESC LIMIT 50");
		}

		// Escape the query for FTS MATCH by wrapping in double quotes and escaping
		// inner quotes
		String escapedQuery = "\"" + query.replace("\"", "\"\"") + "\"";

		return jdbcTemplate
				.queryForList(
						"SELECT sh.* FROM session_history sh " + "JOIN session_history_fts fts ON sh.rowid = fts.rowid "
								+ "WHERE session_history_fts MATCH ? " + "ORDER BY sh.created_at DESC LIMIT 50",
						escapedQuery);
	}

	public void deleteById(String id) {
		jdbcTemplate.update("DELETE FROM session_history WHERE id = ?", id);
	}

	public void deleteByIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return;
		}
		String inSql = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
		jdbcTemplate.update(String.format("DELETE FROM session_history WHERE id IN (%s)", inSql), ids.toArray());
	}

	public void deleteAll() {
		jdbcTemplate.update("DELETE FROM session_history");
	}

	public List<Map<String, Object>> findByIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		String inSql = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
		return jdbcTemplate.queryForList(
				String.format("SELECT id, full_payload FROM session_history WHERE id IN (%s)", inSql), ids.toArray());
	}
}
