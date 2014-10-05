# SwingEDTCheckAgent

Simple agent to check if swing object methods are use in other threads than EventDispatcherThread

## Compile
`./gradlew shadowJar`

## Usage
`java -javaagent:<path to>/SwingEDTCheckAgent-all.jar <rest of the arguments>`
