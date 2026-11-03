package com.scrumai.config;

import com.scrumai.common.logging.RequestIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {
  @Bean
  public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
    FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new RequestIdFilter());
    registration.addUrlPatterns("/*");
    registration.setOrder(1);
    return registration;
  }
}
