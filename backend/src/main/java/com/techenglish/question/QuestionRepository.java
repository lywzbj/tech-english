package com.techenglish.question;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /** Grammar pools for one topic; insertion order keeps seed questions ahead of AI rows. */
    List<Question> findByDomainAndTopicIdAndDeletedOrderByIdAsc(String domain, Long topicId, Integer deleted);

    /** Static reading questions of one article, in display order. */
    List<Question> findByDomainAndArticleIdAndDeletedOrderBySortOrderAsc(String domain, Long articleId,
                                                                         Integer deleted);

    /** One self-test paper: the AI rows generated for a quiz set. */
    List<Question> findByDomainAndQuizSetIdOrderBySortOrderAsc(String domain, Long quizSetId);
}
