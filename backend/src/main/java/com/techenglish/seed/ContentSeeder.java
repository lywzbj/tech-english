package com.techenglish.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.grammar.GrammarLesson;
import com.techenglish.grammar.GrammarLessonRepository;
import com.techenglish.grammar.GrammarTopic;
import com.techenglish.grammar.GrammarTopicRepository;
import com.techenglish.reading.ReadingArticle;
import com.techenglish.reading.ReadingArticleRepository;
import com.techenglish.reading.ReadingQuestion;
import com.techenglish.reading.ReadingQuestionRepository;
import com.techenglish.reading.ReadingTag;
import com.techenglish.reading.ReadingTagRepository;
import com.techenglish.vocabulary.VocabEntry;
import com.techenglish.vocabulary.VocabEntryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
@RequiredArgsConstructor
public class ContentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentSeeder.class);

    private final GrammarTopicRepository topicRepository;
    private final GrammarLessonRepository lessonRepository;
    private final VocabEntryRepository vocabRepository;
    private final ReadingArticleRepository articleRepository;
    private final ReadingQuestionRepository questionRepository;
    private final ReadingTagRepository tagRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        int topics = seedGrammar();
        int words = seedVocabulary();
        int articles = seedReading();
        if (topics + words + articles > 0) {
            log.info("Seeded content: {} grammar topics, {} vocabulary words, {} reading articles",
                    topics, words, articles);
        }
    }

    private int seedGrammar() throws Exception {
        if (topicRepository.count() > 0) {
            return 0;
        }
        int count = 0;
        for (GrammarSeedData.TopicSeed seed : GrammarSeedData.topics()) {
            GrammarTopic topic = new GrammarTopic();
            topic.setSlug(seed.slug());
            topic.setTitle(seed.title());
            topic.setWorld(seed.world());
            topic.setWorldOrder(seed.worldOrder());
            topic.setCefrLevel(seed.cefrLevel());
            topic.setSummary(seed.summary());
            topic.setPrerequisites(objectMapper.writeValueAsString(seed.prerequisites()));
            topic.setSortOrder(count + 1);
            topic.setPublished(true);
            topic = topicRepository.save(topic);

            GrammarLesson lesson = new GrammarLesson();
            lesson.setTopic(topic);
            lesson.setContentMd(seed.contentMd());
            lesson.setExamplesJson(objectMapper.writeValueAsString(seed.examples()));
            lesson.setExercisesJson(objectMapper.writeValueAsString(seed.exercises()));
            lesson.setModel("seed");
            lesson.setPromptVersion("seed-v1");
            lessonRepository.save(lesson);
            count++;
        }
        return count;
    }

    private int seedVocabulary() {
        if (vocabRepository.count() > 0) {
            return 0;
        }
        int count = 0;
        for (VocabSeedData.VocabSeed seed : VocabSeedData.words()) {
            VocabEntry entry = new VocabEntry();
            entry.setLemma(seed.lemma());
            entry.setPos(seed.pos());
            entry.setPhonetic(seed.phonetic());
            entry.setDefinitionEn(seed.definitionEn());
            entry.setDefinitionZh(seed.definitionZh());
            entry.setExampleEn(seed.exampleEn());
            entry.setExampleZh(seed.exampleZh());
            entry.setCategory(seed.category());
            entry.setDomain(seed.domain());
            entry.setSource("Tech English seed");
            vocabRepository.save(entry);
            count++;
        }
        return count;
    }

    private int seedReading() throws Exception {
        if (articleRepository.count() > 0) {
            return 0;
        }
        int count = 0;
        for (ReadingSeedData.ArticleSeed seed : ReadingSeedData.articles()) {
            ReadingArticle article = new ReadingArticle();
            article.setTitle(seed.title());
            article.setSubtitle(seed.subtitle());
            article.setSourceType(seed.sourceType());
            article.setTagId(tagRepository.findBySourceTypeAndDeleted(seed.sourceType(), 0)
                    .map(ReadingTag::getId)
                    .orElse(null));
            article.setSummary(seed.summary());
            article.setContentMd(seed.contentMd());
            int wordCount = countWords(seed.contentMd());
            article.setWordCount(wordCount);
            article.setCefrLevel(seed.cefrLevel());
            article.setTopicsJson(objectMapper.writeValueAsString(seed.topics()));
            article.setEstimatedMinutes(Math.max(3, (int) Math.ceil(wordCount / 180.0)));
            article = articleRepository.save(article);

            int order = 1;
            for (ReadingSeedData.QuestionSeed questionSeed : seed.questions()) {
                ReadingQuestion question = new ReadingQuestion();
                question.setArticleId(article.getId());
                question.setType(questionSeed.type());
                question.setPrompt(questionSeed.prompt());
                question.setOptionsJson(objectMapper.writeValueAsString(questionSeed.options()));
                question.setAnswer(questionSeed.answer());
                question.setExplanation(questionSeed.explanation());
                question.setParagraphRef(questionSeed.paragraphRef());
                question.setSortOrder(order++);
                questionRepository.save(question);
            }
            count++;
        }
        return count;
    }

    private int countWords(String content) {
        String prose = content.replaceAll("```[\\s\\S]*?```", " ");
        String[] words = prose.replaceAll("[#>*_`\\-\\[\\]()]", " ")
                .trim()
                .split("\\s+");
        int count = 0;
        for (String word : words) {
            if (word.matches(".*[A-Za-z].*")) {
                count++;
            }
        }
        return count;
    }
}
