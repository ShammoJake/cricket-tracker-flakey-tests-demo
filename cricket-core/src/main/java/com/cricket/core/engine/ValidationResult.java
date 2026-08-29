package com.cricket.core.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Outcome of validating a delivery: either valid, or a list of reasons why not. */
public final class ValidationResult {

    private static final ValidationResult VALID = new ValidationResult(Collections.<String>emptyList());

    private final List<String> errors;

    private ValidationResult(List<String> errors) {
        this.errors = Collections.unmodifiableList(errors);
    }

    public static ValidationResult valid() {
        return VALID;
    }

    public static ValidationResult invalid(String... reasons) {
        List<String> list = new ArrayList<String>();
        for (String r : reasons) {
            if (r != null && !r.trim().isEmpty()) {
                list.add(r);
            }
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("an invalid result needs at least one reason");
        }
        return new ValidationResult(list);
    }

    static ValidationResult of(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return VALID;
        }
        return new ValidationResult(new ArrayList<String>(reasons));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return errors;
    }

    public int errorCount() {
        return errors.size();
    }

    public String firstError() {
        return errors.isEmpty() ? null : errors.get(0);
    }

    public boolean hasError(String fragment) {
        if (fragment == null) {
            return false;
        }
        for (String e : errors) {
            if (e.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    /** Throws when invalid; returns normally otherwise. */
    public void throwIfInvalid() {
        if (!isValid()) {
            throw new IllegalArgumentException("invalid delivery: " + String.join("; ", errors));
        }
    }

    @Override
    public String toString() {
        return isValid() ? "valid" : "invalid" + errors;
    }
}
