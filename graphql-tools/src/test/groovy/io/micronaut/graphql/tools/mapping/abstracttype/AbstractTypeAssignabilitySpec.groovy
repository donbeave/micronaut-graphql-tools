package io.micronaut.graphql.tools.mapping.abstracttype

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.IncorrectMemberMappingException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

/**
 * Unions and interfaces resolve the object type of a runtime object by its exact class first, and otherwise by the
 * one member whose Java type the object is an instance of.
 */
class AbstractTypeAssignabilitySpec extends AbstractTest {

    static final String UNION_SUBCLASS = "AbstractTypeAssignabilitySpec.unionSubclass"
    static final String UNION_CONTRACT = "AbstractTypeAssignabilitySpec.unionContract"
    static final String INTERFACE_SUBCLASS_AND_CONTRACT = "AbstractTypeAssignabilitySpec.interfaceSubclassAndContract"
    static final String UNION_AMBIGUOUS = "AbstractTypeAssignabilitySpec.unionAmbiguous"
    static final String INTERFACE_AMBIGUOUS = "AbstractTypeAssignabilitySpec.interfaceAmbiguous"
    static final String SUBCLASS_CHAIN = "AbstractTypeAssignabilitySpec.subclassChain"
    static final String UNION_STRAY_MEMBER = "AbstractTypeAssignabilitySpec.unionStrayMember"
    static final String INTERFACE_STRAY_MEMBER = "AbstractTypeAssignabilitySpec.interfaceStrayMember"
    static final String FIELD_ORDER = "AbstractTypeAssignabilitySpec.fieldOrder"

    static String shelfSchema(boolean volumeFirst) {
        String volume = "  volume: Volume\n"
        String shelf = "  shelf: [Shelved!]!\n"
        return """
schema {
  query: Query
}

type Query {
${volumeFirst ? volume + shelf : shelf + volume}}

interface Shelved {
  title: String!
}

type Volume implements Shelved {
  title: String!
}

type Magazine implements Shelved {
  title: String!
}
"""
    }

    @Language("GraphQL")
    static final String PAYLOAD_SCHEMA = """
schema {
  query: Query
}

type Query {
  payload(kind: String!): Payload
}

union Payload = Receipt | Failure

type Receipt {
  code: String!
}

type Failure {
  reason: String!
}
"""

    @Language("GraphQL")
    static final String RESULT_UNION_SCHEMA = """
schema {
  query: Query
}

type Query {
  results: [Result!]!
}

union Result = Song | Album

type Song {
  name: String!
}

type Album {
  name: String!
}
"""

    @Language("GraphQL")
    static final String TRACK_INTERFACE_SCHEMA = """
schema {
  query: Query
}

type Query {
  tracks: [Track!]!
}

interface Track {
  name: String!
}

type Song implements Track {
  name: String!
}

type Album implements Track {
  name: String!
}
"""

    @Language("GraphQL")
    static final String ITEM_UNION_SCHEMA = """
schema {
  query: Query
}

type Query {
  item(bundle: Boolean!): Item
}

union Item = Printed | Digital

type Printed {
  title: String!
}

type Digital {
  title: String!
}
"""

    @Language("GraphQL")
    static final String ITEM_INTERFACE_SCHEMA = """
schema {
  query: Query
}

type Query {
  item(bundle: Boolean!): Media
}

interface Media {
  title: String!
}

type Printed implements Media {
  title: String!
}

type Digital implements Media {
  title: String!
}
"""

    @Language("GraphQL")
    static final String TEXT_SCHEMA = """
schema {
  query: Query
}

type Query {
  texts: [Text]!
}

union Text = Note | LongNote

type Note {
  body: String!
}

type LongNote {
  body: String!
}
"""

    @Language("GraphQL")
    static final String STRAY_UNION_SCHEMA = """
schema {
  query: Query
}

type Query {
  payload(kind: String!): Payload
}

union Payload = Receipt | Stray

type Receipt {
  code: String!
}

type Stray {
  code: String!
}
"""

    @Language("GraphQL")
    static final String STRAY_INTERFACE_SCHEMA = """
schema {
  query: Query
}

type Query {
  payload(kind: String!): Coded
}

interface Coded {
  code: String!
}

type Receipt implements Coded {
  code: String!
}

type Stray implements Coded {
  code: String!
}
"""

    void "a union resolves a subclass and a runtime subclass (proxy) of a mapped class"() {
        given:
        startContext(PAYLOAD_SCHEMA, UNION_SUBCLASS)

        when:
        def result = executeQuery("""
{
    retried: payload(kind: "retried") { __typename ... on Failure { reason } }
    proxy: payload(kind: "proxy") { __typename ... on Receipt { code } }
    plain: payload(kind: "plain") { __typename ... on Receipt { code } }
}
""")

        then:
        result.errors.isEmpty()
        result.data.retried == [__typename: 'Failure', reason: 'timeout, retried']
        result.data.proxy == [__typename: 'Receipt', code: 'R-2']
        result.data.plain == [__typename: 'Receipt', code: 'R-1']
    }

    void "a union member mapped to a contract interface resolves objects of its unannotated implementations"() {
        given:
        startContext(RESULT_UNION_SCHEMA, UNION_CONTRACT)

        when:
        def result = executeQuery("{ results { __typename ... on Song { name } ... on Album { name } } }")

        then:
        result.errors.isEmpty()
        result.data.results == [
                [__typename: 'Song', name: 'Intro'],
                [__typename: 'Song', name: 'Intro (live)'],
                [__typename: 'Album', name: 'Debut'],
        ]
    }

    void "an interface resolves objects of unannotated implementations of a contract and of runtime subclasses"() {
        given:
        startContext(TRACK_INTERFACE_SCHEMA, INTERFACE_SUBCLASS_AND_CONTRACT)

        when:
        def result = executeQuery("{ tracks { __typename name } }")

        then:
        result.errors.isEmpty()
        result.data.tracks == [
                [__typename: 'Song', name: 'Intro (live)'],
                [__typename: 'Album', name: 'Debut'],
        ]
    }

    void "an object that is an instance of two members of a union is a GraphQL error naming both"() {
        given:
        startContext(ITEM_UNION_SCHEMA, UNION_AMBIGUOUS)

        when:
        def result = executeQuery("{ single: item(bundle: false) { __typename } bundle: item(bundle: true) { __typename } }")

        then:
        result.errors.size() == 1
        result.errors[0].path == ['bundle']
        result.errors[0].message == "Can't resolve '/bundle'. Abstract type 'Item' must resolve to an Object type at " +
                "runtime for field 'Query.item'. The object of class ${BundleModel.name} is an instance of more than " +
                "one object type of union Item: Digital (${DigitalGraph.name}), Printed (${PrintedGraph.name}). " +
                "Map its class to one of them, or let it implement only one of their Java types."
        result.data.single == [__typename: 'Printed']
        result.data.bundle == null
    }

    void "an object that is an instance of two implementations of an interface is a GraphQL error naming both"() {
        given:
        startContext(ITEM_INTERFACE_SCHEMA, INTERFACE_AMBIGUOUS)

        when:
        def result = executeQuery("{ bundle: item(bundle: true) { __typename } }")

        then:
        result.errors.size() == 1
        result.errors[0].path == ['bundle']
        result.errors[0].message.endsWith(". The object of class ${BundleModel.name} is an instance of more than " +
                "one object type of interface Media: Digital (${DigitalGraph.name}), Printed (${PrintedGraph.name}). " +
                "Map its class to one of them, or let it implement only one of their Java types.")
        result.data.bundle == null
    }

    void "the exact class wins over a superclass, and a subclass of two mapped classes is an error"() {
        given:
        startContext(TEXT_SCHEMA, SUBCLASS_CHAIN)

        when:
        def result = executeQuery("{ texts { __typename } }")

        then:
        result.errors.size() == 1
        result.errors[0].path == ['texts', 2]
        result.errors[0].message.endsWith(". The object of class ${AnnotatedLongNoteModel.name} is an instance of " +
                "more than one object type of union Text: LongNote (${LongNoteModel.name}), Note (${NoteModel.name}). " +
                "Map its class to one of them, or let it implement only one of their Java types.")
        result.data.texts == [[__typename: 'Note'], [__typename: 'LongNote'], null]
    }

    @Unroll
    void "an interface resolves an implementation of a contract whichever field reaches the object type first (volume first: #volumeFirst)"() {
        given:
        startContext(shelfSchema(volumeFirst), FIELD_ORDER)

        when:
        def result = executeQuery("{ shelf { __typename title } }")

        then:
        result.errors.isEmpty()
        result.data.shelf == [[__typename: 'Volume', title: 'Paperback'], [__typename: 'Magazine', title: 'Monthly']]

        where:
        volumeFirst << [true, false]
    }

    void "a union member mapped to a Java type that is not the declared Java type is a startup error"() {
        given:
        startContext(STRAY_UNION_SCHEMA, UNION_STRAY_MEMBER)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectMemberMappingException
        e.cause.message == """The union Payload is returned as ${PayloadGraph.name}, but its object type Stray is mapped to ${StrayModel.name}, which is not a ${PayloadGraph.name}, so no object the field returns can resolve to Stray.
  GraphQL coordinate: Query.payload (line 7, column 3)
  GraphQL object type: Query
  GraphQL field: payload
  Mapped class: ${StrayQuery.name}
  Mapped method: payload(java.lang.String kind)"""
    }

    void "an interface implementation mapped to a Java type that is not the declared Java type is a startup error"() {
        given:
        startContext(STRAY_INTERFACE_SCHEMA, INTERFACE_STRAY_MEMBER)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectMemberMappingException
        e.cause.message.startsWith("The interface Coded is returned as ${PayloadGraph.name}, but its object type Stray " +
                "is mapped to ${StrayModel.name}, which is not a ${PayloadGraph.name}, so no object the field " +
                "returns can resolve to Stray.")
    }

    // Payload: classes, a subclass and a runtime subclass

    static interface PayloadGraph {
    }

    @GraphQLType
    static class ReceiptModel implements PayloadGraph {
        String code
    }

    @GraphQLType
    static class FailureModel implements PayloadGraph {
        String reason
    }

    static class RetriedFailureModel extends FailureModel {
    }

    @Requires(property = 'spec.name', value = UNION_SUBCLASS)
    @GraphQLRootResolver
    static class PayloadQuery {
        PayloadGraph payload(String kind) {
            switch (kind) {
                case 'retried': return new RetriedFailureModel(reason: 'timeout, retried')
                case 'proxy': return new ReceiptModel() {
                    @Override
                    String getCode() {
                        return 'R-2'
                    }
                }
                default: return new ReceiptModel(code: 'R-1')
            }
        }
    }

    @Requires(property = 'spec.name', value = UNION_SUBCLASS)
    @Factory
    static class PayloadDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Receipt", ReceiptModel)
                    .registerType("Failure", FailureModel)
        }
    }

    // Result / Track: a contract interface with unannotated implementations

    static interface ResultGraph {
        String getName()
    }

    @GraphQLType
    static interface SongGraph extends ResultGraph {
    }

    static class SongModel implements SongGraph {
        String name
    }

    static class LiveSongModel implements SongGraph {
        String name
    }

    @GraphQLType
    static class AlbumModel implements ResultGraph {
        String name
    }

    @Requires(property = 'spec.name', value = UNION_CONTRACT)
    @GraphQLRootResolver
    static class ResultQuery {
        List<ResultGraph> results() {
            return [new SongModel(name: 'Intro'), new LiveSongModel(name: 'Intro (live)'), new AlbumModel(name: 'Debut')]
        }
    }

    @Requires(property = 'spec.name', value = INTERFACE_SUBCLASS_AND_CONTRACT)
    @GraphQLRootResolver
    static class TrackQuery {
        List<ResultGraph> tracks() {
            return [new LiveSongModel(name: 'Intro (live)'), new AlbumModel() {
                @Override
                String getName() {
                    return 'Debut'
                }
            }]
        }
    }

    @Requires(property = 'spec.name', pattern = 'AbstractTypeAssignabilitySpec\\.(unionContract|interfaceSubclassAndContract)')
    @Factory
    static class ResultDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Song", SongGraph)
                    .registerType("Album", AlbumModel)
        }
    }

    // Item / Media: one class implements the Java types of two members

    static interface ItemGraph {
        String getTitle()
    }

    @GraphQLType
    static interface PrintedGraph extends ItemGraph {
    }

    @GraphQLType
    static interface DigitalGraph extends ItemGraph {
    }

    static class PrintedModel implements PrintedGraph {
        String title
    }

    static class BundleModel implements PrintedGraph, DigitalGraph {
        String title
    }

    @Requires(property = 'spec.name', pattern = 'AbstractTypeAssignabilitySpec\\.(unionAmbiguous|interfaceAmbiguous)')
    @GraphQLRootResolver
    static class ItemQuery {
        ItemGraph item(boolean bundle) {
            return bundle ? new BundleModel(title: 'Box set') : new PrintedModel(title: 'Paperback')
        }
    }

    @Requires(property = 'spec.name', pattern = 'AbstractTypeAssignabilitySpec\\.(unionAmbiguous|interfaceAmbiguous)')
    @Factory
    static class ItemDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Printed", PrintedGraph)
                    .registerType("Digital", DigitalGraph)
        }
    }

    // Text: a class, its subclass, and a subclass of both

    static interface TextGraph {
    }

    @GraphQLType
    static class NoteModel implements TextGraph {
        String body
    }

    @GraphQLType
    static class LongNoteModel extends NoteModel {
    }

    static class AnnotatedLongNoteModel extends LongNoteModel {
    }

    @Requires(property = 'spec.name', value = SUBCLASS_CHAIN)
    @GraphQLRootResolver
    static class TextQuery {
        List<TextGraph> texts() {
            return [new NoteModel(body: 'a'), new LongNoteModel(body: 'b'), new AnnotatedLongNoteModel(body: 'c')]
        }
    }

    @Requires(property = 'spec.name', value = SUBCLASS_CHAIN)
    @Factory
    static class TextDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Note", NoteModel)
                    .registerType("LongNote", LongNoteModel)
        }
    }

    // Shelved: a field that declares the annotated implementation of a contract, and an unannotated implementation

    static interface ShelvedGraph {
        String getTitle()
    }

    @GraphQLType
    static interface VolumeGraph extends ShelvedGraph {
    }

    @GraphQLType(VolumeGraph)
    static class VolumeModel implements VolumeGraph {
        String title
    }

    static class PaperbackModel implements VolumeGraph {
        String title = 'Paperback'
    }

    @GraphQLType
    static class MagazineModel implements ShelvedGraph {
        String title = 'Monthly'
    }

    @Requires(property = 'spec.name', value = FIELD_ORDER)
    @GraphQLRootResolver
    static class ShelfQuery {
        VolumeModel volume() {
            return new VolumeModel(title: 'Hardcover')
        }

        List<ShelvedGraph> shelf() {
            return [new PaperbackModel(), new MagazineModel()]
        }
    }

    @Requires(property = 'spec.name', value = FIELD_ORDER)
    @Factory
    static class ShelfDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Volume", VolumeGraph)
                    .registerType("Magazine", MagazineModel)
        }
    }

    // Stray: a member mapped to a class outside the declared Java type

    @GraphQLType
    static class StrayModel {
        String code
    }

    @Requires(property = 'spec.name', pattern = 'AbstractTypeAssignabilitySpec\\.(unionStrayMember|interfaceStrayMember)')
    @GraphQLRootResolver
    static class StrayQuery {
        PayloadGraph payload(String kind) {
            return new ReceiptModel(code: 'R-1')
        }
    }

    @Requires(property = 'spec.name', pattern = 'AbstractTypeAssignabilitySpec\\.(unionStrayMember|interfaceStrayMember)')
    @Factory
    static class StrayDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Receipt", ReceiptModel)
                    .registerType("Stray", StrayModel)
        }
    }

}
