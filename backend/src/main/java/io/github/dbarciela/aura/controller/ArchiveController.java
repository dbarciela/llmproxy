package io.github.dbarciela.aura.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.dbarciela.aura.db.SessionHistoryRepository;

@RestController
@RequestMapping("/api/proxy")
public class ArchiveController {

	private final SessionHistoryRepository repository;
	private final io.github.dbarciela.aura.pipeline.LlmTitleService llmTitleService;
	private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	public ArchiveController(SessionHistoryRepository repository,
			io.github.dbarciela.aura.pipeline.LlmTitleService llmTitleService,
			org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
		this.repository = repository;
		this.llmTitleService = llmTitleService;
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/archive")
	public List<Map<String, Object>> searchArchive(@RequestParam(required = false) String query) {
		return repository.search(query);
	}

	@org.springframework.web.bind.annotation.PostMapping("/archive/title")
	public Map<String, String> generateTitle(@org.springframework.web.bind.annotation.RequestBody List<String> ids) {
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

	@org.springframework.web.bind.annotation.DeleteMapping("/archive/{id}")
	public void deleteSession(@org.springframework.web.bind.annotation.PathVariable String id) {
		repository.deleteById(id);
	}

	@org.springframework.web.bind.annotation.DeleteMapping("/archive/bulk")
	public void deleteSessions(@org.springframework.web.bind.annotation.RequestBody List<String> ids) {
		repository.deleteByIds(ids);
	}

	@org.springframework.web.bind.annotation.DeleteMapping("/archive/all")
	public void deleteAllSessions() {
		repository.deleteAll();
	}

	@org.springframework.web.bind.annotation.PostMapping("/archive/cleanup")
	public Map<String, Object> cleanupRedundant(@org.springframework.web.bind.annotation.RequestBody List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return Map.of("deleted", List.of());
		}

		List<Map<String, Object>> sessions = repository.findByIds(ids);
		if (sessions.isEmpty()) {
			return Map.of("deleted", List.of());
		}

		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		java.util.List<SessionData> parsed = new java.util.ArrayList<>();

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

				com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(reqStr);
				com.fasterxml.jackson.databind.JsonNode messages = root.path("messages");

				if (messages.isArray()) {
					parsed.add(new SessionData(id, messages.size(), reqStr));
				}
			} catch (Exception e) {
				// Ignore parse errors
			}
		}

		parsed.sort(java.util.Comparator.comparingInt(a -> a.messageCount));

		java.util.List<String> toDelete = new java.util.ArrayList<>();

		for (int i = 0; i < parsed.size(); i++) {
			SessionData a = parsed.get(i);
			boolean isRedundant = false;
			for (int j = i + 1; j < parsed.size(); j++) {
				SessionData b = parsed.get(j);

				boolean isSubset = true;
				try {
					com.fasterxml.jackson.databind.JsonNode aRoot = mapper.readTree(a.rawReq);
					com.fasterxml.jackson.databind.JsonNode bRoot = mapper.readTree(b.rawReq);
					com.fasterxml.jackson.databind.JsonNode aMsgs = aRoot.path("messages");
					com.fasterxml.jackson.databind.JsonNode bMsgs = bRoot.path("messages");

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
