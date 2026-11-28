package com.example.caller;

import com.example.model.BookBadge;
import com.example.model.BookCard;
import com.example.model.FindNodeNode;
import com.example.model.FindNodeNodeFolder;
import com.example.model.FindNodeNodeOther;
import com.example.model.GetBookBook;
import com.example.model.GetUser;
import com.example.model.GetUserUser;
import com.example.model.GetUserVariables;
import com.example.model.PostAdded;
import com.example.model.PostAddedPostAdded;
import com.example.model.Role;
import com.example.model.SearchSearch;
import com.example.model.SearchSearchFile;
import com.example.model.SearchSearchFolder;
import com.example.model.SearchSearchOther;
import com.example.model.SearchSearchUser;
import com.example.model.Viewer;
import com.example.model.ViewerData;
import io.micronaut.graphql.tools.client.GraphQLRequest;

import java.util.Map;

public final class JavaCaller {

    private JavaCaller() {
    }

    public static GetUser request(String id) {
        return new GetUser(new GetUserVariables(id, true, null));
    }

    public static GraphQLRequest<Map<String, GetUserUser>> lowLevel(GetUser request) {
        return GraphQLRequest.builder(GetUser.DATA)
                .document(request.document())
                .operationName(request.name())
                .operationType(request.kind())
                .variable("id", request.variables().id())
                .build();
    }

    public static String describe(SearchSearch result) {
        if (result instanceof SearchSearchFolder folder) {
            return "folder " + folder.name() + " " + folder.createdAt();
        } else if (result instanceof SearchSearchFile file) {
            return "file " + file.bytes();
        } else if (result instanceof SearchSearchUser user) {
            return "user " + user.id();
        }
        return "other " + ((SearchSearchOther) result).__typename();
    }

    public static String node(FindNodeNode node) {
        if (node instanceof FindNodeNodeFolder folder) {
            return folder.name() + " " + folder.size() + " " + folder.children().size();
        }
        return ((FindNodeNodeOther) node).__typename();
    }

    public static String card(GetBookBook book) {
        BookCard card = book;
        BookBadge badge = book;
        return card.title() + " " + card.tags() + " " + badge.inStock() + " " + badge.rating() + " " + book.headline();
    }

    public static String user(GetUserUser user) {
        return user.name() + " " + user.role() + " " + (user.role() == Role.UNKNOWN__);
    }

    public static Integer likes(PostAddedPostAdded post) {
        return post.likes();
    }

    public static Object subscription() {
        return new PostAdded().kind();
    }

    public static Object viewer(ViewerData data) {
        return new Viewer().variables() + " " + data.viewer().tags() + " " + data.mine().name();
    }
}
