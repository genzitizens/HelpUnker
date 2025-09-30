package com.helpunker.modules.helprequest.domain.repository;

import com.helpunker.modules.helprequest.domain.model.HelpRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, UUID>,
        JpaSpecificationExecutor<HelpRequest> {
}
