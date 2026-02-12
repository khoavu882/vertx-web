# Suggested Commands

## Build & Test

- **Build Project:** `./gradlew build`
- **Run Tests:** `./gradlew test` (Runs all tests)
- **Run Single Test:** `./gradlew test --tests ClassName.methodName`

## Code Quality

- **Check Formatting:** `./gradlew spotlessCheck`
- **Apply Formatting:** `./gradlew spotlessApply` (Uses Palantir Java Format)

## Run Application

- **Run Dev Mode:** `./gradlew run` (Check `build.gradle` for specific run configuration if needed, usually `run` task is standard application plugin)

## Miscellaneous

- **Clean:** `./gradlew clean`
- **Refresh Dependencies:** `./gradlew build --refresh-dependencies`
