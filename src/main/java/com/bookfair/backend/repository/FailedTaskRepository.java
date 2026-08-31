package com.bookfair.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.bookfair.backend.model.FailedTask;
import com.bookfair.backend.model.enums.FailedTaskStatus;

import org.springframework.data.repository.query.Param;

@Repository
public interface FailedTaskRepository extends JpaRepository<FailedTask, UUID>{

    @Query("SELECT t FROM FailedTask t WHERE t.status IN :statuses AND (t.retryAfter IS NULL OR t.retryAfter <= :now) ORDER BY t.retryAfter ASC NULLS FIRST")
    List<FailedTask> findRetryableTasks(@Param("statuses") List<FailedTaskStatus> statuses, @Param("now") Instant now);

    List<FailedTask> findByReferenceIdAndStatus(UUID referenceId, FailedTaskStatus status);

    long countByStatus(FailedTaskStatus status);

}
