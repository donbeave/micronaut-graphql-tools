package example;

import graphql.schema.Coercing;

import java.time.LocalDate;

public class DateCoercing implements Coercing<LocalDate, String> {

    @Override
    public String serialize(Object dataFetcherResult) {
        return String.valueOf(dataFetcherResult);
    }

    @Override
    public LocalDate parseValue(Object input) {
        return LocalDate.parse(String.valueOf(input));
    }

    @Override
    public LocalDate parseLiteral(Object input) {
        return LocalDate.parse(String.valueOf(input));
    }

}
