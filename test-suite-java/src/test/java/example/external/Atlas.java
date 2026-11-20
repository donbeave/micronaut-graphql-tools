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
package example.external;

// A class as a generator or another module delivers it: no annotation, no dependency on the library.
public class Atlas {

    private final String title;
    private final Region region;
    private final int pages;

    public Atlas(String title, Region region, int pages) {
        this.title = title;
        this.region = region;
        this.pages = pages;
    }

    public String getTitle() {
        return title;
    }

    public Region getRegion() {
        return region;
    }

    public int getPages() {
        return pages;
    }

}
