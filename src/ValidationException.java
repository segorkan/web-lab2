import java.util.List;

class ValidationException extends RuntimeException {
    private final List<String> errors;

    ValidationException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    ValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    List<String> getErrors() { return errors; }
}