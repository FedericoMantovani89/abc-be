package it.abc.musical.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.passay.DefaultPasswordValidator;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.data.EnglishCharacterData;
import org.passay.rule.CharacterRule;
import org.passay.rule.LengthRule;
import org.passay.rule.WhitespaceRule;

import java.util.List;

/**
 * Policy password: 8-128 caratteri, almeno una maiuscola, una minuscola,
 * una cifra e un carattere speciale; nessuno spazio.
 */
public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    private static final PasswordValidator VALIDATOR = new DefaultPasswordValidator(List.of(
            new LengthRule(8, 128),
            new CharacterRule(EnglishCharacterData.UpperCase, 1),
            new CharacterRule(EnglishCharacterData.LowerCase, 1),
            new CharacterRule(EnglishCharacterData.Digit, 1),
            new CharacterRule(EnglishCharacterData.Special, 1),
            new WhitespaceRule()
    ));

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        return password != null && VALIDATOR.validate(new PasswordData(password)).isValid();
    }
}
