package com.techenglish.vocabulary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VocabEntryRepository extends JpaRepository<VocabEntry, Long> {
    Optional<VocabEntry> findByLemmaIgnoreCase(String lemma);

    List<VocabEntry> findAllByOrderByIdAsc();
}
