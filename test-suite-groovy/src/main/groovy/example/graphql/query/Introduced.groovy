package example.graphql.query

import io.micronaut.aop.Introduction

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Introduction
@Retention(RetentionPolicy.RUNTIME)
@interface Introduced {
}
