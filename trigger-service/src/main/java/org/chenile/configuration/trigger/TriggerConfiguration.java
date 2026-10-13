package org.chenile.configuration.trigger;

import org.chenile.trigger.configuration.ChenileTriggerConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Registers trigger dispatch through Chenile's normal configuration package scan. */
@Configuration
@Import(ChenileTriggerConfiguration.class)
public class TriggerConfiguration { }
