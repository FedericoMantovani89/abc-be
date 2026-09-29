package it.abc.musical.exceptions;

public class NotFoundException extends CodedException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String code, Object... args) {
        super(code, args);
    }
}
