# Playground (SpiritboxProject)

This directory is a standalone Gradle build used for developing Spotlight against the same project *shape* used in Spotlight's functional tests (`SpiritboxProject`).

## What this is
- A separate Gradle build rooted at `playground/`.
- Includes the main Spotlight repo as a composite build so the settings plugin is resolved from source.
- Applies the Spotlight settings plugin (`com.fueledbycaffeine.spotlight`).
- Contains an additional included build at `included-build/` (also matching the fixture).

## Spotlight lists
- `gradle/all-projects.txt` is pre-populated to match the project includes in the fixture.
- `gradle/ide-projects.txt` defaults to `:**`.

## Useful tasks
From this directory:
- `../gradlew projects`
- `../gradlew tasks --all`
- `../gradlew checkAllProjectsList`
- `../gradlew fixAllProjectsList`

