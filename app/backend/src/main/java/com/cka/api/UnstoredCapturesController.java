package com.cka.api;

import com.cka.provider.capture.CapturedItems;
import com.cka.provider.capture.UnstoredCapture;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meetings that were captured and never stored, and a way to ask for one again.
 *
 * <p>Nothing retries these by itself. A timer would either recapture every meeting each hour
 * while the LLM is switched off, paying for nothing, or give up and lose them; a person knows
 * which of those they are in, so the list is shown to them and the retry is theirs.
 */
@RestController
@RequestMapping("/api/captures/unstored")
@CrossOrigin(origins = "${cka.api.allowed-origin}")
@RequiredArgsConstructor
class UnstoredCapturesController {

    private final CapturedItems captured;

    @GetMapping
    List<UnstoredCapture> list() {
        return captured.unstored();
    }

    /**
     * The title in the body rather than the path: a meeting's name is a person's free text, and
     * a slash in it would be read as another path segment, or refused by Tomcat when encoded.
     */
    @PostMapping("/retry")
    ResponseEntity<Void> retry(@RequestBody Retry request) {
        return captured.retry(request.title())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    record Retry(String title) {}
}
