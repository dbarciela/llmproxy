package io.github.dbarciela.aura.controller;

import io.github.dbarciela.aura.pipeline.LiveChatBroadcaster;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/proxy")
public class LiveChatController {

    private final LiveChatBroadcaster broadcaster;

    public LiveChatController(LiveChatBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping("/live")
    public SseEmitter liveChat() {
        return broadcaster.subscribe();
    }
}
