package com.qinghe.marketing.entitlement;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimIssueRabbitListenerTest {

    @Test
    void shouldAckOnlyAfterIssueServiceReturns() throws Exception {
        BenefitIssueCommandCodec codec = mock(BenefitIssueCommandCodec.class);
        ClaimIssueService issues = mock(ClaimIssueService.class);
        ClaimFailureService failures = mock(ClaimFailureService.class);
        BenefitIssueCommand command = command();
        when(codec.decode("{}")) .thenReturn(command);
        Channel channel = mock(Channel.class);
        ClaimIssueRabbitListener listener = new ClaimIssueRabbitListener(codec, issues, failures);

        listener.issue(message(), channel);

        verify(issues).issue(command);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void shouldLetContainerRetryAndDeadLetterWhenIssueFails() throws Exception {
        BenefitIssueCommandCodec codec = mock(BenefitIssueCommandCodec.class);
        ClaimIssueService issues = mock(ClaimIssueService.class);
        ClaimFailureService failures = mock(ClaimFailureService.class);
        BenefitIssueCommand command = command();
        when(codec.decode("{}")) .thenReturn(command);
        doThrow(new IllegalStateException("database unavailable")).when(issues).issue(command);
        Channel channel = mock(Channel.class);
        ClaimIssueRabbitListener listener = new ClaimIssueRabbitListener(codec, issues, failures);

        assertThrows(IllegalStateException.class, () -> listener.issue(message(), channel));

        verify(channel, never()).basicAck(7L, false);
        verify(channel, never()).basicNack(7L, false, false);
    }

    @Test
    void shouldRequeueDeadLetterUntilCompensationCompletes() throws Exception {
        BenefitIssueCommandCodec codec = mock(BenefitIssueCommandCodec.class);
        ClaimIssueService issues = mock(ClaimIssueService.class);
        ClaimFailureService failures = mock(ClaimFailureService.class);
        BenefitIssueCommand command = command();
        when(codec.decode("{}")) .thenReturn(command);
        Channel channel = mock(Channel.class);
        ClaimIssueRabbitListener listener = new ClaimIssueRabbitListener(codec, issues, failures);

        listener.compensateDeadLetter(message(), channel);
        verify(failures).fail("EVT-1", "CLM-1", "ISSUE_RETRY_EXHAUSTED");
        verify(channel).basicAck(7L, false);

        doThrow(new IllegalStateException("redis unavailable")).when(failures)
                .fail("EVT-1", "CLM-1", "ISSUE_RETRY_EXHAUSTED");
        listener.compensateDeadLetter(message(), channel);
        verify(channel).basicNack(7L, false, true);
    }

    @Test
    void shouldParkMalformedDeadLetterInsteadOfLoopingForever() throws Exception {
        BenefitIssueCommandCodec codec = mock(BenefitIssueCommandCodec.class);
        ClaimIssueService issues = mock(ClaimIssueService.class);
        ClaimFailureService failures = mock(ClaimFailureService.class);
        when(codec.decode("{}")).thenThrow(new IllegalArgumentException("malformed"));
        Channel channel = mock(Channel.class);
        ClaimIssueRabbitListener listener = new ClaimIssueRabbitListener(codec, issues, failures);

        listener.compensateDeadLetter(message(), channel);

        verify(channel).basicNack(7L, false, false);
        verify(failures, never()).fail("EVT-1", "CLM-1", "ISSUE_RETRY_EXHAUSTED");
    }

    private static Message message() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7L);
        return new Message("{}".getBytes(StandardCharsets.UTF_8), properties);
    }

    private static BenefitIssueCommand command() {
        return new BenefitIssueCommand("EVT-1", "CLM-1", "RSV-1",
                10L, 20L, "request-001");
    }
}
