package com.techenglish.vocabulary;

import com.techenglish.plan.LearningEventService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VocabularyServiceTest {

    private final VocabEntryRepository entryRepository = mock(VocabEntryRepository.class);
    private final UserVocabRepository userVocabRepository = mock(UserVocabRepository.class);
    private final VocabReviewRepository reviewRepository = mock(VocabReviewRepository.class);
    private final SrsScheduler srsScheduler = mock(SrsScheduler.class);
    private final LearningEventService learningEventService = mock(LearningEventService.class);
    private final VocabularyService service = new VocabularyService(
            entryRepository, userVocabRepository, reviewRepository, srsScheduler, learningEventService);

    @Test
    void createsReadingSourcedEntryForWordOutsideDictionary() {
        when(entryRepository.findByLemmaIgnoreCase("idempotency")).thenReturn(Optional.empty());
        when(entryRepository.save(any(VocabEntry.class))).thenAnswer(invocation -> {
            VocabEntry entry = invocation.getArgument(0);
            entry.setId(99L);
            return entry;
        });
        when(userVocabRepository.findByUserIdAndVocabId(1L, 99L)).thenReturn(Optional.empty());
        when(userVocabRepository.save(any(UserVocab.class))).thenAnswer(invocation -> {
            UserVocab item = invocation.getArgument(0);
            item.setId(7L);
            return item;
        });

        VocabularyDtos.VocabCard card = service.add(1L, new VocabularyDtos.AddVocabRequest(
                "idempotency", "An operation is idempotent if calling it twice has the same effect.",
                "幂等性", "", null));

        assertThat(card.userVocabId()).isEqualTo(7L);
        assertThat(card.vocabId()).isEqualTo(99L);
        assertThat(card.lemma()).isEqualTo("idempotency");
        assertThat(card.definitionZh()).isEqualTo("幂等性");
        assertThat(card.contextSentence())
                .isEqualTo("An operation is idempotent if calling it twice has the same effect.");

        ArgumentCaptor<VocabEntry> saved = ArgumentCaptor.forClass(VocabEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo("READING");
        assertThat(saved.getValue().getDomain()).isEqualTo("READING");
        assertThat(saved.getValue().getCategory()).isEqualTo("GENERAL");
        verify(learningEventService).record(eq(1L), eq("VOCAB_ADDED"), eq("VOCAB"), eq(99L),
                eq(Map.of("lemma", "idempotency")));
    }

    @Test
    void keepsCuratedDefinitionWhenWordExistsInDictionary() {
        VocabEntry seeded = new VocabEntry();
        seeded.setId(5L);
        seeded.setLemma("request");
        seeded.setDefinitionEn("A message sent to a server.");
        seeded.setDefinitionZh("请求");
        when(entryRepository.findByLemmaIgnoreCase("request")).thenReturn(Optional.of(seeded));
        when(userVocabRepository.findByUserIdAndVocabId(1L, 5L)).thenReturn(Optional.empty());
        when(userVocabRepository.save(any(UserVocab.class))).thenAnswer(invocation -> {
            UserVocab item = invocation.getArgument(0);
            item.setId(8L);
            return item;
        });

        VocabularyDtos.VocabCard card = service.add(1L, new VocabularyDtos.AddVocabRequest(
                "request", null, "客户端发来的请求", "", "n."));

        assertThat(card.definitionZh()).isEqualTo("请求");
        assertThat(card.definitionEn()).isEqualTo("A message sent to a server.");
        verify(entryRepository, never()).save(any(VocabEntry.class));
    }
}
