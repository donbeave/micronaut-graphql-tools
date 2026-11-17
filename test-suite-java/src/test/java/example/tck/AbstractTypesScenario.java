package example.tck;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.SchemaMappingDictionary;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;

import jakarta.inject.Singleton;

import java.util.List;

/**
 * The fixtures of the TCK scenario {@code abstract-types}: an interface and a union of the same classes.
 */
public final class AbstractTypesScenario {

    private AbstractTypesScenario() {
    }

    public interface Media {
        String getTitle();

        int getSeconds();
    }

    public interface Lookup {
    }

    @GraphQLType
    public static class Song implements Media, Lookup {
        private final String title;
        private final int seconds;
        private final String artist;

        public Song(String title, int seconds, String artist) {
            this.title = title;
            this.seconds = seconds;
            this.artist = artist;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public int getSeconds() {
            return seconds;
        }

        public String getArtist() {
            return artist;
        }
    }

    @GraphQLType
    public static class Podcast implements Media, Lookup {
        private final String title;
        private final int seconds;
        private final String host;

        public Podcast(String title, int seconds, String host) {
            this.title = title;
            this.seconds = seconds;
            this.host = host;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public int getSeconds() {
            return seconds;
        }

        public String getHost() {
            return host;
        }
    }

    @GraphQLType
    public static class Missing implements Lookup {
        private final String reason;

        public Missing(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    @Singleton
    @Requires(property = "tck.scenario", value = "abstract-types")
    public static class Mappings implements TckTypeMappings {
        @Override
        public void register(SchemaMappingDictionary dictionary) {
            dictionary
                    .registerType("Song", Song.class)
                    .registerType("Podcast", Podcast.class)
                    .registerType("Missing", Missing.class);
        }
    }

    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "abstract-types")
    public static class Queries {

        private final List<Media> library = List.of(
                new Song("Aria", 245, "Ada"),
                new Podcast("Weekly", 1800, "Grace"),
                new Song("Nocturne", 180, "Lin"));

        public List<Media> library() {
            return library;
        }

        public Lookup find(String title) {
            return library.stream()
                    .filter(media -> media.getTitle().equals(title))
                    .map(Lookup.class::cast)
                    .findFirst()
                    .orElseGet(() -> new Missing("no media titled " + title));
        }
    }

}
