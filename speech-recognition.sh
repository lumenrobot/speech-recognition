#!/bin/bash
SCRIPT_DIR="$(dirname $0)"
java -Xms256m -Xmx256m -cp $SCRIPT_DIR'/target/dependency/*':$SCRIPT_DIR'/target/classes' org.lskk.lumen.speech.recognition.SpeechRecognitionApp "$@"
