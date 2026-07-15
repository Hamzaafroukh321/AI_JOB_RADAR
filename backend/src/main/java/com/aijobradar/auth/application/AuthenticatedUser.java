package com.aijobradar.auth.application;

import java.util.UUID;

public record AuthenticatedUser(
    UUID id, String email, String displayName, String timezone, String locale) {}
