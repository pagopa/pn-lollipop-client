package it.pagopa.pn.lollipop.client.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.util.ResourceUtils;

@Configuration
@PropertySource(ResourceUtils.CLASSPATH_URL_PREFIX + "application-lollipop.properties") //can be overridden by application.properties
public class LollipopProperties {
}
