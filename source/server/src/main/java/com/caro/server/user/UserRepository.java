package com.caro.server.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Atomic "rating = rating + delta" so concurrent updates never overwrite each other. */
    @Modifying
    @Query("update User u set u.rating = u.rating + :delta where u.id = :id")
    int addRating(@Param("id") long id, @Param("delta") int delta);
}
