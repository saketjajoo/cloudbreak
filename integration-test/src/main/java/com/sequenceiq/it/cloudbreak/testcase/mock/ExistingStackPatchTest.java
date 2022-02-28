package com.sequenceiq.it.cloudbreak.testcase.mock;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.testng.annotations.Test;

import com.sequenceiq.it.cloudbreak.CloudbreakClient;
import com.sequenceiq.it.cloudbreak.assertion.Assertion;
import com.sequenceiq.it.cloudbreak.client.DistroXTestClient;
import com.sequenceiq.it.cloudbreak.client.SdxTestClient;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.MockedTestContext;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.distrox.DistroXTestDto;
import com.sequenceiq.it.cloudbreak.exception.TestFailException;

public class ExistingStackPatchTest extends AbstractMockTest {

    private static final String STACK_PATCH_RESULT_TAG_KEY = "stack-patch-result";

    private static final String STACK_PATCH_RESULT_TAG_VALUE_SUCCESS = "success";

    private static final String STACK_PATCH_RESULT_TAG_VALUE_FAILURE = "failure";

    private static final String STACK_PATCH_RESULT_TAG_VALUE_SKIP = "skip";

    private static final String STACK_PATCH_TRIES_TAG = "stack-patch-tries";

    @Inject
    private SdxTestClient sdxTestClient;

    @Inject
    private DistroXTestClient distroXClient;

    @Override
    protected void setupTest(TestContext testContext) {
        super.setupTest(testContext);
        createDatalake(testContext);
    }

    @Test(dataProvider = TEST_CONTEXT_WITH_MOCK)
    @Description(
            given = "there is a running datahub",
            when = "the datahub stack needs the mock stack patch",
            then = "stack should be patched")
    public void testSuccessfulStackPatch(MockedTestContext testContext) {
        testContext
                .given(DistroXTestDto.class)
                    .addTags(Map.of(STACK_PATCH_RESULT_TAG_KEY, STACK_PATCH_RESULT_TAG_VALUE_SUCCESS))
                .when(distroXClient.create())
                .await(START_IN_PROGRESS)
                .when(distroXClient.get())
                .then((tc, testDto, client) -> {
                    if (!testDto.getResponse().getStatusReason().equals("Stack patch applied")) {
                        throw new TestFailException("Mock stack patcher did not apply stack patch");
                    }
                    return testDto;
                })
                .validate();
    }

    @Test(dataProvider = TEST_CONTEXT_WITH_MOCK)
    @Description(
            given = "there is a running datahub",
            when = "the datahub stack needs the mock stack patch, but it fails to apply",
            then = "stack patch should be retried")
    public void testRetryFailedPatch(MockedTestContext testContext) {
        testContext
                .given(DistroXTestDto.class)
                    .addTags(Map.of(STACK_PATCH_RESULT_TAG_KEY, STACK_PATCH_RESULT_TAG_VALUE_FAILURE))
                .when(distroXClient.create())
                .await(START_IN_PROGRESS)
                .then(sleep())
                .when(distroXClient.get())
                .then(validateStackPatchRetries())
                .validate();
    }

    @Test(dataProvider = TEST_CONTEXT_WITH_MOCK)
    @Description(
            given = "there is a running datahub",
            when = "the datahub stack needs the mock stack patch, but the apply is not successful",
            then = "stack patch should be retried")
    public void testRetrySkippedPatch(MockedTestContext testContext) {
        testContext
                .given(DistroXTestDto.class)
                    .addTags(Map.of(STACK_PATCH_RESULT_TAG_KEY, STACK_PATCH_RESULT_TAG_VALUE_SKIP))
                .when(distroXClient.create())
                .await(START_IN_PROGRESS)
                .then(sleep())
                .when(distroXClient.get())
                .then(validateStackPatchRetries())
                .validate();
    }

    private Assertion<DistroXTestDto, CloudbreakClient> sleep() {
        return (tc, testDto, client) -> {
            // mock stack patch is retried every minute, so wait a few minutes to allow time for retries
            TimeUnit.MINUTES.sleep(3);
            return testDto;
        };
    }

    private Assertion<DistroXTestDto, CloudbreakClient> validateStackPatchRetries() {
        return (tc, testDto, client) -> {
            String tryCountTag = testDto.getResponse().getTagValue(STACK_PATCH_TRIES_TAG);
            if (tryCountTag == null) {
                throw new TestFailException("Mock stack patcher did not apply stack patch");
            }
            int tryCount = Integer.parseInt(tryCountTag);
            if (tryCount <= 1) {
                throw new TestFailException("Mock stack patcher did not retry stack patch. Try count: " + tryCount);
            }
            return testDto;
        };
    }
}
