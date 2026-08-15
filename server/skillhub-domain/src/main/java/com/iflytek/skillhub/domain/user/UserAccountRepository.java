package com.iflytek.skillhub.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for user-account identity lookups and administrative searches.
 */
public interface UserAccountRepository {
    Optional<UserAccount> findById(String id);
    List<UserAccount> findByIdIn(List<String> ids);
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    Page<UserAccount> search(String keyword, UserStatus status, Pageable pageable);

    /**
     * Lists accounts in one status.
     *
     * <p>Separate from {@link #search} on purpose: that query compares the keyword with
     * {@code lower(...)}, and passing a null keyword leaves PostgreSQL to infer the bind type as
     * {@code bytea}, which fails with "function lower(bytea) does not exist". Callers that want
     * every account in a status have no keyword to give, so they get a query without one.
     */
    Page<UserAccount> findByStatus(UserStatus status, Pageable pageable);

    UserAccount save(UserAccount user);
}
