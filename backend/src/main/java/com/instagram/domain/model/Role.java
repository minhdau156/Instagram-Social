package com.instagram.domain.model;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class Role {

    private UUID id;
    private RoleName name;
    private String description;
    private boolean system;
    private Set<Permission> permissions;

    private Role() {
    }

    public UUID getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystem() {
        return system;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean grants(PermissionName permission) {
        return permissions.stream().anyMatch(p -> p.getName() == permission);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Role role = new Role();

        public Builder id(UUID id) {
            role.id = id;
            return this;
        }

        public Builder name(RoleName name) {
            role.name = name;
            return this;
        }

        public Builder description(String description) {
            role.description = description;
            return this;
        }

        public Builder system(boolean system) {
            role.system = system;
            return this;
        }

        public Builder permissions(Set<Permission> permissions) {
            role.permissions = permissions;
            return this;
        }

        public Role build() {
            if (role.permissions == null) {
                role.permissions = Collections.emptySet();
            }
            return role;
        }
    }
}
