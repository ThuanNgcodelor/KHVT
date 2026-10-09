package com.example.quanlymuahang.identity.infrastructure.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

@Service
public class SessionRevocationService {
    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositories;
    public SessionRevocationService(ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositories) { this.repositories = repositories; }

    public void revokeAll(String principalName) { revokeExcept(principalName, null); }

    public void revokeExcept(String principalName, String preservedSessionId) {
        if (principalName == null || principalName.isBlank()) return;
        FindByIndexNameSessionRepository<? extends Session> repository = repositories.getIfAvailable();
        if (repository == null) return;
        repository.findByPrincipalName(principalName).keySet().stream()
                .filter(sessionId -> !sessionId.equals(preservedSessionId))
                .forEach(repository::deleteById);
    }
}
