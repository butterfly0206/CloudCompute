package com.autodesk.compute.test;

import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.exception.IComputeException;
import lombok.experimental.UtilityClass;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

@UtilityClass
public class Matchers {

    public static class MessageMatcher extends BaseMatcher<Exception> {
        private final String contained;

        public MessageMatcher(final String contained) {
            this.contained = contained;
        }

        @Override
        public boolean matches(final Object item) {
            return ((Exception) item).getMessage().contains(contained);
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("Exception message matches " + contained);
        }

        @Override
        public void describeMismatch(final Object item, final Description description) {
            description.appendText(item + "does not match: " + contained);
        }
    }

    public static class ExceptionCodeMatcher extends BaseMatcher<Exception> {
        ComputeErrorCodes expectedCode;

        public ExceptionCodeMatcher(final ComputeErrorCodes expectedCode) {
            this.expectedCode = expectedCode;
        }

        @Override
        public boolean matches(final Object o) {
            return ((IComputeException) o).getCode() == expectedCode;
        }

        @Override
        public void describeTo(final Description description) {
            if (expectedCode != null) {
                description.appendText(String.format("Matches Compute exception code `%s`.",
                        expectedCode.getDescription()));
            }
        }

        @Override
        public void describeMismatch(final Object item, final Description description) {
            description.appendText(item + "does not match: " + expectedCode);
        }
    }
}
