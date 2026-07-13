package io.github.dbarciela.aura.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.dbarciela.aura.pipeline.NotificationDTO;
import jakarta.annotation.PostConstruct;

@Repository
public class NotificationRepository {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public NotificationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@PostConstruct
	public void init() {
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS notifications (" + "id TEXT PRIMARY KEY, "
				+ "source_plugin TEXT, " + "title TEXT, " + "message TEXT, " + "level TEXT, " + "actions_json TEXT, "
				+ "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
	}

	public void save(NotificationDTO notification) {
		String actionsJson = "[]";
		try {
			if (notification.getActions() != null) {
				actionsJson = objectMapper.writeValueAsString(notification.getActions());
			}
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

		jdbcTemplate.update(
				"INSERT INTO notifications (id, source_plugin, title, message, level, actions_json) VALUES (?, ?, ?, ?, ?, ?)",
				notification.getId(), notification.getSourcePlugin(), notification.getTitle(),
				notification.getMessage(), notification.getLevel(), actionsJson);
	}

	public List<NotificationDTO> findAll() {
		return jdbcTemplate.query("SELECT * FROM notifications ORDER BY created_at DESC", this::mapRowToNotification);
	}

	public void deleteById(String id) {
		jdbcTemplate.update("DELETE FROM notifications WHERE id = ?", id);
	}

	public boolean existsBySourcePlugin(String sourcePlugin) {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE source_plugin = ?",
				Integer.class, sourcePlugin);
		return count != null && count > 0;
	}

	private NotificationDTO mapRowToNotification(ResultSet rs, int rowNum) throws SQLException {
		NotificationDTO dto = new NotificationDTO();
		dto.setId(rs.getString("id"));
		dto.setSourcePlugin(rs.getString("source_plugin"));
		dto.setTitle(rs.getString("title"));
		dto.setMessage(rs.getString("message"));
		dto.setLevel(rs.getString("level"));

		String actionsJson = rs.getString("actions_json");
		try {
			if (actionsJson != null && !actionsJson.isEmpty()) {
				List<NotificationDTO.NotificationAction> actions = objectMapper.readValue(actionsJson,
						new TypeReference<List<NotificationDTO.NotificationAction>>() {
						});
				dto.setActions(actions);
			} else {
				dto.setActions(Collections.emptyList());
			}
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			dto.setActions(Collections.emptyList());
		}

		return dto;
	}
}
