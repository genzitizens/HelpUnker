package com.helpunker.helprequest.repository;

import com.helpunker.helprequest.domain.HelpRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, UUID>,
        JpaSpecificationExecutor<HelpRequest> {
}
