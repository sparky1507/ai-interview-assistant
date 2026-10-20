package com.harsh.interviewassistant.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDocumentRepository extends JpaRepository<UserDocument, UUID> {

    List<UserDocument> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Ownership is part of the lookup: another user's document is simply "not found". */
    Optional<UserDocument> findByIdAndUserId(UUID id, UUID userId);
}
