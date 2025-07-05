# Wikipedia Movie Scraper

This project provides the title, genre, director, cast, year, and category (by country or miscellaneous) data of 150k+ Wikipedia movie entries.

## Features
- Scrapes and aggregates movie data from Wikipedia
- Extracts key information: title, genre, director, cast, year, and category
- Covers over 150,000 movie entries

## Usage

### Build the Project

Use Gradle to build the project. From the project root directory, run:

```shell
./gradlew build
```

or on Windows:

```shell
gradlew.bat build
```

### Run the Scraper

To run the main scraper, use:

```shell
./gradlew run
```

or on Windows:

```shell
gradlew.bat run
```

You can also run specific Kotlin files by configuring the `application` main class in `build.gradle.kts` if needed.

See source files in `src/main/kotlin/` for more details.

## Requirements
- Kotlin
- Gradle
