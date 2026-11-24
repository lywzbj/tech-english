package com.techenglish.vocabulary;

import com.techenglish.common.ApiException;
import com.techenglish.plan.LearningEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VocabularyService {

    private final VocabEntryRepository entryRepository;
    private final UserVocabRepository userVocabRepository;
    private final VocabReviewRepository reviewRepository;
    private final SrsScheduler srsScheduler;
    private final LearningEventService learningEventService;

    @Transactional
    public VocabularyDtos.DueResponse due(Long userId, int limit) {
        ensureDeck(userId);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        Instant now = Instant.now();
        long dueCount = userVocabRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, now);
        List<UserVocab> due = userVocabRepository
                .findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                        userId, now, PageRequest.of(0, safeLimit));
        Map<Long, VocabEntry> entries = entryRepository.findAllById(
                        due.stream().map(UserVocab::getVocabId).toList())
                .stream().collect(Collectors.toMap(VocabEntry::getId, Function.identity()));
        List<VocabularyDtos.VocabCard> cards = due.stream()
                .filter(item -> entries.containsKey(item.getVocabId()))
                .map(item -> toCard(item, entries.get(item.getVocabId())))
                .toList();
        return new VocabularyDtos.DueResponse(dueCount, cards);
    }

    @Transactional
    public VocabularyDtos.VocabularyListResponse list(Long userId) {
        ensureDeck(userId);
        List<UserVocab> items = userVocabRepository.findByUserIdOrderByNextReviewAtAsc(userId);
        Map<Long, VocabEntry> entries = entryRepository.findAllById(
                        items.stream().map(UserVocab::getVocabId).toList())
                .stream().collect(Collectors.toMap(VocabEntry::getId, Function.identity()));
        List<VocabularyDtos.VocabCard> cards = items.stream()
                .filter(item -> entries.containsKey(item.getVocabId()))
                .map(item -> toCard(item, entries.get(item.getVocabId())))
                .sorted(Comparator.comparing(VocabularyDtos.VocabCard::status)
                        .thenComparing(VocabularyDtos.VocabCard::lemma))
                .toList();
        long due = userVocabRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, Instant.now());
        return new VocabularyDtos.VocabularyListResponse(cards.size(), due, cards);
    }

    @Transactional
    public VocabularyDtos.ReviewResponse review(Long userId, Long userVocabId, int rating) {
        if (rating < 0 || rating > 3) {
            throw ApiException.badRequest("INVALID_RATING", "评分必须是 0 到 3");
        }
        UserVocab item = userVocabRepository.findByIdAndUserId(userVocabId, userId)
                .orElseThrow(() -> ApiException.notFound("VOCAB_NOT_FOUND", "生词不存在"));
        int intervalBefore = item.getIntervalDays();
        srsScheduler.apply(item, rating);
        item.setStatus(srsScheduler.status(item, rating));
        item.setLastReviewedAt(Instant.now());
        item.setNextReviewAt(Instant.now().plus(item.getIntervalDays(), ChronoUnit.DAYS));
        userVocabRepository.save(item);

        VocabReview review = new VocabReview();
        review.setUserId(userId);
        review.setUserVocabId(item.getId());
        review.setRating(rating);
        review.setIntervalBefore(intervalBefore);
        review.setIntervalAfter(item.getIntervalDays());
        review.setReviewedAt(Instant.now());
        reviewRepository.save(review);

        learningEventService.record(userId, "VOCAB_REVIEW", "USER_VOCAB", item.getId(),
                Map.of("rating", rating, "intervalDays", item.getIntervalDays()));

        long remaining = userVocabRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, Instant.now());
        return new VocabularyDtos.ReviewResponse(
                item.getId(),
                item.getNextReviewAt().toString(),
                item.getIntervalDays(),
                item.getStatus().name(),
                remaining
        );
    }

    @Transactional
    public VocabularyDtos.VocabCard add(Long userId, VocabularyDtos.AddVocabRequest request) {
        VocabEntry entry = entryRepository.findByLemmaIgnoreCase(request.lemma().trim())
                .orElseThrow(() -> ApiException.notFound("WORD_NOT_FOUND",
                        "词库中暂时没有这个词，先在 AI 解释里查看释义"));
        UserVocab item = userVocabRepository.findByUserIdAndVocabId(userId, entry.getId())
                .orElseGet(() -> {
                    UserVocab created = new UserVocab();
                    created.setUserId(userId);
                    created.setVocabId(entry.getId());
                    created.setStatus(VocabStatus.NEW);
                    created.setNextReviewAt(Instant.now());
                    return created;
                });
        if (request.contextSentence() != null && !request.contextSentence().isBlank()) {
            item.setContextSentence(request.contextSentence().trim());
        }
        item = userVocabRepository.save(item);
        learningEventService.record(userId, "VOCAB_ADDED", "VOCAB", entry.getId(),
                Map.of("lemma", entry.getLemma()));
        return toCard(item, entry);
    }

    @Transactional(readOnly = true)
    public VocabularyDtos.LookupResponse lookup(Long userId, String lemma) {
        VocabEntry entry = entryRepository.findByLemmaIgnoreCase(lemma.trim())
                .orElseThrow(() -> ApiException.notFound("WORD_NOT_FOUND", "词库中暂时没有这个词"));
        boolean inDeck = userVocabRepository.findByUserIdAndVocabId(userId, entry.getId()).isPresent();
        return new VocabularyDtos.LookupResponse(
                entry.getId(), entry.getLemma(), entry.getPos(), entry.getPhonetic(),
                entry.getDefinitionEn(), entry.getDefinitionZh(), entry.getExampleEn(),
                entry.getExampleZh(), entry.getCategory(), entry.getDomain(), inDeck
        );
    }

    @Transactional
    public void ensureDeck(Long userId) {
        if (userVocabRepository.countByUserId(userId) > 0) {
            return;
        }
        List<VocabEntry> entries = entryRepository.findAllByOrderByIdAsc().stream().limit(30).toList();
        if (entries.isEmpty()) {
            return;
        }
        List<UserVocab> deck = new ArrayList<>();
        for (VocabEntry entry : entries) {
            UserVocab item = new UserVocab();
            item.setUserId(userId);
            item.setVocabId(entry.getId());
            item.setStatus(VocabStatus.NEW);
            item.setNextReviewAt(Instant.now());
            deck.add(item);
        }
        userVocabRepository.saveAll(deck);
    }

    private VocabularyDtos.VocabCard toCard(UserVocab item, VocabEntry entry) {
        return new VocabularyDtos.VocabCard(
                item.getId(),
                entry.getId(),
                entry.getLemma(),
                entry.getPos(),
                entry.getPhonetic(),
                entry.getDefinitionEn(),
                entry.getDefinitionZh(),
                entry.getExampleEn(),
                entry.getExampleZh(),
                item.getContextSentence(),
                item.getStatus().name(),
                item.getIntervalDays(),
                item.getNextReviewAt() == null ? null : item.getNextReviewAt().toString()
        );
    }
}
