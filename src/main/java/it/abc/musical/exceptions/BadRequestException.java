package it.abc.musical.exceptions;

public class BadRequestException extends CodedException {

    private static final long serialVersionUID = 1L;

    public BadRequestException(String code, Object... args) {
        super(code, args);
    }
}
