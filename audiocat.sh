#!/bin/bash
SCRIPT_DIR="$(dirname $0)"
java -cp $SCRIPT_DIR'/target/dependency/*':$SCRIPT_DIR'/target/classes' org.lskk.lumen.speech.recognition.AudioCatApp "$@"
