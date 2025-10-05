package com.helpunker.assignment.repository;

import com.helpunker.assignment.Assignment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByRequestId(UUID requestId);
}
