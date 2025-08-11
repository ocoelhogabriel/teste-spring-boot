package com.ocoelho.dto;

import java.time.Instant;

public record LogFileDto(String name, long size, Instant lastModified) {}
