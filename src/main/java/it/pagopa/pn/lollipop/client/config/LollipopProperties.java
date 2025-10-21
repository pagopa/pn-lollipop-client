package it.pagopa.pn.lollipop.client.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.util.ResourceUtils;

import javax.annotation.PostConstruct;

@Configuration
@PropertySource(ResourceUtils.CLASSPATH_URL_PREFIX + "application-lollipop.properties") //can be overridden by application.properties
@Data
@ConfigurationProperties(prefix = "lollipop.core.config")
@Slf4j
public class LollipopProperties {
    private String whiteList;

    @PostConstruct
    public void logLoadedProperty() {
        log.info("Loaded whitelist from LollipopProperties: [{}]", whiteList);
    }

}
