package com.caro.server.match;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caro.server.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class MatchPersistenceService {

    @PersistenceContext
    private EntityManager em;

    private final UserRepository users;

    public MatchPersistenceService(UserRepository users) {
        this.users = users;
    }

    /**
     * Stores the finished match and applies the rating changes in one transaction:
     * either everything is saved or nothing is.
     */
    @Transactional
    public MatchRecord save(GameMatch match) {
        MatchRecord record = MatchRecord.of(match);
        // persist (not merge): the id is assigned by us, and a second insert with the same id must fail
        em.persist(record);
        if (record.getResult() != MatchResultType.ABORTED) {
            users.addRating(record.getPlayerXId(), record.getRatingChangeX());
            users.addRating(record.getPlayerOId(), record.getRatingChangeO());
        }
        return record;
    }
}
