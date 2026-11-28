package com.example.caller;

import com.example.model.AddBook;
import com.example.model.BookCount;
import com.example.model.Matrix;
import com.example.model.Titles;
import com.example.model.TopStatus;
import com.example.model.Updated;
import com.example.model.AddBookAddBook;
import com.example.model.BookInput;
import com.example.model.Books;
import com.example.model.BooksBooks;
import com.example.model.CreatePost;
import com.example.model.CreatePostCreatePost;
import com.example.model.FindNode;
import com.example.model.FindNodeNode;
import com.example.model.GetBook;
import com.example.model.GetBookBook;
import com.example.model.GetUser;
import com.example.model.GetUserUser;
import com.example.model.PostFilter;
import com.example.model.Search;
import com.example.model.SearchSearch;
import com.example.model.ShelfView;
import com.example.model.ShelfViewShelf;
import com.example.model.Status;
import com.example.model.Viewer;
import com.example.model.ViewerData;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

import java.util.List;

@GraphQLClient(schema = "models")
public interface ModelsClient {

    @GraphQLOperation(FindNode.DOCUMENT)
    FindNodeNode findNode(String id);

    @GraphQLOperation(CreatePost.DOCUMENT)
    CreatePostCreatePost createPost(String title, @Nullable String body);

    @GraphQLOperation(Search.DOCUMENT)
    List<SearchSearch> search(String text, @Nullable PostFilter filter);

    @GraphQLOperation(GetUser.DOCUMENT)
    GetUserUser getUser(String id, @Nullable Boolean withPosts, @Nullable Integer first);

    @GraphQLOperation(Viewer.DOCUMENT)
    ViewerData viewer();

    @GraphQLOperation(GetBook.DOCUMENT)
    GetBookBook getBook(String id);

    @GraphQLOperation(ShelfView.DOCUMENT)
    ShelfViewShelf shelfView(String name);

    @GraphQLOperation(Books.DOCUMENT)
    List<BooksBooks> books(@Nullable Status status, @Nullable Integer first, boolean withAuthor);

    @GraphQLOperation(BookCount.DOCUMENT)
    int bookCount();

    @GraphQLOperation(Titles.DOCUMENT)
    List<String> titles();

    @GraphQLOperation(Matrix.DOCUMENT)
    List<List<Integer>> matrix();

    @GraphQLOperation(Updated.DOCUMENT)
    @Nullable java.time.OffsetDateTime updated();

    @GraphQLOperation(TopStatus.DOCUMENT)
    Status topStatus();

    @GraphQLOperation(AddBook.DOCUMENT)
    AddBookAddBook addBook(BookInput input, @Nullable Boolean dryRun);
}
