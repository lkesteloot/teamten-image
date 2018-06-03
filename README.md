Java image processing library. See the [Javadoc](https://lkesteloot.github.com/teamten-image).

Uses the JDK's `BufferedImage` objects, treating them as immutable images:

    BufferedImage image = ImageUtils.load(inputPathname);
    image = ImageUtils.trim(image);
    image = ImageUtils.addMargin(image, 20, 20, 20, 20);
    image = ImageUtils.composeOverCheckerboard(image);
    ImageUtils.save(image, outputPathname);

Available in jcenter:

    repositories {
        jcenter()
    }

    dependencies {
        compile 'com.teamten:teamten-image:1.0'
    }

# License

Copyright 2018 Lawrence Kesteloot

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
