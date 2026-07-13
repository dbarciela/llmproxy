package io.github.dbarciela.aura.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.dbarciela.aura.db.SessionHistoryRepository;
import io.github.dbarciela.aura.pipeline.LlmTitleService;

@RestController
@RequestMapping("/api/proxy")
public class ArchiveController {

	private final SessionHistoryRepository repository;
	private final LlmTitleService llmTitleService;
	private final JdbcTemplate jdbcTemplate;

	public ArchiveController(SessionHistoryRepository repository,
			LlmTitleService llmTitleService,
			JdbcTemplate jdbcTemplate) {
		this.repository = repository;
		this.llmTitleService = llmTitleService;
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/archive")
	public List<Map<String, Object>> searchArchive(@RequestParam(required = false) String query) {
		return repository.search(query);
	}

	@PostMapping("/archive/title")
	public Map<String, String> generateTitle(@RequestBody List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			throw new RuntimeException("No sessions provided");
		}

		// Fetch full_payload from DB for the first ID (which should be representative)
		String idForPayload = ids.get(ids.size() - 1); // oldest session usually has the base prompt
		List<Map<String, Object>> results = jdbcTemplate
				.queryForList("SELECT full_payload FROM session_history WHERE id = ?", idForPayload);
		if (results.isEmpty()) {
			// fallback
			idForPayload = ids.get(0);
			results = jdbcTemplate.queryForList("SELECT full_payload FROM session_history WHERE id = ?", idForPayload);
			if (results.isEmpty()) {
				throw new RuntimeException("Session not found");
			}
		}
		String payload = (String) results.get(0).get("full_payload");

		String improvedTitle = llmTitleService.generateTitle(payload);

		for (String id : ids) {
			repository.updateImprovedTitle(id, improvedTitle);
		}

		return Map.of("improved_title", improvedTitle);
	}

	@DeleteMapping("/archive/{id}")
	public void deleteSession(@PathVariable String id) {
		repository.deleteById(id);
	}

	@DeleteMapping("/archive/bulk")
	public void deleteSessions(@RequestBody List<String> ids) {
		repository.deleteByIds(ids);
	}

	@DeleteMapping("/archive/all")
	public void deleteAllSessions() {
		repository.deleteAll();
	}

	@PostMapping("/archive/cleanup")
	public Map<String, Object> cleanupRedundant(@RequestBody List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return Map.of("deleted", List.of());
		}

		List<Map<String, Object>> sessions = repository.findByIds(ids);
		if (sessions.isEmpty()) {
			return Map.of("deleted", List.of());
		}

		ObjectMapper mapper = new ObjectMapper();
		List<SessionData> parsed = new ArrayList<>();

		for (Map<String, Object> row : sessions) {
			String id = (String) row.get("id");
			String fullPayload = (String) row.get("full_payload");
			if (fullPayload == null) {
				continue;
			}

			try {
				String[] parts = fullPayload.split("RESPONSE:\n");
				if (parts.length < 2) {
					continue;
				}
				String reqStr = parts[0].replace("REQUEST:\n", "").trim();

				JsonNode root = mapper.readTree(reqStr);
				JsonNode messages = root.path("messages");

				if (messages.isArray()) {
					parsed.add(new SessionData(id, messages.size(), reqStr));
				}
			} catch (Exception e) {
				// Ignore parse errors
			}
		}

		parsed.sort(Comparator.comparingInt(a -> a.messageCount));

		List<String> toDelete = new ArrayList<>();

		for (int i = 0; i < parsed.size(); i++) {
			SessionData a = parsed.get(i);
			boolean isRedundant = false;
			for (int j = i + 1; j < parsed.size(); j++) {
				SessionData b = parsed.get(j);

				boolean isSubset = true;
				try {
					JsonNode aRoot = mapper.readTree(a.rawReq);
					JsonNode bRoot = mapper.readTree(b.rawReq);
					JsonNode aMsgs = aRoot.path("messages");
					JsonNode bMsgs = bRoot.path("messages");

					if (aMsgs.size() >= bMsgs.size()) {
						isSubset = false;
					} else {
						for (int k = 0; k < aMsgs.size(); k++) {
							String aContent = aMsgs.get(k).path("content").asText();
							String bContent = bMsgs.get(k).path("content").asText();
							if (!aContent.equals(bContent)) {
								isSubset = false;
								break;
							}
						}
					}
				} catch (Exception e) {
					isSubset = false;
				}

				if (isSubset) {
					isRedundant = true;
					break;
				}
			}
			if (isRedundant) {
				toDelete.add(a.id);
			}
		}

		if (!toDelete.isEmpty()) {
			repository.deleteByIds(toDelete);
		}

		return Map.of("deleted", toDelete);
	}

	private static class SessionData {
		String id;
		int messageCount;
		String rawReq;

		SessionData(String id, int messageCount, String rawReq) {
			this.id = id;
			this.messageCount = messageCount;
			this.rawReq = rawReq;
		}
	}
}
