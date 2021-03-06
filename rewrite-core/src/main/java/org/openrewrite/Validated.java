/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite;

import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.internal.lang.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.StreamSupport.stream;

@Incubating(since = "2.0.0")
public interface Validated extends Iterable<Validated> {
    boolean isValid();

    default boolean isInvalid() {
        return !isValid();
    }

    default List<Invalid> failures() {
        return stream(spliterator(), false)
                .filter(Validated::isInvalid)
                .map(v -> (Invalid) v)
                .collect(Collectors.toList());
    }

    static Secret validSecret(String property, String value) {
        return new Secret(property, value);
    }

    static None none() {
        return new None();
    }

    static Valid valid(String property, @Nullable Object value) {
        return new Valid(property, value);
    }

    static <T> Validated test(String property, String message, @Nullable T value, Predicate<T> test) {
        return test.test(value) ?
                valid(property, value) :
                invalid(property, value, message.replace("{}", value == null ? "null" : value.toString()));
    }

    static Validated required(String property, @Nullable Object value) {
        return value != null ?
                valid(property, value) :
                missing(property, value, "is required");
    }

    static Validated notBlank(String property, @Nullable String value) {
        return test(property, "must not be blank", value, s -> value != null && !StringUtils.isBlank(value));
    }

    static Missing missing(String property, @Nullable Object value, String message) {
        return new Missing(property, value, message);
    }

    static Invalid invalid(String property, @Nullable Object value, String message) {
        return invalid(property, value, message, null);
    }

    static Invalid invalid(String property, @Nullable Object value, String message,
                                  @Nullable Throwable exception) {
        return new Invalid(property, value, message, exception);
    }

    default Validated and(Validated validated) {
        if (this instanceof None) {
            return validated;
        }
        return new Both(this, validated);
    }

    default Validated or(Validated validated) {
        if (this instanceof None) {
            return validated;
        }
        return new Either(this, validated);
    }

    <T> T getValue();

    /**
     * Indicates that no validation has occurred. None is considered "valid", effectively a no-op validation.
     */
    class None implements Validated {
        @Override
        public boolean isValid() {
            return true;
        }

        @NonNull
        @Override
        public Iterator<Validated> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public <T> T getValue() {
            throw new IllegalStateException("Value does not exist");
        }
    }

    /**
     * A specialization {@link Valid} that won't print the secret in plain text if the validation is serialized.
     */
    class Secret extends Valid {
        public Secret(String property, String value) {
            super(property, value);
        }

        @Override
        public String toString() {
            return "Secret{" +
                    "property='" + property + '\'' +
                    '}';
        }
    }

    /**
     * A valid property value.
     */
    class Valid implements Validated {
        protected final String property;
        private final Object value;

        public Valid(String property, Object value) {
            this.property = property;
            this.value = value;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @NonNull
        @Override
        public Iterator<Validated> iterator() {
            return Stream.of((Validated) this).iterator();
        }

        public String getProperty() {
            return property;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getValue() {
            return (T) value;
        }

        @Override
        public String toString() {
            return "Valid{" +
                    "property='" + property + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }
    }

    class Invalid implements Validated {
        private final String property;

        @Nullable
        private final Object value;

        private final String message;

        @Nullable
        private final Throwable exception;

        public Invalid(String property, @Nullable Object value, String message, @Nullable Throwable exception) {
            this.property = property;
            this.value = value;
            this.message = message;
            this.exception = exception;
        }

        @Override
        public boolean isValid() {
            return false;
        }

        @NonNull
        @Override
        public Iterator<Validated> iterator() {
            return Stream.of((Validated) this).iterator();
        }

        public String getMessage() {
            return message;
        }

        public String getProperty() {
            return property;
        }

        @Nullable
        public Object getValue() {
            return value;
        }

        @Nullable
        public Throwable getException() {
            return exception;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{" +
                    "property='" + property + '\'' +
                    ", value='" + value + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }
    }

    class Missing extends Invalid {
        public Missing(String property, Object value, String message) {
            super(property, value, message, null);
        }
    }

    class Either implements Validated {
        private final Validated left;
        private final Validated right;

        public Either(Validated left, Validated right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean isValid() {
            return left.isValid() || right.isValid();
        }

        public Optional<Valid> findAny() {
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator(), Spliterator.CONCURRENT), false)
                    .filter(Valid.class::isInstance)
                    .map(Valid.class::cast)
                    .findAny();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getValue() {
            return findAny()
                    .map(v -> (T) v.getValue())
                    .orElseThrow(() -> new IllegalStateException("Value does not exist"));
        }

        @NonNull
        @Override
        public Iterator<Validated> iterator() {
            return Stream.concat(
                    stream(left.spliterator(), false),
                    stream(right.spliterator(), false)
            ).iterator();
        }
    }

    class Both implements Validated {
        private final Validated left;
        private final Validated right;

        public Both(Validated left, Validated right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean isValid() {
            return left.isValid() && right.isValid();
        }

        @Override
        public <T> T getValue() {
            throw new IllegalStateException("Cannot select a single value from two or more Validated");
        }

        @NonNull
        @Override
        public Iterator<Validated> iterator() {
            return Stream.concat(
                    stream(left.spliterator(), false),
                    stream(right.spliterator(), false)
            ).iterator();
        }
    }
}
