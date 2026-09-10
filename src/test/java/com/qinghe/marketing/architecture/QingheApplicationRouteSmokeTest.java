package com.qinghe.marketing.architecture;

import com.qinghe.marketing.QingheMarketingApplication;
import com.qinghe.marketing.campaign.CampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        classes = QingheMarketingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfiguration",
                "spring.rabbitmq.dynamic=false",
                "spring.rabbitmq.listener.simple.auto-startup=false",
                "qinghe.campaign.state-scan-delay-ms=3600000"
        }
)
class QingheApplicationRouteSmokeTest {

    @Autowired
    private TestRestTemplate rest;

    @MockBean
    private DataSource dataSource;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private CampaignService campaignService;

    @Test
    void shouldStartFromQingheApplicationAndExposeQingheRoute() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/member/entitlements", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void shouldNotExposeLegacyBlogRouteByDefault() {
        ResponseEntity<String> response = rest.getForEntity("/blog/hot", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
