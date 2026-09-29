package com.example.board.repository;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.example.board.dto.response.BoardListItemResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.entity.BoardCategory;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class BoardListCacheRepository {

    private static final Logger log = LoggerFactory.getLogger(BoardListCacheRepository.class);
    private static final String VERSION_KEY = "board-list:version";
    private static final String KEY_PREFIX = "board-list:";
    private static final TypeReference<PageResponse<BoardListItemResponse>> RESPONSE_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration ttl;

    public BoardListCacheRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${cache.board-list.enabled}") boolean enabled,
            @Value("${cache.board-list.ttl-seconds}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<PageResponse<BoardListItemResponse>> find(String keyword, BoardCategory category, int page, int size) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(buildKey(keyword, category, page, size));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, RESPONSE_TYPE));
        } catch (Exception e) {
            log.warn("게시글 목록 캐시 조회 실패, DB로 대체합니다", e);
            return Optional.empty();
        }
    }

    public void save(String keyword, BoardCategory category, int page, int size, PageResponse<BoardListItemResponse> response) {
        if (!enabled) {
            return;
        }
        try {
            String value = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(buildKey(keyword, category, page, size), value, ttl);
        } catch (Exception e) {
            log.warn("게시글 목록 캐시 저장 실패", e);
        }
    }

    public void invalidate() {
        try {
            redisTemplate.opsForValue().increment(VERSION_KEY);
        } catch (Exception e) {
            log.warn("게시글 목록 캐시 무효화 실패", e);
        }
    }

    private String buildKey(String keyword, BoardCategory category, int page, int size) {
        return KEY_PREFIX + currentVersion() + ":" + page + ":" + size + ":" + keyword + ":" + category;
    }

    private long currentVersion() {
        try {
            String version = redisTemplate.opsForValue().get(VERSION_KEY);
            return version == null ? 0 : Long.parseLong(version);
        } catch (Exception e) {
            log.warn("게시글 목록 캐시 버전 조회 실패, 기본값(0) 사용", e);
            return 0;
        }
    }
}
