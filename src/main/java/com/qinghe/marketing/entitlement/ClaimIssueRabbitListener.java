package com.qinghe.marketing.entitlement;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "qinghe.claim.consumer.enabled", havingValue = "true")
public class ClaimIssueRabbitListener {

    private final BenefitIssueCommandCodec codec;
    private final ClaimIssueService issueService;
    private final ClaimFailureService failureService;

    public ClaimIssueRabbitListener(BenefitIssueCommandCodec codec,
                                    ClaimIssueService issueService,
                                    ClaimFailureService failureService) {
        this.codec = codec;
        this.issueService = issueService;
        this.failureService = failureService;
    }

    @RabbitListener(queues = "${qinghe.outbox.publisher.queue:qinghe.claim.issue.v1.queue}",
            containerFactory = "qingheClaimListenerContainerFactory", ackMode = "MANUAL")
    public void issue(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        BenefitIssueCommand command = codec.decode(
                new String(message.getBody(), StandardCharsets.UTF_8));
        issueService.issue(command);
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = "${qinghe.claim.consumer.dead-queue:qinghe.claim.issue.v1.dead.queue}",
            ackMode = "MANUAL")
    public void compensateDeadLetter(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        BenefitIssueCommand command;
        try {
            command = codec.decode(
                    new String(message.getBody(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException malformed) {
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        try {
            failureService.fail(command.eventId(), command.claimNo(), "ISSUE_RETRY_EXHAUSTED");
            channel.basicAck(deliveryTag, false);
        } catch (IllegalArgumentException permanentFailure) {
            channel.basicNack(deliveryTag, false, false);
        } catch (RuntimeException failure) {
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
