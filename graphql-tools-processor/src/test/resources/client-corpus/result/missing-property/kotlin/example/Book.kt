package example

data class Book(val id: String, val title: String?, val rating: Int?, val author: Author?, val genre: Genre?, val tags: List<String>?)
