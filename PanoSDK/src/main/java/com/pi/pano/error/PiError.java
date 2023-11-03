package com.pi.pano.error;

public class PiError {

    @PiErrorCode
    private final int code;
    private String message;

    public PiError(int code) {
        this.code = code;
    }

    public PiError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "PiError{" +
                "code=" + code +
                ", message='" + message + '\'' +
                '}';
    }
}
