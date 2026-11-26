package example;

import jakarta.inject.Singleton;

@Singleton
public class Resolvers implements NovelContract, WriterContract {

    @Override
    public String bookExtra(Novel novel) {
        return "b-" + novel.getTitle();
    }

    @Override
    public String authorExtra(Writer writer) {
        return "a-" + writer.getName();
    }

    public String describe(String text) {
        return text;
    }

}
