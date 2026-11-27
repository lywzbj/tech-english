package com.techenglish.seed;

import com.techenglish.question.QuestionType;
import com.techenglish.reading.ReadingSourceType;

import java.util.List;

public final class ReadingSeedData {

    private ReadingSeedData() {
    }

    public record QuestionSeed(
            QuestionType type,
            String prompt,
            List<String> options,
            String answer,
            String explanation,
            String paragraphRef
    ) {
    }

    public record ArticleSeed(
            String title,
            String subtitle,
            ReadingSourceType sourceType,
            String cefrLevel,
            String summary,
            List<String> topics,
            String contentMd,
            List<QuestionSeed> questions
    ) {
    }

    private static QuestionSeed mcq(String prompt, List<String> options, String answer,
                                    String explanation, String paragraphRef) {
        return new QuestionSeed(QuestionType.MCQ, prompt, options, answer, explanation, paragraphRef);
    }

    private static QuestionSeed tf(String prompt, String answer, String explanation, String paragraphRef) {
        return new QuestionSeed(QuestionType.TRUE_FALSE, prompt,
                List.of("TRUE", "FALSE", "NOT GIVEN"), answer, explanation, paragraphRef);
    }

    public static List<ArticleSeed> articles() {
        return List.of(
                new ArticleSeed(
                        "The Quiet Shift to Asynchronous Work",
                        "Why fewer meetings do not automatically mean better work",
                        ReadingSourceType.IELTS,
                        "B2",
                        "Asynchronous work can protect focus and cross time zones, but it also makes writing, trust and decision-making more demanding.",
                        List.of("work", "communication", "productivity"),
                        """
                                # The Quiet Shift to Asynchronous Work

                                For many knowledge workers, the working day still begins with a calendar full of meetings. Yet a growing number of teams are experimenting with a different default: asynchronous work. Instead of discussing every issue in a live call, team members write updates, record short explanations, and respond when they can. The goal is not to remove human contact. It is to protect long blocks of concentration and to make decisions visible to people in different time zones.

                                ## What asynchronous work means

                                Asynchronous communication is often described as communication that does not require everyone to be present at the same moment. A shared document, a recorded walkthrough, or a carefully written pull request can all serve this purpose. The advantage is obvious in distributed teams. An engineer in Asia can review a design while a colleague in Europe is asleep, and the discussion can continue without anyone joining a 6 a.m. call. Written records also make it easier for new team members to understand why a decision was made.

                                ## The benefits

                                There are practical benefits beyond scheduling. When a proposal must be written down, vague ideas become easier to challenge. A document can be read at different speeds, revisited later, and linked from future work. For engineers, this is familiar territory: a good design document is often more useful than a meeting that nobody documented. Teams that write well tend to make fewer repeated mistakes because the reasoning is available to everyone.

                                ## The costs

                                However, asynchronous work is not a free upgrade. Decisions can become slower when a conversation moves across several time zones. Isolation is another risk. People miss the small signals that a video call provides, and junior colleagues may find it harder to ask a quick question. Writing also takes effort. A message that is clear to its author may be ambiguous to a reader who lacks the same context. In this sense, asynchronous work transfers the cost of coordination from meetings to writing.

                                ## A management shift

                                Managers face a similar adjustment. In an office, activity is visible: people are at their desks, in meetings, or talking to colleagues. In an asynchronous team, visibility comes from outcomes, documents, and the quality of decisions. This can feel uncomfortable for managers who equate presence with commitment. The most effective teams therefore set clear expectations about response times, decision ownership, and what must be documented. They also keep some synchronous time for relationship building and for problems that genuinely need fast, messy discussion.

                                ## A deliberate mix

                                The likely future is not a world without meetings. It is a more deliberate mix. Routine updates can be written; complex disagreements may still deserve a conversation. The teams that benefit most are those that treat communication as part of the engineering system: they design it, measure it, and improve it. Asynchronous work is not a shortcut to productivity. It is a different set of trade-offs, and it works best when people are willing to write clearly and trust each other.
                                """,
                        List.of(
                                tf("The article claims that asynchronous work is always more productive than meetings.",
                                        "FALSE",
                                        "文章明确说异步工作不是 free upgrade，也有决策变慢、孤立和写作成本，因此不是“总是更高效”。",
                                        "P4"),
                                mcq("What is the main argument of the article?",
                                        List.of("Asynchronous work has real benefits and real costs.",
                                                "All meetings should be eliminated.",
                                                "Remote work is only useful for engineers."),
                                        "Asynchronous work has real benefits and real costs.",
                                        "全文既讲专注、跨时区、文档化收益，也讲决策、孤立和写作成本。",
                                        "P1-P6"),
                                tf("Managers may need to focus more on outcomes than on visible activity.",
                                        "TRUE",
                                        "第五段说明异步团队里可见性来自结果、文档和决策质量，而不是“人在工位”。",
                                        "P5"),
                                tf("The author predicts that every company will adopt asynchronous work within five years.",
                                        "NOT GIVEN",
                                        "文章预测的是更审慎的混合模式，没有给出“所有公司五年内采用”的时间表。",
                                        "P6"),
                                mcq("According to the article, which skill becomes more important in asynchronous teams?",
                                        List.of("Clear writing", "Public speaking", "Running longer meetings"),
                                        "Clear writing",
                                        "第四段指出异步沟通把协调成本转移到写作上，写得清楚变得更重要。",
                                        "P4")
                        )
                ),
                new ArticleSeed(
                        "How a REST API Works",
                        "A practical tour of resources, HTTP methods, and status codes",
                        ReadingSourceType.TECH,
                        "B1",
                        "A REST API exposes resources through predictable URLs and HTTP methods. Understanding requests, responses, status codes and idempotency is the foundation of backend communication.",
                        List.of("API", "HTTP", "backend"),
                        """
                                # How a REST API Works

                                A REST API is a way for two systems to communicate over HTTP. The client sends a request, the server processes it, and the server returns a response. REST is not a specific library or framework. It is a set of conventions for organizing resources and actions in a predictable way.

                                ## Resources and methods

                                In REST, a resource is usually represented by a URL. For example, `/users` might represent the collection of users, while `/users/42` represents one user. The HTTP method describes the action. GET retrieves data, POST creates a new resource, PUT replaces a resource, PATCH updates part of it, and DELETE removes it. A well-designed API keeps URLs focused on nouns and lets methods express the verb.

                                ## A request in practice

                                A request contains more than a URL. It may include headers, a body, and query parameters. Headers often carry authentication information or describe the expected format. The body carries data for methods such as POST or PUT. Query parameters are useful for filtering and pagination, for example `/orders?status=paid&page=2`.

                                ```http
                                GET /api/users/42 HTTP/1.1
                                Host: api.example.com
                                Authorization: Bearer YOUR_TOKEN
                                Accept: application/json
                                ```

                                ## Status codes

                                The server answers with a status code and, usually, a body. A 200 status means the request succeeded. A 201 status often means a resource was created. A 400 status indicates a client error, while a 401 or 403 status indicates an authentication or authorization problem. A 404 status means the resource was not found. A 500 status means the server encountered an unexpected failure. Status codes matter because they let clients react without guessing from the text of a message.

                                ## Idempotency

                                One important concept is idempotency. An operation is idempotent if calling it multiple times has the same effect as calling it once. GET, PUT, and DELETE are usually idempotent; POST is not necessarily idempotent because repeating it may create duplicate resources. This distinction matters when a client retries a request after a timeout. Without idempotency, a retry can create two orders or two payments.

                                ## Documentation

                                Finally, a REST API should be predictable and well documented. Consumers need to know which fields are required, what errors look like, and how pagination works. Good documentation is not an extra feature. It is part of the interface. When an API is easy to predict, teams can integrate it faster and spend less time debugging avoidable misunderstandings.
                                """,
                        List.of(
                                mcq("What does a GET request usually do?",
                                        List.of("Retrieves a resource", "Creates a new resource", "Deletes a resource"),
                                        "Retrieves a resource", "GET 用于读取数据。", "P2"),
                                tf("A 404 status code means the server crashed.",
                                        "FALSE", "404 表示资源未找到，不是服务器崩溃；服务器异常通常是 5xx。", "P5"),
                                mcq("Which HTTP method is commonly used to create a resource?",
                                        List.of("POST", "GET", "DELETE"), "POST",
                                        "POST 常用于创建新资源。", "P2"),
                                mcq("What does idempotent mean in this article?",
                                        List.of("Calling it multiple times has the same effect as calling it once.",
                                                "It always returns the same status code.",
                                                "It cannot be retried."),
                                        "Calling it multiple times has the same effect as calling it once.",
                                        "第六段给出了 idempotency 的定义，并说明它对重试很重要。", "P6")
                        )
                ),
                new ArticleSeed(
                        "Why Database Indexes Matter",
                        "A small structure with a large impact on query performance",
                        ReadingSourceType.TECH,
                        "B1",
                        "An index helps the database find rows without scanning the whole table, but it also costs storage and slows writes. Good indexing is a trade-off, not a blanket rule.",
                        List.of("database", "performance", "index"),
                        """
                                # Why Database Indexes Matter

                                A database index is a data structure that helps the database find rows more quickly. Without an index, a query may need to scan every row in a table. For a small table this is invisible. For a table with millions of rows, it can turn a fast request into a timeout.

                                ## What an index does

                                A useful analogy is the index at the back of a book. Instead of reading every page to find a topic, you look up the topic and jump to the relevant pages. A database index does something similar: it stores a sorted or structured representation of one or more columns so that the database can locate matching rows efficiently.

                                ```sql
                                CREATE INDEX idx_orders_customer
                                ON orders (customer_id, created_at);
                                ```

                                ## Composite indexes

                                The order of columns in a composite index matters. The index above is well suited to queries that filter by `customer_id` and then sort or filter by `created_at`. It is less useful when a query filters only by `created_at`, because the leading column is missing. This is why copying an index from one query to another can be misleading.

                                ## The cost of indexes

                                Indexes are not free. Each index consumes storage and must be updated when rows are inserted, updated, or deleted. A table with many indexes can therefore have slower writes. Indexes also require maintenance and can become fragmented over time. A database administrator must balance read performance against write cost and operational complexity.

                                ## Measure, do not guess

                                Selectivity is another consideration. An index on a column with only two possible values, such as a boolean flag, may not help much if the values are evenly distributed. The database may still choose a full scan because reading the index would not narrow the search enough. Indexes are most valuable when they substantially reduce the number of rows the database must examine.

                                The practical lesson is to measure before and after adding an index. Use the query plan, observe the actual workload, and check whether the index is being used. An index that looks reasonable in theory may be ignored by the optimizer. Performance work is empirical: the goal is not to have more indexes, but to have the right ones for the queries that matter.
                                """,
                        List.of(
                                mcq("What is the main purpose of a database index?",
                                        List.of("To help the database find rows more quickly",
                                                "To encrypt sensitive columns",
                                                "To replace the need for a database schema"),
                                        "To help the database find rows more quickly",
                                        "第一段直接给出定义：索引帮助数据库更快找到行。", "P1"),
                                tf("Adding an index always improves database performance.",
                                        "FALSE",
                                        "第四段说明索引会占存储并拖慢写入，因此不是“总是提升性能”。", "P4"),
                                mcq("When is an index on a boolean column likely to be less useful?",
                                        List.of("When the values are evenly distributed",
                                                "When the column is part of a primary key",
                                                "When the table has only one row"),
                                        "When the values are evenly distributed",
                                        "第五段说布尔列取值少且分布均匀时，索引可能无法有效缩小搜索范围。", "P5"),
                                mcq("What trade-off does the article describe?",
                                        List.of("Faster reads versus slower writes and extra storage",
                                                "Faster writes versus less storage",
                                                "More indexes versus fewer queries"),
                                        "Faster reads versus slower writes and extra storage",
                                        "文章反复强调索引提升读取但增加存储和写入成本。", "P4")
                        )
                ),
                new ArticleSeed(
                        "Code Review as a Conversation",
                        "How review comments become clearer, kinder, and more useful",
                        ReadingSourceType.TECH,
                        "B2",
                        "Code review works best as a technical conversation about risks, clarity and shared ownership, not as a gatekeeping ritual.",
                        List.of("code review", "communication", "engineering"),
                        """
                                # Code Review as a Conversation

                                Code review is often described as a quality gate. A change is proposed, a reviewer examines it, and the change is either approved or sent back. That description is accurate but incomplete. Review is also a conversation about how a team understands the system. The comments left in a pull request shape not only the code but also the team's shared standards.

                                ## Focus on risks

                                The most useful reviews focus on risks and intent. A reviewer might ask why a retry was added, whether a timeout is safe, or whether a migration can run without downtime. These questions are more valuable than personal preferences about formatting, especially when a formatter can enforce the mechanical rules automatically. A review that only says "this is wrong" without explaining the risk is difficult to learn from.

                                ## Keep changes reviewable

                                Small changes make review easier. A pull request that touches one concern can be understood in a single sitting. A pull request that mixes a refactor, a new feature, and an unrelated dependency update forces the reviewer to hold too many contexts at once. When a change is large, asking for a short design note or a walkthrough can be more effective than reading every line in isolation.

                                ## Separate blocking from optional

                                It also helps to separate blocking comments from optional suggestions. A blocking comment should explain the problem and the expected outcome. A non-blocking comment can be labeled as a suggestion or a question. This distinction keeps the author from treating every comment as a demand and helps the team decide what must be fixed before merging.

                                ## Make reasoning visible

                                Good reviewers ask questions before making accusations. "What happens if this call times out?" invites investigation. "You forgot to handle the timeout" invites defensiveness, even when the observation is correct. The goal is not to be polite at all costs. It is to make the technical reasoning visible so that both people can evaluate it.

                                ## A learning loop

                                Finally, review is a learning loop. A repeated comment is a signal that the team may need a guideline, a lint rule, or a design discussion. The best teams treat review comments as data. They look for patterns, update their standards, and reduce the need for the same conversation next time.
                                """,
                        List.of(
                                tf("The article argues that code review is only about finding bugs.",
                                        "FALSE",
                                        "文章说代码评审还是关于系统理解和团队标准的对话，不只是找 bug。", "P1"),
                                mcq("What makes a pull request easier to review?",
                                        List.of("Keeping it focused on one concern",
                                                "Adding as many changes as possible",
                                                "Removing all context from the description"),
                                        "Keeping it focused on one concern",
                                        "第三段建议一个 PR 只处理一个关注点，避免混合重构、新功能和依赖更新。", "P3"),
                                mcq("How should a non-blocking suggestion be handled?",
                                        List.of("Label it as a suggestion or question",
                                                "Treat it as a mandatory change",
                                                "Ignore it completely"),
                                        "Label it as a suggestion or question",
                                        "第四段建议区分 blocking comments 和 optional suggestions。", "P4"),
                                tf("Repeated review comments can reveal a need for better team standards.",
                                        "TRUE",
                                        "最后一段把重复评论视为数据，可能意味着需要 guideline、lint rule 或设计讨论。", "P6")
                        )
                )
        );
    }
}
