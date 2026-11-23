package dev.ledgerflow.persistence;

import dev.ledgerflow.domain.JournalEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    @EntityGraph(attributePaths = "postings")
    Optional<JournalEntry> findWithPostingsById(UUID id);

    boolean existsByReversesEntryId(UUID reversesEntryId);
}
