package it.abc.musical.exceptions;

public class ConflictException extends CodedException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String code, Object... args) {
        super(code, args);
    }
}
