package com.example.quanlymuahang.sharedkernel.application;

import com.example.quanlymuahang.sharedkernel.web.RequestIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Service
public class AuditRecorder {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public AuditRecorder(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Transactional
    public void record(Long actorId, String action, String entityType, Long entityId, Object safeAfterState) {
        String afterJson;
        try { afterJson = safeAfterState == null ? null : mapper.writeValueAsString(safeAfterState); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Không thể ghi dữ liệu audit", exception); }
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,after_json,request_id,created_at) VALUES (?,?,?,?,?,?,?)",
                actorId, action, entityType, entityId, afterJson, MDC.get(RequestIdFilter.MDC_KEY), Timestamp.from(Instant.now()));
    }
}
