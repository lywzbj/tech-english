package com.techenglish.vocabulary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SrsSchedulerTest {

    private final SrsScheduler scheduler = new SrsScheduler();

    @Test
    void firstSuccessfulReviewSchedulesTomorrow() {
        UserVocab item = new UserVocab();

        scheduler.apply(item, 2);

        assertThat(item.getIntervalDays()).isEqualTo(1);
        assertThat(item.getRepetitions()).isEqualTo(1);
        assertThat(scheduler.status(item, 2)).isEqualTo(VocabStatus.REVIEW);
    }

    @Test
    void forgettingResetsTheIntervalAndRecordsALapse() {
        UserVocab item = new UserVocab();
        item.setIntervalDays(12);
        item.setRepetitions(4);

        scheduler.apply(item, 0);

        assertThat(item.getIntervalDays()).isEqualTo(1);
        assertThat(item.getRepetitions()).isZero();
        assertThat(item.getLapses()).isEqualTo(1);
        assertThat(scheduler.status(item, 0)).isEqualTo(VocabStatus.LEARNING);
    }

    @Test
    void repeatedSuccessfulReviewsIncreaseTheInterval() {
        UserVocab item = new UserVocab();

        scheduler.apply(item, 2);
        assertThat(item.getIntervalDays()).isEqualTo(1);

        scheduler.apply(item, 2);
        assertThat(item.getIntervalDays()).isEqualTo(3);

        scheduler.apply(item, 3);
        assertThat(item.getIntervalDays()).isGreaterThan(3);
        assertThat(item.getRepetitions()).isEqualTo(3);
    }
}
