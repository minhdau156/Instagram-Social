package com.instagram.domain.model;

import java.util.UUID;

public class Permission {

    private UUID id;
    private PermissionName name;
    private String description;

    private Permission() {
    }

    public UUID getId() {
        return id;
    }

    public PermissionName getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Permission permission = new Permission();

        public Builder id(UUID id) {
            permission.id = id;
            return this;
        }

        public Builder name(PermissionName name) {
            permission.name = name;
            return this;
        }

        public Builder description(String description) {
            permission.description = description;
            return this;
        }

        public Permission build() {
            return permission;
        }
    }
}
