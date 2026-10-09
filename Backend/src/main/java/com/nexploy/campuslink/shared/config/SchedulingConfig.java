package com.nexploy.campuslink.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} support app-wide. Currently drives the notification reminder
 * jobs ({@code NotificationReminderService}); scheduled tasks use off-peak cron expressions and are
 * idempotent, so they are safe to run on every application instance.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
