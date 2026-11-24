package com.auradev.erp.dashboard.dto;

/** A concise operational task that can be surfaced on the overview. */
public record DashboardAction(String type, String title, String detail, String tone, String target) {}
