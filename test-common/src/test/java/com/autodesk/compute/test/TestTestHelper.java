package com.autodesk.compute.test;

import com.autodesk.compute.test.categories.UnitTests;
import io.vavr.control.Either;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import static com.autodesk.compute.test.TestHelper.makeAnswer;
import static com.autodesk.compute.test.TestHelper.makeNullAnswer;
import static junit.framework.TestCase.fail;

@Category(UnitTests.class)
@Slf4j
public class TestTestHelper {

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testMakeNullAnswer() {
        int count = 3;
        Answer<Void> answers = makeNullAnswer(Either.right(count), Either.left(new Exception("thrown exception")));

        // The answers should return count number of null and then exception
        try {
            for (int i = 0; i < count; i++) {
                Assert.assertNull(answers.answer(null));
            }
        } catch (Throwable throwable) {
            fail("Exception not expected");
        }

        try {
            Assert.assertNull(answers.answer(null));
            fail("Exception expected");
        } catch (Throwable throwable) {
        }
    }

    @Test
    public void testMakeAnswer() {
        Object theObject = new Object();
        Answer<Object> answers = makeAnswer(Either.right(theObject), Either.left(new Exception("thrown exception")));

        // The answers should return the object first and then exception
        try {
            Assert.assertEquals(theObject, answers.answer(null));
        } catch (Throwable throwable) {
            fail("Exception not expected");
        }

        try {
            Assert.assertNull(answers.answer(null));
            fail("Exception expected");
        } catch (Throwable throwable) {
        }

        answers = makeAnswer(theObject, theObject, theObject);
        try{
            for (int i = 0; i < 3; i++) {
                Assert.assertEquals(theObject, answers.answer(null));
            }
        } catch (Throwable throwable) {
            fail("Exception not expected");
        }
    }
}
