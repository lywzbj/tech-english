package com.techenglish.question;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, Long> {

    List<QuestionAttempt> findTop30ByUserIdAndDomainAndTopicIdOrderByIdDesc(Long userId, String domain,
                                                                            Long topicId);

    /** Keys answered correctly at least once. Monotonic (attempts are never deleted),
     *  so a pool "exhausted" on this set stays exhausted between draw and submit. */
    @Query("""
            select distinct a.questionKey from QuestionAttempt a
            where a.userId = :userId and a.domain = :domain and a.topicId = :topicId and a.correct = true
            """)
    Set<String> findCorrectQuestionKeys(@Param("userId") Long userId, @Param("domain") String domain,
                                        @Param("topicId") Long topicId);

    /** Keys answered wrongly at least once — the candidate set for wrong-answer
     *  re-practice in the boss paper. Also monotonic. */
    @Query("""
            select distinct a.questionKey from QuestionAttempt a
            where a.userId = :userId and a.domain = :domain and a.topicId = :topicId and a.correct = false
            """)
    Set<String> findWrongQuestionKeys(@Param("userId") Long userId, @Param("domain") String domain,
                                      @Param("topicId") Long topicId);

    /** Wrong-answer count for one question: the "这题错过几次" signal. */
    @Query("""
            select a.questionKey, count(a.id) from QuestionAttempt a
            where a.userId = :userId and a.domain = :domain and a.correct = false
            group by a.questionKey
            """)
    List<Object[]> wrongCountsByDomain(@Param("userId") Long userId, @Param("domain") String domain);

    List<QuestionAttempt> findByUserIdAndDomainAndQuestionKeyIn(Long userId, String domain,
                                                                List<String> questionKeys);
}
