package com.ivan.nexus.application.alert;

import com.ivan.nexus.domain.alert.AlertRule;

import java.util.List;

public interface AlertRuleStore {
    List<AlertRule> findEnabled();
}
