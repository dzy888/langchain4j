package dev.langchain4j.rag.query.router;

import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy.FAIL;
import static dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy.ROUTE_TO_ALL;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class LanguageModelQueryRouterTest {

    @Mock
    ContentRetriever catArticlesRetriever;

    @Mock
    ContentRetriever dogArticlesRetriever;

    @Test
    void should_route_to_single_retriever() {

        // given
        Query query = Query.from("Do Labradors shed?");

        // LinkedHashMap is used to ensure a predictable order in the test
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("2");

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactly(dogArticlesRetriever);

        assertThat(model.userMessageText()).isEqualTo(
                """
                Based on the user query, determine the most suitable data source(s) \
                to retrieve relevant information from the following options:
                1: articles about cats
                2: articles about dogs
                It is very important that your answer consists of either a single number \
                or multiple numbers separated by commas and nothing else!
                User query: Do Labradors shed?""");
    }

    @Test
    void should_route_to_single_retriever_builder() {

        // given
        Query query = Query.from("Do Labradors shed?");

        // LinkedHashMap is used to ensure a predictable order in the test
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("2");

        QueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(model)
                .retrieverToDescription(retrieverToDescription)
                .build();

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactly(dogArticlesRetriever);

        assertThat(model.userMessageText()).isEqualTo(
                """
                Based on the user query, determine the most suitable data source(s) \
                to retrieve relevant information from the following options:
                1: articles about cats
                2: articles about dogs
                It is very important that your answer consists of either a single number \
                or multiple numbers separated by commas and nothing else!
                User query: Do Labradors shed?""");
    }

    @Test
    void should_route_to_multiple_retrievers() {

        // given
        Query query = Query.from("Which animal is the fluffiest?");

        Map<ContentRetriever, String> retrieverToDescription = new HashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("1, 2");

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactlyInAnyOrder(catArticlesRetriever, dogArticlesRetriever);
    }

    @Test
    void should_route_to_multiple_retrievers_with_custom_prompt_template() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from(
                "Which source should I use to get answer for '{{query}}'? " +
                        "Options: {{options}}'"
        );

        Query query = Query.from("Which animal is the fluffiest?");

        // LinkedHashMap is used to ensure a predictable order in the test
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("1, 2");

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription, promptTemplate, FAIL);

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactlyInAnyOrder(catArticlesRetriever, dogArticlesRetriever);

        assertThat(model.userMessageText()).isEqualTo("""
                Which source should I use to get answer for \
                'Which animal is the fluffiest?'? \
                Options: \
                1: articles about cats
                2: articles about dogs'""");
    }

    @Test
    void should_not_route_by_default_when_LLM_returns_invalid_response() {

        // given
        Query query = Query.from("Hey what's up?");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Sorry, I don't know");

        final var retrieverToDescription = Map.of(
            catArticlesRetriever, "articles about cats",
            dogArticlesRetriever, "articles about dogs"
        );

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).isEmpty();
    }

    @Test
    void should_not_route_by_default_when_LLM_call_fails() {

        // given
        Query query = Query.from("Hey what's up?");

        ChatModelMock model = ChatModelMock.thatAlwaysThrowsException();

        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).isEmpty();
    }

    @Test
    void should_route_to_all_retrievers_when_LLM_returns_invalid_response() {

        // given
        Query query = Query.from("Hey what's up?");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Sorry, I don't know");
        FallbackStrategy fallbackStrategy = ROUTE_TO_ALL;

        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        QueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(model)
                .retrieverToDescription(retrieverToDescription)
                .fallbackStrategy(fallbackStrategy)
                .build();

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactlyInAnyOrder(catArticlesRetriever, dogArticlesRetriever);
    }

    @Test
    void should_route_to_all_retrievers_when_LLM_call_fails() {

        // given
        Query query = Query.from("Hey what's up?");
        ChatModelMock model = ChatModelMock.thatAlwaysThrowsException();
        FallbackStrategy fallbackStrategy = ROUTE_TO_ALL;

        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");


        QueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(model)
                .retrieverToDescription(retrieverToDescription)
                .fallbackStrategy(fallbackStrategy)
                .build();

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactlyInAnyOrder(catArticlesRetriever, dogArticlesRetriever);
    }

    @Test
    void should_fail_when_LLM_returns_invalid_response() {

        // given
        Query query = Query.from("Hey what's up?");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Sorry, I don't know");
        FallbackStrategy fallbackStrategy = FAIL;

        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        QueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(model)
                .retrieverToDescription(retrieverToDescription)
                .fallbackStrategy(fallbackStrategy)
                .build();

        // when-then
        assertThatThrownBy(() -> router.route(query))
                .hasRootCauseExactlyInstanceOf(NumberFormatException.class);
    }

    @Test
    void should_fail_when_LLM_call_fails() {

        // given
        Query query = Query.from("Hey what's up?");
        ChatModelMock model = ChatModelMock.thatAlwaysThrowsExceptionWithMessage("Something went wrong");
        FallbackStrategy fallbackStrategy = FAIL;

        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        QueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(model)
                .retrieverToDescription(retrieverToDescription)
                .fallbackStrategy(fallbackStrategy)
                .build();

        // when-then
        assertThatThrownBy(() -> router.route(query))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("Something went wrong");
    }

    @Test
    void should_support_Deepseek_in_ollama_remove_thinking_tags_success(){
        //DeepSeek model answer
        String choices ="<think>\n" +
                "Okay, I need to figure out which data source is best for the user's query. The user asked, \"今天新增了哪些商品\" which translates to \"What new products have been added today?\" \n" +
                "\n" +
                "Looking at the options, there are two knowledge bases available: 订单知识库 (Order Knowledge Base) and 商品知识库 (Product Knowledge Base). \n" +
                "\n" +
                "The Order Knowledge Base would likely contain information related to orders, such as purchase history, order status, customer details, etc. It's focused on transactions rather than product listings or updates.\n" +
                "\n" +
                "On the other hand, the Product Knowledge Base is more suited for storing and managing product data. This includes product names, descriptions, specifications, prices, availability, and any updates regarding new products added to the inventory.\n" +
                "\n" +
                "Since the user is specifically asking about newly added items today, they are interested in product information rather than order details. Therefore, the most appropriate source to retrieve this information would be 商品知识库 (Product Knowledge Base).\n" +
                "</think>\n" +
                "\n" +
                "2";
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("2");

        LanguageModelQueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);
        router.addFilterRouter(new FilterRouter() {
            @Override
            public String doFilter(String response) {
                return response.replaceAll("(?si)<think\\b[^>]*>.*?</think>", "");
            }

            @Override
            public int getOrder() {
                return 0;
            }
        });
        String test = router.filterRouterChain.doFilter(choices);
        router.parse(test);
    }

    @Test
    void should_support_Deepseek_in_ollama_remove_thinking_tags_success2(){
        //DeepSeek model answer
        String choices ="<think>\n" +
                "Okay, I need to figure out which data source is best for the user's query. The user asked, \"今天新增了哪些商品\" which translates to \"What new products have been added today?\" \n" +
                "\n" +
                "Looking at the options, there are two knowledge bases available: 订单知识库 (Order Knowledge Base) and 商品知识库 (Product Knowledge Base). \n" +
                "\n" +
                "The Order Knowledge Base would likely contain information related to orders, such as purchase history, order status, customer details, etc. It's focused on transactions rather than product listings or updates.\n" +
                "\n" +
                "On the other hand, the Product Knowledge Base is more suited for storing and managing product data. This includes product names, descriptions, specifications, prices, availability, and any updates regarding new products added to the inventory.\n" +
                "\n" +
                "Since the user is specifically asking about newly added items today, they are interested in product information rather than order details. Therefore, the most appropriate source to retrieve this information would be 商品知识库 (Product Knowledge Base).\n" +
                "</think>\n" +
                "\n" +
                "2";
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("2");

        LanguageModelQueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);
        List<FilterRouter> filterRouterList = new ArrayList<>();
        filterRouterList.add(new FilterRouter() {
            @Override
            public String doFilter(String response) {
                return response.replaceAll("(?si)<think\\b[^>]*>.*?</think>", "");
            }

            @Override
            public int getOrder() {
                return 0;
            }
        });
        filterRouterList.add(new FilterRouter() {
            @Override
            public String doFilter(final String response) {
                return response.replace("1","");
            }

            @Override
            public int getOrder() {
                return 2;
            }
        });
        router.filterRouterChain.addFilters(filterRouterList);
        String test = router.filterRouterChain.doFilter(choices);
        router.parse(test);
    }

    @Test
    void should_support_Deepseek_in_ollama_remove_thinking_tags_failed(){
        //DeepSeek model answer
        String choices ="<think>\n" +
                "Okay, I need to figure out which data source is best for the user's query. The user asked, \"今天新增了哪些商品\" which translates to \"What new products have been added today?\" \n" +
                "\n" +
                "Looking at the options, there are two knowledge bases available: 订单知识库 (Order Knowledge Base) and 商品知识库 (Product Knowledge Base). \n" +
                "\n" +
                "The Order Knowledge Base would likely contain information related to orders, such as purchase history, order status, customer details, etc. It's focused on transactions rather than product listings or updates.\n" +
                "\n" +
                "On the other hand, the Product Knowledge Base is more suited for storing and managing product data. This includes product names, descriptions, specifications, prices, availability, and any updates regarding new products added to the inventory.\n" +
                "\n" +
                "Since the user is specifically asking about newly added items today, they are interested in product information rather than order details. Therefore, the most appropriate source to retrieve this information would be 商品知识库 (Product Knowledge Base).\n" +
                "</think>\n" +
                "\n" +
                "2";
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("2");

        LanguageModelQueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);
        router.parse(choices);
    }
}
