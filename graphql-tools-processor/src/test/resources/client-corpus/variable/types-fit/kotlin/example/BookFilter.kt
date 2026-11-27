package example

data class BookFilter(val title: String?, val tags: List<String>?, val genre: Genre?, val limit: Int, val nested: BookFilter?)
