/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.data;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class BookSellerRepository {

    private static final List<BookSeller> STATE = new ArrayList<>();

    static {
        STATE.add(new BookSeller("ab", "Amaz", 20.00f));
        STATE.add(new BookSeller("ab", "Yah", 21.00f));
        STATE.add(new BookSeller("ax", "Amaz", 19.00f));
        STATE.add(new BookSeller("gd", "Zap", 19.50f));
        STATE.add(new BookSeller("we", "Amaz", 36.00f));
        STATE.add(new BookSeller("we", "Yah", 35.00f));
        STATE.add(new BookSeller("we", "Zap", 34.00f));
    }

    public List<BookSeller> findAllByBookIds(Collection<String> bookIds) {
        return STATE.stream()
                .filter(it -> bookIds.contains(it.getBookId()))
                .collect(Collectors.toList());
    }

}
