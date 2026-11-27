package com.techenglish.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.grammar.GrammarLesson;
import com.techenglish.grammar.GrammarLessonRepository;
import com.techenglish.grammar.GrammarTopic;
import com.techenglish.grammar.GrammarTopicEdge;
import com.techenglish.grammar.GrammarTopicEdgeRepository;
import com.techenglish.grammar.GrammarTopicRepository;
import com.techenglish.question.Question;
import com.techenglish.question.QuestionRepository;
import com.techenglish.reading.ReadingArticle;
import com.techenglish.reading.ReadingArticleRepository;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Order(10)
@RequiredArgsConstructor
public class ContentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentSeeder.class);

    private final GrammarTopicRepository topicRepository;
    private final GrammarTopicEdgeRepository edgeRepository;
    private final GrammarLessonRepository lessonRepository;
    private final VocabEntryRepository vocabRepository;
    private final ReadingArticleRepository articleRepository;
    private final QuestionRepository questionRepository;
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

    /**
     * Idempotent upsert of the built-in syllabus: new slugs get topic + lesson + edges,
     * existing slugs are only checked for missing prerequisite edges (never overwritten,
     * so local lesson edits survive restarts). This doubles as the JSON-to-edge-table
     * backfill for databases that predate the grammar_topic_edge migration.
     */
    private int seedGrammar() throws Exception {
        Map<String, GrammarTopic> bySlug = topicRepository
                .findByDeletedOrderByWorldOrderAscSortOrderAsc(0).stream()
                .collect(Collectors.toMap(GrammarTopic::getSlug, Function.identity(), (first, dup) -> first));
        int added = 0;
        int order = 0;
        for (GrammarSeedData.TopicSeed seed : GrammarSeedData.topics()) {
            order++;
            GrammarTopic topic = bySlug.get(seed.slug());
            if (topic == null) {
                topic = new GrammarTopic();
                topic.setSlug(seed.slug());
                topic.setTitle(seed.title());
                topic.setWorld(seed.world());
                topic.setWorldOrder(seed.worldOrder());
                topic.setCefrLevel(seed.cefrLevel());
                topic.setSummary(seed.summary());
                topic.setPrerequisites(objectMapper.writeValueAsString(seed.prerequisites()));
                topic.setSortOrder(order);
                topic.setPublished(true);
                topic.setSource("SEED");
                topic = topicRepository.save(topic);

                GrammarLesson lesson = new GrammarLesson();
                lesson.setTopic(topic);
                lesson.setContentMd(seed.contentMd());
                lesson.setExamplesJson(objectMapper.writeValueAsString(seed.examples()));
                lesson.setExercisesJson(objectMapper.writeValueAsString(seed.exercises()));
                lesson.setModel("seed");
                lesson.setPromptVersion("seed-v1");
                lessonRepository.save(lesson);

                // 种子题同时物化进统一题库（exercises_json 保留为存档）；key 方案与 backfill 一致。
                for (GrammarSeedData.ExerciseSeed exercise : seed.exercises()) {
                    questionRepository.save(seedQuestion(topic.getId(), exercise));
                }

                bySlug.put(topic.getSlug(), topic);
                added++;
            }
            seedPrerequisiteEdges(topic, seed, bySlug);
        }
        return added;
    }

    /** Insert seed prerequisite edges that are not in the edge table yet. */
    private void seedPrerequisiteEdges(GrammarTopic topic, GrammarSeedData.TopicSeed seed,
                                       Map<String, GrammarTopic> bySlug) {
        for (String prereqSlug : seed.prerequisites()) {
            GrammarTopic prereq = bySlug.get(prereqSlug);
            if (prereq == null) {
                log.warn("Grammar seed edge skipped: unknown prerequisite '{}' of '{}'",
                        prereqSlug, topic.getSlug());
                continue;
            }
            if (!edgeRepository.existsByUpTopicIdAndDownTopicIdAndDeleted(topic.getId(), prereq.getId(), 0)) {
                edgeRepository.save(GrammarTopicEdge.prerequisite(topic.getId(), prereq.getId()));
            }
        }
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
                Question question = new Question();
                question.setQuestionKey("rq-" + article.getId() + "-" + order);
                question.setDomain(Question.DOMAIN_READING);
                question.setArticleId(article.getId());
                question.setType(questionSeed.type().name());
                question.setPrompt(questionSeed.prompt());
                question.setOptionsJson(objectMapper.writeValueAsString(questionSeed.options()));
                question.setAnswer(questionSeed.answer());
                question.setExplanation(questionSeed.explanation());
                question.setSource("SEED");
                question.setParagraphRef(questionSeed.paragraphRef());
                question.setSortOrder(order++);
                questionRepository.save(question);
            }
            count++;
        }
        return count;
    }

    private Question seedQuestion(Long topicId, GrammarSeedData.ExerciseSeed exercise) throws Exception {
        Question row = new Question();
        row.setQuestionKey(Question.seedKey(topicId, exercise.id()));
        row.setDomain(Question.DOMAIN_GRAMMAR);
        row.setTopicId(topicId);
        row.setType(exercise.type() == null ? "MCQ" : exercise.type());
        row.setPool("PRACTICE");
        row.setPrompt(exercise.prompt());
        row.setOptionsJson(objectMapper.writeValueAsString(
                exercise.options() == null ? List.of() : exercise.options()));
        row.setAnswer(exercise.answer() == null ? "" : exercise.answer());
        row.setExplanation(exercise.explanation() == null ? "" : exercise.explanation());
        row.setOptionNotesJson("[]");
        row.setSource("SEED");
        return row;
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
