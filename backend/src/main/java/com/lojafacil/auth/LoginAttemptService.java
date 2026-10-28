package com.lojafacil.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.lojafacil.shared.error.ApiException;
import org.springframework.http.HttpStatus;

@Service
public class LoginAttemptService {
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public void checkAllowed(String... keys) {
        Instant now = Instant.now();
        for (String key : keys) {
            Deque<Instant> q = failures.get(key);
            if (q == null) {
                continue;
            }
            synchronized (q) {
                prune(q, now);
                if (q.size() >= MAX_FAILURES) {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                            "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
                }
            }
        }
    }

    public void recordFailure(String... keys) {
        Instant now = Instant.now();
        for (String key : keys) {
            Deque<Instant> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (q) {
                prune(q, now);
                q.addLast(now);
            }
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    @Scheduled(fixedDelay = 600_000)
    void cleanup() {
        Instant now = Instant.now();
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }

    private static void prune(Deque<Instant> q, Instant now) {
        while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(WINDOW))) {
            q.pollFirst();
        }
    }
}
