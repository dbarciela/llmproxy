package io.github.dbarciela.aura.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.dbarciela.aura.pipeline.plugins.ManualEditorPlugin;

@RestController
@RequestMapping("/api/proxy")
public class QueueController {

	private final ManualEditorPlugin manualEditorPlugin;

	public QueueController(ManualEditorPlugin manualEditorPlugin) {
		this.manualEditorPlugin = manualEditorPlugin;
	}

	@GetMapping("/queue")
	public List<io.github.dbarciela.aura.pipeline.QueueItemDTO> getQueue() {
		return manualEditorPlugin.getQueue();
	}

	@PostMapping("/release/{requestId}")
	public ResponseEntity<Void> release(@PathVariable String requestId, @RequestBody Map<String, String> body) {
		String payload = body.get("payload");
		manualEditorPlugin.release(requestId, payload);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/drop/{requestId}")
	public ResponseEntity<Void> drop(@PathVariable String requestId) {
		manualEditorPlugin.drop(requestId);
		return ResponseEntity.ok().build();
	}

	@PutMapping("/reorder")
	public ResponseEntity<Void> reorder(@RequestBody List<String> order) {
		manualEditorPlugin.reorder(order);
		return ResponseEntity.ok().build();
	}
}
