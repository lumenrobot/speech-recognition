package org.lskk.lumen.speech.recognition;

/**
 * Created by ceefour on 03/10/2015.
 */
public class SpeechRecognitionException extends RuntimeException {

    public SpeechRecognitionException() {
    }

    public SpeechRecognitionException(String message) {
        super(message);
    }

    public SpeechRecognitionException(String message, Throwable cause) {
        super(message, cause);
    }

    public SpeechRecognitionException(Throwable cause) {
        super(cause);
    }

    public SpeechRecognitionException(Throwable cause, String format, Object... args) {
        super(String.format(format, args), cause);
    }

    public SpeechRecognitionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
