package it.pagopa.pn.lollipop.client.config;

import it.pagopa.tech.lollipop.consumer.idp.client.simple.IdpCertSimpleClientConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationProperties(
        prefix = "lollipop.idp.rest.config"
)
@ConfigurationPropertiesScan
public class SpringIdpCertSimpleClientConfig extends IdpCertSimpleClientConfig {}
