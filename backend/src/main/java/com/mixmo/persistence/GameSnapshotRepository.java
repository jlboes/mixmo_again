package com.mixmo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSnapshotRepository extends JpaRepository<GameSnapshotEntity, String> {
}

