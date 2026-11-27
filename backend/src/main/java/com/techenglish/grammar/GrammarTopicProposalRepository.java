package com.techenglish.grammar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrammarTopicProposalRepository extends JpaRepository<GrammarTopicProposal, Long> {

    List<GrammarTopicProposal> findByStatusAndDeletedOrderByCreatedAtDesc(String status, int deleted);
}
