package com.autodesk.compute.test;

import com.pivovarit.function.ThrowingRunnable;
import io.vavr.control.Either;
import lombok.experimental.UtilityClass;
import org.hamcrest.BaseMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.*;

@UtilityClass
public class TestHelper {

    public static String makeString(final Object... objects) {
        final StringBuilder sb = new StringBuilder();
        for (final Object obj : objects) {
            if (obj == null)
                sb.append("null");
            else
                sb.append(obj.toString());
        }
        return sb.toString();
    }

    // Validate that something throws an exception, and validate the exception.
    public void validateThrows(final Class<?> exception, final BaseMatcher<?> matcher, final ThrowingRunnable<Exception> toExecute) {
        try {
            toExecute.run();
            fail("This test should have thrown an exception, but did not");
        } catch (final Exception e) {
            assertEquals(e.getClass(), exception);
            if (matcher != null)
                assertTrue(makeString(e, " did not match: ", matcher), matcher.matches(e));
        }
    }

    // This answer produces either a sequence of nulls, or an exception.
    // The Either.right(Integer) are the number of times to return null.
    // The Either.left(Exception) are the exceptions to throw.
    public Answer<Void> makeNullAnswer(final Either<Exception, Integer>... answers) {
        return new Answer<>() {
            int currentInvocation = 0;
            int currentPositionInList = 0;
            int invocationsOfCurrentPosition = 0;

            @Override
            public Void answer(final InvocationOnMock invocation) throws Throwable {
                final Either<Exception, Integer> currentPos = answers[currentPositionInList];
                ++currentInvocation;
                if (currentPos.isLeft()) {
                    ++currentPositionInList;
                    invocationsOfCurrentPosition = 0;
                    throw currentPos.getLeft();
                } else {
                    if (++invocationsOfCurrentPosition >= currentPos.get()) {
                        invocationsOfCurrentPosition = 0;
                        ++currentPositionInList;
                    }
                    return null;
                }
            }
        };

    }

    // This answer creates a sequence of answers for a mock, either a T,
    // or throwing an exception.
    public <T> Answer<T> makeAnswer(final Either<Exception, T>... answer) {
        return new Answer<>() {
            final Either<Exception, T>[] answers = answer;

            int currentInvocation = 0;

            @Override
            public T answer(final InvocationOnMock invocation) throws Throwable {
                final Either<Exception, T> currentAnswer = answers[currentInvocation++];
                if (currentAnswer.isRight())
                    return currentAnswer.get();
                throw currentAnswer.getLeft();
            }
        };
    }

    // This answer creates a sequence of answers for a mock, either a T,
    // or throwing an exception.
    public <T extends Object> Answer<T> makeAnswer(final T... answer) {
        return new Answer<>() {
            final T[] answers = answer;

            int currentInvocation = 0;

            @Override
            public T answer(final InvocationOnMock invocation) throws Throwable {
                return answers[currentInvocation++];
            }
        };
    }
}
