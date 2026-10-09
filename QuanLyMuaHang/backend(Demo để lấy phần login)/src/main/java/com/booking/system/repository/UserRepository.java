package com.booking.system.repository;

import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import com.booking.system.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository extends JpaRepository<User, String> {
    @EntityGraph(attributePaths = "department")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "department")
    Optional<User> findById(String id);

    boolean existsByEmail(String email);

    List<User> findByRole(RoleEnum role);
    List<User> findByRoleIn(List<RoleEnum> roles);

    @EntityGraph(attributePaths = {"registrationReviewedBy"})
    Page<User> findByStatusOrderByCreatedAtDesc(UserStatus status, Pageable pageable);

    long countByStatus(UserStatus status);

    @EntityGraph(attributePaths = "department")
    @Query("""
            select u from User u
            where (:query is null
                or lower(u.email) like lower(concat('%', :query, '%'))
                or lower(u.fullName) like lower(concat('%', :query, '%')))
              and (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
            order by u.createdAt desc
            """)
    Page<User> searchForAdmin(
            @Param("query") String query,
            @Param("role") RoleEnum role,
            @Param("status") UserStatus status,
            Pageable pageable);
}
