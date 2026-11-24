package com.techenglish.seed;

import java.util.List;

public final class GrammarSeedData {

    private GrammarSeedData() {
    }

    public record ExampleSeed(String sentence, String translation, String note) {
    }

    public record ExerciseSeed(String id, String type, String prompt, List<String> options,
                               String answer, String explanation) {
    }

    public record TopicSeed(
            String slug,
            String title,
            String world,
            int worldOrder,
            String cefrLevel,
            String summary,
            List<String> prerequisites,
            String concept,
            String howTo,
            String mistake,
            List<ExampleSeed> examples,
            List<ExerciseSeed> exercises
    ) {
        public String contentMd() {
            return """
                    ## 核心规则

                    %s

                    ## 怎么用

                    %s

                    ## 中国学习者常见错误

                    %s

                    > 学习建议：先读例句，再做下面的练习。错题会自动进入你的复习队列。
                    """.formatted(concept, howTo, mistake);
        }
    }

    private static ExampleSeed ex(String sentence, String translation, String note) {
        return new ExampleSeed(sentence, translation, note);
    }

    private static ExerciseSeed q(String id, String prompt, List<String> options,
                                  String answer, String explanation) {
        return new ExerciseSeed(id, "MCQ", prompt, options, answer, explanation);
    }

    public static List<TopicSeed> topics() {
        return List.of(
                new TopicSeed(
                        "sentence-basics", "句子成分与基本语序", "句子地基", 1, "A1",
                        "先找主语和谓语，写出完整英语句子。",
                        List.of(),
                        "英语句子通常必须有主语和谓语。中文可以省略主语，英语不能。技术写作里优先用“谁做什么”的主动结构。",
                        "先找动作的发出者（主语），再找动作或状态（谓语）。例如 The service starts automatically 中，The service 是主语，starts 是谓语。",
                        "把 Because... 当成完整句子，或者写出 The API returning data 这种没有谓语的片段。",
                        List.of(
                                ex("The service starts automatically after deployment.",
                                        "服务在部署后自动启动。", "主语 The service，谓语 starts。"),
                                ex("The API returns a JSON response.",
                                        "这个接口返回 JSON 响应。", "技术写作中最常见的“主语 + 及物动词 + 宾语”结构。"),
                                ex("The cache is empty because the application just restarted.",
                                        "缓存是空的，因为应用刚刚重启。", "because 从句不能单独成句。")
                        ),
                        List.of(
                                q("q1", "The application ____ every night at 2 a.m.",
                                        List.of("run", "runs", "running"), "runs",
                                        "主语 The application 是第三人称单数，一般现在时动词加 -s。"),
                                q("q2", "Which of these is a complete sentence?",
                                        List.of("Because the server crashed.", "The server crashed.", "After the deployment."),
                                        "The server crashed.", "只有第二项同时有主语和谓语。"),
                                q("q3", "Choose the best translation: 这个接口返回用户信息。",
                                        List.of("The API returns user information.", "The API returning user information.",
                                                "Because the API returns user information."),
                                        "The API returns user information.", "英语主句需要谓语；returning 不能单独作谓语。")
                        )
                ),
                new TopicSeed(
                        "subject-verb-agreement", "主谓一致", "句子地基", 1, "A2",
                        "谓语和真正的主语保持一致，小心被介词短语干扰。",
                        List.of("sentence-basics"),
                        "谓语动词要和主语在人称和数上一致。真正的主语可能被 of/in/with 等介词短语藏在后面。",
                        "先把介词短语划掉，再找核心名词。The number of users is growing 中，核心主语是 The number，不是 users。",
                        "写成 The list of items are...；这里核心主语是单数 The list，应该用 is。",
                        List.of(
                                ex("The number of active users is increasing.",
                                        "活跃用户数量正在增长。", "核心主语是 The number。"),
                                ex("A number of users are reporting the issue.",
                                        "一些用户正在报告这个问题。", "a number of 表示“一些”，谓语用复数。"),
                                ex("Each service has its own configuration.",
                                        "每个服务都有自己的配置。", "each 作主语时按单数处理。")
                        ),
                        List.of(
                                q("q1", "The number of active users ____ increasing.",
                                        List.of("is", "are", "have"), "is",
                                        "The number 是单数，users 只是介词短语的一部分。"),
                                q("q2", "Each service ____ its own configuration.",
                                        List.of("has", "have", "are"), "has", "each + 单数名词，谓语用单数。"),
                                q("q3", "The logs and metrics ____ stored separately.",
                                        List.of("is", "are", "was"), "are", "and 连接两个主语，谓语用复数。")
                        )
                ),
                new TopicSeed(
                        "articles", "冠词 a / an / the", "句子地基", 1, "A2",
                        "首次提到用 a/an，双方已知用 the，泛指复数常用零冠词。",
                        List.of("subject-verb-agreement"),
                        "a/an 表示一个不确定的、首次提到的可数名词；the 表示双方都知道的对象或独一无二的对象；复数或不可数名词泛指时通常不加冠词。",
                        "第一次说 a new service，第二次说 the service。技术文档中，the 常指前文已经定义过的组件。",
                        "说 a feedback / an information；feedback 和 information 通常不可数，不能直接加 a/an。",
                        List.of(
                                ex("We deployed a new service yesterday. The service is running now.",
                                        "我们昨天部署了一个新服务。这个服务现在运行正常。", "第一次用 a，第二次用 the。"),
                                ex("The information in the logs helped us find the bug.",
                                        "日志里的信息帮我们找到了 bug。", "information 不可数，用 the 限定范围。"),
                                ex("She is an engineer on our team.",
                                        "她是我们团队的一名工程师。", "engineer 以元音音素开头，用 an。")
                        ),
                        List.of(
                                q("q1", "We deployed ____ new service yesterday. ____ service is running now.",
                                        List.of("a / The", "the / A", "a / A"), "a / The",
                                        "首次提到用 a，再次提到用 the。"),
                                q("q2", "____ information in the logs helped us find the bug.",
                                        List.of("A", "An", "The"), "The",
                                        "information 不可数；这里特指日志中的信息，用 The。"),
                                q("q3", "She is ____ engineer on our team.",
                                        List.of("a", "an", "the"), "an", "engineer 以元音音素开头。")
                        )
                ),
                new TopicSeed(
                        "plural-nouns", "名词单复数与不可数名词", "句子地基", 1, "A2",
                        "可数名词注意复数，不可数名词不要加 -s。",
                        List.of("articles"),
                        "可数名词复数通常加 -s/-es；不规则复数需要单独记；information、feedback、software 等通常不可数。",
                        "技术写作里常把不可数名词和可数名词混用。需要计数时用 pieces of feedback、items of information。",
                        "写出 softwares、informations、feedbacks；这些词通常保持原形。",
                        List.of(
                                ex("We received three pieces of feedback about the new feature.",
                                        "我们收到了三条关于新功能的反馈。", "feedback 不可数，用 pieces of 计数。"),
                                ex("All changes must be reviewed before release.",
                                        "所有变更在发布前都必须经过评审。", "change 是可数名词，这里用复数。"),
                                ex("The index can slow down writes if it is not designed well.",
                                        "如果设计不好，索引可能拖慢写入。", "index 的复数是 indexes 或 indices。")
                        ),
                        List.of(
                                q("q1", "We received three ____ about the new feature.",
                                        List.of("feedbacks", "pieces of feedback", "feedback"), "pieces of feedback",
                                        "feedback 通常不可数，计数时用 pieces of feedback。"),
                                q("q2", "All ____ must be reviewed before release.",
                                        List.of("change", "changes", "changings"), "changes",
                                        "change 是可数名词，这里表示多个变更。"),
                                q("q3", "This table needs one more ____.",
                                        List.of("index", "indexes", "indices"), "index",
                                        "one more 后面接单数名词。")
                        )
                ),
                new TopicSeed(
                        "prepositions", "介词 in / on / at 与常见搭配", "句子地基", 1, "A2",
                        "时间、地点和固定搭配分开记，不要逐字翻译。",
                        List.of("plural-nouns"),
                        "介词表达时间、地点、方式和关系。in 用于较大范围、月份、年份；on 用于具体日期和表面；at 用于具体时间点。",
                        "把介词和名词一起记成块：depend on、in production、at 9 a.m.、on Monday。",
                        "说 in Monday、at 2026、depend of；这些是中文直译造成的搭配错误。",
                        List.of(
                                ex("The deployment is scheduled for Monday at 9 a.m.",
                                        "部署安排在周一上午九点。", "具体日期用 on，但这里用 for 表示安排。"),
                                ex("We store the data in MySQL.",
                                        "我们把数据存在 MySQL 里。", "in 表示容器或范围。"),
                                ex("This service depends on the cache.",
                                        "这个服务依赖缓存。", "depend on 是固定搭配。")
                        ),
                        List.of(
                                q("q1", "The meeting is ____ Monday ____ 9 a.m.",
                                        List.of("in / at", "on / at", "at / on"), "on / at",
                                        "具体日期用 on，具体时间点用 at。"),
                                q("q2", "The application depends ____ a healthy database.",
                                        List.of("of", "on", "with"), "on", "depend on 是固定搭配。"),
                                q("q3", "We deployed the fix ____ 2026.",
                                        List.of("on", "at", "in"), "in", "年份前用 in。")
                        )
                ),
                new TopicSeed(
                        "present-simple", "一般现在时", "时态与情态", 2, "A2",
                        "习惯、事实、规则和固定安排，用一般现在时。",
                        List.of("prepositions"),
                        "一般现在时表示习惯、事实、规则和固定安排；第三人称单数动词加 -s。",
                        "描述系统行为、配置规则、日常流程时常用一般现在时：The service starts automatically. The scheduler runs every hour.",
                        "第三人称单数漏加 -s：He go to work；或者把经常性动作写成进行时。",
                        List.of(
                                ex("The scheduler runs a job every hour.",
                                        "调度器每小时运行一个任务。", "主语单数，动词加 -s。"),
                                ex("The API returns data in JSON format.",
                                        "这个接口以 JSON 格式返回数据。", "描述系统的一般行为。"),
                                ex("Our team does code reviews every Friday.",
                                        "我们团队每周五做代码评审。", "do 的第三人称单数是 does。")
                        ),
                        List.of(
                                q("q1", "The scheduler ____ a job every hour.",
                                        List.of("run", "runs", "is running"), "runs", "第三人称单数加 -s。"),
                                q("q2", "Which sentence describes a general fact?",
                                        List.of("The API returned data yesterday.", "The API returns data in JSON format.",
                                                "The API is returning data now."),
                                        "The API returns data in JSON format.", "一般现在时描述一般事实。"),
                                q("q3", "Our team ____ code reviews every Friday.",
                                        List.of("do", "does", "is doing"), "does", "our team 视为单数，用 does。")
                        )
                ),
                new TopicSeed(
                        "past-simple", "一般过去时", "时态与情态", 2, "A2",
                        "已经结束的过去动作，用一般过去时。",
                        List.of("present-simple"),
                        "一般过去时表示已经结束的过去动作或状态；常与 yesterday、last week、in 2024 等时间连用。",
                        "技术复盘里常用过去时描述已经发生的事件：The service crashed at 8 p.m. We rolled back the release.",
                        "和明确过去时间连用现在完成时：I have deployed it yesterday；应改为 I deployed it yesterday。",
                        List.of(
                                ex("We fixed the bug yesterday.",
                                        "我们昨天修复了这个 bug。", "yesterday 明确指向过去。"),
                                ex("She checked the logs before the incident.",
                                        "她在事故前检查了日志。", "check 的过去式是 checked。"),
                                ex("The team rolled back the release last night.",
                                        "团队昨晚回滚了发布。", "roll back 的过去式是 rolled back。")
                        ),
                        List.of(
                                q("q1", "We ____ the bug yesterday.",
                                        List.of("fix", "fixed", "have fixed"), "fixed", "yesterday 要求一般过去时。"),
                                q("q2", "She ____ the logs before the incident.",
                                        List.of("checked", "checks", "is checking"), "checked", "描述过去已完成的动作。"),
                                q("q3", "Which time marker fits the past simple?",
                                        List.of("since Monday", "last Monday", "for two days"), "last Monday",
                                        "last Monday 是明确的过去时间。")
                        )
                ),
                new TopicSeed(
                        "present-perfect", "现在完成时", "时态与情态", 2, "B1",
                        "过去发生，但结果或经验与现在有关。",
                        List.of("past-simple"),
                        "现在完成时连接过去和现在：动作发生在过去，但结果或经验与现在有关。结构是 have/has + 过去分词。",
                        "常与 for、since、already、yet、just 连用。描述“到目前为止”的状态：We have run this service since 2023.",
                        "和明确过去时间连用：I have deployed it yesterday；应改为 I deployed it yesterday。",
                        List.of(
                                ex("We have run this service since 2023.",
                                        "我们从 2023 年起一直运行这个服务。", "since + 起点。"),
                                ex("She hasn't finished the documentation yet.",
                                        "她还没有完成文档。", "yet 常用于否定句和疑问句。"),
                                ex("The team has just released a new version.",
                                        "团队刚刚发布了一个新版本。", "just 表示刚刚完成，结果与现在有关。")
                        ),
                        List.of(
                                q("q1", "We ____ this service since 2023.",
                                        List.of("run", "ran", "have run"), "have run", "since 2023 表示持续到现在。"),
                                q("q2", "She ____ just finished the documentation.",
                                        List.of("has", "have", "is"), "has",
                                        "现在完成时结构是 have/has + 过去分词；主语 She 用 has。"),
                                q("q3", "Which sentence is correct?",
                                        List.of("I have seen that movie yesterday.", "I saw that movie yesterday.",
                                                "I have saw that movie yesterday."),
                                        "I saw that movie yesterday.", "yesterday 是明确过去时间，用一般过去时。")
                        )
                ),
                new TopicSeed(
                        "future-forms", "将来表达 will / be going to / 现在进行时", "时态与情态", 2, "B1",
                        "临时决定、计划、安排和时刻表，用不同的将来表达。",
                        List.of("present-perfect"),
                        "will 表示临时决定或预测；be going to 表示已有计划或明显迹象；现在进行时表示已安排的近期活动；一般现在时表示时刻表。",
                        "看语境选：It will probably work（预测）；We are going to migrate（计划）；I am meeting the client at 3（安排）；The train leaves at 8（时刻表）。",
                        "所有将来都用 will，会让表达显得不自然；已安排的事情更常用现在进行时。",
                        List.of(
                                ex("The release starts at 10 a.m. tomorrow according to the schedule.",
                                        "根据时间表，发布会在明天上午十点开始。", "时刻表用一般现在时。"),
                                ex("Look at the error rate! It is going to fail.",
                                        "看错误率！它要挂了。", "有迹象的预测用 be going to。"),
                                ex("I am meeting the client tomorrow at 3.",
                                        "我明天三点见客户。", "已安排的近期活动用现在进行时。")
                        ),
                        List.of(
                                q("q1", "The release ____ at 10 a.m. tomorrow according to the schedule.",
                                        List.of("starts", "is starting", "will start"), "starts",
                                        "按时刻表发生的动作可用一般现在时。"),
                                q("q2", "Look at the error rate! It ____ to fail.",
                                        List.of("will", "is going to", "is"), "is going to",
                                        "有明显迹象的预测用 be going to。"),
                                q("q3", "Which form best expresses a fixed arrangement?",
                                        List.of("I will meet the client at 3.", "I am meeting the client at 3.",
                                                "I meet the client at 3."), "I am meeting the client at 3.",
                                        "已经安排好的近期活动用现在进行时。")
                        )
                ),
                new TopicSeed(
                        "modals", "情态动词", "时态与情态", 2, "B1",
                        "用 can / may / must / should 表达能力、可能、义务和建议。",
                        List.of("future-forms"),
                        "情态动词表达能力、许可、义务、建议和确定性；后面接动词原形。",
                        "must 表示强义务或高确定；have to 表示外部要求；should 表示建议；may/might 表示可能；can 表示能力或允许。",
                        "写出 He must to go / You should to check；情态动词后直接接动词原形。",
                        List.of(
                                ex("You should back up the database before migrating.",
                                        "迁移前你应该备份数据库。", "should + 动词原形，表示建议。"),
                                ex("The logs might be incomplete; we need more evidence.",
                                        "日志可能不完整，我们需要更多证据。", "might 表示可能性。"),
                                ex("You must restart the service after changing the configuration.",
                                        "修改配置后必须重启服务。", "must 表示强义务。")
                        ),
                        List.of(
                                q("q1", "You ____ back up the database before migrating.",
                                        List.of("should", "should to", "are should"), "should",
                                        "情态动词后直接接动词原形。"),
                                q("q2", "The logs ____ be incomplete; we need more evidence.",
                                        List.of("might", "must to", "can to"), "might", "might 表示可能。"),
                                q("q3", "Which sentence expresses strong obligation?",
                                        List.of("You could restart the service.", "You must restart the service.",
                                                "You might restart the service."),
                                        "You must restart the service.", "must 表示强义务。")
                        )
                ),
                new TopicSeed(
                        "relative-clauses", "定语从句", "复杂句", 3, "B1",
                        "用 who / which / that / whose / where 修饰名词。",
                        List.of("modals"),
                        "定语从句用来修饰名词。who 指人，which 指物，that 可指人或物，whose 表示所属，where 表示地点。",
                        "定义性从句不用逗号；非定义性从句用逗号补充信息，并且不能用 that。",
                        "指物时用 who；或者在非定义性从句里用 that。",
                        List.of(
                                ex("The engineer who fixed the bug is on call tonight.",
                                        "修复这个 bug 的工程师今晚值班。", "who 指人，作从句主语。"),
                                ex("The service, which was deployed yesterday, is stable.",
                                        "这个服务是昨天部署的，目前很稳定。", "非定义性从句用逗号，不能用 that。"),
                                ex("This is the database that stores user profiles.",
                                        "这就是存储用户资料的数据库。", "that 指物，在从句中作主语。")
                        ),
                        List.of(
                                q("q1", "The engineer ____ fixed the bug is on call tonight.",
                                        List.of("which", "who", "whose"), "who", "先行词是人，用 who。"),
                                q("q2", "The service, ____ was deployed yesterday, is stable.",
                                        List.of("that", "which", "who"), "which",
                                        "非定义性定语从句不能用 that，指物用 which。"),
                                q("q3", "This is the database ____ stores user profiles.",
                                        List.of("who", "where", "that"), "that", "从句缺少主语，指物用 that。")
                        )
                ),
                new TopicSeed(
                        "noun-clauses", "名词性从句", "复杂句", 3, "B1",
                        "that / whether / what 引导的从句可以作主语、宾语或表语。",
                        List.of("relative-clauses"),
                        "名词性从句在句子中充当名词成分。that 引导陈述，whether/if 引导是否，what 引导“什么”。从句必须用陈述语序。",
                        "注意语序：I don't know what the problem is，而不是 what is the problem。",
                        "把从句写成疑问句语序：I don't know what is the problem。",
                        List.of(
                                ex("I don't know whether the deployment succeeded.",
                                        "我不知道部署是否成功。", "whether 引导宾语从句。"),
                                ex("Why the service failed is still unclear.",
                                        "服务为什么失败仍然不清楚。", "Why 从句作主语。"),
                                ex("Please tell me what the API returns.",
                                        "请告诉我这个接口返回什么。", "what 从句用陈述语序。")
                        ),
                        List.of(
                                q("q1", "I don't know ____ the deployment succeeded.",
                                        List.of("whether", "that", "what"), "whether", "表示“是否”用 whether。"),
                                q("q2", "____ the service failed is still unclear.",
                                        List.of("Why", "Because", "Which"), "Why", "Why 从句可以作主语，表示原因不明。"),
                                q("q3", "Please tell me ____ the API returns.",
                                        List.of("what", "that", "whether"), "what", "what 从句作 tell 的宾语。")
                        )
                ),
                new TopicSeed(
                        "conditionals", "条件句", "复杂句", 3, "B2",
                        "根据真实程度选择零、第一、第二或第三条件句。",
                        List.of("noun-clauses"),
                        "零条件：一般现在时 + 一般现在时；第一条件：if + 一般现在时, will + 动词原形；第二条件：if + 过去时, would + 动词原形；第三条件：if + had done, would have done。",
                        "先判断事情是事实、可能、假设还是与过去相反，再选结构。if 从句里通常不用 will。",
                        "If I will have time, I will...；if 从句用一般现在时表示将来。",
                        List.of(
                                ex("If the health check fails, the deployment will roll back.",
                                        "如果健康检查失败，部署会回滚。", "第一条件句，真实可能。"),
                                ex("If I had more time, I would write more tests.",
                                        "如果我有更多时间，我会写更多测试。", "第二条件句，与现在事实相反。"),
                                ex("If we had monitored the latency, we would have noticed the issue earlier.",
                                        "如果我们当时监控了延迟，就会更早发现问题。", "第三条件句，与过去事实相反。")
                        ),
                        List.of(
                                q("q1", "If the health check fails, the deployment ____.",
                                        List.of("will roll back", "rolled back", "would roll back"), "will roll back",
                                        "第一条件句：if + 一般现在时，主句用 will。"),
                                q("q2", "If I ____ more time, I would write more tests.",
                                        List.of("have", "had", "will have"), "had", "第二条件句用过去时表示与现在相反。"),
                                q("q3", "If we had monitored the latency, we ____ the issue earlier.",
                                        List.of("would notice", "would have noticed", "noticed"), "would have noticed",
                                        "第三条件句：if + had done，主句用 would have done。")
                        )
                ),
                new TopicSeed(
                        "passive-voice", "被动语态", "复杂句", 3, "B1",
                        "be + 过去分词，强调动作承受者或执行者不重要。",
                        List.of("conditionals"),
                        "被动语态结构是 be + 过去分词；强调动作承受者、不知道执行者或执行者不重要时使用。",
                        "技术文档常用来描述流程：The database is backed up every night. The issue was detected by monitoring.",
                        "把被动写成 be + 动词原形，或者在不必要时滥用 by someone。",
                        List.of(
                                ex("The database is backed up every night.",
                                        "数据库每晚都会备份。", "is backed up 是一般现在时被动。"),
                                ex("The issue was detected by the monitoring system.",
                                        "这个问题被监控系统发现了。", "was detected 是一般过去时被动。"),
                                ex("The configuration must be updated before restart.",
                                        "重启前必须更新配置。", "情态动词 + be + 过去分词。")
                        ),
                        List.of(
                                q("q1", "The database ____ every night.",
                                        List.of("backs up", "is backed up", "is backing up"), "is backed up",
                                        "数据库是动作承受者，用被动。"),
                                q("q2", "The issue ____ by the monitoring system.",
                                        List.of("was detected", "detected", "was detecting"), "was detected",
                                        "问题被检测到，用过去被动。"),
                                q("q3", "Which sentence is passive?",
                                        List.of("The team deployed the service.", "The service was deployed.",
                                                "The service runs."),
                                        "The service was deployed.", "be + 过去分词构成被动。")
                        )
                ),
                new TopicSeed(
                        "non-finite", "非谓语动词", "复杂句", 3, "B2",
                        "to do / doing / done 不能单独作谓语，但能承担多种句子成分。",
                        List.of("passive-voice"),
                        "非谓语动词包括 to do、doing、done；可以作主语、宾语、定语、状语，不能单独作谓语。",
                        "记住常见搭配：need to check、finish running、a deployed service。先判断动作是不是句子的主要谓语。",
                        "写出 I want go / After deploy, we...；to 和动名词不能随意省略。",
                        List.of(
                                ex("We need to check the logs before restarting.",
                                        "重启前我们需要检查日志。", "need to do 是固定搭配。"),
                                ex("Refactoring the service improved latency.",
                                        "重构这个服务改善了延迟。", "动名词作主语。"),
                                ex("The team finished running the migration.",
                                        "团队完成了迁移的运行。", "finish doing 是固定搭配。")
                        ),
                        List.of(
                                q("q1", "We need ____ the logs before restarting.",
                                        List.of("check", "to check", "checking"), "to check", "need to do。"),
                                q("q2", "____ the service improved latency.",
                                        List.of("Refactor", "Refactoring", "Refactored"), "Refactoring",
                                        "动名词作主语。"),
                                q("q3", "The team finished ____ the migration.",
                                        List.of("to run", "running", "run"), "running", "finish doing。")
                        )
                )
        );
    }
}
