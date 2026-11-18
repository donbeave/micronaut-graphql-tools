package io.micronaut.graphql.tools.mapping.resolver.introduction;

import io.micronaut.aop.Introduction;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Introduction
@Retention(RetentionPolicy.RUNTIME)
@interface Answering {
}
