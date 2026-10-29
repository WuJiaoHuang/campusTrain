package com.campustrain.growthservice;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GrowthApplication {
    public static void main(String[] args) {
        SpringApplication.run(GrowthApplication.class, args);
    }

    /** Spring executor discovers @XxlJob handlers after all Spring singletons are available. */
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(
            @Value("${xxl.job.admin.addresses}") String adminAddresses,
            @Value("${xxl.job.accessToken}") String accessToken,
            @Value("${xxl.job.executor.appname}") String appName,
            @Value("${xxl.job.executor.address:}") String address,
            @Value("${xxl.job.executor.ip:}") String ip,
            @Value("${xxl.job.executor.port}") int port,
            @Value("${xxl.job.executor.logpath}") String logPath,
            @Value("${xxl.job.executor.logretentiondays}") int retentionDays) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appName);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(retentionDays);
        return executor;
    }
}
