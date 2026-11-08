package org.openldes.ldesadmin.model.sandbox;

/** Latest bounded result of a running source; no source credentials are returned. */
public record LivePipelineRun(boolean active, long received, PipelineRun latest, String error) {}
