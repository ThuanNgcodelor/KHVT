package com.example.quanlymuahang.identity;

import com.example.quanlymuahang.identity.infrastructure.security.SessionRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.ResolvableType;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SessionRevocationServiceTest {
    @SuppressWarnings("unchecked")
    private final FindByIndexNameSessionRepository<MapSession> repository = mock(FindByIndexNameSessionRepository.class);

    @Test
    void revokeAllDeletesEverySessionReturnedForTheAccount() {
        when(repository.findByPrincipalName("employee@example.test"))
                .thenReturn(Map.of("session-a", new MapSession(), "session-b", new MapSession()));

        service(repository).revokeAll("employee@example.test");

        verify(repository).findByPrincipalName("employee@example.test");
        verify(repository).deleteById("session-a");
        verify(repository).deleteById("session-b");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void passwordChangeKeepsOnlyTheCurrentSession() {
        when(repository.findByPrincipalName("employee@example.test"))
                .thenReturn(Map.of("current-session", new MapSession(), "other-session", new MapSession()));

        service(repository).revokeExcept("employee@example.test", "current-session");

        verify(repository).findByPrincipalName("employee@example.test");
        verify(repository).deleteById("other-session");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void unknownPreservedSessionDoesNotKeepAnotherSession() {
        when(repository.findByPrincipalName("employee@example.test"))
                .thenReturn(Map.of("other-session", new MapSession()));

        service(repository).revokeExcept("employee@example.test", "missing-session");

        verify(repository).findByPrincipalName("employee@example.test");
        verify(repository).deleteById("other-session");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void missingPrincipalDoesNotQueryOrDeleteSessions() {
        SessionRevocationService service = service(repository);

        service.revokeAll(null);
        service.revokeExcept("  ", "current-session");

        verifyNoInteractions(repository);
    }

    @Test
    void servletSessionOnlyTestContextDoesNotRequireRedis() {
        service(null).revokeAll("employee@example.test");
    }

    private SessionRevocationService service(FindByIndexNameSessionRepository<MapSession> repository) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        if (repository != null) beans.registerSingleton("sessionRepository", repository);
        return new SessionRevocationService(beans.getBeanProvider(
                ResolvableType.forClass(FindByIndexNameSessionRepository.class)));
    }
}
