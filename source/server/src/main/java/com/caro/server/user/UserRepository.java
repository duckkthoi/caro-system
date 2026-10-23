package com.caro.server.user;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Atomic "rating = rating + delta" so concurrent updates never overwrite each other. */
    @Modifying
    @Query("update User u set u.rating = u.rating + :delta where u.id = :id")
    int addRating(@Param("id") long id, @Param("delta") int delta);

    /** Changes only the status column, so a rating update running at the same time is not overwritten. */
    @Transactional
    @Modifying
    @Query("update User u set u.status = :status where u.id = :id")
    int updateStatus(@Param("id") long id, @Param("status") UserStatus status);

    /** Leaderboard rows. */
    Page<User> findByRoleAndStatus(Role role, UserStatus status, Pageable pageable);

    /** Rank of a rating = number of players strictly above it + 1. */
    long countByRoleAndStatusAndRatingGreaterThan(Role role, UserStatus status, int rating);

    long countByStatus(UserStatus status);

    @Query("""
            select u from User u
            where lower(u.username) like lower(concat('%', :q, '%'))
               or lower(u.displayName) like lower(concat('%', :q, '%'))
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);
}
