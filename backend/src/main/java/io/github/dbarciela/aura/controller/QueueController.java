package io.github.dbarciela.aura.controller;

import io.github.dbarciela.aura.pipeline.RequestContext;
import io.github.dbarciela.aura.pipeline.plugins.ManualEditorPlugin;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
