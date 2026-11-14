package com.amaya.note.auth;
import java.util.UUID;
public record CurrentUser(UUID id, String role) {}
