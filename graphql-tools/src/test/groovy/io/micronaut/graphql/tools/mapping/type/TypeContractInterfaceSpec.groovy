package io.micronaut.graphql.tools.mapping.type

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

/**
 * A GraphQL type mapped to a Java interface that has a bean introspection of its own (the "contract", annotated with
 * {@code @GraphQLType} or {@code @Introspected}) reads its properties through that introspection, from an object of
 * any class that implements the interface.
 */
class TypeContractInterfaceSpec extends AbstractTest {

    static final String UNANNOTATED_IMPLEMENTATIONS = "TypeContractInterfaceSpec.unannotatedImplementations"
    static final String ANNOTATED_IMPLEMENTATIONS = "TypeContractInterfaceSpec.annotatedImplementations"
    static final String INTROSPECTED_CONTRACT = "TypeContractInterfaceSpec.introspectedContract"
    static final String IMPLEMENTATION_ONLY_GETTER = "TypeContractInterfaceSpec.implementationOnlyGetter"
    static final String NO_CONTRACT_INTROSPECTION = "TypeContractInterfaceSpec.noContractIntrospection"
    static final String FIELD_ORDER = "TypeContractInterfaceSpec.fieldOrder"
    static final String INTERFACE_FIELD_ONLY_ON_IMPLEMENTATION =
            "TypeContractInterfaceSpec.interfaceFieldOnlyOnImplementation"

    @Language("GraphQL")
    static final String REVIEW_SCHEMA = """
schema {
  query: Query
}

type Query {
  reviews: [Review!]!
}

type Review {
  text: String!
  rating: Int!
  summary: String!
}
"""

    @Language("GraphQL")
    static final String LINE_ITEM_SCHEMA = """
schema {
  query: Query
}

type Query {
  item: LineItem!
  items: [LineItem!]!
}

type LineItem {
  name: String!
  amount: Int!
}
"""

    @Language("GraphQL")
    static final String TAG_SCHEMA = """
schema {
  query: Query
}

type Query {
  tags: [Tag!]!
}

type Tag {
  label: String!
}
"""

    @Language("GraphQL")
    static final String ARTICLE_SCHEMA = """
schema {
  query: Query
}

type Query {
  article: Article
}

type Article {
  title: String!
  draftNote: String
}
"""

    @Language("GraphQL")
    static final String MEMO_SCHEMA = """
schema {
  query: Query
}

type Query {
  entries: [Entry!]!
}

interface Entry {
  title: String!
  subtitle: String
}

type Memo implements Entry {
  title: String!
  subtitle: String
}
"""

    @Language("GraphQL")
    static final String CHAPTER_SCHEMA = """
schema {
  query: Query
}

type Query {
  chapter: Chapter
}

type Chapter {
  title: String!
  wordCount: Int!
}
"""

    void "two classes without annotations implement one contract interface and are returned from one field"() {
        given:
        startContext(REVIEW_SCHEMA, UNANNOTATED_IMPLEMENTATIONS)

        when:
        def result = executeQuery("{ reviews { text rating summary } }")

        then:
        result.errors.isEmpty()
        result.data.reviews == [
                [text: 'Solid', rating: 4, summary: 'Solid (4)'],
                [text: 'A classic', rating: 5, summary: 'A classic (5)'],
        ]
    }

    void "two classes annotated with @GraphQLType(contract) are returned from one field"() {
        given:
        startContext(LINE_ITEM_SCHEMA, ANNOTATED_IMPLEMENTATIONS)

        when:
        def result = executeQuery("{ item { name amount } items { name amount } }")

        then:
        result.errors.isEmpty()
        result.data.item == [name: 'Paper', amount: 3]
        result.data.items == [[name: 'Paper', amount: 3], [name: 'Ink', amount: 7]]
    }

    void "a contract interface annotated with @Introspected"() {
        given:
        startContext(TAG_SCHEMA, INTROSPECTED_CONTRACT)

        when:
        def result = executeQuery("{ tags { label } }")

        then:
        result.errors.isEmpty()
        result.data.tags == [[label: 'fiction'], [label: 'poetry']]
    }

    void "a getter that only the implementation declares is not a property of the contract"() {
        given:
        startContext(ARTICLE_SCHEMA, IMPLEMENTATION_ONLY_GETTER)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.message == """The property or method `draftNote` not found in ${ArticleGraph.name}'s type. Properties are read from the interface ${ArticleGraph.name}, so a getter that only an implementation class declares is not a property.
  GraphQL coordinate: Article.draftNote (line 12, column 3)
  GraphQL object type: Article
  GraphQL field: draftNote"""
        e.cause.mappingContext.graphQlObjectType == 'Article'
        e.cause.mappingContext.graphQlField == 'draftNote'
    }

    void "a field of an SDL interface that only the implementation of the contract declares is an error"() {
        given:
        startContext(MEMO_SCHEMA, INTERFACE_FIELD_ONLY_ON_IMPLEMENTATION)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.message == """The property or method `subtitle` not found in ${MemoGraph.name}'s type. Properties are read from the interface ${MemoGraph.name}, so a getter that only an implementation class declares is not a property.
  GraphQL coordinate: Memo.subtitle (line 17, column 3)
  GraphQL object type: Memo
  GraphQL field: subtitle"""
    }

    static String cardSchema(boolean featuredFirst) {
        String featured = "  featured: Card\n"
        String deck = "  deck: [Face!]!\n"
        return """
schema {
  query: Query
}

type Query {
${featuredFirst ? featured + deck : deck + featured}}

interface Face {
  label: String!
}

type Card implements Face {
  label: String!
}
"""
    }

    @Unroll
    void "the implementations of an interface resolve the same whichever field reaches the object type first (featured first: #featuredFirst)"() {
        given:
        startContext(cardSchema(featuredFirst), FIELD_ORDER)

        when:
        def result = executeQuery("{ deck { __typename label } }")

        then:
        result.errors.isEmpty()
        result.data.deck == [[__typename: 'Card', label: 'Ace'], [__typename: 'Card', label: 'Ace (foil)']]

        where:
        featuredFirst << [true, false]
    }

    void "an interface without its own introspection keeps the implementation-based lookup"() {
        given:
        startContext(CHAPTER_SCHEMA, NO_CONTRACT_INTROSPECTION)

        when:
        def result = executeQuery("{ chapter { title wordCount } }")

        then:
        result.errors.isEmpty()
        result.data.chapter == [title: 'Openings', wordCount: 1200]
    }

    // two implementations without annotations (shape: one contract, a plain and a "featured" implementation)

    @GraphQLType
    static interface ReviewGraph {
        String getText()

        int getRating()
    }

    static class ReviewModel implements ReviewGraph {
        String text
        int rating
    }

    static class FeaturedReviewModel implements ReviewGraph {
        String text
        int rating
        String badge
    }

    @Requires(property = 'spec.name', value = UNANNOTATED_IMPLEMENTATIONS)
    @GraphQLRootResolver
    static class ReviewQuery {
        List<ReviewGraph> reviews() {
            return [
                    new ReviewModel(text: 'Solid', rating: 4),
                    new FeaturedReviewModel(text: 'A classic', rating: 5, badge: 'editor'),
            ]
        }
    }

    @Requires(property = 'spec.name', value = UNANNOTATED_IMPLEMENTATIONS)
    @GraphQLTypeResolver(ReviewGraph)
    static class ReviewResolver {
        String summary(ReviewGraph review) {
            return "${review.text} (${review.rating})"
        }
    }

    // two implementations, each annotated with @GraphQLType(contract) (shape: a short and a detailed implementation)

    @GraphQLType
    static interface LineItemGraph {
        String getName()

        int getAmount()
    }

    @GraphQLType(LineItemGraph)
    static class LineItemModel implements LineItemGraph {
        String name
        int amount
    }

    @GraphQLType(LineItemGraph)
    static class DetailedLineItemModel implements LineItemGraph {
        String name
        int amount
        String description
    }

    @Requires(property = 'spec.name', value = ANNOTATED_IMPLEMENTATIONS)
    @GraphQLRootResolver
    static class LineItemQuery {
        LineItemModel item() {
            return new LineItemModel(name: 'Paper', amount: 3)
        }

        List<LineItemGraph> items() {
            return [
                    new LineItemModel(name: 'Paper', amount: 3),
                    new DetailedLineItemModel(name: 'Ink', amount: 7, description: 'black'),
            ]
        }
    }

    // a contract annotated with @Introspected

    @Introspected
    static interface TagGraph {
        String getLabel()
    }

    static class TagModel implements TagGraph {
        String label
    }

    static class SystemTagModel implements TagGraph {
        String getLabel() {
            return 'poetry'
        }
    }

    @Requires(property = 'spec.name', value = INTROSPECTED_CONTRACT)
    @GraphQLRootResolver
    static class TagQuery {
        List<TagGraph> tags() {
            return [new TagModel(label: 'fiction'), new SystemTagModel()]
        }
    }

    // the contract has no draftNote, only the implementation has

    @GraphQLType
    static interface ArticleGraph {
        String getTitle()
    }

    @GraphQLType(ArticleGraph)
    static class ArticleModel implements ArticleGraph {
        String title
        String draftNote
    }

    @Requires(property = 'spec.name', value = IMPLEMENTATION_ONLY_GETTER)
    @GraphQLRootResolver
    static class ArticleQuery {
        ArticleGraph article() {
            return new ArticleModel(title: 'Openings', draftNote: 'check the dates')
        }
    }

    // the contract of an implementation of an SDL interface lacks one of the interface's fields

    static interface EntryGraph {
    }

    @GraphQLType
    static interface MemoGraph extends EntryGraph {
        String getTitle()
    }

    static class MemoModel implements MemoGraph {
        String title
        String subtitle
    }

    @Requires(property = 'spec.name', value = INTERFACE_FIELD_ONLY_ON_IMPLEMENTATION)
    @GraphQLRootResolver
    static class MemoQuery {
        List<EntryGraph> entries() {
            return [new MemoModel(title: 'Agenda', subtitle: 'draft')]
        }
    }

    @Requires(property = 'spec.name', value = INTERFACE_FIELD_ONLY_ON_IMPLEMENTATION)
    @io.micronaut.context.annotation.Factory
    static class MemoDictionary {
        @io.micronaut.context.annotation.Bean
        @jakarta.inject.Singleton
        io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary.registerType("Memo", MemoGraph)
        }
    }

    // one object type, an interface field and a field that declares one of the annotated implementations

    static interface FaceGraph {
        String getLabel()
    }

    @GraphQLType
    static interface CardGraph extends FaceGraph {
    }

    @GraphQLType(CardGraph)
    static class CardModel implements CardGraph {
        String label
    }

    @GraphQLType(CardGraph)
    static class FoilCardModel implements CardGraph {
        String label
    }

    @Requires(property = 'spec.name', value = FIELD_ORDER)
    @GraphQLRootResolver
    static class CardQuery {
        CardModel featured() {
            return new CardModel(label: 'Ace')
        }

        List<FaceGraph> deck() {
            return [new CardModel(label: 'Ace'), new FoilCardModel(label: 'Ace (foil)')]
        }
    }

    @Requires(property = 'spec.name', value = FIELD_ORDER)
    @io.micronaut.context.annotation.Factory
    static class CardDictionary {
        @io.micronaut.context.annotation.Bean
        @jakarta.inject.Singleton
        io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary.registerType("Card", CardGraph)
        }
    }

    // an interface without an introspection of its own: the one class annotated with @GraphQLType(interface) is read

    static interface ChapterGraph {
        String getTitle()
    }

    @GraphQLType(ChapterGraph)
    static class ChapterModel implements ChapterGraph {
        String title
        int wordCount
    }

    @Requires(property = 'spec.name', value = NO_CONTRACT_INTROSPECTION)
    @GraphQLRootResolver
    static class ChapterQuery {
        ChapterGraph chapter() {
            return new ChapterModel(title: 'Openings', wordCount: 1200)
        }
    }

}
