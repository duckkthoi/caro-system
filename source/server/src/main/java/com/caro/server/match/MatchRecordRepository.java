package com.caro.server.match;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, UUID> {
}
