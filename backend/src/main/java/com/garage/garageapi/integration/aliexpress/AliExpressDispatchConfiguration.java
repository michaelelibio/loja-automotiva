package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.fulfillment.OrderFulfillmentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="aliexpress.orders.auto-dispatch-enabled",havingValue="true")
public class AliExpressDispatchConfiguration {
    @Bean(name="aliExpressDispatchExecutor")
    ThreadPoolTaskExecutor aliExpressDispatchExecutor() {
        var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(1);executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);executor.setThreadNamePrefix("aliexpress-dispatch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);executor.setAwaitTerminationSeconds(35);
        return executor;
    }
    @Bean AliExpressDispatchWorker aliExpressDispatchWorker(OrderFulfillmentRepository repository,
            AliExpressOrderService service,@org.springframework.beans.factory.annotation.Qualifier("aliExpressDispatchExecutor") ThreadPoolTaskExecutor executor,
            AliExpressAutomationPolicy automation) {
        return new AliExpressDispatchWorker(repository,service,executor,automation);
    }
}
