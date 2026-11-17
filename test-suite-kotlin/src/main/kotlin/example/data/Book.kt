package example.data

import io.micronaut.core.annotation.Introspected

@Introspected
class Book(val id: String, val title: String, val year: Int) {

    override fun toString() = "Book{id='$id', title='$title', year=$year}"

    override fun equals(other: Any?) = this === other || other is Book && id == other.id

    override fun hashCode() = id.hashCode()
}
