package com.example.quanlymuahang.sharedkernel.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class AuditQueryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public AuditQueryService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Transactional(readOnly = true)
    public AuditPage search(String action, String entityType, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(page, 0);
        String normalizedAction = blankToNull(action);
        String normalizedType = blankToNull(entityType);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE (? IS NULL OR action = ?) AND (? IS NULL OR entity_type = ?)",
                Long.class, normalizedAction, normalizedAction, normalizedType, normalizedType);
        List<AuditRecord> records = jdbc.query("SELECT id,actor_user_id,action,entity_type,entity_id,request_id,after_json,created_at FROM audit_logs " +
                        "WHERE (? IS NULL OR action = ?) AND (? IS NULL OR entity_type = ?) ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, row) -> toRecord(rs), normalizedAction, normalizedAction, normalizedType, normalizedType, safeSize, (long) safePage * safeSize);
        return new AuditPage(records, safePage, safeSize, total);
    }

    private AuditRecord toRecord(ResultSet rs) throws SQLException {
        JsonNode after = null;
        String json = rs.getString("after_json");
        if (json != null) try { after = mapper.readTree(json); } catch (Exception ignored) { }
        Timestamp timestamp = rs.getTimestamp("created_at");
        return new AuditRecord(rs.getLong("id"), (Long) rs.getObject("actor_user_id"), rs.getString("action"),
                rs.getString("entity_type"), (Long) rs.getObject("entity_id"), rs.getString("request_id"), after,
                timestamp == null ? null : timestamp.toInstant());
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public record AuditRecord(Long id, Long actorUserId, String action, String entityType, Long entityId,
                              String requestId, JsonNode afterState, Instant createdAt) {}
    public record AuditPage(List<AuditRecord> content, int page, int size, long totalElements) {}
}
