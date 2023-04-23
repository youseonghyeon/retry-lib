package retry;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class RetryAutoConfig {

    @Bean
    public RetryableAspect retryableAspect() {
        return new RetryableAspect();
    }
}
